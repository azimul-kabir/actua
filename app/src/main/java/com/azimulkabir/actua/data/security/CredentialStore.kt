package com.azimulkabir.actua.data.security

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.azimulkabir.actua.DisconnectResetActivity
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("connection", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = preferences.getString("server_url", "").orEmpty()
        private set(value) { preferences.edit().putString("server_url", value).apply() }

    var fallbackServerUrl: String
        get() = preferences.getString("fallback_server_url", "").orEmpty()
        private set(value) { preferences.edit().putString("fallback_server_url", value).apply() }

    /**
     * Sent with every request to the server, e.g. Cloudflare Access service-token headers. The values
     * are secrets, so they are encrypted with the same Keystore key as the token. Headers saved as
     * plaintext by older versions are encrypted, and the plaintext removed, on first read.
     */
    var customHeaders: Map<String, String>
        get() {
            preferences.getString(LEGACY_HEADERS, null)?.let { legacy ->
                val headers = runCatching { parseHeaders(legacy) }.getOrDefault(emptyMap())
                writeHeaders(headers, preferences.edit()).commit()
                return headers
            }
            val encrypted = preferences.getString(HEADERS, null) ?: return emptyMap()
            val iv = preferences.getString(HEADERS_IV, null) ?: return emptyMap()
            // Like the token, unreadable after a device transfer because the Keystore key stays behind.
            return runCatching { parseHeaders(decrypt(encrypted, iv)) }.getOrDefault(emptyMap())
        }
        set(value) {
            writeHeaders(value, preferences.edit()).apply()
        }

    fun saveConnection(url: String, token: String, fallbackUrl: String = "") {
        val (encrypted, iv) = encrypt(token)
        preferences.edit()
            .putString("server_url", url)
            .putString("fallback_server_url", fallbackUrl)
            .putString("token", encrypted)
            .putString("token_iv", iv)
            .remove(SESSION_EXPIRED)
            .apply()
    }

    /** True after the server rejected the saved token, until the user signs in again. */
    val sessionExpired: Boolean get() = preferences.getBoolean(SESSION_EXPIRED, false)

    /**
     * Forgets only the rejected token, like Actual removing `user-token`. The server addresses,
     * headers, downloaded budgets, their encryption keys and unsynced changes are kept for re-sign-in.
     */
    fun expireSession() {
        preferences.edit().remove("token").remove("token_iv").putBoolean(SESSION_EXPIRED, true).commit()
    }

    fun updateServerUrls(url: String, fallbackUrl: String) {
        preferences.edit().putString("server_url", url)
            .putString("fallback_server_url", fallbackUrl).apply()
    }

    fun token(): String? = runCatching {
        decrypt(preferences.getString("token", null)!!, preferences.getString("token_iv", null)!!)
    }.getOrNull()

    /** Manual disconnect is protected by a final-sync/reset confirmation flow. */
    fun clear() {
        appContext.startActivity(Intent(appContext, DisconnectResetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    internal fun clearCredentialsNow() {
        preferences.edit().clear().commit()
    }

    private fun writeHeaders(headers: Map<String, String>, editor: SharedPreferences.Editor): SharedPreferences.Editor {
        editor.remove(LEGACY_HEADERS)
        if (headers.isEmpty()) return editor.remove(HEADERS).remove(HEADERS_IV)
        val json = JSONObject().apply { headers.forEach { (name, headerValue) -> put(name, headerValue) } }
        val (encrypted, iv) = encrypt(json.toString())
        return editor.putString(HEADERS, encrypted).putString(HEADERS_IV, iv)
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        val json = JSONObject(raw)
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    /** Returns the Base64 ciphertext and IV. */
    private fun encrypt(plaintext: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.doFinal(plaintext.toByteArray())
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(encrypted: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    companion object {
        private const val KEY_ALIAS = "actua_server_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val SESSION_EXPIRED = "session_expired"
        private const val LEGACY_HEADERS = "custom_headers"
        private const val HEADERS = "custom_headers_encrypted"
        private const val HEADERS_IV = "custom_headers_iv"
    }
}
