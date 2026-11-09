package com.azimulkabir.actua.data.budget.backup

import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.BudgetMetadata
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import java.io.InputStream

/**
 * Restores a decrypted backup archive into the on-device budgets, whichever remote target it came from: over an
 * open budget through [BackupService] (which keeps its safety copy and validates the archive), or as a new
 * standalone budget through [BudgetFileManager].
 */
class BudgetRestoreSink(
    private val files: BudgetFileManager,
    private val backupService: BackupService,
) : BackupRestoreSink {
    /** Held under the sync pause throughout, so no sync touches the budget between import and swap. */
    override fun restoreInto(budgetId: String, archive: InputStream): BudgetMetadata = ActualSyncRunner.whileSyncPaused {
        val imported = backupService.importArchive(budgetId, archive)
        backupService.restore(budgetId, imported.name)
        files.metadata(budgetId) ?: error("Restored budget metadata not found")
    }

    override fun importAsNew(archive: InputStream): BudgetMetadata = files.importLocalBudget(archive)
}
