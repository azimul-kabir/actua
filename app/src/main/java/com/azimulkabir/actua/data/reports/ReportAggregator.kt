package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction

/**
 * Upstream `balanceTypeOp` for custom reports: `totalDebts` ([DEBTS], only amounts < 0),
 * `totalAssets` ([ASSETS], only amounts > 0), `totalTotals` ([NET], the signed sum), and
 * `netAssets`/`netDebts` ([NET_ASSETS]/[NET_DEBTS], the signed sum clamped to one side by
 * [clampNet]). `BUDGETED` (`totalBudgeted`) reads budget-engine cells instead of transactions,
 * so it never reaches [ReportAggregator.select] - [SavedReportEngine] branches on it before
 * building a filter.
 */
enum class ReportBalanceType {
    DEBTS, ASSETS, NET, NET_ASSETS, NET_DEBTS, BUDGETED;

    /**
     * Upstream `recalculate`/`custom-spreadsheet`: `netAssets` keeps a net amount only when it is
     * positive (`total > 0 ? total : 0`) and `netDebts` only when negative; every other balance
     * type passes the amount through.
     */
    fun clampNet(cents: Long): Long = when (this) {
        NET_ASSETS -> maxOf(cents, 0L)
        NET_DEBTS -> minOf(cents, 0L)
        else -> cents
    }

    /** True for the balance types [clampNet] actually clamps. */
    val clampsNet: Boolean get() = this == NET_ASSETS || this == NET_DEBTS
}

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
            // uncategorized row instead of a hard-coded transfer exclusion. Upstream's
            // `filterHiddenItems` keeps off-budget rows even with showUncategorized off.
            if ((!f.showUncategorized && !account.offBudget) || f.categoryIds != null || f.categoryGroupIds != null) return false
        } else {
            val (category, group) = categoriesById[categoryId] ?: return false
            if (!f.showHiddenCategories && (category.hidden || group.hidden)) return false
            if (f.categoryIds != null && categoryId !in f.categoryIds) return false
            if (f.categoryGroupIds != null && group.id !in f.categoryGroupIds) return false
        }
        return when (f.balanceType) {
            ReportBalanceType.DEBTS -> tx.amountCents < 0
            ReportBalanceType.ASSETS -> tx.amountCents > 0
            ReportBalanceType.NET, ReportBalanceType.NET_ASSETS, ReportBalanceType.NET_DEBTS -> true
            // Budgeted reports never select transactions; SavedReportEngine reads budget cells instead.
            ReportBalanceType.BUDGETED -> false
        }
    }

    /** The selection's total as one interval, clamped by [ReportBalanceType.clampNet]. */
    fun total(transactions: List<ActualTransaction>, filter: ReportFilter): Long =
        filter.balanceType.clampNet(select(transactions, filter).sumOf { it.amountCents })

    /**
     * Per-category or per-group totals, largest magnitude first. For `netAssets`/`netDebts` each
     * group's net is clamped by [ReportBalanceType.clampNet] and groups left at zero are dropped,
     * like upstream's `filterEmptyRows` with "show empty" off.
     */
    fun groupTotals(
        transactions: List<ActualTransaction>,
        filter: ReportFilter,
        grouping: ReportGrouping,
    ): List<ReportGroupTotal> = select(transactions, filter)
        .filter { grouping != ReportGrouping.PAYEE || it.payeeId != null }
        .groupBy { tx ->
            // Upstream's `filterHiddenItems`: a categorized on-budget row goes to its own category
            // (or group); every other row goes to a synthetic item - for categories "Off budget"
            // (any off-budget row), "Transfers" or "Uncategorized", for groups one combined
            // "Uncategorized & Off budget" group.
            val entry = tx.categoryId?.let(categoriesById::get)?.takeUnless { accountsById[tx.accountId]?.offBudget == true }
            when (grouping) {
                ReportGrouping.CATEGORY -> entry?.first?.id ?: when {
                    accountsById[tx.accountId]?.offBudget == true -> OFF_BUDGET_BUCKET_ID
                    tx.transferAccountId != null -> TRANSFER_BUCKET_ID
                    else -> null
                }
                ReportGrouping.CATEGORY_GROUP -> entry?.second?.id ?: UNCATEGORIZED_GROUP_ID
                ReportGrouping.PAYEE -> tx.payeeId
                ReportGrouping.ACCOUNT -> tx.accountId
            }
        }
        .map { (id, rows) ->
            val name = when (grouping) {
                ReportGrouping.CATEGORY -> when (id) {
                    TRANSFER_BUCKET_ID -> "Transfers"
                    OFF_BUDGET_BUCKET_ID -> "Off budget"
                    else -> id?.let { categoriesById[it]?.first?.name }
                }
                ReportGrouping.CATEGORY_GROUP ->
                    if (id == UNCATEGORIZED_GROUP_ID) "Uncategorized & Off budget" else id?.let { groupsById[it]?.name }
                ReportGrouping.PAYEE -> rows.firstNotNullOfOrNull { it.payeeName?.takeIf(String::isNotBlank) }
                ReportGrouping.ACCOUNT -> id?.let { accountsById[it]?.name }
            } ?: if (grouping == ReportGrouping.PAYEE) "Unknown" else "Uncategorized"
            ReportGroupTotal(id, name, filter.balanceType.clampNet(rows.sumOf { it.amountCents }), rows.map { it.id })
        }
        .filterNot { it.totalCents == 0L && filter.balanceType.clampsNet }
        .sortedWith(compareByDescending<ReportGroupTotal> { kotlin.math.abs(it.totalCents) }.thenBy { it.name })

    private companion object {
        /** Synthetic grouping keys for upstream's uncategorized items (`ReportOptions.ts`). */
        const val TRANSFER_BUCKET_ID = "\u0000transfer"
        const val OFF_BUDGET_BUCKET_ID = "\u0000off_budget"
        const val UNCATEGORIZED_GROUP_ID = "\u0000uncategorized"
    }
}
