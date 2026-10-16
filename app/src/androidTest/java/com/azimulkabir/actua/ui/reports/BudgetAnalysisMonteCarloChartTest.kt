package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.DrillDownDirection
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.components.formatMoneyCents
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Slice 10 of #1106 (#1116): Budget Analysis bars and the Monte Carlo fan chart. */
@RunWith(AndroidJUnit4::class)
class BudgetAnalysisMonteCarloChartTest {
    @get:Rule val compose = createComposeRule()

    private fun setContent(widget: ReportWidget, onQuery: (DrillDownQuery) -> Unit = {}) = compose.setContent {
        MaterialTheme {
            ReportsScreen(
                snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("o", "Overview", listOf(widget)))),
                hideDecimalPlaces = false,
                loadDrillDownTransactions = { query ->
                    onQuery(query)
                    listOf(Transaction("t", "2026-04-09", "Fresh Market", "Food", "Checking", -700, cleared = true))
                },
            )
        }
    }

    private val april = DrillDownQuery(
        startDate = 20260401, endDate = 20260430, categoryIds = setOf("food"),
        direction = DrillDownDirection.EXPENSE, month = "2026-04",
    )

    private val budgetAnalysis = ReportWidget(
        id = "ba", kind = ReportWidgetKind.BUDGET_ANALYSIS, name = "Budget Analysis",
        points = listOf(
            ReportPoint("2026-03", 1_000_00, 1_300_00, tertiaryCents = -300_00),
            ReportPoint("2026-04", 1_000_00, 700_00, tertiaryCents = 50_00, overspendingCents = 300_00, drillDownQuery = april),
        ),
        balanceCents = 50_00,
    )

    @Test
    fun tappingASpentBarOpensThatMonthsSpending() {
        var requested: DrillDownQuery? = null
        setContent(budgetAnalysis) { requested = it }

        compose.onNodeWithContentDescription("2026-04 Spent:", substring = true).performClick()
        compose.waitForIdle()

        assertEquals(april, requested)
        compose.onNodeWithText("Fresh Market").assertIsDisplayed()
    }

    @Test
    fun tooltipShowsAllFourValuesForTheSelectedMonth() {
        setContent(budgetAnalysis)
        // Budgeted has no drill-down, so a tap on it only selects the month.
        compose.onNodeWithContentDescription("2026-04 Budgeted:", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Budgeted ${formatMoneyCents(1_000_00, false)}", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Spent ${formatMoneyCents(700_00, false)}", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Overspending ${formatMoneyCents(300_00, false)}", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Balance ${formatMoneyCents(50_00, false)}", substring = true).assertIsDisplayed()
    }

    @Test
    fun monteCarloTooltipShowsTheRangeForThatYearWithoutDrillDown() {
        val widget = ReportWidget(
            id = "mc", kind = ReportWidgetKind.MONTE_CARLO, name = "Monte Carlo", percentage = 97.6, subtitle = "to age 75",
            points = listOf(
                ReportPoint("2026-05", 500_000_00, 500_000_00, percentileCents = List(5) { 500_000_00 }),
                ReportPoint(
                    "2027-05", 500_000_00, 400_000_00,
                    percentileCents = listOf(400_000_00, 450_000_00, 500_000_00, 550_000_00, 620_000_00),
                ),
            ),
        )
        setContent(widget)
        compose.onNodeWithContentDescription("2027-05:", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(
            "Likely range ${formatMoneyCents(450_000_00, false)} – ${formatMoneyCents(550_000_00, false)}", substring = true,
        ).assertIsDisplayed()
        compose.onNodeWithText("10–90%: ${formatMoneyCents(400_000_00, false)} – ${formatMoneyCents(620_000_00, false)}", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Tap to view transactions").assertDoesNotExist()
    }
}
