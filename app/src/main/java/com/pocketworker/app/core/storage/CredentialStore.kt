package com.pocketworker.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitOAuthAccount
import com.pocketworker.app.core.git.GitOAuthProvider
import com.pocketworker.app.core.git.extractHostFromGitUrl
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialStore {
    fun getRepoCredentials(repoId: String): GitCredentials?
    fun saveRepoCredentials(repoId: String, credentials: GitCredentials)
    fun removeRepoCredentials(repoId: String)

    fun getProjectCredentials(projectId: String): GitCredentials?
    fun saveProjectCredentials(projectId: String, credentials: GitCredentials)
    fun removeProjectCredentials(projectId: String)

    fun getDefaultCredentials(): GitCredentials?
    fun saveDefaultCredentials(credentials: GitCredentials)
    fun clearDefaultCredentials()

    // Connected OAuth accounts (Layer 1)
    fun getOAuthAccounts(): List<GitOAuthAccount> = emptyList()
    fun getOAuthAccount(accountId: String): GitOAuthAccount? =
        getOAuthAccounts().firstOrNull { it.id == accountId }

    /** Saves or updates an OAuth account and returns the stored account (preserving ID on re-login). */
    fun saveOAuthAccount(account: GitOAuthAccount): GitOAuthAccount = account
    fun removeOAuthAccount(accountId: String) = Unit

    fun getDefaultOAuthAccountIdsByHost(): Map<String, String> = emptyMap()
    fun getDefaultOAuthAccountForHost(host: String): GitOAuthAccount? {
        val norm = host.trim().lowercase(Locale.ROOT)
        if (norm.isEmpty()) return null
        val accounts = getOAuthAccounts()
        val preferredId = getDefaultOAuthAccountIdsByHost()[norm]
        return accounts.firstOrNull { it.id == preferredId && it.normalizedHost == norm }
            ?: accounts.firstOrNull { it.normalizedHost == norm }
    }

    fun setDefaultOAuthAccountForHost(host: String, accountId: String) = Unit

    // Project / Repo OAuth account bindings (Layer 2)
    fun getProjectOAuthAccountId(projectId: String): String? = null
    fun setProjectOAuthAccountId(projectId: String, accountId: String?) = Unit
    fun getRepoOAuthAccountId(repoId: String): String? = null
    fun setRepoOAuthAccountId(repoId: String, accountId: String?) = Unit

    // Persisted OAuth Client IDs (optional runtime override / self-hosted GitLab server config)
    fun getOAuthClientId(provider: GitOAuthProvider, host: String = provider.defaultHost): String? = null
    fun saveOAuthClientId(provider: GitOAuthProvider, clientId: String, host: String = provider.defaultHost) = Unit

    fun getAiApiKey(provider: AiProvider): String?
    fun saveAiApiKey(provider: AiProvider, apiKey: String)
    fun clearAiApiKey(provider: AiProvider)

    fun getAiProxyUrl(): String?
    fun saveAiProxyUrl(proxyUrl: String)
    fun clearAiProxyUrl()

    fun getAllAiApiKeys(): Map<AiProvider, String> {
        val map = LinkedHashMap<AiProvider, String>()
        AiProvider.entries.forEach { provider ->
            val key = getAiApiKey(provider)
            if (!key.isNullOrBlank()) {
                map[provider] = key
            }
        }
        return map
    }

    /**
     * Resolves the OAuth account that applies for [projectId]/[repoId]/[remoteUrl] if OAuth takes
     * precedence over manual PATs at the winning scope.
     *
     * Priority:
     * 1. Repo explicit OAuth binding (if manual repo PAT is set instead, returns null so repo PAT wins)
     * 2. Project explicit OAuth binding (if manual project PAT is set instead, returns null so project PAT wins)
     * 3. Default connected OAuth account matching [remoteUrl]'s host
     */
    fun resolveOAuthAccountForRepo(
        projectId: String?,
        repoId: String?,
        remoteUrl: String? = null
    ): GitOAuthAccount? {
        if (!repoId.isNullOrBlank()) {
            getRepoOAuthAccountId(repoId)?.let { id ->
                getOAuthAccount(id)?.let { return it }
            }
            if (getRepoCredentials(repoId) != null) return null
        }
        if (!projectId.isNullOrBlank()) {
            getProjectOAuthAccountId(projectId)?.let { id ->
                getOAuthAccount(id)?.let { return it }
            }
            if (getProjectCredentials(projectId) != null) return null
        }
        val host = extractHostFromGitUrl(remoteUrl) ?: return null
        return getDefaultOAuthAccountForHost(host)
    }

    fun resolveForRepo(
        projectId: String,
        repoId: String?,
        remoteUrl: String?
    ): GitCredentials? {
        if (!repoId.isNullOrBlank()) {
            getRepoOAuthAccountId(repoId)?.let { id ->
                getOAuthAccount(id)?.let { return it.toGitCredentials() }
            }
            getRepoCredentials(repoId)?.let { return it }
        }
        getProjectOAuthAccountId(projectId)?.let { id ->
            getOAuthAccount(id)?.let { return it.toGitCredentials() }
        }
        getProjectCredentials(projectId)?.let { return it }
        extractHostFromGitUrl(remoteUrl)?.let { host ->
            getDefaultOAuthAccountForHost(host)?.let { return it.toGitCredentials() }
        }
        return getDefaultCredentials()
    }

    fun resolveForRepo(projectId: String, repoId: String?): GitCredentials? =
        resolveForRepo(projectId = projectId, repoId = repoId, remoteUrl = null)
}

