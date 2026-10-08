package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import com.azimulkabir.actua.data.budget.model.ActualBudgetMonth
import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryBudget
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

    @Test fun cashFlowCurrentMonthStopsAtToday() {
        val meta = """{"timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"}}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("cash", "cash-flow-card", meta),
            listOf(transaction("past", 500), transaction("future", 900, date = 20260525)),
            today = today,
        )
        assertEquals(500L, widget.points.single().primaryCents)
        assertEquals(listOf("past"), widget.points.single().transactionIds)
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
            "custom-report" to ReportWidgetKind.MISSING_REPORT,
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
        val accounts = listOf(ActualAccount("a", "A", ActualAccountType.CHECKING, false, false, 0.0, 0))
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

    @Test fun dashboardCustomReportWidgetShowsDeletedStateWhenSavedReportMissing() {
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
        assertEquals(ReportWidgetKind.MISSING_REPORT, widget.kind)
        assertEquals("This custom report has been deleted.", widget.markdown)
        assertEquals(null, widget.valueCents)
        assertEquals(emptyList<Any>(), widget.categories)
        assertEquals(emptyList<Any>(), widget.points)
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

    @Test fun balanceForecastIncludesAccountlessSchedulesWhenNoAccountFilterIsSet() {
        val meta = """{"timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"}}"""
        val schedule = ActualScheduleSummary(
            "sched1", "Unlinked", null, DayDate(2026, 5, 25), null, null, null,
            null, ScheduledAmount.Fixed(-20_000), ScheduleAmountOp.EXACT, null,
            ScheduleDateCondition.Fixed(DayDate(2026, 5, 25)), false, false, null, null, false, null, null, null,
        )
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("bf", "balance-forecast-card", meta), emptyList(), today = today,
            accountBalances = mapOf("checking" to 0L), schedules = listOf(schedule),
        )
        assertEquals(-20_000L, widget.valueCents)
        assertEquals("1 scheduled transactions included", widget.subtitle)
    }

    @Test fun balanceForecastExcludesAccountlessSchedulesWhenAnExplicitAccountFilterIsSet() {
        val meta = """{"timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"},"accounts":["checking"]}"""
        val schedule = ActualScheduleSummary(
            "sched1", "Unlinked", null, DayDate(2026, 5, 25), null, null, null,
            null, ScheduledAmount.Fixed(-20_000), ScheduleAmountOp.EXACT, null,
            ScheduleDateCondition.Fixed(DayDate(2026, 5, 25)), false, false, null, null, false, null, null, null,
        )
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("bf", "balance-forecast-card", meta), emptyList(), today = today,
            accountBalances = mapOf("checking" to 0L), schedules = listOf(schedule),
        )
        assertEquals(0L, widget.valueCents)
        assertEquals("No scheduled transactions in this range", widget.subtitle)
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

    @Test fun monteCarloPlansAYearsWithdrawalAgainstInflationCarriedFromThePriorYearNotThisOne() {
        // Zero growth/volatility isolates inflation timing: with a 1,000 flat annual withdrawal and
        // 50% inflation/year (no variance), a withdrawal planned against the inflation accumulated
        // through the *prior* year (upstream's order) leaves 7,500 after two years - 10,000 - 1,000 -
        // 1,500. Applying this year's inflation before that year's own withdrawal (the bug) instead
        // inflates year 1's withdrawal too, leaving only 6,250 - 10,000 - 1,500 - 2,250.
        val meta = """{"currentAge":60,"targetAge":62,"simulationCount":1000,
            "inflationMean":0.5,"inflationStdDev":0.0,
            "pots":[{"id":"p1","startingBalance":10000,"expectedReturnMean":0.0,"returnStdDev":0.0}],
            "spendingPhases":[{"annualWithdrawal":1000}]}"""
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("mc", "monte-carlo-card", meta), emptyList(), today = today,
        )
        assertEquals(7_500L, widget.points.last().primaryCents)
    }

    @Test fun monteCarloAppliesOneSharedMarketShockAcrossAllPotsInsteadOfIndependentDraws() {
        // Upstream draws one shared market shock per simulated year and scales it by each pot's own
        // volatility, so N pots with identical mean/stddev behave exactly like one pot holding their
        // combined balance. Independent per-pot draws (the bug) would diversify this away and change
        // the result, so splitting the same total balance across two identical pots must be a no-op.
        val singlePotMeta = """{"currentAge":60,"targetAge":61,"simulationCount":1000,"inflationMean":null,
            "pots":[{"id":"p1","startingBalance":200000,"expectedReturnMean":0.05,"returnStdDev":0.3}],
            "spendingPhases":[{"annualWithdrawal":0}]}"""
        val twoPotMeta = """{"currentAge":60,"targetAge":61,"simulationCount":1000,"inflationMean":null,
            "pots":[{"id":"p1","startingBalance":100000,"expectedReturnMean":0.05,"returnStdDev":0.3},
                {"id":"p2","startingBalance":100000,"expectedReturnMean":0.05,"returnStdDev":0.3}],
            "spendingPhases":[{"annualWithdrawal":0}]}"""
        val single = CoreReportEngine.compute(
            DashboardWidgetRow("mc1", "monte-carlo-card", singlePotMeta), emptyList(), today = today,
        )
        val twoPot = CoreReportEngine.compute(
            DashboardWidgetRow("mc2", "monte-carlo-card", twoPotMeta), emptyList(), today = today,
        )
        assertEquals(
            single.points.map { it.primaryCents to it.secondaryCents },
            twoPot.points.map { it.primaryCents to it.secondaryCents },
        )
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

    @Test fun budgetAnalysisScopesBudgetedAndSpentToCategoryConditionsAndTracksBalance() {
        val meta = """{"timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"},
            "conditions":[{"field":"category","op":"is","value":"food"}]}"""
        val budget = ActualBudgetMonth(
            "2026-05",
            listOf(
                categoryBudget("food", budgeted = 1_000, spent = -400, available = 600),
                categoryBudget("rent", budgeted = 2_000, spent = -2_000, available = 0),
            ),
            emptyList(), null, emptyList(), emptyList(),
        )
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("ba", "budget-analysis-card", meta), emptyList(),
            budgetMonth = { budget }, today = today,
        )
        val point = widget.points.single()
        assertEquals(1_000L, point.primaryCents)
        assertEquals(400L, point.secondaryCents)
        assertEquals(600L, point.tertiaryCents)
        assertEquals(600L, widget.balanceCents)
    }

    @Test fun budgetAnalysisExcludesHiddenCategoriesUnlessRequested() {
        val meta = """{"timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"}}"""
        val budget = ActualBudgetMonth(
            "2026-05",
            listOf(categoryBudget("food", budgeted = 1_000, spent = -400, available = 600)),
            emptyList(), null,
            listOf(categoryBudget("gifts", budgeted = 500, spent = 0, available = 500, hidden = true)),
            emptyList(),
        )
        val hiddenExcluded = CoreReportEngine.compute(
            DashboardWidgetRow("ba", "budget-analysis-card", meta), emptyList(),
            budgetMonth = { budget }, today = today,
        )
        assertEquals(1_000L, hiddenExcluded.points.single().primaryCents)

        val hiddenMeta = """{"timeFrame":{"mode":"static","start":"2026-05","end":"2026-05"},"showHiddenCategories":true}"""
        val hiddenIncluded = CoreReportEngine.compute(
            DashboardWidgetRow("ba", "budget-analysis-card", hiddenMeta), emptyList(),
            budgetMonth = { budget }, today = today,
        )
        assertEquals(1_500L, hiddenIncluded.points.single().primaryCents)
    }

    private fun categoryBudget(id: String, budgeted: Long, spent: Long, available: Long, hidden: Boolean = false) =
        ActualCategoryBudget(
            "2026-05", id, id, "group", "Group", 1.0, 1.0, budgeted, spent, available,
            available - budgeted - spent, hidden, false, null, false, false, null, null, null,
        )

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
