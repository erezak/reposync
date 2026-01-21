package com.erez.reposync.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

class GitHubApi {
    suspend fun exchangeCodeForToken(
        clientId: String,
        clientSecret: String?,
        code: String,
        redirectUri: String,
        codeVerifier: String
    ): GitHubToken = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "client_id" to clientId,
            "code" to code,
            "redirect_uri" to redirectUri,
            "code_verifier" to codeVerifier
        )
        if (!clientSecret.isNullOrBlank()) {
            params["client_secret"] = clientSecret
        }
        val requestBody = formBody(params)
        val connection = openConnection("https://github.com/login/oauth/access_token", "POST")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.doOutput = true
        connection.outputStream.use { it.write(requestBody.toByteArray()) }
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Token exchange failed: ${connection.responseCode} ${response.body}")
        }
        val body = response.body
        val json = runCatching { JSONObject(body) }.getOrNull()
        val accessToken = json?.optString("access_token")?.takeIf { it.isNotBlank() }
            ?: parseUrlEncoded(body)["access_token"]
        if (accessToken.isNullOrBlank()) {
            val error = json?.optString("error")?.takeIf { it.isNotBlank() }
                ?: parseUrlEncoded(body)["error"]
            val errorDescription = json?.optString("error_description")?.takeIf { it.isNotBlank() }
                ?: parseUrlEncoded(body)["error_description"]
                ?: parseUrlEncoded(body)["error_uri"]
            val message = listOfNotNull(error, errorDescription).joinToString(": ")
            throw IllegalStateException(
                if (message.isNotBlank()) "GitHub auth failed: $message" else "GitHub auth failed: missing access_token"
            )
        }
        val tokenType = json?.optString("token_type")?.takeIf { it.isNotBlank() } ?: "bearer"
        val scope = json?.optString("scope")?.takeIf { it.isNotBlank() } ?: ""
        val refreshToken = json?.optString("refresh_token")?.takeIf { it.isNotBlank() }
            ?: parseUrlEncoded(body)["refresh_token"]
        val expiresIn = json?.optLong("expires_in", 0L)?.takeIf { it > 0 }
        val expiresAt = expiresIn?.let { (System.currentTimeMillis() / 1000L) + it }
        return@withContext GitHubToken(
            accessToken = accessToken,
            tokenType = tokenType,
            scope = scope,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresAt
        )
    }

    suspend fun getUser(accessToken: String): GitHubUser = withContext(Dispatchers.IO) {
        val connection = openConnection("https://api.github.com/user", "GET")
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("GitHub user fetch failed: ${connection.responseCode} ${response.body}")
        }
        val json = JSONObject(response.body)
        return@withContext GitHubUser(login = json.getString("login"))
    }

    suspend fun listOwnedRepos(
        accessToken: String,
        page: Int,
        perPage: Int
    ): GitHubRepoPage = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/user/repos" +
            "?visibility=all" +
            "&affiliation=owner" +
            "&sort=updated" +
            "&direction=desc" +
            "&per_page=$perPage" +
            "&page=$page"
        val connection = openConnection(url, "GET")
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Repo list failed: ${connection.responseCode} ${response.body}")
        }
        val array = JSONArray(response.body)
        val repos = mutableListOf<GitHubRepo>()
        for (i in 0 until array.length()) {
            repos.add(parseRepo(array.getJSONObject(i)))
        }
        val nextPage = parseNextPage(response.linkHeader)
        return@withContext GitHubRepoPage(repos = repos, nextPage = nextPage)
    }

    private fun parseRepo(json: JSONObject): GitHubRepo {
        val updatedAt = json.optString("updated_at")
        val updatedInstant = runCatching { Instant.parse(updatedAt) }.getOrElse { Instant.EPOCH }
        return GitHubRepo(
            id = json.getLong("id"),
            name = json.getString("name"),
            fullName = json.getString("full_name"),
            isPrivate = json.optBoolean("private", false),
            updatedAt = updatedInstant,
            defaultBranch = json.optString("default_branch", "main"),
            cloneUrl = json.getString("clone_url")
        )
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "RepoSync")
        return connection
    }

    private fun formBody(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}" 
        }
    }

    private data class HttpResponse(val body: String, val linkHeader: String?)

    private fun readResponse(connection: HttpURLConnection): HttpResponse {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        } ?: ""
        val link = connection.getHeaderField("Link")
        return HttpResponse(body, link)
    }

    private fun parseNextPage(linkHeader: String?): Int? {
        if (linkHeader.isNullOrBlank()) return null
        val parts = linkHeader.split(",")
        for (part in parts) {
            val segments = part.split(";")
            if (segments.size < 2) continue
            val rel = segments[1]
            if (!rel.contains("rel=\"next\"")) continue
            val urlPart = segments[0].trim().removePrefix("<").removeSuffix(">")
            val query = urlPart.substringAfter("?", "")
            val params = query.split("&").associate { param ->
                val key = param.substringBefore("=")
                val value = param.substringAfter("=", "")
                key to value
            }
            return params["page"]?.toIntOrNull()
        }
        return null
    }

    private fun parseUrlEncoded(body: String): Map<String, String> {
        if (!body.contains("=") || body.contains("{") || body.contains("[")) return emptyMap()
        return body.split("&").mapNotNull { pair ->
            val key = pair.substringBefore("=", "").trim()
            if (key.isBlank()) return@mapNotNull null
            val value = pair.substringAfter("=", "").trim()
            key to value
        }.toMap()
    }
}
