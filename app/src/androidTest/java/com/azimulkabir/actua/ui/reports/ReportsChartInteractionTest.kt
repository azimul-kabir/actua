package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #496 (Actual PWA parity): the Cash Flow and Calendar dashboard widgets used to be static
 * text/bars with no way to read an exact value, unlike the Actual PWA's tap-for-detail charts, and
 * Spending had no PWA-style "more/less spent than comparison" callout. This exercises the new
 * tap-to-reveal-a-tooltip interactions and the Spending delta callout.
 */
@RunWith(AndroidJUnit4::class)
class ReportsChartInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingTheCashFlowChartRevealsThePeriodTooltip() {
        val widget = ReportWidget(
            id = "cash-flow",
            kind = ReportWidgetKind.CASH_FLOW,
            name = "Cash Flow",
            points = listOf(ReportPoint("2026-08", 150_000_00, 90_000_00)),
        )
        val page = ReportDashboardPage("main", "Main", listOf(widget))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(page)),
                    hideDecimalPlaces = false,
                )
            }
        }

        compose.onNodeWithText("Net", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Cash flow chart with 1 periods. Tap a period to read income and expense.")
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Net", substring = true).assertExists()
    }

    @Test fun spendingWidgetShowsHowMuchMoreWasSpentThanTheComparison() {
        val widget = ReportWidget(
            id = "spending",
            kind = ReportWidgetKind.SPENDING,
            name = "Spending",
            valueCents = 118_573_00,
            comparisonCents = 63_848_00,
        )
        val page = ReportDashboardPage("main", "Main", listOf(widget))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(page)),
                    hideDecimalPlaces = false,
                )
            }
        }

        compose.onNodeWithText("more spent", substring = true).assertExists()
    }

    @Test fun tappingACalendarDayRevealsItsIncomeAndExpense() {
        val widget = ReportWidget(
            id = "calendar",
            kind = ReportWidgetKind.CALENDAR,
            name = "Calendar",
            points = listOf(ReportPoint("2026-08-15", 5_000_00, 1_200_00)),
        )
        val page = ReportDashboardPage("main", "Main", listOf(widget))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(page)),
                    hideDecimalPlaces = false,
                )
            }
        }

        compose.onNodeWithText("15").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Out", substring = true).assertExists()
    }

    @Test fun sankeyRendersACategoryPerNodeAndARemainingNodeForUnspentIncome() {
        val widget = ReportWidget(
            id = "sankey",
            kind = ReportWidgetKind.SANKEY,
            name = "Sankey",
            valueCents = 500_00,
            comparisonCents = 300_00,
            categories = listOf(
                com.azimulkabir.actua.model.ReportCategory("Rent", 200_00),
                com.azimulkabir.actua.model.ReportCategory("Groceries", 100_00),
            ),
        )
        val page = ReportDashboardPage("main", "Main", listOf(widget))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(page)),
                    hideDecimalPlaces = false,
                )
            }
        }

        compose.onNodeWithText("Rent").assertExists()
        compose.onNodeWithText("Groceries").assertExists()
        // Income (500) exceeds categorized expenses (300), so the diagram should show the
        // uncategorized/unspent remainder as its own flow node rather than silently dropping it.
        compose.onNodeWithText("Remaining").assertExists()
    }

    @Test fun tappingASankeyLegendRowHighlightsItInTheSummary() {
        val widget = ReportWidget(
            id = "sankey",
            kind = ReportWidgetKind.SANKEY,
            name = "Sankey",
            valueCents = 300_00,
            comparisonCents = 300_00,
            categories = listOf(com.azimulkabir.actua.model.ReportCategory("Rent", 300_00)),
        )
        val page = ReportDashboardPage("main", "Main", listOf(widget))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(page)),
                    hideDecimalPlaces = false,
                )
            }
        }

        compose.onNodeWithText("Expenses").assertExists()
        compose.onNodeWithText("Rent").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Expenses").assertDoesNotExist()
    }

    @Test fun stackedBarChartShowsAPerCategoryLegendAndDrillsDownOnASegmentTap() {
        val widget = ReportWidget(
            id = "stacked",
            kind = ReportWidgetKind.CUSTOM_REPORT,
            name = "Spending by category",
            graphType = "StackedBarGraph",
            timeMode = true,
            points = listOf(
                ReportPoint(
                    "2026-01", -1200_00,
                    segments = listOf(
                        ReportCategory("Rent", -1000_00, listOf("tx-rent")),
                        ReportCategory("Food", -200_00, listOf("tx-food")),
                    ),
                ),
            ),
        )
        val page = ReportDashboardPage("main", "Main", listOf(widget))
        var requestedIds: List<String>? = null

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(page)),
                    hideDecimalPlaces = false,
                    loadTransactions = { ids ->
                        requestedIds = ids
                        ids.map { id -> Transaction(id, "2026-01-10", "Landlord", "Rent", "Checking", 1000, cleared = true) }
                    },
                )
            }
        }

        compose.onNodeWithText("Rent").assertExists()
        compose.onNodeWithText("Food").assertExists()

        // "Food" is the smaller segment and is drawn stacked above "Rent", so it sits at the top
        // of the single bar; tapping there should drill down to its own transactions.
        compose.onNodeWithContentDescription(
            "Stacked bar chart with 1 periods. Tap a bar for its breakdown, or a segment to view its transactions.",
        ).performTouchInput { click(topCenter + Offset(0f, 5f)) }
        compose.waitForIdle()

        assertEquals(listOf("tx-food"), requestedIds)
    }
}
