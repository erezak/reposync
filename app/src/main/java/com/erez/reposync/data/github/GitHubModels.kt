package com.erez.reposync.data.github

import java.time.Instant

data class GitHubToken(
    val accessToken: String,
    val tokenType: String,
    val scope: String,
    val refreshToken: String? = null,
    val expiresAtEpochSeconds: Long? = null
)

data class GitHubUser(
    val login: String
)

data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val updatedAt: Instant,
    val defaultBranch: String,
    val cloneUrl: String
)

data class GitHubRepoPage(
    val repos: List<GitHubRepo>,
    val nextPage: Int?
)
