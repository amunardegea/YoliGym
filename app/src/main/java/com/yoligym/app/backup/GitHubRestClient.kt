package com.yoligym.app.backup

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

class GitHubApiException(
    val httpCode: Int,
    message: String,
    val isAuthError: Boolean = httpCode == 401 || httpCode == 403,
    val isTransient: Boolean = httpCode == 409 || httpCode == 429 || httpCode >= 500
) : Exception(message)

class GitHubRestClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val USER_AGENT = "YoliGym-Android"
        private const val ACCEPT_JSON = "application/vnd.github+json"
        private const val ACCEPT_RAW = "application/vnd.github.raw+json"
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()

        fun parseRepoInfo(jsonBody: String): GitHubRepoInfo {
            val obj = JSONObject(jsonBody)
            val ownerObj = obj.optJSONObject("owner")
            val owner = ownerObj?.optString("login")?.trim().orEmpty()
            val name = obj.optString("name").trim()
            val defaultBranch = obj.optString("default_branch").trim().ifEmpty { "main" }
            val isPrivate = obj.optBoolean("private", true)
            val perms = obj.optJSONObject("permissions")
            val canPush = perms?.optBoolean("push", false) ?: true
            return GitHubRepoInfo(
                owner = owner,
                name = name,
                defaultBranch = defaultBranch,
                isPrivate = isPrivate,
                canPush = canPush
            )
        }

        fun parseFileSha(jsonBody: String): String? {
            if (jsonBody.isBlank()) return null
            val obj = JSONObject(jsonBody)
            return obj.optString("sha").trim().takeIf { it.isNotEmpty() && it != "null" }
        }

        fun parseDirectoryListing(jsonBody: String): List<GitHubBackupFile> {
            if (jsonBody.isBlank()) return emptyList()
            val arr = JSONArray(jsonBody)
            val result = mutableListOf<GitHubBackupFile>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optString("type") != "file") continue
                val name = obj.optString("name").trim()
                val path = obj.optString("path").trim()
                val sha = obj.optString("sha").trim()
                if (name.isEmpty() || path.isEmpty() || sha.isEmpty()) continue
                val sizeBytes = obj.optLong("size", 0L).coerceAtLeast(0L)
                result.add(
                    GitHubBackupFile(
                        name = name,
                        path = path,
                        sha = sha,
                        sizeBytes = sizeBytes
                    )
                )
            }
            return result
        }

        fun buildPutContentPayload(
            message: String,
            utf8JsonContent: String,
            branch: String,
            existingSha: String?
        ): String {
            val base64Content = Base64.getEncoder()
                .encodeToString(utf8JsonContent.toByteArray(Charsets.UTF_8))
            val obj = JSONObject()
                .put("message", message)
                .put("content", base64Content)
                .put("branch", branch)
            if (!existingSha.isNullOrBlank()) {
                obj.put("sha", existingSha)
            }
            return obj.toString()
        }

        fun buildDeletePayload(
            message: String,
            sha: String,
            branch: String
        ): String {
            return JSONObject()
                .put("message", message)
                .put("sha", sha)
                .put("branch", branch)
                .toString()
        }
    }

    fun verifyRepository(token: String, owner: String, repo: String): GitHubRepoInfo {
        val url = GITHUB_API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("repos")
            .addPathSegment(owner)
            .addPathSegment(repo)
            .build()
        val req = baseRequestBuilder(url, token)
            .get()
            .build()
        return parseRepoInfo(executeForBody(req))
    }

    fun getFileShaIfExists(
        token: String,
        owner: String,
        repo: String,
        path: String,
        branch: String
    ): String? {
        val url = buildContentsUrl(owner, repo, path, branch)
        val req = baseRequestBuilder(url, token)
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 404) return null
            if (!resp.isSuccessful) {
                throw buildApiException(resp, body)
            }
            return parseFileSha(body)
        }
    }

    fun uploadOrUpdateDailyBackup(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        path: String,
        userId: String,
        dateLabel: String,
        jsonContent: String
    ) {
        val commitMessage = "YoliGym backup ($userId) $dateLabel"
        var attempt = 0
        while (true) {
            attempt++
            val existingSha = getFileShaIfExists(token, owner, repo, path, branch)
            val payload = buildPutContentPayload(
                message = commitMessage,
                utf8JsonContent = jsonContent,
                branch = branch,
                existingSha = existingSha
            )
            val url = buildContentsUrl(owner, repo, path)
            val req = baseRequestBuilder(url, token)
                .put(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) return
                if (resp.code == 409 && attempt == 1) {
                    // Retry once with refreshed SHA if another device updated concurrently.
                    continue
                }
                throw buildApiException(resp, body)
            }
        }
    }

    fun listUserBackups(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        userId: String
    ): List<GitHubBackupFile> {
        val folderPath = GitHubBackupNaming.buildUserFolderPath(userId)
        val url = buildContentsUrl(owner, repo, folderPath, branch)
        val req = baseRequestBuilder(url, token)
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 404) return emptyList()
            if (!resp.isSuccessful) {
                throw buildApiException(resp, body)
            }
            return parseDirectoryListing(body)
                .filter { GitHubBackupNaming.matchesUserDailyFile(it.name, userId) }
                .sortedByDescending { it.name }
        }
    }

    fun deleteFile(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        file: GitHubBackupFile,
        userId: String
    ) {
        val url = buildContentsUrl(owner, repo, file.path)
        val payload = buildDeletePayload(
            message = "Prune old YoliGym backup ($userId): ${file.name}",
            sha = file.sha,
            branch = branch
        )
        val req = baseRequestBuilder(url, token)
            .delete(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (resp.code == 404) return
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                throw buildApiException(resp, body)
            }
        }
    }

    fun downloadBackupContent(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        filePath: String
    ): String {
        val url = buildContentsUrl(owner, repo, filePath, branch)
        val req = baseRequestBuilder(url, token, acceptHeader = ACCEPT_RAW)
            .get()
            .build()
        return executeForBody(req)
    }

    private fun buildContentsUrl(
        owner: String,
        repo: String,
        path: String,
        branch: String? = null
    ): HttpUrl {
        val builder = GITHUB_API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("repos")
            .addPathSegment(owner)
            .addPathSegment(repo)
            .addPathSegment("contents")
        path.split('/')
            .filter { it.isNotEmpty() }
            .forEach { builder.addPathSegment(it) }
        if (!branch.isNullOrBlank()) {
            builder.addQueryParameter("ref", branch)
        }
        return builder.build()
    }

    private fun baseRequestBuilder(
        url: HttpUrl,
        token: String,
        acceptHeader: String = ACCEPT_JSON
    ): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", acceptHeader)
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", USER_AGENT)
    }

    private fun executeForBody(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw buildApiException(response, bodyStr)
            }
            return bodyStr
        }
    }

    private fun buildApiException(response: Response, body: String): GitHubApiException {
        val code = response.code
        val apiMessage = runCatching {
            JSONObject(body).optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()

        return when (code) {
            401 -> GitHubApiException(
                httpCode = 401,
                message = "Token de GitHub inválido o caducado (HTTP 401). Actualízalo en Ajustes.",
                isAuthError = true,
                isTransient = false
            )
            403 -> {
                val rateRemaining = response.header("X-RateLimit-Remaining")
                val isRateLimit = rateRemaining == "0" ||
                    (apiMessage?.contains("rate limit", ignoreCase = true) == true)
                if (isRateLimit) {
                    GitHubApiException(
                        httpCode = 403,
                        message = "Límite temporal de la API de GitHub alcanzado (HTTP 403). Se reintentará más tarde.",
                        isAuthError = false,
                        isTransient = true
                    )
                } else {
                    GitHubApiException(
                        httpCode = 403,
                        message = "El token no tiene permiso de escritura en este repositorio (HTTP 403). Activa «Contents: Read and write» en el token.",
                        isAuthError = true,
                        isTransient = false
                    )
                }
            }
            404 -> GitHubApiException(
                httpCode = 404,
                message = "Repositorio o rama no encontrados en GitHub (HTTP 404). Comprueba usuario/repositorio, que el repo tenga al menos un commit (README) y que el token tenga acceso.",
                isAuthError = false,
                isTransient = false
            )
            409 -> GitHubApiException(
                httpCode = 409,
                message = "Conflicto al escribir en GitHub o repositorio sin commit inicial (HTTP 409). ${apiMessage ?: ""}".trim(),
                isAuthError = false,
                isTransient = true
            )
            422 -> GitHubApiException(
                httpCode = 422,
                message = "GitHub rechazó la operación (HTTP 422): ${apiMessage ?: "comprueba el nombre de la rama"}",
                isAuthError = false,
                isTransient = false
            )
            429 -> GitHubApiException(
                httpCode = 429,
                message = "Demasiadas peticiones a GitHub (HTTP 429). Se reintentará automáticamente.",
                isAuthError = false,
                isTransient = true
            )
            else -> GitHubApiException(
                httpCode = code,
                message = if (!apiMessage.isNullOrBlank()) {
                    "GitHub HTTP $code: $apiMessage"
                } else {
                    "Error de GitHub (HTTP $code)"
                },
                isAuthError = false,
                isTransient = code >= 500
            )
        }
    }
}
