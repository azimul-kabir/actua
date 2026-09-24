package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction

/**
 * Upstream `balanceType`: which transactions of the selected set contribute
 * (`totalDebts`, `totalAssets`, `netAssets`, `netDebts` in loot-core custom reports).
 * `BUDGETED` (`totalBudgeted`) reads budget-engine cells instead of transactions, so it never
 * reaches [ReportAggregator.select] - [SavedReportEngine] branches on it before building a filter.
 */
enum class ReportBalanceType { DEBTS, ASSETS, NET_ASSETS, NET_DEBTS, BUDGETED }

enum class ReportGrouping { CATEGORY, CATEGORY_GROUP, PAYEE, ACCOUNT }

/** Shared filter for every report; dates are Actual YYYYMMDD day integers, inclusive. */
data class ReportFilter(
    val startDate: Int,
    val endDate: Int,
    /** Null means every account. */
    val accountIds: Set<String>? = null,
    val categoryIds: Set<String>? = null,
    val categoryGroupIds: Set<String>? = null,
    val showOffBudget: Boolean = false,
    val showHiddenCategories: Boolean = false,
    val showUncategorized: Boolean = true,
    val balanceType: ReportBalanceType = ReportBalanceType.DEBTS,
)

data class ReportGroupTotal(
    val id: String?,
    val name: String,
    /** Signed sum of contributing amounts in cents, before the report sign convention. */
    val totalCents: Long,
    val transactionIds: List<String>,
)

/**
 * Integer-cent report aggregation following Actual's custom-report semantics
 * (loot-core `server/reports`): tombstones and split parents are ignored, split children
 * carry their own category, transfers fall into the config-driven uncategorized/synthetic
 * "Transfers" bucket like upstream (no hard-coded exclusion), off-budget accounts are
 * excluded unless requested, and cleared/reconciled state never matters.
 */
class ReportAggregator(accounts: List<ActualAccount>, groups: List<ActualCategoryGroup>) {
    private val accountsById = accounts.associateBy { it.id }
    private val categoriesById = groups.flatMap { g -> g.categories.map { it to g } }.associate { (c, g) -> c.id to (c to g) }
    private val groupsById = groups.associateBy { it.id }

    fun categoryIsIncome(categoryId: String?): Boolean = categoryId?.let { categoriesById[it]?.first?.isIncome } == true

    /**
     * True when [tx] transfers money between two accounts with the same on/off-budget status -
     * a same-status transfer moves money without being real income or spending, which cash-flow
     * style widgets (e.g. [SavedReportEngine.incomeExpense]) exclude outright rather than
     * bucketing as a synthetic "Transfers" bucket the way custom reports do.
     */
    fun isBudgetTransfer(tx: ActualTransaction): Boolean {
        val transferAccountId = tx.transferAccountId ?: return false
        val account = accountsById[tx.accountId] ?: return false
        val counterpart = accountsById[transferAccountId]
        return counterpart == null || counterpart.offBudget == account.offBudget
    }

    /** Transactions that the filter includes, in input order. */
    fun select(transactions: List<ActualTransaction>, filter: ReportFilter): List<ActualTransaction> =
        transactions.filter { includes(it, filter) }

    fun includes(tx: ActualTransaction, f: ReportFilter): Boolean {
        if (tx.tombstone || tx.isParent) return false
        if (tx.date < f.startDate || tx.date > f.endDate) return false
        val account = accountsById[tx.accountId] ?: return false
        if (account.offBudget && !f.showOffBudget) return false
        if (f.accountIds != null && tx.accountId !in f.accountIds) return false
        val categoryId = tx.categoryId
        if (categoryId == null) {
            // Transfers have no category of their own (upstream's synthetic "Transfers" bucket),
            // so they follow the same showUncategorized/category-filter toggles as any other
            // uncategorized row instead of a hard-coded transfer exclusion.
            if (!f.showUncategorized || f.categoryIds != null || f.categoryGroupIds != null) return false
        } else {
            val (category, group) = categoriesById[categoryId] ?: return false
            if (!f.showHiddenCategories && (category.hidden || group.hidden)) return false
            if (f.categoryIds != null && categoryId !in f.categoryIds) return false
            if (f.categoryGroupIds != null && group.id !in f.categoryGroupIds) return false
        }
        return when (f.balanceType) {
            ReportBalanceType.DEBTS -> tx.amountCents < 0
            ReportBalanceType.ASSETS -> tx.amountCents > 0
            ReportBalanceType.NET_ASSETS, ReportBalanceType.NET_DEBTS -> true
            // Budgeted reports never select transactions; SavedReportEngine reads budget cells instead.
            ReportBalanceType.BUDGETED -> false
        }
    }

    fun total(transactions: List<ActualTransaction>, filter: ReportFilter): Long =
        select(transactions, filter).sumOf { it.amountCents }

    /** Per-category or per-group totals, largest magnitude first. */
    fun groupTotals(
        transactions: List<ActualTransaction>,
        filter: ReportFilter,
        grouping: ReportGrouping,
    ): List<ReportGroupTotal> = select(transactions, filter)
        .groupBy { tx ->
            val entry = tx.categoryId?.let(categoriesById::get)
            when (grouping) {
                ReportGrouping.CATEGORY ->
                    entry?.first?.id ?: if (tx.transferAccountId != null) TRANSFER_BUCKET_ID else null
                ReportGrouping.CATEGORY_GROUP ->
                    entry?.second?.id ?: if (tx.transferAccountId != null) TRANSFER_BUCKET_ID else null
                ReportGrouping.PAYEE -> tx.payeeId
                ReportGrouping.ACCOUNT -> tx.accountId
            }
        }
        .map { (id, rows) ->
            val name = when (grouping) {
                ReportGrouping.CATEGORY -> if (id == TRANSFER_BUCKET_ID) "Transfers" else id?.let { categoriesById[it]?.first?.name }
                ReportGrouping.CATEGORY_GROUP -> if (id == TRANSFER_BUCKET_ID) "Transfers" else id?.let { groupsById[it]?.name }
                ReportGrouping.PAYEE -> rows.firstNotNullOfOrNull { it.payeeName?.takeIf(String::isNotBlank) }
                ReportGrouping.ACCOUNT -> id?.let { accountsById[it]?.name }
            } ?: if (grouping == ReportGrouping.PAYEE) "Unknown" else "Uncategorized"
            ReportGroupTotal(id, name, rows.sumOf { it.amountCents }, rows.map { it.id })
        }
        .sortedWith(compareByDescending<ReportGroupTotal> { kotlin.math.abs(it.totalCents) }.thenBy { it.name })

    private companion object {
        /** Synthetic grouping key for transfers under [ReportGrouping.CATEGORY]/[ReportGrouping.CATEGORY_GROUP]. */
        const val TRANSFER_BUCKET_ID = "\u0000transfer"
    }
}
