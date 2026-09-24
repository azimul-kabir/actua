package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduledAmount
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
        assertEquals(1_000L, widget.valueCents)
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
        assertEquals(2_000L, widget.points.single().primaryCents)
        assertEquals(700L, widget.points.single().secondaryCents)
    }

    @Test fun cashFlowPointsCarryContributingTransactionIdsForDrillDown() {
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
        assertEquals(setOf("income", "expense"), widget.points.single().transactionIds.toSet())
    }

    @Test fun calendarPointsCarryContributingTransactionIdsForDrillDown() {
        val meta = """{"timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"}}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("cal", "calendar-card", meta),
            listOf(transaction("day10-a", 500), transaction("day10-b", -200), transaction("day11", -100, date = 20260511)),
            today = today,
        )
        val day10 = widget.points.single { it.period == "2026-05-10" }
        assertEquals(setOf("day10-a", "day10-b"), day10.transactionIds.toSet())
        assertEquals(listOf("day11"), widget.points.single { it.period == "2026-05-11" }.transactionIds)
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

    @Test fun spendingSingleMonthCarriesCurrentAndComparisonTransactionIds() {
        val meta = """{"mode":"single-month","compare":"2026-05","compareTo":"2026-04","isLive":false}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("spending", "spending-card", meta),
            listOf(transaction("may", -1_000), transaction("april", -800, date = 20260410)),
            today = today,
        )
        assertEquals(listOf("may"), widget.valueTransactionIds)
        assertEquals(listOf("april"), widget.comparisonTransactionIds)
    }

    @Test fun spendingBudgetModeHasNoComparisonTransactionIds() {
        val meta = """{"mode":"budget","compare":"2026-05","isLive":true}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("spending", "spending-card", meta),
            listOf(transaction("spent", -1_000)),
            budgetedByCategory = { mapOf("food" to 3_100L) },
            today = LocalDate.of(2026, 5, 10),
        )
        assertEquals(listOf("spent"), widget.valueTransactionIds)
        assertEquals(emptyList<String>(), widget.comparisonTransactionIds)
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

    @Test fun allActualiWidgetTypesHaveNativeKinds() {
        val expected = mapOf(
            "age-of-money-card" to ReportWidgetKind.AGE_OF_MONEY,
            "formula-card" to ReportWidgetKind.FORMULA,
            "custom-report" to ReportWidgetKind.CUSTOM_REPORT,
            "calendar-card" to ReportWidgetKind.CALENDAR,
            "crossover-card" to ReportWidgetKind.CROSSOVER,
            "budget-analysis-card" to ReportWidgetKind.BUDGET_ANALYSIS,
            "sankey-card" to ReportWidgetKind.SANKEY,
            "balance-forecast-card" to ReportWidgetKind.BALANCE_FORECAST,
            "monte-carlo-card" to ReportWidgetKind.MONTE_CARLO,
        )
        expected.forEach { (type, kind) ->
            val meta = if (type == "formula-card") """{"formula":"=1+2*3"}""" else null
            assertEquals(kind, CoreReportEngine.compute(
                DashboardWidgetRow(type, type, meta), emptyList(), today = today,
            ).kind)
        }
        assertEquals(700L, CoreReportEngine.compute(
            DashboardWidgetRow("formula", "formula-card", """{"formula":"=1+2*3"}"""),
            emptyList(), today = today,
        ).valueCents)
    }

    @Test fun dashboardCustomReportWidgetUsesSavedReportNameAndGraphType() {
        val accounts = listOf(ActualAccount("a", "A", ActualAccountType.CHECKING, false, false, 0, 0))
        val groups = listOf(ActualCategoryGroup("g", "G", false, false, 1.0,
            listOf(ActualCategory("c", "C", "g", false, false, 1.0))))
        val saved = SavedReportRow(
            "report1", "Monthly Expenses", null, null, false, "This month",
            "Category", "Payment", false, false, true, null, "DonutGraph", null, "and", "Monthly",
        )
        val widgetRow = DashboardWidgetRow("widget1", "custom-report", """{"id":"report1"}""")
        val pages = CoreReportEngine.dashboards(
            pages = emptyList(),
            widgets = { listOf(widgetRow) },
            transactions = listOf(transaction("t1", -500).copy(accountId = "a", categoryId = "c")),
            accounts = accounts,
            groups = groups,
            savedReports = listOf(saved),
            today = today,
        )
        val widget = pages.single().widgets.single()
        assertEquals("widget1", widget.id)
        assertEquals(ReportWidgetKind.CUSTOM_REPORT, widget.kind)
        assertEquals("Monthly Expenses", widget.name)
        assertEquals("DonutGraph", widget.graphType)
    }

    @Test fun dashboardCustomReportWidgetFallsBackWhenSavedReportMissing() {
        val widgetRow = DashboardWidgetRow("widget1", "custom-report", """{"id":"missing"}""")
        val pages = CoreReportEngine.dashboards(
            pages = emptyList(),
            widgets = { listOf(widgetRow) },
            transactions = listOf(transaction("t1", -500)),
            accounts = emptyList(),
            groups = emptyList(),
            savedReports = emptyList(),
            today = today,
        )
        val widget = pages.single().widgets.single()
        assertEquals("widget1", widget.id)
        assertEquals("Custom Report", widget.name)
        assertEquals(null, widget.graphType)
    }

    @Test fun balanceForecastWalksPostedTransactionsAndScheduledOccurrences() {
        val meta = """{"timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"}}"""
        val schedule = ActualScheduleSummary(
            "sched1", "Rent", null, DayDate(2026, 5, 25), null, null, "checking",
            null, ScheduledAmount.Fixed(-20_000), ScheduleAmountOp.EXACT, null,
            ScheduleDateCondition.Fixed(DayDate(2026, 5, 25)), false, false, null, null, false, null, null, null,
        )
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("bf", "balance-forecast-card", meta),
            listOf(transaction("before", -1_000, date = 20260401), transaction("inRange", 500, date = 20260510)),
            today = today,
            accountBalances = mapOf("checking" to 0L),
            schedules = listOf(schedule),
        )
        assertEquals(ReportWidgetKind.BALANCE_FORECAST, widget.kind)
        // starting balance (-1,000 before the window) + 500 posted in-window - 20,000 scheduled = -20,500
        assertEquals(-20_500L, widget.valueCents)
        assertEquals(-20_500L, widget.comparisonCents)
        assertEquals("1 scheduled transactions included", widget.subtitle)
        assertEquals(listOf("2026-05"), widget.points.map { it.period })
    }

    @Test fun balanceForecastNotesFilteredRangeWhenNoScheduledOccurrences() {
        val meta = """{"timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"}}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("bf", "balance-forecast-card", meta),
            listOf(transaction("inRange", -500, date = 20260510)),
            today = today,
            accountBalances = mapOf("checking" to 0L),
        )
        assertEquals("No scheduled transactions in this range", widget.subtitle)
        assertEquals(-500L, widget.valueCents)
    }

    @Test fun monteCarloReports100PercentSuccessWhenReturnsComfortablyFundZeroVolatilitySpending() {
        val meta = """{"currentAge":60,"targetAge":70,"simulationCount":1000,"inflationMean":null,
            "pots":[{"id":"p1","startingBalance":10000000,"expectedReturnMean":0.10,"returnStdDev":0.0}],
            "spendingPhases":[{"annualWithdrawal":100000}]}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("mc", "monte-carlo-card", meta), emptyList(), today = today,
        )
        assertEquals(ReportWidgetKind.MONTE_CARLO, widget.kind)
        assertEquals(100.0, widget.percentage)
        assertEquals("to age 70", widget.subtitle)
        assertEquals(11, widget.points.size)
    }

    @Test fun monteCarloReports0PercentSuccessWhenSpendingCannotBeCoveredAtAll() {
        val meta = """{"currentAge":60,"targetAge":70,"simulationCount":1000,"inflationMean":null,
            "pots":[{"id":"p1","startingBalance":1000,"expectedReturnMean":0.0,"returnStdDev":0.0}],
            "spendingPhases":[{"annualWithdrawal":100000}]}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("mc", "monte-carlo-card", meta), emptyList(), today = today,
        )
        assertEquals(0.0, widget.percentage)
    }

    @Test fun monteCarloFallsBackToAccountBalancesWhenNoPotsConfigured() {
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("mc", "monte-carlo-card", null), emptyList(), today = today,
            accountBalances = mapOf("checking" to 10_000_000L),
        )
        assertEquals(ReportWidgetKind.MONTE_CARLO, widget.kind)
        assertEquals("Monte Carlo Analysis", widget.name)
        assertEquals("to age 90", widget.subtitle)
        assertEquals(10_000_000L, widget.points.first().primaryCents)
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
