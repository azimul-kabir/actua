package com.azimulkabir.actua.data.budget.backup

import java.io.InputStream
import java.io.OutputStream

/** A backup file held by a remote target (Google Drive today), as the Backups screen lists it. */
data class RemoteBackupItem(
    val id: String,
    val name: String,
    val budgetId: String?,
    val budgetName: String?,
    val sizeBytes: Long,
    val modifiedTime: String?,
    val isEncrypted: Boolean,
)

/** What the shared pipeline tells a [BackupTransport] about the file it is about to store. */
data class BackupUpload(
    /** File name on the target, `<local archive name>.actuabkp`. */
    val name: String,
    val budgetId: String,
    val budgetName: String,
    /** Name of the local `.zip` archive the file was made from. */
    val archiveName: String,
    /** ISO-8601 instant of the upload. */
    val createdAt: String,
    /** Exact size in bytes of the encrypted content, for targets that need it up front. */
    val contentLength: Long,
)

/**
 * Where encrypted backups are kept. A transport only moves bytes and lists what is there: archive creation,
 * ACTUABKP encryption, retention and restore stay in [EncryptedBackupPipeline] so every target behaves the same.
 * Implementations are cheap to create and are used for one operation.
 */
interface BackupTransport {
    /**
     * Stores [content] (an already encrypted ACTUABKP container) for [BackupUpload.budgetId] and returns the
     * stored file. A failed upload must not leave a file that [list] reports as a backup.
     */
    fun upload(request: BackupUpload, content: InputStream): RemoteBackupItem

    /** The backup files held for [budgetId]. Files only: folders or other entries are not backups. */
    fun list(budgetId: String): List<RemoteBackupItem>

    /** The backup files held for every budget, for restoring on a fresh install. */
    fun listAll(): List<RemoteBackupItem>

    /** Writes the stored file [backupId] to [output]. */
    fun download(backupId: String, output: OutputStream)

    /** Removes [backupId]. Removing a file that is already gone is not an error. */
    fun delete(backupId: String)
}

class PassphraseRequiredException : Exception("Backup encryption password is required to restore this backup")

class InvalidPassphraseException(message: String = "Incorrect backup encryption password") : Exception(message)
