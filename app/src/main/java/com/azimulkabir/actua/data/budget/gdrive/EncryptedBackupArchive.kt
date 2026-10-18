package com.azimulkabir.actua.data.budget.gdrive

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed class BackupEncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidHeader : BackupEncryptionException("The backup archive header is invalid or unrecognized")
    data object UnsupportedVersion : BackupEncryptionException("Unsupported backup archive encryption version")
    data object AuthenticationFailed : BackupEncryptionException("Authentication failed: invalid passphrase or corrupted backup")
    data object CorruptedPayload : BackupEncryptionException("The encrypted backup payload is corrupted or truncated")
}

/**
 * Versioned, authenticated client-side streaming encryption for backup archives.
 *
 * Container format (Version 1):
 * - Magic (8 bytes): "ACTUABKP"
 * - Version (1 byte): 0x01
 * - Salt (16 bytes): Random cryptographic salt
 * - KDF Iterations (4 bytes Int): Number of PBKDF2-HMAC-SHA512 iterations (default: 100,000)
 * - Canary IV (12 bytes) + Canary Ciphertext (32 bytes): Encrypted "ACTUA_CANARY_OK" verification token
 * - Streaming Chunks:
 *     - Chunk length (4 bytes Int, 0 terminates)
 *     - Final flag (1 byte: 1 if EOF, 0 otherwise)
 *     - Chunk IV (12 bytes)
 *     - Ciphertext + GCM Tag (16 bytes)
 */
object EncryptedBackupArchive {
    private val MAGIC = "ACTUABKP".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION: Byte = 1
    private const val DEFAULT_ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val SALT_BYTES = 16
    private const val CHUNK_SIZE = 64 * 1024 // 64 KB streaming chunk size
    private val CANARY_PLAINTEXT = "ACTUA_CANARY_OK!".toByteArray(Charsets.US_ASCII) // Exactly 16 bytes

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Checks whether the provided passphrase correctly decrypts the canary token in the header.
     * Returns true if valid, false if invalid, without streaming or decrypting the payload.
     */
    fun validatePassphrase(input: InputStream, passphrase: CharArray): Boolean {
        val dataIn = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        dataIn.readFully(magic)
        if (!magic.contentEquals(MAGIC)) return false

        val version = dataIn.readByte()
        if (version != FORMAT_VERSION) return false

        val salt = ByteArray(SALT_BYTES)
        dataIn.readFully(salt)
        val iterations = dataIn.readInt()

        val canaryIv = ByteArray(IV_BYTES)
        dataIn.readFully(canaryIv)
        val canaryEncrypted = ByteArray(CANARY_PLAINTEXT.size + TAG_BITS / 8)
        dataIn.readFully(canaryEncrypted)

        val key = deriveKey(passphrase, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES") as SecretKey, GCMParameterSpec(TAG_BITS, canaryIv))
            }
            val decryptedCanary = cipher.doFinal(canaryEncrypted)
            decryptedCanary.contentEquals(CANARY_PLAINTEXT)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Encrypts plaintext stream into versioned authenticated archive output.
     */
    fun encrypt(
        plaintextInput: InputStream,
        encryptedOutput: OutputStream,
        passphrase: CharArray,
        random: SecureRandom = SecureRandom(),
    ) {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt, DEFAULT_ITERATIONS)

        val dataOut = DataOutputStream(encryptedOutput)
        dataOut.write(MAGIC)
        dataOut.writeByte(FORMAT_VERSION.toInt())
        dataOut.write(salt)
        dataOut.writeInt(DEFAULT_ITERATIONS)

        // Write canary token
        val canaryIv = ByteArray(IV_BYTES).also(random::nextBytes)
        val canaryCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES") as SecretKey, GCMParameterSpec(TAG_BITS, canaryIv))
        }
        val canaryEncrypted = canaryCipher.doFinal(CANARY_PLAINTEXT)
        dataOut.write(canaryIv)
        dataOut.write(canaryEncrypted)

        // Stream plaintext in chunks
        val buffer = ByteArray(CHUNK_SIZE)
        var chunkIndex = 0L
        while (true) {
            val read = readChunk(plaintextInput, buffer)
            val isFinal = read < CHUNK_SIZE
            val chunkIv = generateChunkIv(random, chunkIndex)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES") as SecretKey, GCMParameterSpec(TAG_BITS, chunkIv))
            }
            val ciphertext = cipher.doFinal(buffer, 0, read)

            dataOut.writeInt(ciphertext.size)
            dataOut.writeByte(if (isFinal) 1 else 0)
            dataOut.write(chunkIv)
            dataOut.write(ciphertext)
            chunkIndex++

            if (isFinal) break
        }
        dataOut.flush()
    }

    /**
     * Decrypts versioned authenticated archive input into plaintext output.
     */
    fun decrypt(
        encryptedInput: InputStream,
        plaintextOutput: OutputStream,
        passphrase: CharArray,
    ) {
        val dataIn = DataInputStream(encryptedInput)
        val magic = ByteArray(MAGIC.size)
        dataIn.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw BackupEncryptionException.InvalidHeader

        val version = dataIn.readByte()
        if (version != FORMAT_VERSION) throw BackupEncryptionException.UnsupportedVersion

        val salt = ByteArray(SALT_BYTES)
        dataIn.readFully(salt)
        val iterations = dataIn.readInt()
        if (iterations < 1000) throw BackupEncryptionException.InvalidHeader

        val canaryIv = ByteArray(IV_BYTES)
        dataIn.readFully(canaryIv)
        val canaryEncrypted = ByteArray(CANARY_PLAINTEXT.size + TAG_BITS / 8)
        dataIn.readFully(canaryEncrypted)

        val key = deriveKey(passphrase, salt, iterations)
        try {
            val canaryCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES") as SecretKey, GCMParameterSpec(TAG_BITS, canaryIv))
            }
            val decryptedCanary = canaryCipher.doFinal(canaryEncrypted)
            if (!decryptedCanary.contentEquals(CANARY_PLAINTEXT)) {
                throw BackupEncryptionException.AuthenticationFailed
            }
        } catch (_: GeneralSecurityException) {
            throw BackupEncryptionException.AuthenticationFailed
        }

        while (true) {
            val chunkSize = try {
                dataIn.readInt()
            } catch (_: java.io.EOFException) {
                break
            }
            val isFinal = dataIn.readByte().toInt() == 1
            val chunkIv = ByteArray(IV_BYTES)
            dataIn.readFully(chunkIv)

            val ciphertext = ByteArray(chunkSize)
            dataIn.readFully(ciphertext)

            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES") as SecretKey, GCMParameterSpec(TAG_BITS, chunkIv))
                }
                val plaintext = cipher.doFinal(ciphertext)
                plaintextOutput.write(plaintext)
            } catch (error: GeneralSecurityException) {
                throw BackupEncryptionException.AuthenticationFailed
            }

            if (isFinal) break
        }
        plaintextOutput.flush()
    }

    private fun readChunk(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) break
            offset += count
        }
        return offset
    }

    private fun generateChunkIv(random: SecureRandom, chunkIndex: Long): ByteArray {
        val iv = ByteArray(IV_BYTES)
        random.nextBytes(iv)
        // Embed 64-bit chunk counter in lowest 8 bytes to enforce sequential ordering
        ByteBuffer.wrap(iv, 4, 8).putLong(chunkIndex)
        return iv
    }
}
