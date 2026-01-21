package com.erez.reposync.data.github

import android.net.Uri
import android.util.Base64
import com.erez.reposync.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

class GitHubAuthRepository(
    private val authStore: GitHubAuthStore,
    private val api: GitHubApi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow(GitHubAuthState())
    val authState: StateFlow<GitHubAuthState> = _authState.asStateFlow()

    init {
        val token = authStore.getAccessToken()
        if (!token.isNullOrBlank()) {
            _authState.value = _authState.value.copy(
                isAuthenticated = true,
                userLogin = authStore.getUserLogin().orEmpty()
            )
            scope.launch {
                refreshUser(token)
            }
        }
    }

    fun startLogin(): String {
        val state = UUID.randomUUID().toString()
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        authStore.saveOauthState(state)
        authStore.savePkceVerifier(verifier)
        val scope = "repo read:user"
        return "https://github.com/login/oauth/authorize" +
            "?client_id=$CLIENT_ID" +
            "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
            "&scope=${Uri.encode(scope)}" +
            "&state=$state" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"
    }

    suspend fun handleRedirect(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (uri.scheme != REDIRECT_SCHEME || uri.host != REDIRECT_HOST) return@withContext false
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val desc = uri.getQueryParameter("error_description")
                ?: uri.getQueryParameter("error_uri")
            clearOAuthTemp()
            val message = if (!desc.isNullOrBlank()) "$error: $desc" else error
            setError("GitHub login failed: $message")
            return@withContext true
        }
        val code = uri.getQueryParameter("code") ?: return@withContext false
        val state = uri.getQueryParameter("state") ?: ""
        val expectedState = authStore.getOauthState()
        if (expectedState.isNullOrBlank() || expectedState != state) {
            clearOAuthTemp()
            setError("GitHub login failed: invalid state")
            return@withContext true
        }
        val verifier = authStore.getPkceVerifier()
        if (verifier.isNullOrBlank()) {
            clearOAuthTemp()
            setError("GitHub login failed: missing verifier")
            return@withContext true
        }
        setLoading(true)
        return@withContext try {
            val token = api.exchangeCodeForToken(CLIENT_ID, CLIENT_SECRET, code, REDIRECT_URI, verifier)
            authStore.saveToken(token)
            clearOAuthTemp()
            val user = api.getUser(token.accessToken)
            authStore.saveUserLogin(user.login)
            _authState.value = GitHubAuthState(
                isAuthenticated = true,
                userLogin = user.login,
                isLoading = false,
                error = ""
            )
            true
        } catch (ex: Exception) {
            clearOAuthTemp()
            setError(ex.message ?: "GitHub login failed")
            true
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        authStore.clearToken()
        _authState.value = GitHubAuthState()
    }

    fun clearError() {
        _authState.value = _authState.value.copy(error = "")
    }

    private fun setLoading(loading: Boolean) {
        _authState.value = _authState.value.copy(isLoading = loading, error = "")
    }

    private fun setError(message: String) {
        _authState.value = _authState.value.copy(isLoading = false, error = message)
    }

    private fun clearOAuthTemp() {
        authStore.clearOauthState()
        authStore.clearPkceVerifier()
    }

    private suspend fun refreshUser(accessToken: String) {
        try {
            val user = api.getUser(accessToken)
            authStore.saveUserLogin(user.login)
            _authState.value = _authState.value.copy(userLogin = user.login)
        } catch (_: Exception) {
            // Ignore refresh errors; keep token as-is.
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    companion object {
        const val CLIENT_ID = "Ov23liybIFoUZb8XWJq6"
        const val REDIRECT_URI = "reposync://oauth"
        const val REDIRECT_SCHEME = "reposync"
        const val REDIRECT_HOST = "oauth"
        val CLIENT_SECRET: String = BuildConfig.GITHUB_CLIENT_SECRET
    }
}

data class GitHubAuthState(
    val isAuthenticated: Boolean = false,
    val userLogin: String = "",
    val isLoading: Boolean = false,
    val error: String = ""
)
