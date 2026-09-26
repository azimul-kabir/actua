package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportAggregatorTest {
    private fun account(id: String, offBudget: Boolean = false) =
        ActualAccount(id, id, ActualAccountType.CHECKING, offBudget, false, 0.0, 0)

    private val groups = listOf(
        ActualCategoryGroup("g-food", "Food", false, false, 1.0, listOf(
            ActualCategory("groceries", "Groceries", "g-food", false, false, 1.0),
            ActualCategory("dining", "Dining", "g-food", false, false, 2.0),
            ActualCategory("old", "Old", "g-food", false, true, 3.0),
        )),
        ActualCategoryGroup("g-inc", "Income", true, false, 2.0, listOf(
            ActualCategory("salary", "Salary", "g-inc", true, false, 1.0),
        )),
    )
    private val accounts = listOf(account("chk"), account("sav"), account("house", offBudget = true))
    private val aggregator = ReportAggregator(accounts, groups)
    private var n = 0

    private fun tx(
        amount: Long, category: String? = null, account: String = "chk", date: Int = 20260115,
        transferTo: String? = null, tombstone: Boolean = false, parent: Boolean = false,
        cleared: Boolean = false, reconciled: Boolean = false,
    ) = ActualTransaction(
        id = "t${n++}", accountId = account, date = date, amountCents = amount, payeeId = null, payeeName = null,
        categoryId = category, categoryName = null, notes = null, cleared = cleared, reconciled = reconciled,
        transferId = transferTo?.let { "x" }, isParent = parent, parentId = null, tombstone = tombstone,
        sortOrder = null, importedPayee = null, scheduleId = null, transferAccountId = transferTo,
    )

    private val jan = ReportFilter(20260101, 20260131)

    @Test fun `sums debts per category in integer cents without drift`() {
        val rows = List(10) { tx(-10, "groceries") } + tx(-5, "dining")
        val totals = aggregator.groupTotals(rows, jan, ReportGrouping.CATEGORY)
        assertEquals(listOf("Groceries" to -100L, "Dining" to -5L), totals.map { it.name to it.totalCents })
    }

    @Test fun `debts ignore refunds while net debts subtract them`() {
        val rows = listOf(tx(-1000, "groceries"), tx(300, "groceries"))
        assertEquals(-1000L, aggregator.total(rows, jan))
        assertEquals(-700L, aggregator.total(rows, jan.copy(balanceType = ReportBalanceType.NET_DEBTS)))
    }

    @Test fun `transfers follow showUncategorized like upstream, with no hard-coded exclusion`() {
        val rows = listOf(
            tx(-500, transferTo = "sav"),
            tx(-700, transferTo = "house"),
        )
        assertEquals(-1200L, aggregator.total(rows, jan))
        assertEquals(0L, aggregator.total(rows, jan.copy(showUncategorized = false)))
    }

    @Test fun `transfers land in a synthetic Transfers bucket for categorized grouping`() {
        val rows = listOf(tx(-500, transferTo = "sav"), tx(-25, "groceries"), tx(-50))
        val byCategory = aggregator.groupTotals(rows, jan, ReportGrouping.CATEGORY)
        assertEquals(
            listOf("Transfers" to -500L, "Uncategorized" to -50L, "Groceries" to -25L),
            byCategory.map { it.name to it.totalCents },
        )
        val byGroup = aggregator.groupTotals(rows, jan, ReportGrouping.CATEGORY_GROUP)
        assertEquals(
            listOf("Transfers" to -500L, "Uncategorized" to -50L, "Food" to -25L),
            byGroup.map { it.name to it.totalCents },
        )
    }

    @Test fun `isBudgetTransfer flags same-status transfers but not budget-to-off-budget`() {
        assertEquals(true, aggregator.isBudgetTransfer(tx(-500, transferTo = "sav")))
        assertEquals(false, aggregator.isBudgetTransfer(tx(-700, transferTo = "house")))
        assertEquals(false, aggregator.isBudgetTransfer(tx(-100, "groceries")))
    }

    @Test fun `off-budget accounts need opt-in`() {
        val rows = listOf(tx(-100, "groceries"), tx(-900, account = "house"))
        assertEquals(-100L, aggregator.total(rows, jan))
        assertEquals(-1000L, aggregator.total(rows, jan.copy(showOffBudget = true)))
    }

    @Test fun `tombstones split parents and date range are excluded`() {
        val rows = listOf(
            tx(-100, "groceries"), tx(-200, "groceries", tombstone = true),
            tx(-300, parent = true), tx(-50, "groceries", date = 20260201),
            tx(-40, "dining", date = 20260101), tx(-30, "dining", date = 20260131),
        )
        assertEquals(-170L, aggregator.total(rows, jan))
    }

    @Test fun `split children land in their own categories`() {
        val rows = listOf(tx(-600, "groceries"), tx(-400, "dining"), tx(-1000, parent = true))
        val totals = aggregator.groupTotals(rows, jan, ReportGrouping.CATEGORY_GROUP)
        assertEquals(listOf("Food" to -1000L), totals.map { it.name to it.totalCents })
    }

    @Test fun `hidden categories and uncategorized follow toggles`() {
        val rows = listOf(tx(-100, "old"), tx(-50), tx(-25, "groceries"))
        assertEquals(-75L, aggregator.total(rows, jan))
        assertEquals(-175L, aggregator.total(rows, jan.copy(showHiddenCategories = true)))
        assertEquals(-25L, aggregator.total(rows, jan.copy(showUncategorized = false)))
    }

    @Test fun `category account and group filters combine`() {
        val rows = listOf(tx(-100, "groceries"), tx(-200, "dining"), tx(-300, "groceries", account = "sav"))
        assertEquals(-400L, aggregator.total(rows, jan.copy(categoryIds = setOf("groceries"))))
        assertEquals(-100L, aggregator.total(rows, jan.copy(categoryIds = setOf("groceries"), accountIds = setOf("chk"))))
        assertEquals(-600L, aggregator.total(rows, jan.copy(categoryGroupIds = setOf("g-food"))))
    }

    @Test fun `cleared and reconciled status does not alter totals`() {
        val rows = listOf(tx(-100, "groceries"), tx(-100, "groceries", cleared = true), tx(-100, "groceries", reconciled = true))
        assertEquals(-300L, aggregator.total(rows, jan))
    }

    @Test fun `income assets and transaction ids reconcile with totals`() {
        val rows = listOf(tx(200000, "salary"), tx(-100, "groceries"))
        val f = jan.copy(balanceType = ReportBalanceType.ASSETS)
        val totals = aggregator.groupTotals(rows, f, ReportGrouping.CATEGORY)
        assertEquals(200000L, totals.single().totalCents)
        assertEquals(aggregator.select(rows, f).map { it.id }, totals.flatMap { it.transactionIds })
    }
}

