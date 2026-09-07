package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.RemoteBudgetFile
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import com.azimulkabir.actua.data.sync.EncryptionKeyManager
import com.azimulkabir.actua.data.sync.LoadedEncryptionKey
import com.azimulkabir.actua.data.sync.SyncEncryption
import java.io.ByteArrayInputStream
import java.util.Base64

sealed class BudgetDownloadException(message: String) : Exception(message) {
    data object EncryptionPasswordRequired : BudgetDownloadException("Encryption password required")
    data object EncryptionKeyChanged : BudgetDownloadException("The budget encryption key changed")
    data object InvalidEncryptionMetadata : BudgetDownloadException("Invalid budget encryption metadata")
}

class BudgetDownloadService(
    private val server: ActualServerClient,
    private val files: BudgetFileManager,
    private val keyStore: BudgetEncryptionKeyStore,
) {
    fun unlock(serverUrl: String, token: String, fileId: String, password: String): LoadedEncryptionKey {
        val loaded = EncryptionKeyManager.deriveAndValidate(password, server.getKeyInfo(serverUrl, token, fileId))
        keyStore.store(fileId, loaded)
        return loaded
    }

    fun download(serverUrl: String, token: String, remote: RemoteBudgetFile): BudgetMetadata {
        val key = if (remote.encryptedKeyId != null) {
            keyStore.load(remote.fileId) ?: throw BudgetDownloadException.EncryptionPasswordRequired
        } else null
        var archive = server.downloadFile(serverUrl, token, remote.fileId)
        if (key != null) archive = decryptArchive(serverUrl, token, remote.fileId, key, archive)
        return files.importBudget(ByteArrayInputStream(archive), remote.fileId, remote.groupId)
    }

    private fun decryptArchive(
        serverUrl: String,
        token: String,
        fileId: String,
        key: LoadedEncryptionKey,
        ciphertext: ByteArray,
    ): ByteArray {
        val metadata = server.getFileInfo(serverUrl, token, fileId).encryption
            ?: throw BudgetDownloadException.InvalidEncryptionMetadata
        if (metadata.keyId != key.keyId) {
            keyStore.remove(fileId)
            throw BudgetDownloadException.EncryptionKeyChanged
        }
        val iv = metadata.ivBase64?.decodeBase64OrNull()
            ?: throw BudgetDownloadException.InvalidEncryptionMetadata
        val authTag = metadata.authTagBase64?.decodeBase64OrNull()
            ?: throw BudgetDownloadException.InvalidEncryptionMetadata
        return SyncEncryption.decrypt(ciphertext, iv, authTag, key.key)
    }
}

private fun String.decodeBase64OrNull(): ByteArray? = runCatching { Base64.getDecoder().decode(this) }.getOrNull()
