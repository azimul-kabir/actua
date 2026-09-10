package com.azimulkabir.actua.data.security

import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.azimulkabir.actua.DisconnectResetActivity
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

    fun saveConnection(url: String, token: String, fallbackUrl: String = "") {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.doFinal(token.toByteArray())
        preferences.edit()
            .putString("server_url", url)
            .putString("fallback_server_url", fallbackUrl)
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("token_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun updateServerUrls(url: String, fallbackUrl: String) {
        preferences.edit().putString("server_url", url)
            .putString("fallback_server_url", fallbackUrl).apply()
    }

    fun token(): String? = runCatching {
        val encrypted = Base64.decode(preferences.getString("token", null), Base64.NO_WRAP)
        val iv = Base64.decode(preferences.getString("token_iv", null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        }
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
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
    }
}