class InMemoryCredentialStore : CredentialStore {
    private val byRepo = mutableMapOf<String, GitCredentials>()
    private val byProject = mutableMapOf<String, GitCredentials>()
    private var defaultCreds: GitCredentials? = null
    private val oauthAccounts = mutableListOf<GitOAuthAccount>()
    private val hostDefaults = mutableMapOf<String, String>()
    private val projectOAuthBindings = mutableMapOf<String, String>()
    private val repoOAuthBindings = mutableMapOf<String, String>()
    private val oauthClientIds = mutableMapOf<String, String>()
    private val aiApiKeys = mutableMapOf<AiProvider, String>()
    private var aiProxyUrl: String? = null

    override fun getRepoCredentials(repoId: String): GitCredentials? = byRepo[repoId]

    override fun saveRepoCredentials(repoId: String, credentials: GitCredentials) {
        if (credentials.tokenOrPassword.isBlank()) {
            removeRepoCredentials(repoId)
            return
        }
        repoOAuthBindings.remove(repoId)
        byRepo[repoId] = credentials
    }

    override fun removeRepoCredentials(repoId: String) {
        byRepo.remove(repoId)
    }

    override fun getProjectCredentials(projectId: String): GitCredentials? = byProject[projectId]

    override fun saveProjectCredentials(projectId: String, credentials: GitCredentials) {
        if (credentials.tokenOrPassword.isBlank()) {
            removeProjectCredentials(projectId)
            return
        }
        projectOAuthBindings.remove(projectId)
        byProject[projectId] = credentials
    }

    override fun removeProjectCredentials(projectId: String) {
        byProject.remove(projectId)
    }

    override fun getDefaultCredentials(): GitCredentials? = defaultCreds

    override fun saveDefaultCredentials(credentials: GitCredentials) {
        if (credentials.tokenOrPassword.isBlank()) {
            clearDefaultCredentials()
            return
        }
        defaultCreds = credentials
    }

    override fun clearDefaultCredentials() {
        defaultCreds = null
    }

    override fun getOAuthAccounts(): List<GitOAuthAccount> = oauthAccounts.toList()

    override fun saveOAuthAccount(account: GitOAuthAccount): GitOAuthAccount {
        val existingIdx = oauthAccounts.indexOfFirst {
            it.id == account.id || (
                it.provider == account.provider &&
                    it.normalizedHost == account.normalizedHost &&
                    it.username.equals(account.username, ignoreCase = true)
                )
        }
        val stored = if (existingIdx >= 0) {
            val preserved = account.copy(
                id = oauthAccounts[existingIdx].id,
                createdAtMillis = oauthAccounts[existingIdx].createdAtMillis
            )
            oauthAccounts[existingIdx] = preserved
            preserved
        } else {
            oauthAccounts.add(account)
            account
        }
        if (hostDefaults[stored.normalizedHost] == null) {
            hostDefaults[stored.normalizedHost] = stored.id
        }
        return stored
    }

