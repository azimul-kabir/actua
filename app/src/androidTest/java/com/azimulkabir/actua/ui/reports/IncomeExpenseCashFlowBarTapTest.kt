package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
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
import com.azimulkabir.actua.ui.components.formatMoneyCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for slice 5 of #1106 (#1111):
 * Income/Expense and Cash Flow bars open transactions in one tap,
 * legend toggles series visibility, and TalkBack reads and opens each bar.
 */
@RunWith(AndroidJUnit4::class)
class IncomeExpenseCashFlowBarTapTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tappingIncomeBarOpensThatMonthsIncomeTransactionsWithMatchingTotal() {
        val incomeCategory = ReportCategory("Income", 350_00L, listOf("tx-inc-1"))
        val expenseCategory = ReportCategory("Expenses", 120_00L, listOf("tx-exp-1"))
        val point = ReportPoint("2026-05", 350_00L, 120_00L, listOf("tx-inc-1", "tx-exp-1"))
        val widget = ReportWidget(
            id = "income-expense",
            kind = ReportWidgetKind.INCOME_EXPENSE,
            name = "Income vs expenses",
            valueCents = 230_00L,
            points = listOf(point),
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
                        listOf(Transaction("tx-inc-1", "2026-05-10", "Acme Corp", "Salary", "Checking", 350_00L, cleared = true))
                    },
                )
            }
        }

        // Tap the May 2026 Income bar directly in one tap
        compose.onNodeWithContentDescription("2026-05 Income:", substring = true)
            .assertIsDisplayed()
            .performClick()

        compose.waitForIdle()

        // Sheet opened with transactions and total matching the bar
        assertTrue("Transactions should have been loaded", requestedIds != null)
        compose.onNodeWithText("Acme Corp").assertIsDisplayed()
        compose.onNodeWithText(formatMoneyCents(350_00L, false), substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingExpenseBarOpensThatMonthsExpenseTransactionsWithMatchingTotal() {
        val incomeCategory = ReportCategory("Income", 350_00L, listOf("tx-inc-1"))
        val expenseCategory = ReportCategory("Expenses", 120_00L, listOf("tx-exp-1"))
        val point = ReportPoint("2026-05", 350_00L, 120_00L, listOf("tx-inc-1", "tx-exp-1"))
        val widget = ReportWidget(
            id = "income-expense",
            kind = ReportWidgetKind.INCOME_EXPENSE,
            name = "Income vs expenses",
            valueCents = 230_00L,
            points = listOf(point),
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
                        listOf(Transaction("tx-exp-1", "2026-05-12", "Grocery Mart", "Food", "Checking", -120_00L, cleared = true))
                    },
                )
            }
        }

        // Tap the May 2026 Expense bar directly in one tap
        compose.onNodeWithContentDescription("2026-05 Expense:", substring = true)
            .assertIsDisplayed()
            .performClick()

        compose.waitForIdle()

        assertTrue("Transactions should have been loaded", requestedIds != null)
        compose.onNodeWithText("Grocery Mart").assertIsDisplayed()
        compose.onNodeWithText(formatMoneyCents(120_00L, false), substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingCashFlowBarOpensThatMonthsTransactions() {
        val point = ReportPoint("2026-07", 400_00L, 180_00L, listOf("tx-cf-1"))
        val widget = ReportWidget(
            id = "cash-flow",
            kind = ReportWidgetKind.CASH_FLOW,
            name = "Cash Flow",
            valueCents = 220_00L,
            points = listOf(point),
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
                        listOf(Transaction("tx-cf-1", "2026-07-01", "Client", "Consulting", "Checking", 400_00L, cleared = true))
                    },
                )
            }
        }

        // One tap on the Income bar in Cash Flow
        compose.onNodeWithContentDescription("2026-07 Income:", substring = true)
            .assertIsDisplayed()
            .performClick()

        compose.waitForIdle()
        assertEquals(listOf("tx-cf-1"), requestedIds)
        compose.onNodeWithText("Client").assertIsDisplayed()
    }

    @Test
    fun legendTogglesSeriesVisibility() {
        val point = ReportPoint("2026-05", 350_00L, 120_00L, listOf("tx-1"))
        val widget = ReportWidget(
            id = "income-expense",
            kind = ReportWidgetKind.INCOME_EXPENSE,
            name = "Income vs expenses",
            points = listOf(point),
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

        // Both bars initially visible
        compose.onNodeWithContentDescription("2026-05 Income:", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("2026-05 Expense:", substring = true).assertIsDisplayed()

        // Tap legend to hide Income
        compose.onNodeWithContentDescription("Hide Income series").performClick()
        compose.waitForIdle()

        // Income bar is now hidden, Expense bar still visible
        compose.onNodeWithContentDescription("2026-05 Income:", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("2026-05 Expense:", substring = true).assertIsDisplayed()

        // Tap legend again to restore Income
        compose.onNodeWithContentDescription("Show Income series").performClick()
        compose.waitForIdle()

        // Income bar is visible again
        compose.onNodeWithContentDescription("2026-05 Income:", substring = true).assertIsDisplayed()
    }
}
