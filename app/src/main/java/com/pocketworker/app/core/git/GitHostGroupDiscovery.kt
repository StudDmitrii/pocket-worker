package com.pocketworker.app.core.git

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class DiscoveredRepo(
    val id: String,
    val name: String,
    val fullName: String,
    val cloneUrl: String,
    val defaultBranch: String?,
    val description: String,
    val isPrivate: Boolean
)

sealed interface ParsedGroupUrl {
    val rawUrl: String
    val hostBaseUrl: String

    data class GitHub(
        override val rawUrl: String,
        override val hostBaseUrl: String,
        val owner: String,
        val isExplicitOrg: Boolean
    ) : ParsedGroupUrl

    data class GitLab(
        override val rawUrl: String,
        override val hostBaseUrl: String,
        val groupPath: String
    ) : ParsedGroupUrl
}

interface RepoGroupDiscoveryService {
    fun discoverRepositories(
        groupUrl: String,
        credentials: GitCredentials? = null
    ): List<DiscoveredRepo>
}

data class HttpTextResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap()
)

interface HttpTextClient {
    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpTextResponse
}

class UrlConnectionHttpTextClient : HttpTextClient {
    override fun get(url: String, headers: Map<String, String>): HttpTextResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }.orEmpty()
            val responseHeaders = mutableMapOf<String, String>()
            connection.headerFields.forEach { (key, values) ->
                if (!key.isNullOrBlank() && !values.isNullOrEmpty()) {
                    responseHeaders[key.lowercase()] = values.first()
                }
            }
            HttpTextResponse(statusCode = code, body = body, headers = responseHeaders)
        } finally {
            connection.disconnect()
        }
    }
}

