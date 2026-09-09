package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.model.ReportWidgetKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** JSON-backed report metadata requires Android's real org.json implementation. */
class CoreReportEngineTest {
    private val today = LocalDate.of(2026, 5, 20)

    @Test fun summaryParsesDoubleEncodedContentAndTransferCondition() {
        val meta = """{"name":"Spent","timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"},"conditions":[{"field":"transfer","op":"is","value":false}],"content":"{\"type\":\"sum\"}"}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("one", "summary-card", meta),
            listOf(transaction("expense", -1_000), transaction("income", 2_000), transaction("transfer", -500, transfer = "other")),
            today = today,
        )
        assertEquals(ReportWidgetKind.SUMMARY, widget.kind)
        assertEquals(1_000, widget.valueCents)
    }

    @Test fun cashFlowDropsTransfersAndOffBudgetAccounts() {
        val meta = """{"timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"}}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("cash", "cash-flow-card", meta),
            listOf(
                transaction("income", 2_000), transaction("expense", -700),
                transaction("transfer", -500, transfer = "other"), transaction("off", -900, account = "off"),
            ),
            context = RuleContext(offBudgetAccountIds = setOf("off")),
            today = today,
        )
        assertEquals(2_000, widget.points.single().primaryCents)
        assertEquals(700, widget.points.single().secondaryCents)
    }

    @Test fun summaryCurrentMonthStopsAtToday() {
        val meta = """{"timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"}}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("summary", "summary-card", meta),
            listOf(transaction("past", -500), transaction("future", -900, date = 20260525)),
            today = today,
        )
        assertEquals(-500L, widget.valueCents)
    }

    @Test fun spendingBudgetUsesSyncedCategoryBudgetsAndMonthToDateProration() {
        val meta = """{"mode":"budget","compare":"2026-05","isLive":true}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("spending", "spending-card", meta),
            listOf(transaction("spent", -1_000)),
            budgetedByCategory = { mapOf("food" to 3_100L) },
            today = LocalDate.of(2026, 5, 10),
        )
        assertEquals(1_000L, widget.valueCents)
        assertEquals(1_000L, widget.comparisonCents)
    }

    @Test fun slidingWindowKeepsConfiguredMonthCount() {
        val range = CoreReportEngine.timeFrame(
            JSONObject("""{"mode":"sliding-window","start":"2025-11","end":"2026-01"}"""), today,
        )
        assertEquals(LocalDate.of(2026, 3, 1), range.first)
        assertEquals(LocalDate.of(2026, 5, 31), range.second)
    }

    @Test fun unknownSyncedWidgetStaysVisibleAsUnsupportedMetadata() {
        val widget = CoreReportEngine.compute(DashboardWidgetRow("future", "future-card", null), emptyList(), today = today)
        assertEquals(ReportWidgetKind.UNSUPPORTED, widget.kind)
        assertEquals("future-card", widget.sourceType)
    }

    private fun transaction(
        id: String,
        amount: Long,
        transfer: String? = null,
        account: String = "checking",
        date: Int = 20260510,
    ) = ActualTransaction(
        id, account, date, amount, null, null, null, null, null,
        false, false, transfer, false, null, false, null, null, null, transfer,
    )
}
