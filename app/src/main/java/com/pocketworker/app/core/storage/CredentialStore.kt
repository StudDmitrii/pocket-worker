package com.pocketworker.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.pocketworker.app.core.git.GitCredentials
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialStore {
    fun getProjectCredentials(projectId: String): GitCredentials?
    fun saveProjectCredentials(projectId: String, credentials: GitCredentials)
    fun removeProjectCredentials(projectId: String)

    fun getDefaultCredentials(): GitCredentials?
    fun saveDefaultCredentials(credentials: GitCredentials)
    fun clearDefaultCredentials()
}

class InMemoryCredentialStore : CredentialStore {
    private val byProject = mutableMapOf<String, GitCredentials>()
    private var defaultCreds: GitCredentials? = null

    override fun getProjectCredentials(projectId: String): GitCredentials? = byProject[projectId]

    override fun saveProjectCredentials(projectId: String, credentials: GitCredentials) {
        byProject[projectId] = credentials
    }

    override fun removeProjectCredentials(projectId: String) {
        byProject.remove(projectId)
    }

    override fun getDefaultCredentials(): GitCredentials? = defaultCreds

    override fun saveDefaultCredentials(credentials: GitCredentials) {
        defaultCreds = credentials
    }

    override fun clearDefaultCredentials() {
        defaultCreds = null
    }
}

class KeystoreCredentialStore(context: Context) : CredentialStore {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun getProjectCredentials(projectId: String): GitCredentials? {
        return readEncrypted(KEY_PROJECT_PREFIX + projectId)
    }

    override fun saveProjectCredentials(projectId: String, credentials: GitCredentials) {
        if (credentials.tokenOrPassword.isBlank()) {
            removeProjectCredentials(projectId)
            return
        }
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
        private const val KEY_PROJECT_PREFIX = "project_creds_"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "pocket_worker_git_creds_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
