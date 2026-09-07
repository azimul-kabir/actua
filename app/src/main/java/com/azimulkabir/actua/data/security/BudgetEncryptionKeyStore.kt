package com.azimulkabir.actua.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.azimulkabir.actua.data.sync.LoadedEncryptionKey
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores derived budget keys, never encryption passwords. */
class BudgetEncryptionKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("budget_encryption", Context.MODE_PRIVATE)

    fun store(fileId: String, loadedKey: LoadedEncryptionKey) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val encrypted = cipher.doFinal(loadedKey.key)
        val value = JSONObject()
            .put("keyId", loadedKey.keyId)
            .put("key", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .toString()
        preferences.edit().putString(preferenceKey(fileId), value).apply()
    }

    fun load(fileId: String): LoadedEncryptionKey? = runCatching {
        val json = JSONObject(preferences.getString(preferenceKey(fileId), null) ?: return null)
        val encrypted = Base64.decode(json.getString("key"), Base64.NO_WRAP)
        val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        }
        LoadedEncryptionKey(json.getString("keyId"), cipher.doFinal(encrypted))
    }.getOrNull()

    fun remove(fileId: String) {
        preferences.edit().remove(preferenceKey(fileId)).apply()
    }

    private fun preferenceKey(fileId: String): String = "encryptKey.$fileId"

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
        private const val KEY_ALIAS = "actua_budget_encryption_keys"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
