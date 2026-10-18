package com.azimulkabir.actua.data.budget.gdrive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages encrypted storage of the user's backup passphrase using the Android Keystore.
 * Stored in private SharedPreferences 'gdrive_backup_encryption', explicitly excluded
 * from Android cloud backups and device-to-device transfer.
 */
class GoogleDriveEncryptionStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("gdrive_backup_encryption", Context.MODE_PRIVATE)

    fun savePassphrase(passphrase: CharArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val bytes = String(passphrase).toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(bytes)
        preferences.edit()
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadPassphrase(): CharArray? = runCatching {
        val payloadBase64 = preferences.getString("payload", null) ?: return null
        val ivBase64 = preferences.getString("iv", null) ?: return null

        val encrypted = Base64.decode(payloadBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        }
        val decrypted = String(cipher.doFinal(encrypted), Charsets.UTF_8)
        decrypted.toCharArray()
    }.getOrNull()

    fun hasPassphrase(): Boolean = preferences.contains("payload")

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
        private const val KEY_ALIAS = "actua_gdrive_backup_passphrase"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /**
         * Generates a high-entropy, human-friendly recovery passphrase.
         * Formatted in 4 groups of 4 characters separated by hyphens (e.g. "k8m2-px9w-4n7v-r2yq").
         */
        fun generateRandomPassphrase(length: Int = 16): String {
            val chars = "abcdefghjkmnpqrstuvwxyz23456789"
            val random = SecureRandom()
            val sb = StringBuilder()
            for (i in 0 until length) {
                if (i > 0 && i % 4 == 0) sb.append('-')
                sb.append(chars[random.nextInt(chars.length)])
            }
            return sb.toString()
        }
    }
}