    override fun removeOAuthAccount(accountId: String) {
        val removed = oauthAccounts.firstOrNull { it.id == accountId } ?: return
        oauthAccounts.removeAll { it.id == accountId }
        projectOAuthBindings.entries.removeAll { it.value == accountId }
        repoOAuthBindings.entries.removeAll { it.value == accountId }
        if (hostDefaults[removed.normalizedHost] == accountId) {
            val replacement = oauthAccounts.firstOrNull { it.normalizedHost == removed.normalizedHost }
            if (replacement != null) {
                hostDefaults[removed.normalizedHost] = replacement.id
            } else {
                hostDefaults.remove(removed.normalizedHost)
            }
        }
    }

    override fun getDefaultOAuthAccountIdsByHost(): Map<String, String> = hostDefaults.toMap()

    override fun setDefaultOAuthAccountForHost(host: String, accountId: String) {
        val norm = host.trim().lowercase(Locale.ROOT)
        if (norm.isNotEmpty() && oauthAccounts.any { it.id == accountId && it.normalizedHost == norm }) {
            hostDefaults[norm] = accountId
        }
    }

    override fun getProjectOAuthAccountId(projectId: String): String? {
        val id = projectOAuthBindings[projectId] ?: return null
        return id.takeIf { oauthAccounts.any { acc -> acc.id == it } }
    }

    override fun setProjectOAuthAccountId(projectId: String, accountId: String?) {
        val clean = accountId?.trim()?.takeIf { it.isNotEmpty() }
        if (clean == null) {
            projectOAuthBindings.remove(projectId)
        } else {
            byProject.remove(projectId)
            projectOAuthBindings[projectId] = clean
        }
    }

    override fun getRepoOAuthAccountId(repoId: String): String? {
        val id = repoOAuthBindings[repoId] ?: return null
        return id.takeIf { oauthAccounts.any { acc -> acc.id == it } }
    }

    override fun setRepoOAuthAccountId(repoId: String, accountId: String?) {
        val clean = accountId?.trim()?.takeIf { it.isNotEmpty() }
        if (clean == null) {
            repoOAuthBindings.remove(repoId)
        } else {
            byRepo.remove(repoId)
            repoOAuthBindings[repoId] = clean
        }
    }

    override fun getOAuthClientId(provider: GitOAuthProvider, host: String): String? {
        val key = "${provider.id}:${host.trim().lowercase(Locale.ROOT)}"
        return oauthClientIds[key]
    }

    override fun saveOAuthClientId(provider: GitOAuthProvider, clientId: String, host: String) {
        val key = "${provider.id}:${host.trim().lowercase(Locale.ROOT)}"
        val clean = clientId.trim()
        if (clean.isEmpty()) oauthClientIds.remove(key) else oauthClientIds[key] = clean
    }

    override fun getAiApiKey(provider: AiProvider): String? = aiApiKeys[provider]

    override fun saveAiApiKey(provider: AiProvider, apiKey: String) {
        val clean = apiKey.trim()
        if (clean.isEmpty()) {
            aiApiKeys.remove(provider)
        } else {
            aiApiKeys[provider] = clean
        }
    }

    override fun clearAiApiKey(provider: AiProvider) {
        aiApiKeys.remove(provider)
    }

    override fun getAiProxyUrl(): String? = aiProxyUrl

    override fun saveAiProxyUrl(proxyUrl: String) {
        val clean = proxyUrl.trim()
        aiProxyUrl = clean.ifEmpty { null }
    }

    override fun clearAiProxyUrl() {
        aiProxyUrl = null
    }
}

class KeystoreCredentialStore(context: Context) : CredentialStore {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun getRepoCredentials(repoId: String): GitCredentials? {
        return readEncrypted(KEY_REPO_PREFIX + repoId)
    }

