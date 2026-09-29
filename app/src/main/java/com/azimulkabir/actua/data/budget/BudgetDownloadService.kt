package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.ActualServerException
import com.azimulkabir.actua.data.network.FileEncryptionMetadata
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

    /** The installed copy of [remote], if this device has one with a database to open. */
    fun localCopy(remote: RemoteBudgetFile): BudgetMetadata? = files.listLocalBudgets()
        .firstOrNull { it.cloudFileId == remote.fileId && files.databaseFile(it.id).isFile }

    /**
     * Opens the installed copy of [remote] when there is one, like Actual's `load-budget`, and
     * downloads only budgets that aren't on this device. Re-downloading would replace the local
     * database and discard messages that haven't been synced yet.
     */
    fun openOrDownload(serverUrl: String, token: String, remote: RemoteBudgetFile): BudgetMetadata =
        localCopy(remote) ?: download(serverUrl, token, remote)

    /**
     * Like Actual's `download()`, the server's file info is authoritative: its `encryptMeta` decides
     * whether to decrypt, and its group and key id replace whatever the archive's own metadata says.
     * Actual only re-uploads snapshots every few days, so the archive's copy can be stale.
     */
    fun download(serverUrl: String, token: String, remote: RemoteBudgetFile): BudgetMetadata {
        val info = server.getFileInfo(serverUrl, token, remote.fileId)
        if (info.deleted) throw ActualServerException.FileNotFound
        val encryption = info.encryption
        val key = encryption?.let {
            keyStore.load(remote.fileId) ?: throw BudgetDownloadException.EncryptionPasswordRequired
        }
        var archive = server.downloadFile(serverUrl, token, remote.fileId)
        if (encryption != null && key != null) archive = decryptArchive(remote.fileId, encryption, key, archive)
        return files.importBudget(
            ByteArrayInputStream(archive),
            CloudFileIdentity(remote.fileId, info.groupId, encryption?.keyId),
        )
    }

    private fun decryptArchive(
        fileId: String,
        metadata: FileEncryptionMetadata,
        key: LoadedEncryptionKey,
        ciphertext: ByteArray,
    ): ByteArray {
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
