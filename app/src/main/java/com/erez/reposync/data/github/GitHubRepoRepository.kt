package com.erez.reposync.data.github

class GitHubRepoRepository(
    private val authStore: GitHubAuthStore,
    private val api: GitHubApi
) {
    suspend fun listOwnedRepos(page: Int, perPage: Int): GitHubRepoPage {
        val token = authStore.getAccessToken() ?: throw IllegalStateException("GitHub not authenticated")
        return api.listOwnedRepos(token, page, perPage)
    }
}
