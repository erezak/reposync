package com.erez.reposync.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erez.reposync.AppServices
import com.erez.reposync.data.crypto.SshKeyGenerator
import com.erez.reposync.data.github.GitHubRepo
import com.erez.reposync.data.model.AuthMethod
import com.erez.reposync.data.model.IgnoreRules
import com.erez.reposync.data.model.Profile
import com.erez.reposync.data.model.SyncPolicy
import com.erez.reposync.data.model.SyncPolicyType
import com.erez.reposync.data.model.SyncStatus
import com.erez.reposync.data.repo.SyncRepository
import com.erez.reposync.data.repo.TestConnectionResult
import com.erez.reposync.work.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileEditorViewModel(services: AppServices) : ViewModel() {
    private val profileRepository = services.profileRepository
    private val safRepository = services.safRepository
    private val syncRepository: SyncRepository = services.syncRepository
    private val githubAuthRepository = services.githubAuthRepository
    private val githubRepoRepository = services.githubRepoRepository
    private val sshKeyGenerator = SshKeyGenerator()
    private val workScheduler = WorkScheduler(services.appContext)

    private val _state = MutableStateFlow(ProfileEditorState())
    val state: StateFlow<ProfileEditorState> = _state

    init {
        viewModelScope.launch {
            githubAuthRepository.authState.collect { auth ->
                update {
                    copy(
                        githubAuthenticated = auth.isAuthenticated,
                        githubLogin = auth.userLogin,
                        githubAuthLoading = auth.isLoading,
                        githubAuthError = auth.error,
                        githubRepos = if (auth.isAuthenticated) githubRepos else emptyList(),
                        githubNextPage = if (auth.isAuthenticated) githubNextPage else 1,
                        githubHasNextPage = if (auth.isAuthenticated) githubHasNextPage else false
                    )
                }
            }
        }
    }

    fun loadProfile(profileId: String?) {
        if (profileId.isNullOrBlank()) return
        viewModelScope.launch {
            val profile = profileRepository.getById(profileId) ?: return@launch
            val folderName = runCatching {
                val uri = Uri.parse(profile.targetTreeUri)
                safRepository.getTreeDocument(uri)?.name
            }.getOrNull() ?: ""
            val hasToken = profileRepository.getHttpsToken(profile.id)?.isNotBlank() == true
            _state.update {
                it.fromProfile(profile, profileRepository.getSshPublicKey(profileId), folderName, hasToken)
            }
        }
    }

    fun updateName(value: String) = update { copy(name = value) }
    fun updateRemoteUrl(value: String) = update { copy(remoteUrl = value) }
    fun updateBranch(value: String) = update { copy(branch = value) }
    fun updateAuthMethod(value: AuthMethod) = update { copy(authMethod = value) }
    fun updateHttpsUsername(value: String) = update { copy(httpsUsername = value) }
    fun updateHttpsToken(value: String) = update { copy(httpsToken = value) }
    fun updateAuthorName(value: String) = update { copy(authorName = value) }
    fun updateAuthorEmail(value: String) = update { copy(authorEmail = value) }
    fun updateCommitTemplate(value: String) = update { copy(commitTemplate = value) }
    fun updateIgnorePatterns(value: String) = update { copy(ignorePatterns = value) }
    fun applyIgnorePreset(preset: IgnorePreset) = update {
        copy(ignorePreset = preset.id, ignorePatterns = preset.patterns.joinToString("\n"))
    }
    fun updatePropagateDeletes(value: Boolean) = update { copy(propagateDeletes = value) }
    fun updatePeriodicEnabled(value: Boolean) = update { copy(periodicEnabled = value) }
    fun updateIntervalMinutes(value: Long) = update { copy(intervalMinutes = value) }
    fun updateRequiresUnmetered(value: Boolean) = update { copy(requiresUnmetered = value) }
    fun updateRequiresCharging(value: Boolean) = update { copy(requiresCharging = value) }
    fun updateRequiresBatteryNotLow(value: Boolean) = update { copy(requiresBatteryNotLow = value) }
    fun updateTreeUri(uri: Uri) {
        safRepository.persistTreePermission(uri)
        val name = safRepository.getTreeDocument(uri)?.name ?: ""
        update { copy(targetTreeUri = uri.toString(), targetTreeName = name) }
    }

    fun updateSetupMode(mode: SetupMode) = update { copy(setupMode = mode) }

    fun startGitHubLogin(): String = githubAuthRepository.startLogin()

    fun logoutGitHub() {
        viewModelScope.launch {
            githubAuthRepository.logout()
            update {
                copy(
                    githubRepos = emptyList(),
                    githubNextPage = 1,
                    githubHasNextPage = false
                )
            }
        }
    }

    fun clearGitHubError() {
        githubAuthRepository.clearError()
    }

    fun loadGitHubRepos(reset: Boolean = true) {
        viewModelScope.launch {
            if (!state.value.githubAuthenticated) {
                update { copy(githubReposError = "Login required to load repositories") }
                return@launch
            }
            val nextPage = if (reset) 1 else state.value.githubNextPage
            if (nextPage == null) return@launch
            update {
                copy(
                    githubReposLoading = true,
                    githubReposError = "",
                    githubRepos = if (reset) emptyList() else githubRepos,
                    githubNextPage = if (reset) 1 else githubNextPage,
                    githubHasNextPage = if (reset) false else githubHasNextPage
                )
            }
            try {
                val page = githubRepoRepository.listOwnedRepos(nextPage, GITHUB_PAGE_SIZE)
                update {
                    val merged = if (reset) page.repos else githubRepos + page.repos
                    copy(
                        githubRepos = merged,
                        githubReposLoading = false,
                        githubNextPage = page.nextPage,
                        githubHasNextPage = page.nextPage != null
                    )
                }
            } catch (ex: Exception) {
                update {
                    copy(
                        githubReposLoading = false,
                        githubReposError = ex.message ?: "Failed to load repositories"
                    )
                }
            }
        }
    }

    fun selectGitHubRepo(repo: GitHubRepo) {
        update {
            copy(
                remoteUrl = repo.cloneUrl,
                branch = repo.defaultBranch.ifBlank { "main" },
                name = if (name.isBlank() || name == "Repo") repo.name else name,
                authMethod = AuthMethod.GITHUB_OAUTH,
                httpsUsername = "x-access-token"
            )
        }
    }

    fun saveToken(token: String) {
        if (state.value.hasSavedToken) {
            update { copy(connectionStatus = "Delete existing token first") }
            return
        }
        if (token.isBlank()) {
            update { copy(connectionStatus = "Token is empty") }
            return
        }
        val id = state.value.id
        if (id.isNotBlank()) {
            profileRepository.saveHttpsToken(id, token)
            update { copy(connectionStatus = "Token saved", hasSavedToken = true) }
        } else {
            update { copy(pendingToken = token, httpsToken = token, connectionStatus = "Token staged", hasSavedToken = true) }
        }
    }

    fun deleteToken() {
        val id = state.value.id
        if (id.isNotBlank()) {
            profileRepository.removeHttpsToken(id)
        }
        update { copy(httpsToken = "", pendingToken = "", hasSavedToken = false, connectionStatus = "Token deleted") }
    }

    fun generateSshKey() {
        val id = state.value.id
        val result = sshKeyGenerator.generateEd25519()
        if (id.isNotBlank()) {
            profileRepository.saveSshKeyPair(id, result.privateKeyPkcs8Base64, result.publicKeyOpenSsh)
            update { copy(sshPublicKey = result.publicKeyOpenSsh) }
        } else {
            update { copy(pendingPrivateKey = result.privateKeyPkcs8Base64, sshPublicKey = result.publicKeyOpenSsh) }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            update { copy(isBusy = true, connectionStatus = "Testing connection...") }
            try {
                val profile = buildProfile() ?: return@launch
                if (profile.authMethod == AuthMethod.GITHUB_OAUTH && !state.value.githubAuthenticated) {
                    update { copy(connectionStatus = "GitHub login required") }
                    return@launch
                }
                if (profile.authMethod == AuthMethod.HTTPS_TOKEN && state.value.httpsToken.isBlank() && state.value.pendingToken.isBlank() && !state.value.hasSavedToken) {
                    update { copy(connectionStatus = "HTTPS token is missing") }
                    return@launch
                }
                val tokenToSave = state.value.httpsToken.ifBlank { state.value.pendingToken }
                if (tokenToSave.isNotBlank()) {
                    profileRepository.saveHttpsToken(profile.id, tokenToSave)
                }
                if (state.value.pendingPrivateKey.isNotBlank()) {
                    profileRepository.saveSshKeyPair(profile.id, state.value.pendingPrivateKey, state.value.sshPublicKey)
                }
                when (val result = syncRepository.testConnection(profile)) {
                    is TestConnectionResult.Success -> update { copy(connectionStatus = "Connection OK") }
                    is TestConnectionResult.HostKeyNotTrusted -> update {
                        copy(
                            connectionStatus = "Host key not trusted",
                            pendingHostKey = result.info
                        )
                    }
                    is TestConnectionResult.Failure -> update { copy(connectionStatus = result.message) }
                }
            } finally {
                update { copy(isBusy = false) }
            }
        }
    }

    fun trustPendingHostKey() {
        val info = state.value.pendingHostKey ?: return
        viewModelScope.launch {
            syncRepository.trustHostKey(info)
            update { copy(pendingHostKey = null, connectionStatus = "Host key trusted") }
        }
    }

    fun saveProfile(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val profile = buildProfile() ?: return@launch
            profileRepository.upsert(profile)
            if (state.value.pendingToken.isNotBlank()) {
                profileRepository.saveHttpsToken(profile.id, state.value.pendingToken)
            }
            if (state.value.pendingPrivateKey.isNotBlank()) {
                profileRepository.saveSshKeyPair(profile.id, state.value.pendingPrivateKey, state.value.sshPublicKey)
            }
            workScheduler.schedule(profile)
            onComplete(profile.id)
        }
    }

    fun setupRepository(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val current = state.value
            if (current.targetTreeUri.isBlank()) {
                update { copy(connectionStatus = "Select a target folder first.") }
                return@launch
            }
            if (current.remoteUrl.isBlank()) {
                update { copy(connectionStatus = "Enter a remote URL.") }
                return@launch
            }
            if (current.authMethod == AuthMethod.GITHUB_OAUTH && !current.githubAuthenticated) {
                update { copy(connectionStatus = "GitHub login required") }
                return@launch
            }
            if (current.authMethod == AuthMethod.HTTPS_TOKEN && current.httpsToken.isBlank() && current.pendingToken.isBlank() && !current.hasSavedToken) {
                update { copy(connectionStatus = "HTTPS token is missing") }
                return@launch
            }
            update { copy(isBusy = true, connectionStatus = "Setting up repository...") }
            try {
                val profile = buildProfile() ?: return@launch
                profileRepository.upsert(profile)
                val tokenToSave = state.value.httpsToken.ifBlank { state.value.pendingToken }
                if (tokenToSave.isNotBlank()) {
                    profileRepository.saveHttpsToken(profile.id, tokenToSave)
                }
                if (state.value.pendingPrivateKey.isNotBlank()) {
                    profileRepository.saveSshKeyPair(profile.id, state.value.pendingPrivateKey, state.value.sshPublicKey)
                }
                val onStep: (String) -> Unit = { step ->
                    update { copy(connectionStatus = "Setup: $step") }
                }
                val result = when (state.value.setupMode) {
                    SetupMode.CLONE -> syncRepository.setupClone(profile, onStep)
                    SetupMode.IMPORT -> syncRepository.setupImport(profile, onStep)
                }
                workScheduler.schedule(profile)
                if (result.status == SyncStatus.SUCCESS) {
                    update { copy(connectionStatus = "Setup complete") }
                    onComplete(profile.id)
                } else {
                    update {
                        copy(connectionStatus = "Setup failed: ${result.errorMessage ?: result.status.name}")
                    }
                }
            } finally {
                update { copy(isBusy = false) }
            }
        }
    }

    fun deleteProfile(deleteContent: Boolean, onComplete: () -> Unit) {
        val profileId = state.value.id
        if (profileId.isBlank()) return
        viewModelScope.launch {
            syncRepository.deleteProfile(profileId, deleteContent)
            onComplete()
        }
    }

    private fun buildProfile(): Profile? {
        val current = state.value
        if (current.targetTreeUri.isBlank()) return null
        val authUsername = if (current.authMethod == AuthMethod.GITHUB_OAUTH) {
            "x-access-token"
        } else {
            current.httpsUsername
        }
        return Profile(
            id = if (current.id.isBlank()) java.util.UUID.randomUUID().toString() else current.id,
            name = current.name.ifBlank { "Repo" },
            targetTreeUri = current.targetTreeUri,
            remoteUrl = current.remoteUrl,
            branch = current.branch.ifBlank { "main" },
            authMethod = current.authMethod,
            httpsUsername = authUsername.ifBlank { "token" },
            authorName = current.authorName.ifBlank { "RepoSync" },
            authorEmail = current.authorEmail.ifBlank { "reposync@local" },
            commitMessageTemplate = current.commitTemplate.ifBlank { "Sync <timestamp> (<device>)" },
            propagateDeletes = current.propagateDeletes,
            syncPolicy = if (current.periodicEnabled) {
                SyncPolicy(
                    SyncPolicyType.PERIODIC,
                    intervalMinutes = current.intervalMinutes,
                    requiresUnmetered = current.requiresUnmetered,
                    requiresCharging = current.requiresCharging,
                    requiresBatteryNotLow = current.requiresBatteryNotLow
                )
            } else {
                SyncPolicy(SyncPolicyType.MANUAL)
            },
            ignoreRules = IgnoreRules(
                patterns = current.ignorePatterns.lines().filter { it.isNotBlank() },
                preset = current.ignorePreset
            )
        )
    }

    private fun update(block: ProfileEditorState.() -> ProfileEditorState) {
        _state.update { it.block() }
    }
}