class HttpRepoGroupDiscoveryService(
    private val httpClient: HttpTextClient = UrlConnectionHttpTextClient()
) : RepoGroupDiscoveryService {

    override fun discoverRepositories(
        groupUrl: String,
        credentials: GitCredentials?
    ): List<DiscoveredRepo> {
        return when (val parsed = parseGroupUrl(groupUrl)) {
            is ParsedGroupUrl.GitHub -> discoverGitHub(parsed, credentials)
            is ParsedGroupUrl.GitLab -> discoverGitLab(parsed, credentials)
        }
    }

    private fun discoverGitHub(
        parsed: ParsedGroupUrl.GitHub,
        credentials: GitCredentials?
    ): List<DiscoveredRepo> {
        val token = credentials?.tokenOrPassword?.trim()?.takeIf { it.isNotEmpty() }
        val headers = buildMap {
            put("Accept", "application/vnd.github+json")
            put("User-Agent", "PocketWorker-Android")
            if (token != null) {
                put("Authorization", "Bearer $token")
            }
        }
        val apiBase = if (parsed.hostBaseUrl.contains("github.com")) {
            "https://api.github.com"
        } else {
            "${parsed.hostBaseUrl}/api/v3"
        }

        val encodedOwner = urlEncodeSegment(parsed.owner)
        val endpointPath = if (parsed.isExplicitOrg) {
            "/orgs/$encodedOwner/repos?per_page=$PAGE_SIZE&type=all&sort=updated"
        } else {
            val ownerResp = httpClient.get("$apiBase/users/$encodedOwner", headers)
            ensureSuccess(ownerResp, "Профиль или организация GitHub «${parsed.owner}» не найдены")
            val ownerType = runCatching { JSONObject(ownerResp.body).optString("type", "") }.getOrDefault("")
            if (ownerType.equals("Organization", ignoreCase = true)) {
                "/orgs/$encodedOwner/repos?per_page=$PAGE_SIZE&type=all&sort=updated"
            } else {
                val authenticatedLogin = if (token != null) {
                    val selfResp = httpClient.get("$apiBase/user", headers)
                    if (selfResp.statusCode in 200..299) {
                        runCatching { JSONObject(selfResp.body).optString("login", "") }.getOrNull()
                    } else null
                } else null

                if (authenticatedLogin != null && authenticatedLogin.equals(parsed.owner, ignoreCase = true)) {
                    "/user/repos?per_page=$PAGE_SIZE&affiliation=owner&sort=updated"
                } else {
                    "/users/$encodedOwner/repos?per_page=$PAGE_SIZE&sort=updated"
                }
            }
        }

        val collected = mutableListOf<DiscoveredRepo>()
        for (page in 1..MAX_PAGES) {
            val resp = httpClient.get("$apiBase$endpointPath&page=$page", headers)
            ensureSuccess(resp, "Не удалось получить список репозиториев GitHub для «${parsed.owner}»")
            val array = JSONArray(resp.body)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val cloneUrl = obj.optString("clone_url", "").trim()
                val name = obj.optString("name", "").trim()
                if (cloneUrl.isEmpty() || name.isEmpty()) continue
                collected.add(
                    DiscoveredRepo(
                        id = obj.optLong("id", i.toLong()).toString(),
                        name = name,
                        fullName = obj.optString("full_name", name),
                        cloneUrl = cloneUrl,
                        defaultBranch = obj.optString("default_branch", "").ifBlank { null },
                        description = obj.optString("description", "").takeIf { it != "null" }.orEmpty(),
                        isPrivate = obj.optBoolean("private", false)
                    )
                )
            }
            if (array.length() < PAGE_SIZE) break
        }
        return collected.distinctBy { it.cloneUrl }
    }

    private fun discoverGitLab(
        parsed: ParsedGroupUrl.GitLab,
        credentials: GitCredentials?
    ): List<DiscoveredRepo> {
        val token = credentials?.tokenOrPassword?.trim()?.takeIf { it.isNotEmpty() }
        val headers = buildMap {
            put("Accept", "application/json")
            put("User-Agent", "PocketWorker-Android")
            if (token != null) {
                put("PRIVATE-TOKEN", token)
            }
        }

        val encodedPath = urlEncodeSegment(parsed.groupPath)
        val groupQuery = "${parsed.hostBaseUrl}/api/v4/groups/$encodedPath/projects" +
            "?per_page=$PAGE_SIZE&include_subgroups=true&archived=false&simple=true&order_by=last_activity_at&sort=desc"
        val userQuery = "${parsed.hostBaseUrl}/api/v4/users/$encodedPath/projects" +
            "?per_page=$PAGE_SIZE&archived=false&simple=true&order_by=last_activity_at&sort=desc"

        var activeBaseQuery = groupQuery
        val collected = mutableListOf<DiscoveredRepo>()

        for (page in 1..MAX_PAGES) {
            var resp = httpClient.get("$activeBaseQuery&page=$page", headers)
            if (page == 1 && resp.statusCode == 404 && '/' !in parsed.groupPath) {
                // Fallback to GitLab user projects when URL points to a personal namespace
                activeBaseQuery = userQuery
                resp = httpClient.get("$activeBaseQuery&page=$page", headers)
            }
            ensureSuccess(resp, "Группа или пользователь GitLab «${parsed.groupPath}» не найдены")

            val array = JSONArray(resp.body)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val cloneUrl = obj.optString("http_url_to_repo", "").trim()
                val pathSlug = obj.optString("path", "").trim()
                val displayName = obj.optString("name", "").trim()
                val repoName = pathSlug.ifEmpty { displayName }
                if (cloneUrl.isEmpty() || repoName.isEmpty()) continue
                collected.add(
                    DiscoveredRepo(
                        id = obj.optLong("id", i.toLong()).toString(),
                        name = repoName,
                        fullName = obj.optString("path_with_namespace", repoName),
                        cloneUrl = cloneUrl,
                        defaultBranch = obj.optString("default_branch", "").ifBlank { null },
                        description = obj.optString("description", "").takeIf { it != "null" }.orEmpty(),
                        isPrivate = obj.optString("visibility", "") == "private"
                    )
                )
            }
            val nextPage = resp.headers["x-next-page"].orEmpty().trim()
            if (nextPage.isEmpty() || array.length() < PAGE_SIZE) break
        }
        return collected.distinctBy { it.cloneUrl }
    }

    private fun ensureSuccess(resp: HttpTextResponse, notFoundMessage: String) {
        if (resp.statusCode in 200..299) return
        val apiMessage = runCatching {
            JSONObject(resp.body).optString("message", "")
        }.getOrDefault("")
        val detail = if (apiMessage.isNotBlank()) " ($apiMessage)" else ""
        when (resp.statusCode) {
            401 -> throw IllegalStateException(
                "Ошибка авторизации (401): включите авторизацию и укажите действительный токен (PAT)$detail"
            )
            403 -> throw IllegalStateException(
                "Доступ запрещён (403): проверьте права токена или лимит запросов API$detail"
            )
            404 -> throw IllegalStateException("$notFoundMessage (404)$detail")
            else -> throw IllegalStateException("Ошибка сервера (${resp.statusCode})$detail")
        }
    }

    private fun urlEncodeSegment(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 5

        fun parseGroupUrl(rawUrl: String): ParsedGroupUrl {
            val trimmed = rawUrl.trim()
            require(trimmed.isNotEmpty()) { "Укажите ссылку на группу или организацию" }

            val normalized = if ("://" in trimmed) trimmed else "https://$trimmed"
            val uri = runCatching { URI(normalized) }.getOrElse {
                throw IllegalArgumentException("Некорректная ссылка: $trimmed")
            }

            val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: "https"
            val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Не удалось определить домен в ссылке")
            val portPart = if (uri.port != -1) ":${uri.port}" else ""
            val hostBaseUrl = "$scheme://$host$portPart"

            val rawSegments = uri.path.orEmpty()
                .split('/')
                .filter { it.isNotBlank() }

            if (rawSegments.isEmpty()) {
                throw IllegalArgumentException("Укажите путь к группе или организации в ссылке")
            }

            if (host == "github.com" || host == "www.github.com") {
                val (owner, isExplicitOrg) = when {
                    rawSegments[0].equals("orgs", ignoreCase = true) && rawSegments.size >= 2 ->
                        rawSegments[1] to true
                    rawSegments[0].equals("users", ignoreCase = true) && rawSegments.size >= 2 ->
                        rawSegments[1] to false
                    rawSegments.size == 1 ->
                        rawSegments[0].removeSuffix(".git") to false
                    else -> throw IllegalArgumentException(
                        "Для GitHub укажите ссылку на организацию или профиль (например, https://github.com/my-org), а не на конкретный репозиторий"
                    )
                }
                require(owner.isNotBlank()) { "Не удалось определить имя организации или пользователя GitHub" }
                return ParsedGroupUrl.GitHub(
                    rawUrl = trimmed,
                    hostBaseUrl = hostBaseUrl,
                    owner = owner,
                    isExplicitOrg = isExplicitOrg
                )
            }

            // GitLab (gitlab.com or self-hosted GitLab)
            val withoutGroupsPrefix = if (rawSegments[0].equals("groups", ignoreCase = true)) {
                rawSegments.drop(1)
            } else {
                rawSegments
            }
            val beforeDash = withoutGroupsPrefix.takeWhile { it != "-" }
                .mapIndexed { idx, seg ->
                    if (idx == withoutGroupsPrefix.takeWhile { it != "-" }.lastIndex) {
                        seg.removeSuffix(".git")
                    } else seg
                }
                .filter { it.isNotBlank() }

            val groupPath = beforeDash.joinToString("/")
            require(groupPath.isNotEmpty()) { "Не удалось определить группу GitLab в ссылке" }

            return ParsedGroupUrl.GitLab(
                rawUrl = trimmed,
                hostBaseUrl = hostBaseUrl,
                groupPath = groupPath
            )
        }
    }
}
