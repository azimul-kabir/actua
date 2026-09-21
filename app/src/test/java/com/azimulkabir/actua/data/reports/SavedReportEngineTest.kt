package com.azimulkabir.actua.data.reports

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SavedReportEngineTest {
    private fun row(range: String?, static: Boolean = false, start: String? = null, end: String? = null) =
        SavedReportRow("r", "R", start, end, static, range, "Category", "Payment", false, false, true, null,
            "DonutGraph", null, "and", "Monthly")

    @Test fun `relative ranges resolve from today`() {
        val today = LocalDate.of(2026, 9, 22)
        assertEquals(LocalDate.of(2026, 6, 1) to LocalDate.of(2026, 8, 31), SavedReportEngine.dateRange(row("Last 3 months"), today))
        assertEquals(LocalDate.of(2026, 1, 1) to today, SavedReportEngine.dateRange(row("Year to date"), today))
    }

    @Test fun `static ranges use stored month bounds`() {
        val r = SavedReportEngine.dateRange(row(null, true, "2026-02", "2026-03"), LocalDate.of(2026, 9, 22))
        assertEquals(LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 3, 31), r)
    }

    @Test fun `balance types map to upstream semantics`() {
        assertEquals(ReportBalanceType.DEBTS, SavedReportEngine.balanceType("Payment"))
        assertEquals(ReportBalanceType.ASSETS, SavedReportEngine.balanceType("Deposit"))
        assertEquals(ReportBalanceType.NET_ASSETS, SavedReportEngine.balanceType("Net"))
    }
}

class SavedReportViewFilterTest {
    private val accounts = listOf(
        com.azimulkabir.actua.data.budget.model.ActualAccount("a", "A", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0, 0),
        com.azimulkabir.actua.data.budget.model.ActualAccount("b", "B", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 1, 0),
    )
    private val groups = listOf(com.azimulkabir.actua.data.budget.model.ActualCategoryGroup("g", "G", false, false, 1.0,
        listOf(com.azimulkabir.actua.data.budget.model.ActualCategory("c", "C", "g", false, false, 1.0))))
    private fun tx(id: String, acct: String, date: Int, amount: Long) = com.azimulkabir.actua.data.budget.model.ActualTransaction(
        id, acct, date, amount, null, null, "c", null, null, false, false, null, false, null, false, null, null, null, null)
    private val saved = SavedReportRow("r", "R", "2026-01", "2026-01", true, null, "Category", "Payment", false, false, true,
        null, "DonutGraph", null, "and", "Monthly")
    private val rows = listOf(tx("1", "a", 20260110, -100), tx("2", "b", 20260111, -50), tx("3", "a", 20250601, -7))
    private val today = LocalDate.of(2026, 9, 22)

    @Test fun `account override narrows totals`() {
        val w = SavedReportEngine.compute(saved, rows, accounts, groups, today,
            com.azimulkabir.actua.model.ReportViewFilter(accountIds = setOf("a")))
        assertEquals(-100L, w.valueCents)
    }

    @Test fun `date preset overrides saved range`() {
        val w = SavedReportEngine.compute(saved, rows, accounts, groups, today,
            com.azimulkabir.actua.model.ReportViewFilter(datePreset = "All time"))
        assertEquals(-157L, w.valueCents)
    }
}

class IntervalPointsTest {
    private fun tx(date: Int, amount: Long) = com.azimulkabir.actua.data.budget.model.ActualTransaction(
        "t$date", "a", date, amount, null, null, null, null, null, false, false, null, false, null, false, null, null, null, null)
    private val today = LocalDate.of(2026, 3, 15)

    @Test fun `monthly buckets fill gaps and sum cents`() {
        val pts = SavedReportEngine.intervalPoints(listOf(tx(20260105, -100), tx(20260120, -1), tx(20260310, -5)),
            "Monthly", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), today)
        assertEquals(listOf("2026-01" to -101L, "2026-02" to 0L, "2026-03" to -5L), pts.map { it.period to it.primaryCents })
    }

    @Test fun `weekly buckets start on Sunday`() {
        val pts = SavedReportEngine.intervalPoints(listOf(tx(20260304, -10), tx(20260308, -20)),
            "Weekly", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 14), today)
        assertEquals(listOf("2026-03-01" to -10L, "2026-03-08" to -20L), pts.map { it.period to it.primaryCents })
    }
}
