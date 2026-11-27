package com.pocketworker.app.core.git

import com.pocketworker.app.core.i18n.AppLocale
import com.pocketworker.app.core.storage.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class OAuthHttpResponse(
    val statusCode: Int,
    val body: String
)

interface OAuthHttpClient {
    fun get(url: String, headers: Map<String, String> = emptyMap()): OAuthHttpResponse
    fun postForm(
        url: String,
        formParams: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): OAuthHttpResponse
}

class UrlConnectionOAuthHttpClient : OAuthHttpClient {
    override fun get(url: String, headers: Map<String, String>): OAuthHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return readResponse(connection)
    }

    override fun postForm(
        url: String,
        formParams: Map<String, String>,
        headers: Map<String, String>
    ): OAuthHttpResponse {
        val encodedBody = formParams.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(encodedBody)
            writer.flush()
        }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): OAuthHttpResponse {
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }.orEmpty()
            OAuthHttpResponse(statusCode = code, body = body)
        } finally {
            connection.disconnect()
        }
    }
}

interface GlobalGitAuthorStore {
    fun hasCustomAuthor(): Boolean
    fun saveAuthor(name: String, email: String)
}

object JGitGlobalAuthorStore : GlobalGitAuthorStore {
    override fun hasCustomAuthor(): Boolean {
        return runCatching {
            val userConfig = SystemReader.getInstance().openUserConfig(null, FS.DETECTED)
            userConfig.load()
            val name = userConfig.getString("user", null, "name")
            val email = userConfig.getString("user", null, "email")
            !name.isNullOrBlank() || !email.isNullOrBlank()
        }.getOrDefault(false)
    }

    override fun saveAuthor(name: String, email: String) {
        runCatching {
            val userConfig = SystemReader.getInstance().openUserConfig(null, FS.DETECTED)
            userConfig.load()
            userConfig.setString("user", null, "name", name)
            userConfig.setString("user", null, "email", email)
            userConfig.save()
        }
    }
}

private data class PendingGitLabPkceSession(
    val state: String,
    val codeVerifier: String,
    val baseUrl: String,
    val host: String,
    val clientId: String,
    val redirectUri: String
)