    override fun saveRepoCredentials(repoId: String, credentials: GitCredentials) {
        if (credentials.tokenOrPassword.isBlank()) {
            removeRepoCredentials(repoId)
            return
        }
        prefs.edit().remove(KEY_REPO_OAUTH_PREFIX + repoId).apply()
        writeEncrypted(KEY_REPO_PREFIX + repoId, credentials)
    }

    override fun removeRepoCredentials(repoId: String) {
        prefs.edit().remove(KEY_REPO_PREFIX + repoId).apply()
    }

    override fun getProjectCredentials(projectId: String): GitCredentials? {
        return readEncrypted(KEY_PROJECT_PREFIX + projectId)
    }

    override fun saveProjectCredentials(projectId: String, credentials: GitCredentials) {
        if (credentials.tokenOrPassword.isBlank()) {
            removeProjectCredentials(projectId)
            return
        }
        prefs.edit().remove(KEY_PROJECT_OAUTH_PREFIX + projectId).apply()
        writeEncrypted(KEY_PROJECT_PREFIX + projectId, credentials)
    }

    override fun removeProjectCredentials(projectId: String) {
        prefs.edit().remove(KEY_PROJECT_PREFIX + projectId).apply()
    }

    override fun getDefaultCredentials(): GitCredentials? {
        return readEncrypted(KEY_DEFAULT)
    }

    override fun saveDefaultCredentials(credentials: GitCredentials) {
        if (credentials.tokenOrPassword.isBlank()) {
            clearDefaultCredentials()
            return
        }
        writeEncrypted(KEY_DEFAULT, credentials)
    }

    override fun clearDefaultCredentials() {
        prefs.edit().remove(KEY_DEFAULT).apply()
    }

