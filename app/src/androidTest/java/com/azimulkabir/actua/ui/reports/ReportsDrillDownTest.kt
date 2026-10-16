package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Issue #230 (reporting parity): the calculation layer (`ReportAggregator`/`SavedReportEngine`)
 * has fixture coverage, but the Compose wiring that turns a tapped segment into the transactions
 * behind it did not. This exercises that path end to end for the income vs expenses card, which
 * is the report family most directly tied to the drill-down acceptance criteria.
 */
@RunWith(AndroidJUnit4::class)
class ReportsDrillDownTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingACategoryOpensItsContributingTransactions() {
        val incomeCategory = ReportCategory("Income", 300_00, listOf("tx-1"))
        val expenseCategory = ReportCategory("Expenses", -100_00, listOf("tx-2"))
        val widget = ReportWidget(
            id = "income-expense",
            kind = ReportWidgetKind.INCOME_EXPENSE,
            name = "Income vs expenses",
            valueCents = 200_00,
            points = listOf(ReportPoint("2026-05", 300_00, 100_00)),
            categories = listOf(incomeCategory, expenseCategory),
        )
        val overview = ReportDashboardPage("overview", "Overview", listOf(widget))
        var requestedIds: List<String>? = null

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(overview)),
                    hideDecimalPlaces = false,
                    loadTransactions = { ids ->
                        requestedIds = ids
                        ids.map { id -> Transaction(id, "2026-05-10", "Employer", "Salary", "Checking", 300, cleared = true) }
                    },
                )
            }
        }

        compose.onNodeWithText("Income").performClick()

        compose.waitForIdle()
        assertEquals(listOf("tx-1"), requestedIds)
        compose.onNodeWithText("Employer").assertExists()
    }

    @Test fun tappingACashFlowPeriodOpensItsContributingTransactions() {
        val widget = ReportWidget(
            id = "cash",
            kind = ReportWidgetKind.CASH_FLOW,
            name = "Cash Flow",
            points = listOf(ReportPoint("2026-05", 300_00, 100_00, listOf("tx-cash"))),
        )
        val overview = ReportDashboardPage("overview", "Overview", listOf(widget))
        var requestedIds: List<String>? = null

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(overview)),
                    hideDecimalPlaces = false,
                    loadTransactions = { ids ->
                        requestedIds = ids
                        ids.map { id -> Transaction(id, "2026-05-10", "Employer", "Salary", "Checking", 300, cleared = true) }
                    },
                )
            }
        }

        // Tap the bar chart to select the period, then tap the resulting (clickable) summary to
        // drill down; the period label alone is ambiguous since the chart's always-visible
        // first/last range row repeats the same "2026-05" text for a single-point chart.
        compose.onNodeWithContentDescription("Cash flow chart with 1 periods. Tap a period to read income and expense.")
            .performClick()
        compose.onNode(hasText("2026-05") and hasClickAction()).performClick()

        compose.waitForIdle()
        assertEquals(listOf("tx-cash"), requestedIds)
    }

    @Test fun tappingASpendingTotalOpensItsContributingTransactions() {
        val widget = ReportWidget(
            id = "spending",
            kind = ReportWidgetKind.SPENDING,
            name = "Spending",
            valueCents = 100_00,
            comparisonCents = 80_00,
            valueTransactionIds = listOf("tx-current"),
            comparisonTransactionIds = listOf("tx-comparison"),
        )
        val overview = ReportDashboardPage("overview", "Overview", listOf(widget))
        var requestedIds: List<String>? = null

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(overview)),
                    hideDecimalPlaces = false,
                    loadTransactions = { ids ->
                        requestedIds = ids
                        ids.map { id -> Transaction(id, "2026-05-10", "Employer", "Salary", "Checking", 300, cleared = true) }
                    },
                )
            }
        }

        compose.onNodeWithText("This month").performClick()

        compose.waitForIdle()
        assertEquals(listOf("tx-current"), requestedIds)
    }

    @Test fun tappingACalendarDayWithTransactionsOpensItsContributingTransactionsDirectly() {
        val widget = ReportWidget(
            id = "calendar",
            kind = ReportWidgetKind.CALENDAR,
            name = "Calendar",
            points = listOf(
                ReportPoint("2026-08-15", 5_000_00, 1_200_00, listOf("tx-cal-1", "tx-cal-2")),
            ),
        )
        val overview = ReportDashboardPage("overview", "Overview", listOf(widget))
        var requestedIds: List<String>? = null

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(overview)),
                    hideDecimalPlaces = false,
                    loadTransactions = { ids ->
                        requestedIds = ids
                        ids.map { id -> Transaction(id, "2026-08-15", "Store", "Shopping", "Checking", 600, cleared = true) }
                    },
                )
            }
        }

        // Issue #1107: One tap on a day with transactions opens that day's sheet straight away.
        compose.onNodeWithText("15").performClick()

        compose.waitForIdle()
        assertEquals(listOf("tx-cal-1", "tx-cal-2"), requestedIds)
        compose.onNodeWithText("Store").assertExists()
        compose.onNodeWithText("2 transactions", substring = true).assertExists()
    }

    @Test fun tappingACalendarDayWithoutTransactionsDoesNotOpenTransactions() {
        val widget = ReportWidget(
            id = "calendar",
            kind = ReportWidgetKind.CALENDAR,
            name = "Calendar",
            points = listOf(
                ReportPoint("2026-08-15", 5_000_00, 1_200_00, emptyList()),
            ),
        )
        val overview = ReportDashboardPage("overview", "Overview", listOf(widget))
        var requestedIds: List<String>? = null

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(overview)),
                    hideDecimalPlaces = false,
                    loadTransactions = { ids ->
                        requestedIds = ids
                        emptyList()
                    },
                )
            }
        }

        compose.onNodeWithText("15").performClick()
        compose.waitForIdle()

        assertEquals(null, requestedIds)
        compose.onNodeWithText("Out", substring = true).assertExists()
    }

    @Test fun calendarDayHasTalkBackDescriptionAndActionLabel() {
        val widget = ReportWidget(
            id = "calendar",
            kind = ReportWidgetKind.CALENDAR,
            name = "Calendar",
            points = listOf(
                ReportPoint("2026-08-15", 5_000_00, 1_200_00, listOf("tx-cal-1")),
            ),
        )
        val overview = ReportDashboardPage("overview", "Overview", listOf(widget))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(overview)),
                    hideDecimalPlaces = false,
                )
            }
        }

        compose.onNodeWithContentDescription("August 15, in", substring = true).assertExists()
    }
}

