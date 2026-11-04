package com.azimulkabir.actua.data.budget.webdav

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The saved WebDAV connection. The app password and the backup encryption password are encrypted with an
 * Android Keystore key (like the Actual server token and the Google Drive backup password) and are never
 * logged. The preferences file is excluded from Android cloud backup and device transfer, and its Keystore key
 * never leaves the device, so a copy of the file could not be read anyway.
 */
class WebDavBackupStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = preferences.contains(SERVER_URL) && preferences.contains(PASSWORD)

    /** The saved connection, or null when none is saved or its password can no longer be decrypted. */
    fun load(): WebDavConfig? {
        val url = preferences.getString(SERVER_URL, null) ?: return null
        val username = preferences.getString(USERNAME, null) ?: return null
        val password = decrypt(PASSWORD) ?: return null
        return WebDavConfig(
            serverUrl = url,
            username = username,
            password = password,
            folder = preferences.getString(FOLDER, WebDavConfig.DEFAULT_FOLDER).orEmpty(),
            allowInsecureHttp = preferences.getBoolean(ALLOW_HTTP, false),
        )
    }

    fun save(config: WebDavConfig) {
        val (password, passwordIv) = encrypt(config.password)
        preferences.edit()
            .putString(SERVER_URL, config.serverUrl.trim())
            .putString(USERNAME, config.username.trim())
            .putString(FOLDER, config.folder.trim())
            .putBoolean(ALLOW_HTTP, config.allowInsecureHttp)
            .putString(PASSWORD, password)
            .putString(PASSWORD + IV_SUFFIX, passwordIv)
            .apply()
    }

    fun hasPassphrase(): Boolean = preferences.contains(PASSPHRASE)

    fun loadPassphrase(): CharArray? = decrypt(PASSPHRASE)?.toCharArray()

    fun savePassphrase(passphrase: CharArray) {
        val (encrypted, iv) = encrypt(String(passphrase))
        preferences.edit().putString(PASSPHRASE, encrypted).putString(PASSPHRASE + IV_SUFFIX, iv).apply()
    }

    var lastSuccessfulBackupMillis: Long
        get() = preferences.getLong(LAST_SUCCESS, 0L)
        set(value) { preferences.edit().putLong(LAST_SUCCESS, value).apply() }

    var lastBackupError: String?
        get() = preferences.getString(LAST_ERROR, null)
        set(value) { preferences.edit().apply { if (value == null) remove(LAST_ERROR) else putString(LAST_ERROR, value) }.apply() }

    /** Forgets the address, both passwords and the status. Backups already on the server are not touched. */
    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun decrypt(key: String): String? = runCatching {
        val encrypted = Base64.decode(preferences.getString(key, null) ?: return null, Base64.NO_WRAP)
        val iv = Base64.decode(preferences.getString(key + IV_SUFFIX, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        }
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    /** Returns the Base64 ciphertext and IV. */
    private fun encrypt(plaintext: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES = "webdav_backup"
        const val KEY_ALIAS = "actua_webdav_backup"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SUFFIX = "_iv"
        const val SERVER_URL = "server_url"
        const val USERNAME = "username"
        const val FOLDER = "folder"
        const val ALLOW_HTTP = "allow_insecure_http"
        const val PASSWORD = "password"
        const val PASSPHRASE = "passphrase"
        const val LAST_SUCCESS = "last_successful_backup_millis"
        const val LAST_ERROR = "last_backup_error"
    }
}
