package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction

/** What the report widgets read from a budget: its accounts, category groups and report transactions. */
class ReportDataSnapshot(
    val accounts: List<ActualAccount>,
    val groups: List<ActualCategoryGroup>,
    val transactions: List<ActualTransaction>,
) {
    val offBudgetAccountIds: Set<String> by lazy { accounts.filter { it.offBudget }.mapTo(mutableSetOf()) { it.id } }
    val incomeCategoryIds: Set<String> by lazy {
        groups.flatMap { it.categories }.filter { it.isIncome }.mapTo(mutableSetOf()) { it.id }
    }
}

/**
 * The report data every report widget reads, loaded once and shared. `WidgetUpdater.requestAll` updates all
 * widgets together, so without this each report widget would load every report transaction itself.
 *
 * The data-change token is the budget's sync clock: its timestamp and Merkle root, which every local and
 * synced write updates (the Merkle root also moves when a remote message older than the local clock arrives,
 * which the timestamp alone would miss). A write therefore changes the key and the next update reads again.
 */
object SharedReportData {
    private val cache = ReportDataCache<ReportDataSnapshot>()

    /**
     * The report data of [db], the open budget [budgetId]. A budget with no id or no sync clock has nothing
     * to tell when its data changes, so it is read directly and never held.
     */
    fun get(budgetId: String?, db: ActualBudgetDatabase): ReportDataSnapshot {
        val clock = if (budgetId == null) null else db.loadClock()
        if (budgetId == null || clock == null) return load(db)
        val key = ReportDataCache.Key(budgetId, "${clock.timestamp}|${clock.merkle.hash}")
        return cache.get(key) { load(db) }
    }

    private fun load(db: ActualBudgetDatabase) =
        ReportDataSnapshot(db.fetchAccounts(), db.fetchCategoryGroups(), db.fetchTransactionsForReports())
}
