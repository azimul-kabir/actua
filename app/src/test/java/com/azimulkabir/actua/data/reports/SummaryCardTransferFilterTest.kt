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
 * #1526: the default YTD summary cards filter with `transfer is false`, which Actual's
 * conditionsToAQL compiles to `transfer_id = NULL` (the `transferred_id` column) and nothing else.
 */
class SummaryCardTransferFilterTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val context = RuleContext(offBudgetAccountIds = setOf("cd"))

    // The exact default cards Actual creates (see BlankBudgetFactory).
    private val totalIncome = """{"name":"Total Income (YTD)","content":"{\"type\":\"sum\",\"fontSize\":20}","timeFrame":{"start":"2024-01-01","end":"2024-12-31","mode":"yearToDate"},"conditions":[{"field":"amount","op":"gt","value":0},{"field":"account","op":"onBudget","value":""},{"field":"transfer","op":"is","value":false}],"conditionsOp":"and"}"""
    private val totalExpenses = """{"name":"Total Expenses (YTD)","content":"{\"type\":\"sum\",\"fontSize\":20}","timeFrame":{"start":"2024-01-01","end":"2024-12-31","mode":"yearToDate"},"conditions":[{"field":"amount","op":"lt","value":0},{"field":"account","op":"onBudget","value":""},{"field":"transfer","op":"is","value":false}],"conditionsOp":"and"}"""

    private fun tx(
        id: String, account: String, date: Int, amount: Long,
        transferId: String? = null, transferAccountId: String? = null, categoryId: String? = "groceries",
    ) = ActualTransaction(
        id, account, date, amount, "payee-$id", "Payee", categoryId, null, null, true, false, transferId,
        false, null, false, null, null, null, transferAccountId,
    )

    private fun card(meta: String, rows: List<ActualTransaction>) =
        CoreReportEngine.compute(DashboardWidgetRow("w", "summary-card", meta), rows, context = context, today = today).valueCents

    private val rows = listOf(
        tx("jan", "boa", 20260115, -1_000),
        tx("jul", "visa", 20260720, -2_000),
        tx("pay", "boa", 20260131, 50_000),
        // A payee that names an account but isn't linked as a transfer still counts, as in Actual.
        tx("unlinked", "boa", 20260301, -4_000, transferAccountId = "visa"),
        tx("blankPayeeAccount", "discover", 20260302, -8_000, transferAccountId = ""),
        // Linked transfers (both legs carry transferred_id) are excluded.
        tx("toVisa", "boa", 20260310, -16_000, transferId = "fromBoa", transferAccountId = "visa"),
        tx("fromBoa", "visa", 20260310, 16_000, transferId = "toVisa", transferAccountId = "boa"),
        // transfer_id = NULL is a NULL check: an empty string is not NULL in Actual either.
        tx("emptyLink", "boa", 20260311, -32_000, transferId = ""),
        // Off-budget accounts and last year stay out.
        tx("offBudget", "cd", 20260312, -64_000),
        tx("lastYear", "boa", 20251231, -128_000),
    )

    @Test fun ytdExpensesCountUnlinkedTransferPayeesAndExcludeLinkedTransfers() {
        assertEquals(-(1_000L + 2_000 + 4_000 + 8_000), card(totalExpenses, rows))
    }

    @Test fun ytdIncomeExcludesTheLinkedTransferLeg() {
        assertEquals(50_000L, card(totalIncome, rows))
    }

    @Test fun categoryNoneReportFilterOnlyExcludesLinkedTransfers() {
        // Upstream conditionSpecialCases: `category is (none)` becomes
        // category is null AND transfer is false AND parent is false.
        val rule = Rule.parse("report", null, "and", """[{"field":"category","op":"is","value":null}]""", "[]")
        fun matches(row: ActualTransaction) = RulesEngine.matches(row, rule.conditions, rule.conditionsOp, context)
        assertTrue(matches(tx("u", "boa", 20260301, -100, transferAccountId = "visa", categoryId = null)))
        assertFalse(matches(tx("l", "boa", 20260301, -100, transferId = "other", transferAccountId = "visa", categoryId = null)))
    }
}
