package com.erez.reposync.data.git

import android.content.Context
import com.erez.reposync.data.crypto.CryptoStore
import com.erez.reposync.data.github.GitHubAuthStore
import com.erez.reposync.data.model.AuthMethod
import com.erez.reposync.data.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PullResult
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.SshSessionFactory
import java.io.File
import java.time.Instant

class GitClient(
    private val context: Context,
    private val cryptoStore: CryptoStore,
    private val githubAuthStore: GitHubAuthStore,
    private val knownHostsStore: KnownHostsStore = KnownHostsStore(context)
) {

    suspend fun cloneRepository(profile: Profile, targetDir: File): Git = withContext(Dispatchers.IO) {
        val clone = Git.cloneRepository()
            .setURI(profile.remoteUrl)
            .setDirectory(targetDir)
            .setBranch(profile.branch)
            .setCredentialsProvider(credentialsProviderFor(profile))
        withSshIfNeeded(profile) { clone.call() }
    }

    suspend fun initRepository(targetDir: File, branch: String): Git = withContext(Dispatchers.IO) {
        val git = Git.init().setDirectory(targetDir).call()
        git.repository.config.setString("init", null, "defaultBranch", branch)
        git.repository.config.save()
        git
    }

    suspend fun openRepository(targetDir: File): Git = withContext(Dispatchers.IO) {
        val repo: Repository = FileRepositoryBuilder()
            .setGitDir(File(targetDir, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        Git(repo)
    }

    suspend fun setRemote(git: Git, url: String) = withContext(Dispatchers.IO) {
        val config = git.repository.config
        config.setString("remote", "origin", "url", url)
        config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
        config.save()
    }

    suspend fun stageAll(git: Git) = withContext(Dispatchers.IO) {
        git.add().addFilepattern(".").call()
    }

    suspend fun commitIfNeeded(
        git: Git,
        authorName: String,
        authorEmail: String,
        messageTemplate: String
    ): Boolean = withContext(Dispatchers.IO) {
        val status = git.status().call()
        if (status.hasUncommittedChanges()) {
            val message = messageTemplate
                .replace("<timestamp>", Instant.now().toString())
                .replace("<device>", android.os.Build.MODEL ?: "Android")
            git.commit()
                .setMessage(message)
                .setAuthor(PersonIdent(authorName, authorEmail))
                .call()
            true
        } else {
            false
        }
    }

    suspend fun pullRebase(git: Git, profile: Profile): PullResult = withContext(Dispatchers.IO) {
        val pull = git.pull()
            .setRebase(true)
            .setCredentialsProvider(credentialsProviderFor(profile))
        withSshIfNeeded(profile) { pull.call() }
    }

    suspend fun push(git: Git, profile: Profile) = withContext(Dispatchers.IO) {
        val push = git.push()
            .setCredentialsProvider(credentialsProviderFor(profile))
        withSshIfNeeded(profile) { push.call() }
    }

    suspend fun fetch(git: Git, profile: Profile) = withContext(Dispatchers.IO) {
        val fetch = git.fetch()
            .setCredentialsProvider(credentialsProviderFor(profile))
        withSshIfNeeded(profile) { fetch.call() }
    }

    suspend fun checkoutBranch(git: Git, branch: String) = withContext(Dispatchers.IO) {
        val exists = git.repository.findRef("refs/heads/$branch") != null
        if (exists) {
            git.checkout().setName(branch).call()
        } else {
            git.checkout().setCreateBranch(true).setName(branch).call()
        }
    }

    suspend fun lsRemote(profile: Profile): Map<String, String> = withContext(Dispatchers.IO) {
        val cmd = Git.lsRemoteRepository()
            .setRemote(profile.remoteUrl)
            .setCredentialsProvider(credentialsProviderFor(profile))
        withSshIfNeeded(profile) { cmd.call() }
            .associate { it.name to it.objectId.name }
    }

    fun getKnownHostsStore(): KnownHostsStore = knownHostsStore

    private fun credentialsProviderFor(profile: Profile): CredentialsProvider? {
        return when (profile.authMethod) {
            AuthMethod.HTTPS_TOKEN -> {
                val token = cryptoStore.getSecret("token_${profile.id}") ?: ""
                UsernamePasswordCredentialsProvider(profile.httpsUsername, token)
            }
            AuthMethod.GITHUB_OAUTH -> {
                val token = githubAuthStore.getAccessToken() ?: ""
                UsernamePasswordCredentialsProvider("x-access-token", token)
            }
            AuthMethod.SSH_KEY -> null
        }
    }

    private fun <T> withSshIfNeeded(profile: Profile, action: () -> T): T {
        if (profile.authMethod != AuthMethod.SSH_KEY) return action()
        val privateKey = cryptoStore.getSecret("ssh_priv_${profile.id}") ?: ""
        val publicKey = cryptoStore.getSecret("ssh_pub_${profile.id}") ?: ""
        val wrappedFactory = RepoSyncSshSessionFactory.create(context, privateKey, publicKey, knownHostsStore)
        val previous = SshSessionFactory.getInstance()
        return try {
            SshSessionFactory.setInstance(wrappedFactory.factory)
            action()
        } finally {
            SshSessionFactory.setInstance(previous)
            wrappedFactory.close()
        }
    }

    fun validateSshKnownHost(profile: Profile) {
        if (profile.authMethod != AuthMethod.SSH_KEY) return
        val host = parseRemoteHost(profile.remoteUrl)
        if (host == null || !knownHostsStore.isTrusted(host.host, host.port)) {
            throw TransportException("SSH host key not trusted. Run Test Connection to trust host key.")
        }
    }

    fun repoDirFor(profileId: String): File {
        return File(context.filesDir, "repos/$profileId")
    }

    fun ensureRepoDir(profileId: String): File {
        val dir = repoDirFor(profileId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun hasRepo(profileId: String): Boolean {
        val dir = repoDirFor(profileId)
        return File(dir, ".git").exists()
    }

    fun parseRemoteHost(remoteUrl: String): HostAndPort? {
        return try {
            val uri = URIish(remoteUrl)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 22
            HostAndPort(host, port)
        } catch (ex: Exception) {
            if (remoteUrl.contains(":") && remoteUrl.contains("@")) {
                val hostPart = remoteUrl.substringAfter("@").substringBefore(":")
                HostAndPort(hostPart, 22)
            } else {
                null
            }
        }
    }

    data class HostAndPort(val host: String, val port: Int)
}
