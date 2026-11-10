package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.data.rules.RulesEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * #1526: YTD summary cards that exclude the Starting Balances category with
 * `category doesNotContain "Starting Balances"`. Actual compiles that to
 * `NOT UNICODE_LIKE(..) OR category.name IS NULL`, so uncategorized transactions still count.
 */
class SummaryCardCategoryFilterTest {
    private val today = LocalDate.of(2026, 11, 9)
    private val context = RuleContext(
        offBudgetAccountIds = setOf("cd"),
        categoryNames = mapOf("food" to "Food", "pay" to "Paycheck", "sb" to "Starting Balances"),
    )

    // The four cards as exported from Actual in the report, differing only in name and amount condition.
    private fun card(name: String, type: String, amountOp: String) =
        """{"name":"$name","content":"{\"type\":\"$type\",\"fontSize\":20}","timeFrame":{"start":"2026-01","end":"2026-11","mode":"yearToDate"},"conditions":[{"field":"amount","op":"$amountOp","value":0},{"field":"account","op":"onBudget","value":""},{"field":"transfer","op":"is","value":false},{"field":"category","op":"doesNotContain","value":"Starting Balances","type":"id"}],"conditionsOp":"and"}"""

    private val totalIncome = card("Total Income (YTD)", "sum", "gt")
    private val totalExpenses = card("Total Expenses (YTD)", "sum", "lt")
    private val perTransaction = card("Avg Per Transaction", "avgPerTransact", "lt")

    private fun tx(id: String, account: String, date: Int, amount: Long, category: String?, transferId: String? = null) =
        ActualTransaction(
            id, account, date, amount, "payee-$id", "Payee", category, null, null, true, false, transferId,
            false, null, false, null, null, null, null,
        )

    private fun value(meta: String, rows: List<ActualTransaction>) =
        CoreReportEngine.compute(DashboardWidgetRow("w", "summary-card", meta), rows, context = context, today = today).valueCents

    private val rows = listOf(
        // Categorized and uncategorized (bank-synced or CSV-imported) rows all count.
        tx("food", "boa", 20260115, -1_000, "food"),
        tx("uncategorized1", "boa", 20260220, -2_000, null),
        tx("uncategorized2", "visa", 20260721, -4_000, null),
        tx("paycheck", "boa", 20260131, 50_000, "pay"),
        tx("uncategorizedIncome", "boa", 20260301, 6_000, null),
        // Excluded: the Starting Balances category, a linked transfer leg, an off-budget account, last year.
        tx("startingBalance", "boa", 20260101, -80_000, "sb"),
        tx("startingBalanceIncome", "visa", 20260101, 90_000, "sb"),
        tx("transfer", "boa", 20260310, -8_000, null, transferId = "other-leg"),
        tx("offBudget", "cd", 20260312, -16_000, null),
        tx("lastYear", "boa", 20251231, -32_000, null),
    )

    @Test fun ytdExpensesCountUncategorizedRowsAndSkipStartingBalances() {
        assertEquals(-(1_000L + 2_000 + 4_000), value(totalExpenses, rows))
    }

    @Test fun ytdIncomeCountsUncategorizedRowsAndSkipsStartingBalances() {
        assertEquals(50_000L + 6_000, value(totalIncome, rows))
    }

    @Test fun averagePerTransactionUsesTheSameRows() {
        assertEquals(-(1_000L + 2_000 + 4_000) / 3, value(perTransaction, rows))
    }

    @Test fun reportFiltersTreatAMissingNameAsNotContaining() {
        fun matches(field: String, op: String, value: String, row: ActualTransaction) = RulesEngine.matches(
            row, Rule.parse("report", null, "and", """[{"field":"$field","op":"$op","value":"$value"}]""", "[]").conditions,
            context = context,
        )
        val uncategorized = tx("u", "boa", 20260301, -100, null)
        val sb = tx("s", "boa", 20260301, -100, "sb")
        assertTrue(matches("category", "doesNotContain", "Starting Balances", uncategorized))
        assertFalse(matches("category", "doesNotContain", "Starting Balances", sb))
        assertTrue(matches("category", "doesNotContain", "Starting Balances", tx("f", "boa", 20260301, -100, "food")))
        // "contains" still needs a name to match.
        assertFalse(matches("category", "contains", "Starting Balances", uncategorized))
        assertTrue(matches("category", "contains", "Starting Balances", sb))
    }
}