    @Synchronized
    override fun getOAuthAccounts(): List<GitOAuthAccount> {
        val encoded = prefs.getString(KEY_OAUTH_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val decrypted = decrypt(encoded)
            val arr = JSONArray(String(decrypted, StandardCharsets.UTF_8))
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    GitOAuthAccount.fromJson(obj)?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    override fun saveOAuthAccount(account: GitOAuthAccount): GitOAuthAccount {
        val current = getOAuthAccounts().toMutableList()
        val existingIdx = current.indexOfFirst {
            it.id == account.id || (
                it.provider == account.provider &&
                    it.normalizedHost == account.normalizedHost &&
                    it.username.equals(account.username, ignoreCase = true)
                )
        }
        val stored = if (existingIdx >= 0) {
            val preserved = account.copy(
                id = current[existingIdx].id,
                createdAtMillis = current[existingIdx].createdAtMillis
            )
            current[existingIdx] = preserved
            preserved
        } else {
            current.add(account)
            account
        }
        writeOAuthAccounts(current)

        val defaults = readHostDefaults().toMutableMap()
        if (defaults[stored.normalizedHost].isNullOrBlank()) {
            defaults[stored.normalizedHost] = stored.id
            writeHostDefaults(defaults)
        }
        return stored
    }

    @Synchronized
    override fun removeOAuthAccount(accountId: String) {
        val current = getOAuthAccounts().toMutableList()
        val removed = current.firstOrNull { it.id == accountId } ?: return
        current.removeAll { it.id == accountId }
        writeOAuthAccounts(current)

        val editor = prefs.edit()
        prefs.all.forEach { (k, v) ->
            if ((k.startsWith(KEY_PROJECT_OAUTH_PREFIX) || k.startsWith(KEY_REPO_OAUTH_PREFIX)) && v == accountId) {
                editor.remove(k)
            }
        }
        editor.apply()

        val defaults = readHostDefaults().toMutableMap()
        if (defaults[removed.normalizedHost] == accountId) {
            val replacement = current.firstOrNull { it.normalizedHost == removed.normalizedHost }
            if (replacement != null) {
                defaults[removed.normalizedHost] = replacement.id
            } else {
                defaults.remove(removed.normalizedHost)
            }
            writeHostDefaults(defaults)
        }
    }

    @Synchronized
    override fun getDefaultOAuthAccountIdsByHost(): Map<String, String> = readHostDefaults()

    @Synchronized
    override fun setDefaultOAuthAccountForHost(host: String, accountId: String) {
        val norm = host.trim().lowercase(Locale.ROOT)
        if (norm.isEmpty()) return
        val accounts = getOAuthAccounts()
        if (accounts.none { it.id == accountId && it.normalizedHost == norm }) return
        val defaults = readHostDefaults().toMutableMap()
        defaults[norm] = accountId
        writeHostDefaults(defaults)
    }

    override fun getProjectOAuthAccountId(projectId: String): String? {
        val id = prefs.getString(KEY_PROJECT_OAUTH_PREFIX + projectId, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return id.takeIf { getOAuthAccount(it) != null }
    }

    override fun setProjectOAuthAccountId(projectId: String, accountId: String?) {
        val clean = accountId?.trim()?.takeIf { it.isNotEmpty() }
        val editor = prefs.edit()
        if (clean == null) {
            editor.remove(KEY_PROJECT_OAUTH_PREFIX + projectId)
        } else {
            editor.remove(KEY_PROJECT_PREFIX + projectId)
            editor.putString(KEY_PROJECT_OAUTH_PREFIX + projectId, clean)
        }
        editor.apply()
    }

    override fun getRepoOAuthAccountId(repoId: String): String? {
        val id = prefs.getString(KEY_REPO_OAUTH_PREFIX + repoId, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return id.takeIf { getOAuthAccount(it) != null }
    }

    override fun setRepoOAuthAccountId(repoId: String, accountId: String?) {
        val clean = accountId?.trim()?.takeIf { it.isNotEmpty() }
        val editor = prefs.edit()
        if (clean == null) {
            editor.remove(KEY_REPO_OAUTH_PREFIX + repoId)
        } else {
            editor.remove(KEY_REPO_PREFIX + repoId)
            editor.putString(KEY_REPO_OAUTH_PREFIX + repoId, clean)
        }
        editor.apply()
    }

    override fun getOAuthClientId(provider: GitOAuthProvider, host: String): String? {
        val key = oauthClientIdKey(provider, host)
        return prefs.getString(key, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    override fun saveOAuthClientId(provider: GitOAuthProvider, clientId: String, host: String) {
        val key = oauthClientIdKey(provider, host)
        val clean = clientId.trim()
        if (clean.isEmpty()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, clean).apply()
        }
    }

    private fun oauthClientIdKey(provider: GitOAuthProvider, host: String): String =
        "${KEY_OAUTH_CLIENT_ID_PREFIX}${provider.id}_${host.trim().lowercase(Locale.ROOT)}"

    private fun writeOAuthAccounts(accounts: List<GitOAuthAccount>) {
        if (accounts.isEmpty()) {
            prefs.edit().remove(KEY_OAUTH_ACCOUNTS).apply()
            return
        }
        val arr = JSONArray()
        accounts.forEach { arr.put(it.toJson()) }
        val encrypted = encrypt(arr.toString().toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString(KEY_OAUTH_ACCOUNTS, encrypted).apply()
    }

    private fun readHostDefaults(): Map<String, String> {
        val raw = prefs.getString(KEY_OAUTH_HOST_DEFAULTS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val result = LinkedHashMap<String, String>()
            obj.keys().forEach { key ->
                val value = obj.optString(key, "").trim()
                if (value.isNotEmpty()) {
                    result[key.lowercase(Locale.ROOT)] = value
                }
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun writeHostDefaults(map: Map<String, String>) {
        if (map.isEmpty()) {
            prefs.edit().remove(KEY_OAUTH_HOST_DEFAULTS).apply()
            return
        }
        val obj = JSONObject()
        map.forEach { (host, id) -> obj.put(host.lowercase(Locale.ROOT), id) }
        prefs.edit().putString(KEY_OAUTH_HOST_DEFAULTS, obj.toString()).apply()
    }

    override fun getAiApiKey(provider: AiProvider): String? {
        if (provider == AiProvider.OPENROUTER || provider == AiProvider.GEMINI) {
            migrateLegacyOpenRouterKeyIfNeeded()
        }
        return readEncrypted(aiKeyStorageKey(provider))?.tokenOrPassword
    }

    private fun migrateLegacyOpenRouterKeyIfNeeded() {
        val legacyKey = readEncrypted(aiKeyStorageKey(AiProvider.GEMINI))?.tokenOrPassword
        if (legacyKey != null && legacyKey.startsWith("sk-or-")) {
            val existingOpenRouter = readEncrypted(aiKeyStorageKey(AiProvider.OPENROUTER))?.tokenOrPassword
            if (existingOpenRouter.isNullOrBlank()) {
                writeEncrypted(
                    aiKeyStorageKey(AiProvider.OPENROUTER),
                    GitCredentials(AiProvider.OPENROUTER.name.lowercase(), legacyKey)
                )
            }
            prefs.edit().remove(aiKeyStorageKey(AiProvider.GEMINI)).apply()
        }
    }

    override fun saveAiApiKey(provider: AiProvider, apiKey: String) {
        val clean = apiKey.trim()
        if (clean.isEmpty()) {
            clearAiApiKey(provider)
            return
        }
        writeEncrypted(aiKeyStorageKey(provider), GitCredentials(provider.name.lowercase(), clean))
    }

    override fun clearAiApiKey(provider: AiProvider) {
        prefs.edit().remove(aiKeyStorageKey(provider)).apply()
    }

    override fun getAiProxyUrl(): String? {
        return readEncrypted(KEY_AI_PROXY_URL)?.tokenOrPassword
    }

    override fun saveAiProxyUrl(proxyUrl: String) {
        val clean = proxyUrl.trim()
        if (clean.isEmpty()) {
            clearAiProxyUrl()
            return
        }
        writeEncrypted(KEY_AI_PROXY_URL, GitCredentials("proxy", clean))
    }

    override fun clearAiProxyUrl() {
        prefs.edit().remove(KEY_AI_PROXY_URL).apply()
    }

    private fun aiKeyStorageKey(provider: AiProvider): String =
        KEY_AI_API_KEY_PREFIX + provider.name.lowercase()

    private fun writeEncrypted(key: String, credentials: GitCredentials) {
        val json = JSONObject().apply {
            put("u", credentials.username)
            put("t", credentials.tokenOrPassword)
        }.toString()
        val encrypted = encrypt(json.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString(key, encrypted).apply()
    }

    private fun readEncrypted(key: String): GitCredentials? {
        val encoded = prefs.getString(key, null) ?: return null
        return runCatching {
            val decrypted = decrypt(encoded)
            val json = JSONObject(String(decrypted, StandardCharsets.UTF_8))
            val token = json.optString("t", "")
            if (token.isBlank()) null else GitCredentials(
                username = json.optString("u", ""),
                tokenOrPassword = token
            )
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plainBytes: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainBytes)
        val combined = ByteArray(1 + iv.size + cipherText.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(cipherText, 0, combined, 1 + iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): ByteArray {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val ivLen = combined[0].toInt() and 0xFF
        val iv = combined.copyOfRange(1, 1 + ivLen)
        val cipherText = combined.copyOfRange(1 + ivLen, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    companion object {
        private const val PREFS_NAME = "pocket_worker_credentials"
        private const val KEY_DEFAULT = "default_git_credentials"
        private const val KEY_OAUTH_ACCOUNTS = "git_oauth_accounts_v1"
        private const val KEY_OAUTH_HOST_DEFAULTS = "git_oauth_host_defaults_v1"
        private const val KEY_PROJECT_OAUTH_PREFIX = "project_oauth_binding_"
        private const val KEY_REPO_OAUTH_PREFIX = "repo_oauth_binding_"
        private const val KEY_OAUTH_CLIENT_ID_PREFIX = "git_oauth_client_id_"
        private const val KEY_AI_API_KEY_PREFIX = "ai_agent_api_key_"
        private const val KEY_AI_PROXY_URL = "ai_agent_proxy_url"
        private const val KEY_PROJECT_PREFIX = "project_creds_"
        private const val KEY_REPO_PREFIX = "repo_creds_"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "pocket_worker_git_creds_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
