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