class GitOAuthManager(
    private val credentialStore: CredentialStore,
    private val httpClient: OAuthHttpClient = UrlConnectionOAuthHttpClient(),
    private val globalAuthorStore: GlobalGitAuthorStore = JGitGlobalAuthorStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val _flowState = MutableStateFlow<GitOAuthFlowState>(GitOAuthFlowState.Idle)
    val flowState: StateFlow<GitOAuthFlowState> = _flowState.asStateFlow()

    private val _accountsVersion = MutableStateFlow(0L)
    val accountsVersion: StateFlow<Long> = _accountsVersion.asStateFlow()

    private val refreshMutex = Mutex()
    private val secureRandom = SecureRandom()

    @Volatile
    private var pendingGitLabSession: PendingGitLabPkceSession? = null

    private var activeFlowJob: Job? = null

    fun notifyAccountsChanged() {
        _accountsVersion.value = _accountsVersion.value + 1L
    }

    fun effectiveClientId(
        provider: GitOAuthProvider,
        host: String = provider.defaultHost
    ): String {
        val normHost = host.trim().lowercase().ifEmpty { provider.defaultHost }
        val saved = credentialStore.getOAuthClientId(provider, normHost)
        if (!saved.isNullOrBlank()) return saved
        return if (normHost == provider.defaultHost) {
            GitOAuthDefaults.buildConfigClientId(provider)
        } else {
            ""
        }
    }

    fun resetFlow() {
        activeFlowJob?.cancel()
        activeFlowJob = null
        pendingGitLabSession = null
        _flowState.value = GitOAuthFlowState.Idle
    }

    fun startGitHubDeviceFlow(clientIdInput: String) {
        val cleanClientId = clientIdInput.trim()
        val s = AppLocale.current
        if (cleanClientId.isEmpty()) {
            _flowState.value = GitOAuthFlowState.Error(GitOAuthProvider.GITHUB, s.oauthErrClientIdRequired)
            return
        }
        credentialStore.saveOAuthClientId(GitOAuthProvider.GITHUB, cleanClientId, GitOAuthProvider.GITHUB.defaultHost)

        activeFlowJob?.cancel()
        pendingGitLabSession = null
        _flowState.value = GitOAuthFlowState.Starting(GitOAuthProvider.GITHUB)

        activeFlowJob = scope.launch(ioDispatcher) {
            try {
                val deviceResp = httpClient.postForm(
                    url = "${GitOAuthProvider.GITHUB.defaultBaseUrl}/login/device/code",
                    formParams = mapOf(
                        "client_id" to cleanClientId,
                        "scope" to GitOAuthDefaults.GITHUB_SCOPES
                    ),
                    headers = mapOf(
                        "Accept" to "application/json",
                        "User-Agent" to USER_AGENT
                    )
                )
                val deviceJson = runCatching { JSONObject(deviceResp.body) }.getOrNull()
                if (deviceResp.statusCode !in 200..299 || deviceJson == null) {
                    val errDetail = deviceJson?.optString("error_description").orEmpty()
                        .ifBlank { deviceJson?.optString("error").orEmpty() }
                        .ifBlank { "HTTP ${deviceResp.statusCode}" }
                    _flowState.value = GitOAuthFlowState.Error(
                        GitOAuthProvider.GITHUB,
                        AppLocale.current.oauthErrRequestFailed(errDetail)
                    )
                    return@launch
                }

                val deviceError = deviceJson.optString("error", "").trim()
                if (deviceError.isNotEmpty()) {
                    val detail = deviceJson.optString("error_description", deviceError).ifBlank { deviceError }
                    _flowState.value = GitOAuthFlowState.Error(
                        GitOAuthProvider.GITHUB,
                        AppLocale.current.oauthErrRequestFailed(detail)
                    )
                    return@launch
                }

                val deviceCode = deviceJson.optString("device_code", "").trim()
                val userCode = deviceJson.optString("user_code", "").trim()
                val verificationUri = deviceJson.optString(
                    "verification_uri",
                    "https://github.com/login/device"
                ).trim().ifEmpty { "https://github.com/login/device" }
                val expiresInSec = deviceJson.optLong("expires_in", 900L).coerceAtLeast(60L)
                var pollIntervalSec = deviceJson.optLong("interval", 5L).coerceAtLeast(1L)

                if (deviceCode.isEmpty() || userCode.isEmpty()) {
                    _flowState.value = GitOAuthFlowState.Error(
                        GitOAuthProvider.GITHUB,
                        AppLocale.current.oauthErrRequestFailed("Incomplete device code response")
                    )
                    return@launch
                }

                val expiresAtMillis = nowProvider() + expiresInSec * 1000L
                _flowState.value = GitOAuthFlowState.GitHubDeviceCodeReady(
                    userCode = userCode,
                    verificationUri = verificationUri,
                    expiresAtMillis = expiresAtMillis
                )

                while (nowProvider() < expiresAtMillis) {
                    delayFn(pollIntervalSec * 1000L)

                    val tokenResp = httpClient.postForm(
                        url = "${GitOAuthProvider.GITHUB.defaultBaseUrl}/login/oauth/access_token",
                        formParams = mapOf(
                            "client_id" to cleanClientId,
                            "device_code" to deviceCode,
                            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                        ),
                        headers = mapOf(
                            "Accept" to "application/json",
                            "User-Agent" to USER_AGENT
                        )
                    )
                    val tokenJson = runCatching { JSONObject(tokenResp.body) }.getOrNull()
                    if (tokenJson == null) {
                        _flowState.value = GitOAuthFlowState.Error(
                            GitOAuthProvider.GITHUB,
                            AppLocale.current.oauthErrRequestFailed("HTTP ${tokenResp.statusCode}")
                        )
                        return@launch
                    }

                    val accessToken = tokenJson.optString("access_token", "").trim()
                    if (accessToken.isNotEmpty()) {
                        _flowState.value = GitOAuthFlowState.ExchangingToken(GitOAuthProvider.GITHUB)
                        completeGitHubSignIn(accessToken, cleanClientId)
                        return@launch
                    }

                    when (val errCode = tokenJson.optString("error", "").trim()) {
                        "authorization_pending" -> continue
                        "slow_down" -> {
                            val serverInterval = tokenJson.optLong("interval", 0L)
                            pollIntervalSec = if (serverInterval > pollIntervalSec) {
                                serverInterval
                            } else {
                                pollIntervalSec + 5L
                            }
                            continue
                        }
                        "expired_token" -> {
                            _flowState.value = GitOAuthFlowState.Error(
                                GitOAuthProvider.GITHUB,
                                AppLocale.current.oauthErrDeviceExpired
                            )
                            return@launch
                        }
                        "access_denied" -> {
                            _flowState.value = GitOAuthFlowState.Error(
                                GitOAuthProvider.GITHUB,
                                AppLocale.current.oauthErrAccessDenied
                            )
                            return@launch
                        }
                        else -> {
                            val detail = tokenJson.optString("error_description", errCode).ifBlank {
                                errCode.ifEmpty { "HTTP ${tokenResp.statusCode}" }
                            }
                            _flowState.value = GitOAuthFlowState.Error(
                                GitOAuthProvider.GITHUB,
                                AppLocale.current.oauthErrRequestFailed(detail)
                            )
                            return@launch
                        }
                    }
                }

                _flowState.value = GitOAuthFlowState.Error(
                    GitOAuthProvider.GITHUB,
                    AppLocale.current.oauthErrDeviceExpired
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (ex: Throwable) {
                _flowState.value = GitOAuthFlowState.Error(
                    GitOAuthProvider.GITHUB,
                    AppLocale.current.oauthErrRequestFailed(ex.localizedMessage ?: ex.javaClass.simpleName)
                )
            }
        }
    }

    private fun completeGitHubSignIn(accessToken: String, clientId: String) {
        val headers = mapOf(
            "Authorization" to "Bearer $accessToken",
            "Accept" to "application/vnd.github+json",
            "User-Agent" to USER_AGENT
        )
        val userResp = httpClient.get("https://api.github.com/user", headers)
        val userJson = runCatching { JSONObject(userResp.body) }.getOrNull()
        if (userResp.statusCode !in 200..299 || userJson == null) {
            _flowState.value = GitOAuthFlowState.Error(
                GitOAuthProvider.GITHUB,
                AppLocale.current.oauthErrProfileFailed("HTTP ${userResp.statusCode}")
            )
            return
        }

        val login = userJson.optString("login", "").trim()
        if (login.isEmpty()) {
            _flowState.value = GitOAuthFlowState.Error(
                GitOAuthProvider.GITHUB,
                AppLocale.current.oauthErrProfileFailed("Missing login")
            )
            return
        }
        val displayName = userJson.optString("name", "").takeIf { it.isNotBlank() && it != "null" } ?: login
        var email = userJson.optString("email", "").takeIf { it.isNotBlank() && it != "null" }

        if (email == null) {
            val emailsResp = runCatching { httpClient.get("https://api.github.com/user/emails", headers) }.getOrNull()
            if (emailsResp != null && emailsResp.statusCode in 200..299) {
                email = pickPrimaryGitHubEmail(emailsResp.body)
            }
        }
        if (email == null) {
            val numericId = userJson.optLong("id", 0L)
            email = if (numericId > 0L) {
                "$numericId+$login@users.noreply.github.com"
            } else {
                "$login@users.noreply.github.com"
            }
        }

        val account = GitOAuthAccount(
            id = UUID.randomUUID().toString(),
            provider = GitOAuthProvider.GITHUB,
            host = GitOAuthProvider.GITHUB.defaultHost,
            baseUrl = GitOAuthProvider.GITHUB.defaultBaseUrl,
            username = login,
            displayName = displayName,
            email = email,
            accessToken = accessToken,
            refreshToken = null,
            expiresAtMillis = null,
            clientId = clientId,
            createdAtMillis = nowProvider()
        )
        val stored = credentialStore.saveOAuthAccount(account)
        val autoFilled = autoFillGlobalAuthorIfUnset(stored)
        notifyAccountsChanged()
        _flowState.value = GitOAuthFlowState.Completed(account = stored, autoFilledAuthor = autoFilled)
    }

    /**
     * Generates a GitLab Authorization Code + PKCE URL (supports both `gitlab.com` and self-hosted GitLab).
     * Returns the browser URL to launch, or null if input validation failed (state updated to Error).
     */
    fun prepareGitLabAuthorization(
        baseUrlInput: String,
        clientIdInput: String
    ): String? {
        val s = AppLocale.current
        val cleanClientId = clientIdInput.trim()
        if (cleanClientId.isEmpty()) {
            _flowState.value = GitOAuthFlowState.Error(GitOAuthProvider.GITLAB, s.oauthErrClientIdRequired)
            return null
        }

        val rawUrl = baseUrlInput.trim().ifEmpty { GitOAuthProvider.GITLAB.defaultBaseUrl }
        val normalizedBase = normalizeGitBaseUrl(rawUrl)
        val host = extractHostFromGitUrl(normalizedBase)
        if (normalizedBase.isEmpty() || host.isNullOrBlank()) {
            _flowState.value = GitOAuthFlowState.Error(GitOAuthProvider.GITLAB, s.oauthErrInvalidServerUrl)
            return null
        }

        credentialStore.saveOAuthClientId(GitOAuthProvider.GITLAB, cleanClientId, host)

        activeFlowJob?.cancel()
        activeFlowJob = null

        val codeVerifier = generateRandomUrlSafe(48)
        val codeChallenge = computeS256Challenge(codeVerifier)
        val state = generateRandomUrlSafe(24)
        val redirectUri = GitOAuthDefaults.REDIRECT_URI_GITLAB

        pendingGitLabSession = PendingGitLabPkceSession(
            state = state,
            codeVerifier = codeVerifier,
            baseUrl = normalizedBase,
            host = host,
            clientId = cleanClientId,
            redirectUri = redirectUri
        )

        val authUrl = buildString {
            append(normalizedBase)
            append("/oauth/authorize")
            append("?client_id=").append(urlEncode(cleanClientId))
            append("&redirect_uri=").append(urlEncode(redirectUri))
            append("&response_type=code")
            append("&state=").append(urlEncode(state))
            append("&scope=").append(urlEncode(GitOAuthDefaults.GITLAB_SCOPES))
            append("&code_challenge=").append(urlEncode(codeChallenge))
            append("&code_challenge_method=S256")
        }

        _flowState.value = GitOAuthFlowState.WaitingForGitLabRedirect(host = host, authUrl = authUrl)
        return authUrl
    }

    /**
     * Handles deep-link callback `pocketworker://oauth/gitlab?code=...&state=...` delivered to MainActivity.
     * Returns true if the URL matched our OAuth scheme and was processed.
     */
    fun handleRedirectUrl(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        if (!uri.scheme.equals("pocketworker", ignoreCase = true) ||
            !uri.host.equals("oauth", ignoreCase = true)
        ) {
            return false
        }

        val pending = pendingGitLabSession ?: return true
        pendingGitLabSession = null

        val query = parseQueryParams(uri.rawQuery.orEmpty())
        val error = query["error"]?.trim().orEmpty()
        if (error.isNotEmpty()) {
            val desc = query["error_description"]?.trim().orEmpty()
            val message = if (error == "access_denied") {
                AppLocale.current.oauthErrAccessDenied
            } else {
                AppLocale.current.oauthErrRequestFailed(desc.ifEmpty { error })
            }
            _flowState.value = GitOAuthFlowState.Error(GitOAuthProvider.GITLAB, message)
            return true
        }

        val returnedState = query["state"]?.trim().orEmpty()
        if (returnedState != pending.state) {
            _flowState.value = GitOAuthFlowState.Error(
                GitOAuthProvider.GITLAB,
                AppLocale.current.oauthErrStateMismatch
            )
            return true
        }

        val code = query["code"]?.trim().orEmpty()
        if (code.isEmpty()) {
            _flowState.value = GitOAuthFlowState.Error(
                GitOAuthProvider.GITLAB,
                AppLocale.current.oauthErrMissingCode
            )
            return true
        }

        activeFlowJob?.cancel()
        _flowState.value = GitOAuthFlowState.ExchangingToken(GitOAuthProvider.GITLAB)
        activeFlowJob = scope.launch(ioDispatcher) {
            exchangeGitLabCode(pending, code)
        }
        return true
    }

    private fun exchangeGitLabCode(pending: PendingGitLabPkceSession, code: String) {
        try {
            val tokenResp = httpClient.postForm(
                url = "${pending.baseUrl}/oauth/token",
                formParams = mapOf(
                    "client_id" to pending.clientId,
                    "code" to code,
                    "grant_type" to "authorization_code",
                    "redirect_uri" to pending.redirectUri,
                    "code_verifier" to pending.codeVerifier
                ),
                headers = mapOf(
                    "Accept" to "application/json",
                    "User-Agent" to USER_AGENT
                )
            )
            val tokenJson = runCatching { JSONObject(tokenResp.body) }.getOrNull()
            val accessToken = tokenJson?.optString("access_token", "")?.trim().orEmpty()
            if (tokenResp.statusCode !in 200..299 || tokenJson == null || accessToken.isEmpty()) {
                val detail = tokenJson?.optString("error_description").orEmpty()
                    .ifBlank { tokenJson?.optString("error").orEmpty() }
                    .ifBlank { "HTTP ${tokenResp.statusCode}" }
                _flowState.value = GitOAuthFlowState.Error(
                    GitOAuthProvider.GITLAB,
                    AppLocale.current.oauthErrRequestFailed(detail)
                )
                return
            }

            val refreshToken = tokenJson.optString("refresh_token", "").trim().ifEmpty { null }
            val expiresInSec = tokenJson.optLong("expires_in", 0L)
            val expiresAtMillis = if (expiresInSec > 0L) nowProvider() + expiresInSec * 1000L else null

            val userResp = httpClient.get(
                url = "${pending.baseUrl}/api/v4/user",
                headers = mapOf(
                    "Authorization" to "Bearer $accessToken",
                    "Accept" to "application/json",
                    "User-Agent" to USER_AGENT
                )
            )
            val userJson = runCatching { JSONObject(userResp.body) }.getOrNull()
            if (userResp.statusCode !in 200..299 || userJson == null) {
                _flowState.value = GitOAuthFlowState.Error(
                    GitOAuthProvider.GITLAB,
                    AppLocale.current.oauthErrProfileFailed("HTTP ${userResp.statusCode}")
                )
                return
            }

            val username = userJson.optString("username", "").trim()
            if (username.isEmpty()) {
                _flowState.value = GitOAuthFlowState.Error(
                    GitOAuthProvider.GITLAB,
                    AppLocale.current.oauthErrProfileFailed("Missing username")
                )
                return
            }
            val displayName = userJson.optString("name", "").takeIf { it.isNotBlank() && it != "null" } ?: username
            val email = listOf("commit_email", "email", "public_email")
                .firstNotNullOfOrNull { key ->
                    userJson.optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }
                }

            val account = GitOAuthAccount(
                id = UUID.randomUUID().toString(),
                provider = GitOAuthProvider.GITLAB,
                host = pending.host,
                baseUrl = pending.baseUrl,
                username = username,
                displayName = displayName,
                email = email,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMillis = expiresAtMillis,
                clientId = pending.clientId,
                createdAtMillis = nowProvider()
            )
            val stored = credentialStore.saveOAuthAccount(account)
            val autoFilled = autoFillGlobalAuthorIfUnset(stored)
            notifyAccountsChanged()
            _flowState.value = GitOAuthFlowState.Completed(account = stored, autoFilledAuthor = autoFilled)
        } catch (ce: CancellationException) {
            throw ce
        } catch (ex: Throwable) {
            _flowState.value = GitOAuthFlowState.Error(
                GitOAuthProvider.GITLAB,
                AppLocale.current.oauthErrRequestFailed(ex.localizedMessage ?: ex.javaClass.simpleName)
            )
        }
    }

    /**
     * Refreshes an expired (or soon-to-expire) GitLab OAuth access token using its refresh token.
     * Protected by [refreshMutex] so concurrent Git calls never double-spend a rotated refresh token.
     */
    suspend fun refreshAccountIfNeeded(accountId: String): GitOAuthAccount? {
        return refreshMutex.withLock {
            val current = credentialStore.getOAuthAccount(accountId) ?: return@withLock null
            if (!current.needsRefresh(nowProvider())) return@withLock current
            val refreshToken = current.refreshToken?.takeIf { it.isNotBlank() } ?: return@withLock current
            if (current.provider != GitOAuthProvider.GITLAB) return@withLock current

            val clientId = current.clientId.ifBlank {
                effectiveClientId(GitOAuthProvider.GITLAB, current.normalizedHost)
            }
            if (clientId.isBlank()) return@withLock current

            val refreshed = withContext(ioDispatcher) {
                runCatching {
                    val resp = httpClient.postForm(
                        url = "${current.baseUrl}/oauth/token",
                        formParams = mapOf(
                            "client_id" to clientId,
                            "refresh_token" to refreshToken,
                            "grant_type" to "refresh_token",
                            "redirect_uri" to GitOAuthDefaults.REDIRECT_URI_GITLAB
                        ),
                        headers = mapOf(
                            "Accept" to "application/json",
                            "User-Agent" to USER_AGENT
                        )
                    )
                    if (resp.statusCode !in 200..299) return@runCatching null
                    val json = JSONObject(resp.body)
                    val newAccess = json.optString("access_token", "").trim()
                    if (newAccess.isEmpty()) return@runCatching null
                    val newRefresh = json.optString("refresh_token", "").trim().ifEmpty { refreshToken }
                    val expiresInSec = json.optLong("expires_in", 0L)
                    val newExpiresAt = if (expiresInSec > 0L) nowProvider() + expiresInSec * 1000L else null
                    current.copy(
                        accessToken = newAccess,
                        refreshToken = newRefresh,
                        expiresAtMillis = newExpiresAt
                    )
                }.getOrNull()
            }

            if (refreshed != null) {
                val stored = credentialStore.saveOAuthAccount(refreshed)
                notifyAccountsChanged()
                stored
            } else {
                current
            }
        }
    }

    /**
     * Resolves credentials using the 2-layer / 3-tier hierarchy (Repo -> Project -> Host Default OAuth -> Manual Default)
     * and transparently refreshes expired GitLab OAuth tokens before returning [GitCredentials].
     */
    suspend fun resolveFreshCredentials(
        projectId: String?,
        repoId: String?,
        remoteUrl: String? = null
    ): GitCredentials? {
        val oauthAccount = credentialStore.resolveOAuthAccountForRepo(projectId, repoId, remoteUrl)
        if (oauthAccount != null) {
            val fresh = refreshAccountIfNeeded(oauthAccount.id) ?: oauthAccount
            return fresh.toGitCredentials()
        }
        return if (!projectId.isNullOrBlank()) {
            credentialStore.resolveForRepo(projectId, repoId, remoteUrl)
        } else {
            credentialStore.getDefaultCredentials()
        }
    }

    private fun autoFillGlobalAuthorIfUnset(account: GitOAuthAccount): Boolean {
        val email = account.email?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val name = account.displayName.trim().ifEmpty { account.username.trim() }
        if (name.isEmpty()) return false
        if (globalAuthorStore.hasCustomAuthor()) return false
        globalAuthorStore.saveAuthor(name, email)
        return true
    }

    private fun pickPrimaryGitHubEmail(body: String): String? {
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null
        var verifiedFallback: String? = null
        var anyFallback: String? = null
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val email = obj.optString("email", "").trim()
            if (email.isEmpty()) continue
            val primary = obj.optBoolean("primary", false)
            val verified = obj.optBoolean("verified", false)
            if (primary && verified) return email
            if (verified && verifiedFallback == null) verifiedFallback = email
            if (anyFallback == null) anyFallback = email
        }
        return verifiedFallback ?: anyFallback
    }

    private fun generateRandomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun computeS256Challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        rawQuery.split('&').forEach { pair ->
            if (pair.isBlank()) return@forEach
            val idx = pair.indexOf('=')
            val key = if (idx >= 0) pair.substring(0, idx) else pair
            val value = if (idx >= 0) pair.substring(idx + 1) else ""
            val decodedKey = runCatching { URLDecoder.decode(key, "UTF-8") }.getOrDefault(key)
            val decodedValue = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
            if (decodedKey.isNotEmpty()) {
                map[decodedKey] = decodedValue
            }
        }
        return map
    }

    companion object {
        private const val USER_AGENT = "PocketWorker-Android"
    }
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
