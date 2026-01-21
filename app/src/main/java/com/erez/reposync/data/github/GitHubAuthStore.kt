package com.erez.reposync.data.github

import com.erez.reposync.data.crypto.CryptoStore

class GitHubAuthStore(private val cryptoStore: CryptoStore) {
    fun saveToken(token: GitHubToken) {
        cryptoStore.putSecret(GitHubAuthKeys.ACCESS_TOKEN, token.accessToken)
        cryptoStore.putSecret(GitHubAuthKeys.TOKEN_TYPE, token.tokenType)
        cryptoStore.putSecret(GitHubAuthKeys.SCOPE, token.scope)
        token.refreshToken?.let { cryptoStore.putSecret(GitHubAuthKeys.REFRESH_TOKEN, it) }
        token.expiresAtEpochSeconds?.let { cryptoStore.putSecret(GitHubAuthKeys.EXPIRES_AT, it.toString()) }
    }

    fun getAccessToken(): String? = cryptoStore.getSecret(GitHubAuthKeys.ACCESS_TOKEN)

    fun getUserLogin(): String? = cryptoStore.getSecret(GitHubAuthKeys.USER_LOGIN)

    fun saveUserLogin(login: String) {
        cryptoStore.putSecret(GitHubAuthKeys.USER_LOGIN, login)
    }

    fun clearToken() {
        cryptoStore.removeSecret(GitHubAuthKeys.ACCESS_TOKEN)
        cryptoStore.removeSecret(GitHubAuthKeys.REFRESH_TOKEN)
        cryptoStore.removeSecret(GitHubAuthKeys.EXPIRES_AT)
        cryptoStore.removeSecret(GitHubAuthKeys.TOKEN_TYPE)
        cryptoStore.removeSecret(GitHubAuthKeys.SCOPE)
        cryptoStore.removeSecret(GitHubAuthKeys.USER_LOGIN)
    }

    fun saveOauthState(state: String) {
        cryptoStore.putSecret(GitHubAuthKeys.OAUTH_STATE, state)
    }

    fun getOauthState(): String? = cryptoStore.getSecret(GitHubAuthKeys.OAUTH_STATE)

    fun clearOauthState() {
        cryptoStore.removeSecret(GitHubAuthKeys.OAUTH_STATE)
    }

    fun savePkceVerifier(verifier: String) {
        cryptoStore.putSecret(GitHubAuthKeys.PKCE_VERIFIER, verifier)
    }

    fun getPkceVerifier(): String? = cryptoStore.getSecret(GitHubAuthKeys.PKCE_VERIFIER)

    fun clearPkceVerifier() {
        cryptoStore.removeSecret(GitHubAuthKeys.PKCE_VERIFIER)
    }
}
