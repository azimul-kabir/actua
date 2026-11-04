package com.azimulkabir.actua.data.budget.backup

import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetMetadata
import com.azimulkabir.actua.data.budget.gdrive.EncryptedBackupArchive
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import java.util.UUID

/** Where a restored archive goes. */
interface BackupRestoreSink {
    /** Replaces the open budget [budgetId] with the archive, keeping the safety copy [BackupService] makes. */
    fun restoreInto(budgetId: String, archive: InputStream): BudgetMetadata

    /** Imports the archive as a new standalone budget. */
    fun importAsNew(archive: InputStream): BudgetMetadata
}

/**
 * Archive encryption, upload, retention, listing and restore for a [BackupTransport]. Everything that is not
 * about moving bytes lives here, so a second target only has to implement the transport.
 *
 * Nothing is sent unencrypted: [upload] encrypts the archive into a staging file in [workDir] before the
 * transport sees any of it, and every staging file is removed afterwards.
 */
class EncryptedBackupPipeline(
    private val transport: BackupTransport,
    private val passphraseProvider: () -> CharArray?,
    private val budgetNameOf: (budgetId: String) -> String?,
    private val restoreSink: BackupRestoreSink,
    private val workDir: File,
    private val clock: () -> Instant = Instant::now,
) {
    /** Encrypts [archiveFile], uploads it, then prunes old remote backups. Returns the uploaded file. */
    fun upload(budgetId: String, archiveFile: File): RemoteBackupItem {
        require(archiveFile.isFile) { "Archive file does not exist: ${archiveFile.path}" }
        val passphrase = passphraseProvider()
            ?: throw IllegalStateException("Backup encryption password not configured")

        val stagingFile = File(workDir, ".staging-${UUID.randomUUID()}.actuabkp")
        try {
            // 1. Client-side authenticated encryption (before anything leaves the device)
            archiveFile.inputStream().use { input ->
                FileOutputStream(stagingFile).use { output ->
                    EncryptedBackupArchive.encrypt(input, output, passphrase)
                }
            }

            // 2. Upload the encrypted container
            val request = BackupUpload(
                name = "${archiveFile.name.removeSuffix(".zip")}.actuabkp",
                budgetId = budgetId,
                budgetName = budgetNameOf(budgetId) ?: budgetId,
                archiveName = archiveFile.name,
                createdAt = clock().toString(),
            )
            val uploaded = stagingFile.inputStream().use { stream -> transport.upload(request, stream) }

            // 3. Safe remote retention pruning: only runs after the new upload is verified
            prune(budgetId, uploaded.id)
            return uploaded
        } finally {
            stagingFile.delete()
        }
    }

    private fun prune(budgetId: String, currentUploadedId: String) {
        val remoteFiles = runCatching { transport.list(budgetId) }.getOrNull() ?: return
        if (remoteFiles.none { it.id == currentUploadedId }) {
            // New upload not yet visible in listing; preserve all remote backups
            return
        }

        val datedBackups = remoteFiles.mapNotNull { file ->
            val date = parseBackupDate(file.name) ?: file.modifiedTime?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            } ?: return@mapNotNull null
            BackupService.Companion.DatedBackup(file.id, date)
        }.sortedByDescending { it.date }

        val toRemove = BackupService.backupsToRemove(datedBackups, clock())
        // Invariant: Never delete the backup we just verified and uploaded
        val safeToRemove = toRemove.filter { it != currentUploadedId }

        for (backupId in safeToRemove) {
            runCatching { transport.delete(backupId) }
        }
    }

    /** The remote backups of [budgetId], or of every budget when it is null, newest first. */
    fun listBackups(budgetId: String? = null): List<RemoteBackupItem> {
        val items = if (budgetId != null) transport.list(budgetId) else transport.listAll()
        return items.sortedByDescending { it.modifiedTime ?: it.name }
    }

    /**
     * Downloads [remoteBackupId], decrypts it (with [passphrase], or the stored one) and restores it either into
     * [targetBudgetId] or as a new standalone budget.
     */
    fun restore(
        remoteBackupId: String,
        passphrase: CharArray? = null,
        targetBudgetId: String? = null,
    ): BudgetMetadata {
        val tempDownloaded = File(workDir, ".gdrive-download-${UUID.randomUUID()}.tmp")
        val tempDecrypted = File(workDir, ".gdrive-decrypted-${UUID.randomUUID()}.zip")

        try {
            // 1. Download the stored file
            FileOutputStream(tempDownloaded).use { out ->
                transport.download(remoteBackupId, out)
            }

            // 2. Detect container format (ACTUABKP v1 or plain ZIP)
            val isEncrypted = isActuaBackupArchive(tempDownloaded)
            val archiveToRestore = if (isEncrypted) {
                val effectivePassphrase = passphrase ?: passphraseProvider()
                    ?: throw PassphraseRequiredException()

                // Fast canary validation before full decryption
                val valid = tempDownloaded.inputStream().use { inStream ->
                    EncryptedBackupArchive.validatePassphrase(inStream, effectivePassphrase)
                }
                if (!valid) throw InvalidPassphraseException()

                // Decrypt to standard ZIP archive
                tempDownloaded.inputStream().use { inStream ->
                    FileOutputStream(tempDecrypted).use { outStream ->
                        EncryptedBackupArchive.decrypt(inStream, outStream, effectivePassphrase)
                    }
                }
                tempDecrypted
            } else {
                tempDownloaded
            }

            // 3. Restore: either into an open budget or as a new standalone budget
            return archiveToRestore.inputStream().use { stream ->
                if (targetBudgetId != null) restoreSink.restoreInto(targetBudgetId, stream)
                else restoreSink.importAsNew(stream)
            }
        } finally {
            tempDownloaded.delete()
            tempDecrypted.delete()
        }
    }

    private fun isActuaBackupArchive(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val magic = ByteArray(8)
            val read = input.read(magic)
            read == 8 && String(magic, Charsets.US_ASCII) == "ACTUABKP"
        }
    }.getOrDefault(false)

    companion object {
        private val backupNameFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

        fun parseBackupDate(name: String): Instant? = runCatching {
            val base = name.removeSuffix(".actuabkp").removeSuffix(".zip")
            val date = backupNameFormat.parse(base) ?: return null
            date.toInstant()
        }.getOrNull()
    }
}
