package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.network.RemoteBudgetFile
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import java.io.File

sealed class BudgetRecoveryException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object NotServerBacked : BudgetRecoveryException("This budget isn't linked to a server file")
    class BackupFailed(cause: Throwable) : BudgetRecoveryException("Could not back up this budget first", cause)
}

data class BudgetResetRecoveryResult(
    val metadata: BudgetMetadata,
    /** The verified archive written before anything was replaced; restorable from Backups. */
    val backup: File,
    /** Local edits that were not on the server and are not in the new file. */
    val unsyncedChanges: Int,
)

/**
 * Recovery after the server reports `file-has-reset` (docs/SYNC_PARITY.md section 8): back up the
 * local copy, verify the backup, download the server's snapshot, and only then swap it in. Every
 * step before the swap leaves the local budget untouched, and the swap restores the previous files
 * if it fails. Callers must have the user's confirmation first and close any open handle on the
 * budget's database (the app's repository) before calling.
 */
class BudgetResetRecovery(
    private val files: BudgetFileManager,
    private val backups: BackupService,
    private val downloader: BudgetDownloadService,
) {
    /** Local changes that haven't synced, shown to the user before they confirm. */
    fun unsyncedChanges(budgetId: String): Int =
        ActualBudgetDatabase.open(files.databaseFile(budgetId)).use { it.unsyncedMessageCount() }

    fun recover(serverUrl: String, token: String, budgetId: String): BudgetResetRecoveryResult =
        // Held against the sync runner so no sync starts or is mid-flight while the files change.
        ActualSyncRunner.whileSyncPaused {
            val local = files.metadata(budgetId) ?: throw IllegalArgumentException("Budget no longer exists on this device")
            val fileId = local.cloudFileId ?: throw BudgetRecoveryException.NotServerBacked
            val unsynced = unsyncedChanges(budgetId)

            val backup = try {
                backups.makeBackup(budgetId)
            } catch (error: Exception) {
                throw BudgetRecoveryException.BackupFailed(error)
            }
            verifyBackup(budgetId, backup)

            val remote = RemoteBudgetFile(fileId, local.groupId, local.budgetName.orEmpty(), local.encryptKeyId)
            val metadata = downloader.redownload(serverUrl, token, remote, budgetId)
            BudgetResetRecoveryResult(metadata, backup, unsynced)
        }

    /** The backup must open as this budget before the local copy is allowed to change. */
    private fun verifyBackup(budgetId: String, backup: File) {
        try {
            val (database, metadata) = files.extractBackup(backup)
            database.delete()
            if (metadata.id != budgetId) throw BudgetFileException.IncompatibleBudget
        } catch (error: Exception) {
            throw BudgetRecoveryException.BackupFailed(error)
        }
    }
}