data class ProfileEditorState(
    val id: String = "",
    val name: String = "",
    val targetTreeUri: String = "",
    val targetTreeName: String = "",
    val remoteUrl: String = "",
    val branch: String = "main",
    val authMethod: AuthMethod = AuthMethod.GITHUB_OAUTH,
    val httpsUsername: String = "token",
    val httpsToken: String = "",
    val hasSavedToken: Boolean = false,
    val githubAuthenticated: Boolean = false,
    val githubLogin: String = "",
    val githubAuthLoading: Boolean = false,
    val githubAuthError: String = "",
    val githubRepos: List<GitHubRepo> = emptyList(),
    val githubReposLoading: Boolean = false,
    val githubReposError: String = "",
    val githubNextPage: Int? = 1,
    val githubHasNextPage: Boolean = false,
    val authorName: String = "",
    val authorEmail: String = "",
    val commitTemplate: String = "Sync <timestamp> (<device>)",
    val ignorePatterns: String = "",
    val ignorePreset: String? = null,
    val propagateDeletes: Boolean = false,
    val periodicEnabled: Boolean = false,
    val intervalMinutes: Long = 60,
    val requiresUnmetered: Boolean = false,
    val requiresCharging: Boolean = false,
    val requiresBatteryNotLow: Boolean = false,
    val setupMode: SetupMode = SetupMode.CLONE,
    val pendingToken: String = "",
    val pendingPrivateKey: String = "",
    val sshPublicKey: String = "",
    val connectionStatus: String = "",
    val pendingHostKey: com.erez.reposync.data.git.SshHostKeyFetcher.HostKeyInfo? = null,
    val isBusy: Boolean = false
) {
    fun fromProfile(profile: Profile, sshPublicKey: String?, treeName: String, hasSavedToken: Boolean): ProfileEditorState {
        return copy(
            id = profile.id,
            name = profile.name,
            targetTreeUri = profile.targetTreeUri,
            targetTreeName = treeName,
            remoteUrl = profile.remoteUrl,
            branch = profile.branch,
            authMethod = profile.authMethod,
            httpsUsername = profile.httpsUsername,
            httpsToken = "",
            hasSavedToken = hasSavedToken,
            authorName = profile.authorName,
            authorEmail = profile.authorEmail,
            commitTemplate = profile.commitMessageTemplate,
            ignorePatterns = profile.ignoreRules.patterns.joinToString("\n"),
            ignorePreset = profile.ignoreRules.preset,
            propagateDeletes = profile.propagateDeletes,
            periodicEnabled = profile.syncPolicy.type == SyncPolicyType.PERIODIC,
            intervalMinutes = profile.syncPolicy.intervalMinutes ?: 60,
            requiresUnmetered = profile.syncPolicy.requiresUnmetered,
            requiresCharging = profile.syncPolicy.requiresCharging,
            requiresBatteryNotLow = profile.syncPolicy.requiresBatteryNotLow,
            sshPublicKey = sshPublicKey ?: ""
        )
    }
}

enum class SetupMode {
    CLONE,
    IMPORT
}

enum class IgnorePreset(val id: String, val label: String, val patterns: List<String>) {
    GENERIC(
        id = "generic",
        label = "Generic",
        patterns = listOf(
            "*.tmp",
            "*.swp",
            ".DS_Store",
            "Thumbs.db",
            ".Trash",
            "~$*"
        )
    ),
    OBSIDIAN(
        id = "obsidian",
        label = "Obsidian",
        patterns = listOf(
            ".obsidian/workspace",
            ".obsidian/cache",
            ".obsidian/trash",
            ".obsidian/graph.json",
            ".obsidian/plugins/*/node_modules"
        )
    )
}

private const val GITHUB_PAGE_SIZE = 30
