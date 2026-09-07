package com.azimulkabir.actua.data.sync

import org.json.JSONObject
import java.util.Base64

data class ServerKeyInfo(val id: String, val salt: String, val test: String?)
data class LoadedEncryptionKey(val keyId: String, val key: ByteArray) {
    override fun equals(other: Any?): Boolean = other is LoadedEncryptionKey &&
        keyId == other.keyId && key.contentEquals(other.key)
    override fun hashCode(): Int = 31 * keyId.hashCode() + key.contentHashCode()
}

sealed class EncryptionKeyException(message: String) : Exception(message) {
    data object InvalidPassword : EncryptionKeyException("Incorrect encryption password")
    data object UnsupportedLegacyKey : EncryptionKeyException("Unsupported legacy encryption key")
    data object MalformedTestMessage : EncryptionKeyException("Unreadable encryption key test")
}

object EncryptionKeyManager {
    fun deriveAndValidate(password: String, keyInfo: ServerKeyInfo): LoadedEncryptionKey {
        val testJson = keyInfo.test ?: throw EncryptionKeyException.UnsupportedLegacyKey
        val test = try {
            val json = JSONObject(testJson)
            val metadata = json.getJSONObject("meta")
            KeyTest(
                ciphertext = Base64.getDecoder().decode(json.getString("value")),
                iv = Base64.getDecoder().decode(metadata.getString("iv")),
                authTag = Base64.getDecoder().decode(metadata.getString("authTag")),
            )
        } catch (error: Exception) {
            throw EncryptionKeyException.MalformedTestMessage
        }
        val key = SyncEncryption.deriveKey(password, keyInfo.salt)
        try {
            SyncEncryption.decrypt(test.ciphertext, test.iv, test.authTag, key)
        } catch (error: SyncEncryptionException) {
            throw EncryptionKeyException.InvalidPassword
        }
        return LoadedEncryptionKey(keyInfo.id, key)
    }

    private data class KeyTest(val ciphertext: ByteArray, val iv: ByteArray, val authTag: ByteArray)
}
