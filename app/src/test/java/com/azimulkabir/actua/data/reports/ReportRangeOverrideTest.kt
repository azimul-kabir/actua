package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.model.ReportRange
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Slice 11 of #1106 (#1117): a viewer-side range recomputes one widget and never edits its settings. */
class ReportRangeOverrideTest {
    private val today = LocalDate.of(2026, 6, 15)
    private val accounts = listOf(
        ActualAccount("chk", "Checking", ActualAccountType.CHECKING, offBudget = false, closed = false, sortOrder = 1.0, balanceCents = 0),
    )
    private val storedMeta = """{"name":"Flow","timeFrame":{"mode":"static","start":"2026-01","end":"2026-06"}}"""
    private val rows = listOf(
        DashboardWidgetRow("flow", "cash-flow-card", storedMeta),
        DashboardWidgetRow("other", "net-worth-card", null),
    )

    private fun tx(id: String, date: Int, amount: Long) = ActualTransaction(
        id = id, accountId = "chk", date = date, amountCents = amount, payeeId = null, payeeName = null,
        categoryId = null, categoryName = null, notes = null, cleared = true, reconciled = false,
        transferId = null, isParent = false, parentId = null, tombstone = false, sortOrder = 0.0,
        importedPayee = null, scheduleId = null, transferAccountId = null,
    )

    private val transactions = (1..6).map { tx("t$it", 2026_00_00 + it * 100 + 10, 100_00L * it) }

    private fun dashboard(range: ReportRange? = null, filter: (DashboardWidgetRow) -> Boolean = { true }) =
        CoreReportEngine.dashboards(
            listOf(DashboardPageRow("p", "Page")), widgets = { rows }, transactions = transactions,
            accounts = accounts, groups = emptyList(), today = today, widgetFilter = filter, range = range,
        ).single().widgets

    @Test fun `without a range the widget uses its own saved time frame`() {
        assertEquals(6, dashboard().first { it.id == "flow" }.points.size)
    }

    @Test fun `a range recomputes only the requested widget over that range`() {
        val widgets = dashboard(ReportRange.THREE_MONTHS) { it.id == "flow" }
        assertEquals(listOf("flow"), widgets.map { it.id })
        assertEquals(listOf("2026-04", "2026-05", "2026-06"), widgets.single().points.map { it.period })
        assertEquals(12, dashboard(ReportRange.TWELVE_MONTHS) { it.id == "flow" }.single().points.size)
    }

    @Test fun `year to date starts in january and all time starts at the first transaction`() {
        assertEquals("2026-01", dashboard(ReportRange.YEAR_TO_DATE) { it.id == "flow" }.single().points.first().period)
        assertEquals("2026-01", dashboard(ReportRange.ALL_TIME) { it.id == "flow" }.single().points.first().period)
    }

    @Test fun `overriding the time frame returns new settings and leaves the stored ones alone`() {
        val overridden = CoreReportEngine.overrideTimeFrame(storedMeta, ReportRange.SIX_MONTHS, today, YearMonth.of(2025, 3))
        val frame = JSONObject(overridden).getJSONObject("timeFrame")
        assertEquals("static", frame.getString("mode"))
        assertEquals("2026-01", frame.getString("start"))
        assertEquals("2026-06", frame.getString("end"))
        assertEquals("Flow", JSONObject(overridden).getString("name"))
        // The stored row is untouched.
        assertTrue(storedMeta.contains("\"start\":\"2026-01\""))
        assertEquals(storedMeta, rows.first().metaJson)
        assertEquals("yearToDate", JSONObject(CoreReportEngine.overrideTimeFrame(null, ReportRange.YEAR_TO_DATE, today, null))
            .getJSONObject("timeFrame").getString("mode"))
        val all = JSONObject(CoreReportEngine.overrideTimeFrame(storedMeta, ReportRange.ALL_TIME, today, YearMonth.of(2025, 3)))
            .getJSONObject("timeFrame")
        assertEquals("full", all.getString("mode"))
        assertEquals("2025-03", all.getString("start"))
    }
}
