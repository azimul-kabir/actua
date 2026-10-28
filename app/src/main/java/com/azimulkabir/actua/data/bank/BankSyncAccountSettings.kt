package com.azimulkabir.actua.data.bank

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase

/** The synced `preferences` ids of Actual's per-account Bank Sync toggles (`<name>-<accountId>`). */
enum class BankSyncFlag(val prefix: String, val default: Boolean) {
    ImportPending("sync-import-pending", true),
    ImportNotes("sync-import-notes", true),
    ImportTransactions("sync-import-transactions", true),
    UpdateDates("sync-update-dates", false),
    ReimportDeleted("sync-reimport-deleted", true),
    ;

    fun preferenceId(accountId: String) = "$prefix-$accountId"
}

/** An account's bank sync toggles; a preference that was never set reads as Actual's default. */
data class BankSyncAccountSettings(val flags: Map<BankSyncFlag, Boolean>) {
    operator fun get(flag: BankSyncFlag): Boolean = flags[flag] ?: flag.default

    companion object {
        fun read(database: ActualBudgetDatabase, accountId: String): BankSyncAccountSettings {
            val stored = database.fetchPreferences(BankSyncFlag.entries.map { it.preferenceId(accountId) })
            // Actual's `String(value ?? default) === 'true'`.
            return BankSyncAccountSettings(BankSyncFlag.entries.associateWith { flag ->
                (stored[flag.preferenceId(accountId)] ?: flag.default.toString()) == "true"
            })
        }
    }
}
