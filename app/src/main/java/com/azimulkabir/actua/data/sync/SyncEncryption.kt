package com.azimulkabir.actua.data.sync

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed class SyncEncryptionException(message: String) : Exception(message) {
    data object EncryptionFailed : SyncEncryptionException("Unable to encrypt sync data")
    data object DecryptionFailed : SyncEncryptionException("Unable to decrypt sync data")
}

object SyncEncryption {
    private const val ITERATIONS = 10_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12

    /** The salt is the UTF-8 bytes of the base64-looking server string. */
    fun deriveKey(password: String, salt: String): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt.encodeToByteArray(), ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plaintext: ByteArray, key: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        return try {
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            val combined = cipher(Cipher.ENCRYPT_MODE, key, iv).doFinal(plaintext)
            val ciphertext = combined.copyOf(combined.size - TAG_BITS / 8)
            val authTag = combined.copyOfRange(combined.size - TAG_BITS / 8, combined.size)
            SyncProtocol.encodeEncryptedData(iv, authTag, ciphertext)
        } catch (error: GeneralSecurityException) {
            throw SyncEncryptionException.EncryptionFailed
        }
    }

    fun decrypt(payload: ByteArray, key: ByteArray): ByteArray {
        return try {
            val encrypted = SyncProtocol.decodeEncryptedData(payload)
            require(encrypted.iv.size == IV_BYTES && encrypted.authTag.size == TAG_BITS / 8)
            val combined = encrypted.data + encrypted.authTag
            cipher(Cipher.DECRYPT_MODE, key, encrypted.iv).doFinal(combined)
        } catch (error: Exception) {
            throw SyncEncryptionException.DecryptionFailed
        }
    }

    fun decrypt(
        ciphertext: ByteArray,
        iv: ByteArray,
        authTag: ByteArray,
        key: ByteArray,
    ): ByteArray = try {
        cipher(Cipher.DECRYPT_MODE, key, iv).doFinal(ciphertext + authTag)
    } catch (error: GeneralSecurityException) {
        throw SyncEncryptionException.DecryptionFailed
    }

    private fun cipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES") as SecretKey, GCMParameterSpec(TAG_BITS, iv))
        }
}

class ActualMessageCipher(private val key: ByteArray) : MessageCipher {
    init {
        require(key.size == 32) { "Actual encryption keys must be 256 bits" }
    }

    override fun encrypt(plaintext: ByteArray): ByteArray = SyncEncryption.encrypt(plaintext, key)
    override fun decrypt(payload: ByteArray): ByteArray = SyncEncryption.decrypt(payload, key)
}
