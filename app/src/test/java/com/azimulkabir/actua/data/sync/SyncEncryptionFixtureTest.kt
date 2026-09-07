package com.azimulkabir.actua.data.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class SyncEncryptionFixtureTest {
    private val password = "correct horse battery staple"
    private val salt = "XfsOVKaxbU9A5bYyrQAasQrjy0Nvd3rllUW+ZRWCdTE="

    @Test
    fun derivedKeyMatchesActualFixture() {
        val derived = Base64.getEncoder().encodeToString(SyncEncryption.deriveKey(password, salt))
        assertEquals("C5SPIW2Fp7eRmnDhY8TmMUeJ6YgT4FCLWXbT4XJd/jE=", derived)
    }

    @Test
    fun decryptsActualKeyTestFixture() {
        val key = SyncEncryption.deriveKey(password, salt)
        val plaintext = SyncEncryption.decrypt(
            ciphertext = Base64.getDecoder().decode("q6+m+aF63TaiPBXc8fqlqmmUQ4VIJZzk4NFa/uPsmyQ="),
            iv = Base64.getDecoder().decode("AqLh9LX6BdIiavoO"),
            authTag = Base64.getDecoder().decode("ikPp5J5szWjUn3XlE1TQFg=="),
            key = key,
        )
        assertEquals(32, plaintext.size)
    }

    @Test
    fun encryptedSyncMessageRoundTripsThroughProtobufEnvelope() {
        val key = SyncEncryption.deriveKey(password, salt)
        val cipher = ActualMessageCipher(key)
        val plaintext = "S:Chécking ✓".encodeToByteArray()
        val payload = cipher.encrypt(plaintext)
        val decoded = SyncProtocol.decodeEncryptedData(payload)
        assertEquals(12, decoded.iv.size)
        assertEquals(16, decoded.authTag.size)
        assertArrayEquals(plaintext, cipher.decrypt(payload))
    }
}
