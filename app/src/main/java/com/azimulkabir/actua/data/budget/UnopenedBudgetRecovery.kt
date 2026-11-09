package com.azimulkabir.actua.data.budget

import android.content.Context
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.TrustedCertificateStore
import com.azimulkabir.actua.data.network.UrlConnectionTransport
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import com.azimulkabir.actua.data.security.CredentialStore
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import com.azimulkabir.actua.data.sync.SyncedLogMarkers

/**
 * User-confirmed recovery for a local budget the app couldn't open (#1509). The app never deletes
 * such a budget by itself; every action here runs only after the user picks it. Blocking: call off
 * the main thread.
 */
class UnopenedBudgetRecovery(context: Context) {
    private val files = BudgetFileManager(context)
    private val backups = BackupService(context, files)
    private val credentials = CredentialStore(context)
    private val resetRecovery = BudgetResetRecovery(
        files,
        backups,
        BudgetDownloadService(
            ActualServerClient(UrlConnectionTransport(TrustedCertificateStore(context))).apply {
                customHeaders = credentials.customHeaders
            },
            files,
            BudgetEncryptionKeyStore(context),
        ),
        SyncedLogMarkers(context),
    )

    /** Unsynced local changes, or null when they can't be counted. */
    fun unsyncedChanges(budgetId: String): Int? = resetRecovery.unsyncedChanges(budgetId)

    fun backups(budgetId: String): List<BackupItem> = runCatching { backups.availableBackups(budgetId) }.getOrDefault(emptyList())

    /** Re-download needs a server-backed budget and a signed-in server. */
    fun canRedownload(metadata: BudgetMetadata): Boolean =
        metadata.isServerBacked && credentials.token() != null && credentials.serverUrl.isNotBlank()

    /**
     * Replaces the local copy with the server's. The local copy is backed up and the backup verified
     * first; if that fails, nothing changes (see [BudgetResetRecovery.recover]).
     */
    fun redownload(budgetId: String): BudgetResetRecoveryResult {
        val token = credentials.token() ?: throw IllegalStateException("Not signed in to a server")
        return resetRecovery.recover(credentials.serverUrl, token, budgetId)
    }

    fun restore(budgetId: String, backupId: String) = ActualSyncRunner.whileSyncPaused {
        backups.restore(budgetId, backupId)
    }

    /** Removes the budget, its local backups and its receipt photos from this device. */
    fun delete(budgetId: String) = ActualSyncRunner.whileSyncPaused {
        files.deleteBudget(budgetId)
    }
}
