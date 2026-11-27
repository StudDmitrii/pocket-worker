package com.pocketworker.app.core.git

import com.pocketworker.app.BuildConfig
import org.json.JSONObject
import java.net.URI
import java.util.Locale

enum class GitOAuthProvider(
    val id: String,
    val displayName: String,
    val defaultHost: String,
    val defaultBaseUrl: String
) {
    GITHUB(
        id = "github",
        displayName = "GitHub",
        defaultHost = "github.com",
        defaultBaseUrl = "https://github.com"
    ),
    GITLAB(
        id = "gitlab",
        displayName = "GitLab",
        defaultHost = "gitlab.com",
        defaultBaseUrl = "https://gitlab.com"
    );

    companion object {
        fun fromId(id: String?): GitOAuthProvider? = entries.firstOrNull {
            it.id.equals(id, ignoreCase = true)
        }
    }
}

object GitOAuthDefaults {
    const val REDIRECT_URI_GITLAB = "pocketworker://oauth/gitlab"
    const val GITHUB_SCOPES = "repo read:user user:email"
    const val GITLAB_SCOPES = "read_user write_repository read_api"

    fun buildConfigClientId(provider: GitOAuthProvider): String = when (provider) {
        GitOAuthProvider.GITHUB -> BuildConfig.GITHUB_OAUTH_CLIENT_ID.trim()
        GitOAuthProvider.GITLAB -> BuildConfig.GITLAB_OAUTH_CLIENT_ID.trim()
    }
}

data class GitOAuthAccount(
    val id: String,
    val provider: GitOAuthProvider,
    val host: String,
    val baseUrl: String,
    val username: String,
    val displayName: String,
    val email: String? = null,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtMillis: Long? = null,
    val clientId: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val normalizedHost: String get() = host.trim().lowercase(Locale.ROOT)
    val isSelfHosted: Boolean get() = normalizedHost != provider.defaultHost

    fun toGitCredentials(): GitCredentials = when (provider) {
        GitOAuthProvider.GITHUB -> GitCredentials(
            username = username.ifBlank { "x-access-token" },
            tokenOrPassword = accessToken,
            isOAuthToken = true
        )
        GitOAuthProvider.GITLAB -> GitCredentials(
            username = "oauth2",
            tokenOrPassword = accessToken,
            isOAuthToken = true
        )
    }

    fun needsRefresh(
        nowMillis: Long = System.currentTimeMillis(),
        leewayMillis: Long = 120_000L
    ): Boolean {
        val exp = expiresAtMillis ?: return false
        return !refreshToken.isNullOrBlank() && (nowMillis + leewayMillis >= exp)
    }

    fun shortLabel(): String = if (isSelfHosted) {
        "${provider.displayName} • $normalizedHost (@$username)"
    } else {
        "${provider.displayName} (@$username)"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("provider", provider.id)
        put("host", normalizedHost)
        put("baseUrl", baseUrl.trimEnd('/'))
        put("username", username)
        put("displayName", displayName)
        if (!email.isNullOrBlank()) put("email", email)
        put("accessToken", accessToken)
        if (!refreshToken.isNullOrBlank()) put("refreshToken", refreshToken)
        if (expiresAtMillis != null) put("expiresAtMillis", expiresAtMillis)
        put("clientId", clientId)
        put("createdAtMillis", createdAtMillis)
    }

    companion object {
        fun fromJson(json: JSONObject): GitOAuthAccount? {
            val id = json.optString("id", "").trim()
            val provider = GitOAuthProvider.fromId(json.optString("provider", "")) ?: return null
            val accessToken = json.optString("accessToken", "").trim()
            val username = json.optString("username", "").trim()
            if (id.isEmpty() || accessToken.isEmpty() || username.isEmpty()) return null
            val host = json.optString("host", provider.defaultHost).trim().lowercase(Locale.ROOT)
                .ifEmpty { provider.defaultHost }
            val baseUrl = json.optString("baseUrl", "https://$host").trim().trimEnd('/')
                .ifEmpty { provider.defaultBaseUrl }
            val displayName = json.optString("displayName", username).trim().ifEmpty { username }
            val email = json.optString("email", "").trim().ifEmpty { null }
            val refreshToken = json.optString("refreshToken", "").trim().ifEmpty { null }
            val expiresAtMillis = if (json.has("expiresAtMillis") && !json.isNull("expiresAtMillis")) {
                json.optLong("expiresAtMillis")
            } else {
                null
            }
            return GitOAuthAccount(
                id = id,
                provider = provider,
                host = host,
                baseUrl = baseUrl,
                username = username,
                displayName = displayName,
                email = email,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMillis = expiresAtMillis,
                clientId = json.optString("clientId", "").trim(),
                createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis())
            )
        }
    }
}

sealed interface GitOAuthFlowState {
    data object Idle : GitOAuthFlowState

    data class Starting(val provider: GitOAuthProvider) : GitOAuthFlowState

    data class GitHubDeviceCodeReady(
        val userCode: String,
        val verificationUri: String,
        val expiresAtMillis: Long
    ) : GitOAuthFlowState

    data class WaitingForGitLabRedirect(
        val host: String,
        val authUrl: String
    ) : GitOAuthFlowState

    data class ExchangingToken(val provider: GitOAuthProvider) : GitOAuthFlowState

    data class Completed(
        val account: GitOAuthAccount,
        val autoFilledAuthor: Boolean
    ) : GitOAuthFlowState

    data class Error(
        val provider: GitOAuthProvider,
        val message: String
    ) : GitOAuthFlowState
}

/**
 * Normalizes a user-supplied Git host or URL into a clean base URL without trailing slash.
 * Defaults to `https://` unless the user explicitly specified `http://` (e.g. local self-hosted GitLab).
 */
fun normalizeGitBaseUrl(rawInput: String): String {
    val trimmed = rawInput.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    val withScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        else -> "https://$trimmed"
    }
    return runCatching {
        val uri = URI(withScheme)
        val scheme = (uri.scheme ?: "https").lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return withScheme.trimEnd('/')
        val portPart = if (uri.port > 0 && uri.port != 443 && uri.port != 80) ":${uri.port}" else ""
        val pathPart = uri.rawPath?.trimEnd('/').orEmpty()
        "$scheme://$host$portPart$pathPart"
    }.getOrElse { withScheme.trimEnd('/') }
}

/**
 * Extracts a normalized lowercase host (including non-default port if present) from a Git remote/clone URL.
 * Supports `https://`, `http://`, and SCP-style `git@host:owner/repo.git` URLs.
 */
fun extractHostFromGitUrl(rawUrl: String?): String? {
    val trimmed = rawUrl?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    // Handle SCP-style SSH URLs like git@github.com:user/repo.git
    if (!trimmed.contains("://") && trimmed.contains('@') && trimmed.contains(':')) {
        val afterAt = trimmed.substringAfter('@')
        val hostPart = afterAt.substringBefore(':').trim().lowercase(Locale.ROOT)
        if (hostPart.isNotEmpty()) return hostPart
    }

    val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
    return runCatching {
        val uri = URI(candidate)
        val host = uri.host?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        val port = uri.port
        if (port > 0 && port != 443 && port != 80) "$host:$port" else host
    }.getOrNull()
}
