package com.azimulkabir.actua.data.budget.gdrive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

class EncryptedBackupArchiveTest {

    private val testPassphrase = "correct-horse-battery-staple-42".toCharArray()
    private val wrongPassphrase = "wrong-password-attempt-99".toCharArray()

    @Test
    fun encryptAndDecryptRoundtripMatchesPlaintext() {
        val plaintext = "Hello Actua Google Drive Encrypted Backup!".toByteArray(Charsets.UTF_8)
        val encryptedOut = ByteArrayOutputStream()

        EncryptedBackupArchive.encrypt(
            plaintextInput = ByteArrayInputStream(plaintext),
            encryptedOutput = encryptedOut,
            passphrase = testPassphrase,
        )

        val encryptedBytes = encryptedOut.toByteArray()
        assertTrue(encryptedBytes.size > plaintext.size)

        val decryptedOut = ByteArrayOutputStream()
        EncryptedBackupArchive.decrypt(
            encryptedInput = ByteArrayInputStream(encryptedBytes),
            plaintextOutput = decryptedOut,
            passphrase = testPassphrase,
        )

        assertArrayEquals(plaintext, decryptedOut.toByteArray())
    }

    @Test
    fun multiChunkPayloadRoundtripMatchesPlaintext() {
        // Generate 150 KB payload (spans 3 x 64KB chunks: 64KB + 64KB + 22KB)
        val largePlaintext = ByteArray(150 * 1024)
        Random(12345).nextBytes(largePlaintext)

        val encryptedOut = ByteArrayOutputStream()
        EncryptedBackupArchive.encrypt(
            plaintextInput = ByteArrayInputStream(largePlaintext),
            encryptedOutput = encryptedOut,
            passphrase = testPassphrase,
        )

        val decryptedOut = ByteArrayOutputStream()
        EncryptedBackupArchive.decrypt(
            encryptedInput = ByteArrayInputStream(encryptedOut.toByteArray()),
            plaintextOutput = decryptedOut,
            passphrase = testPassphrase,
        )

        assertArrayEquals(largePlaintext, decryptedOut.toByteArray())
    }

    @Test
    fun fastCanaryValidationAcceptsCorrectPassphraseAndRejectsIncorrect() {
        val plaintext = "Canary validation test".toByteArray()
        val encryptedOut = ByteArrayOutputStream()

        EncryptedBackupArchive.encrypt(
            plaintextInput = ByteArrayInputStream(plaintext),
            encryptedOutput = encryptedOut,
            passphrase = testPassphrase,
        )

        val encryptedBytes = encryptedOut.toByteArray()

        // Correct passphrase passes canary check
        assertTrue(EncryptedBackupArchive.validatePassphrase(ByteArrayInputStream(encryptedBytes), testPassphrase))

        // Incorrect passphrase fails canary check
        assertFalse(EncryptedBackupArchive.validatePassphrase(ByteArrayInputStream(encryptedBytes), wrongPassphrase))
    }

    @Test
    fun decryptionWithIncorrectPassphraseThrowsAuthenticationFailed() {
        val plaintext = "Secret financial transactions and accounts".toByteArray()
        val encryptedOut = ByteArrayOutputStream()

        EncryptedBackupArchive.encrypt(
            plaintextInput = ByteArrayInputStream(plaintext),
            encryptedOutput = encryptedOut,
            passphrase = testPassphrase,
        )

        val decryptedOut = ByteArrayOutputStream()
        assertThrows(BackupEncryptionException.AuthenticationFailed::class.java) {
            EncryptedBackupArchive.decrypt(
                encryptedInput = ByteArrayInputStream(encryptedOut.toByteArray()),
                plaintextOutput = decryptedOut,
                passphrase = wrongPassphrase,
            )
        }
        assertEquals(0, decryptedOut.size())
    }

    @Test
    fun corruptedCiphertextThrowsAuthenticationFailed() {
        val plaintext = "Untampered data integrity test".toByteArray()
        val encryptedOut = ByteArrayOutputStream()

        EncryptedBackupArchive.encrypt(
            plaintextInput = ByteArrayInputStream(plaintext),
            encryptedOutput = encryptedOut,
            passphrase = testPassphrase,
        )

        val tamperedBytes = encryptedOut.toByteArray()
        // Tamper with a byte in the payload area (towards the end)
        tamperedBytes[tamperedBytes.size - 5] = (tamperedBytes[tamperedBytes.size - 5] xor 0xFF.toByte())

        val decryptedOut = ByteArrayOutputStream()
        assertThrows(BackupEncryptionException.AuthenticationFailed::class.java) {
            EncryptedBackupArchive.decrypt(
                encryptedInput = ByteArrayInputStream(tamperedBytes),
                plaintextOutput = decryptedOut,
                passphrase = testPassphrase,
            )
        }
    }

    @Test
    fun corruptedMagicHeaderThrowsInvalidHeader() {
        val plaintext = "Header validation test".toByteArray()
        val encryptedOut = ByteArrayOutputStream()

        EncryptedBackupArchive.encrypt(
            plaintextInput = ByteArrayInputStream(plaintext),
            encryptedOutput = encryptedOut,
            passphrase = testPassphrase,
        )

        val invalidHeaderBytes = encryptedOut.toByteArray()
        invalidHeaderBytes[0] = 'X'.code.toByte()

        assertThrows(BackupEncryptionException.InvalidHeader::class.java) {
            EncryptedBackupArchive.decrypt(
                encryptedInput = ByteArrayInputStream(invalidHeaderBytes),
                plaintextOutput = ByteArrayOutputStream(),
                passphrase = testPassphrase,
            )
        }
    }
}