class ReportAggregatorScaleTest {
    private val accounts = listOf(ActualAccount("a", "A", ActualAccountType.CHECKING, false, false, 0.0, 0))
    private val groups = listOf(ActualCategoryGroup("g", "G", false, false, 1.0,
        (0 until 50).map { ActualCategory("c$it", "C$it", "g", false, false, it.toDouble()) }))
    private val rows = (0 until 200_000).map { i ->
        ActualTransaction("t$i", "a", 20260101 + i % 28, -(i % 997 + 1).toLong(), null, null, "c${i % 50}", null, null,
            false, false, null, false, null, false, null, null, null, null)
    }
    private val aggregator = ReportAggregator(accounts, groups)
    private val filter = ReportFilter(20260101, 20260131)

    @Test fun `large ledger totals reconcile with drill-down ids`() {
        val totals = aggregator.groupTotals(rows, filter, ReportGrouping.CATEGORY)
        assertEquals(rows.sumOf { it.amountCents }, totals.sumOf { it.totalCents })
        assertEquals(rows.size, totals.sumOf { it.transactionIds.size })
    }

    /**
     * Guards against an accidentally-quadratic aggregation regression (e.g. re-scanning
     * transactions per category instead of a single pass) on a 200k-row ledger, the scale
     * a "large real-world budget" acceptance criterion (issue #230) cares about.
     */
    @Test fun `large ledger aggregates in one linear pass, not per-group`() {
        val singleGroupTotals = { aggregator.groupTotals(rows, filter, ReportGrouping.CATEGORY) }
        singleGroupTotals() // warm up JIT before timing.
        val start = System.nanoTime()
        repeat(5) { singleGroupTotals() }
        val perCallMillis = (System.nanoTime() - start) / 5_000_000
        assertTrue("expected < 500ms per pass over 200k rows, was ${perCallMillis}ms", perCallMillis < 500)
    }
}
