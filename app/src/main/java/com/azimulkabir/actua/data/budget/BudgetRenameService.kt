package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.network.ActualServerClient

/**
 * Renames a budget the way Actual does (loot-core `saveMetadataPrefs`): for a server budget the
 * server's file name is updated first and nothing local changes if that fails; then the synced
 * `prefs/budgetName` message is logged and `metadata.json` gets the new name. A standalone budget is
 * renamed locally. Callers should release any open handle on the budget's database first.
 */
class BudgetRenameService(
    private val server: ActualServerClient,
    private val files: BudgetFileManager,
) {
    fun rename(serverUrl: String, token: String?, budgetId: String, newName: String): BudgetMetadata {
        val name = newName.trim()
        require(name.isNotEmpty()) { "Name cannot be empty" }
        val local = files.metadata(budgetId) ?: throw IllegalArgumentException("Budget no longer exists on this device")
        require(!local.isDemo) { "The demo budget can't be renamed" }
        val fileId = local.cloudFileId?.takeIf(String::isNotBlank)
        if (fileId != null) {
            server.updateFileName(serverUrl, requireNotNull(token) { "Sign in to rename a server budget" }, fileId, name)
            ActualBudgetDatabase.open(files.databaseFile(budgetId)).use { ActualEntityWriter(it).recordBudgetName(name) }
        }
        return files.renameBudget(budgetId, name)
    }
}
