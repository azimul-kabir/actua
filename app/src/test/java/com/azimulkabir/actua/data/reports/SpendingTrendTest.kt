package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingTrendTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val offBudget = setOf("mortgage")
    private val income = setOf("salary")

    private fun tx(
        id: String, date: Int, amount: Long, account: String = "checking", category: String? = "food",
        tombstone: Boolean = false,
    ) = ActualTransaction(id, account, date, amount, null, null, category, null, null, false, false, null,
        false, null, tombstone, null, null, null, null)

    private fun trend(rows: List<ActualTransaction>, months: Int = 6) =
        SpendingTrendBuilder.build(rows, offBudget, income, today, months)

    @Test
    fun coversTheLastSixMonthsOldestFirstEndingWithTheCurrentOne() {
        val trend = trend(emptyList())
        assertEquals(
            listOf("2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11").map(YearMonth::parse),
            trend.months.map { it.month },
        )
        assertEquals(listOf(false, false, false, false, false, true), trend.months.map { it.current })
    }

    @Test
    fun crossesAYearBoundary() {
        val january = SpendingTrendBuilder.build(emptyList(), offBudget, income, LocalDate.of(2027, 1, 5))
        assertEquals(YearMonth.of(2026, 8), january.months.first().month)
        assertEquals(YearMonth.of(2027, 1), january.months.last().month)
    }

    @Test
    fun totalsEachMonthAsPositiveCentsAndTheWholePeriod() {
        val trend = trend(listOf(
            tx("1", 20260610, -12_345),
            tx("2", 20260628, -5_000),
            tx("3", 20261015, -70_000),
            tx("4", 20261105, -2_500),
            tx("5", 20261111, -99),
        ))
        assertEquals(17_345L, trend.months[0].spentCents)
        assertEquals(0L, trend.months[1].spentCents)
        assertEquals(70_000L, trend.months[4].spentCents)
        assertEquals(2_599L, trend.currentCents)
        assertEquals(70_000L, trend.previousCents)
        assertEquals(17_345L + 70_000L + 2_599L, trend.totalCents)
    }

    @Test
    fun countsSpendingTheWayTheSpendingReportDoes() {
        val trend = trend(listOf(
            tx("spent", 20261101, -1_000),
            tx("income", 20261102, 500_000, category = "salary"),
            tx("incomeRefund", 20261102, -700, category = "salary"),
            tx("offBudget", 20261103, -80_000, account = "mortgage"),
            tx("deleted", 20261104, -9_000, tombstone = true),
            tx("refund", 20261105, 250),
            tx("older", 20250101, -4_000),
        ))
        // Only the on-budget, non-income, live rows count, and a refund reduces what was spent.
        assertEquals(750L, trend.currentCents)
        assertEquals(750L, trend.totalCents)
    }

    @Test
    fun matchesTheSpendingReportForTheSameMonth() {
        val rows = listOf(tx("1", 20261001, -10_000), tx("2", 20261031, -2_500), tx("3", 20261101, -999))
        val spending = CoreReportEngine.spendingTransactions(rows, offBudget, income)
        assertEquals(CoreReportEngine.spentInMonth(spending, YearMonth.of(2026, 10)), trend(rows).previousCents)
        assertEquals(12_500L, trend(rows).previousCents)
    }

    @Test
    fun isEmptyOnlyWhenNothingWasSpentInAnyMonth() {
        assertTrue(trend(emptyList()).isEmpty)
        assertTrue(trend(listOf(tx("income", 20261102, 500_000, category = "salary"))).isEmpty)
        assertFalse(trend(listOf(tx("old", 20260601, -1))).isEmpty)
    }

    @Test
    fun aShorterTrendForTheSmallestSize() {
        val trend = trend(listOf(tx("1", 20261015, -300), tx("2", 20261101, -200)), months = 2)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), trend.months.map { it.month })
        assertEquals(300L, trend.previousCents)
        assertEquals(200L, trend.currentCents)
    }
}
