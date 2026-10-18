package com.azimulkabir.actua.data.budget.gdrive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GoogleDriveTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long,
    val accountEmail: String?,
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() / 1000 >= (expiresAtEpochSeconds - 60) // 60s safety buffer
}

/**
 * Manages encrypted Google Drive OAuth tokens using Android Keystore.
 * Stored in private SharedPreferences 'actua_gdrive_auth', excluded from backup/device transfer.
 */
class GoogleDriveTokenStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("actua_gdrive_auth", Context.MODE_PRIVATE)

    fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long,
        accountEmail: String? = null,
    ) {
        val existing = loadTokens()
        val effectiveRefreshToken = refreshToken ?: existing?.refreshToken
        val expiresAt = (System.currentTimeMillis() / 1000) + expiresInSeconds

        val json = JSONObject()
            .put("accessToken", accessToken)
            .put("refreshToken", effectiveRefreshToken ?: JSONObject.NULL)
            .put("expiresAt", expiresAt)
            .put("accountEmail", accountEmail ?: existing?.accountEmail ?: JSONObject.NULL)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val iv = cipher.iv

        preferences.edit()
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadTokens(): GoogleDriveTokens? = runCatching {
        val payloadBase64 = preferences.getString("payload", null) ?: return null
        val ivBase64 = preferences.getString("iv", null) ?: return null

        val encrypted = Base64.decode(payloadBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        }
        val decrypted = String(cipher.doFinal(encrypted), Charsets.UTF_8)
        val json = JSONObject(decrypted)

        GoogleDriveTokens(
            accessToken = json.getString("accessToken"),
            refreshToken = if (json.isNull("refreshToken")) null else json.optString("refreshToken").takeIf(String::isNotBlank),
            expiresAtEpochSeconds = json.optLong("expiresAt", 0L),
            accountEmail = if (json.isNull("accountEmail")) null else json.optString("accountEmail").takeIf(String::isNotBlank),
        )
    }.getOrNull()

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val KEY_ALIAS = "actua_gdrive_tokens"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
