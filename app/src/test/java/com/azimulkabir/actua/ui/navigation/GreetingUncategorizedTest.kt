package com.azimulkabir.actua.ui.navigation

import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import com.azimulkabir.actua.ui.budget.uncategorizedTransactionsForBudgetMonth
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The greeting's count to categorize is the Budget screen banner's count for the current month (#1660). */
class GreetingUncategorizedTest {
    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `counts only this month's uncategorized rows`() {
        val rows = listOf(
            transaction("this-month", "Uncategorized", "20261103"),
            transaction("also-this-month", "Uncategorized", "20261130"),
            transaction("last-month", "Uncategorized", "20261028"),
            transaction("next-month", "Uncategorized", "20261201"),
            transaction("last-year", "Uncategorized", "20251110"),
        )

        assertEquals(2, uncategorizedThisMonth(rows, today))
    }

    @Test
    fun `a split counts as one categorized row, not by its lines`() {
        // The transaction list holds the split parent (category "Split"); its lines, categorized or not,
        // are not list rows, so neither the banner nor the greeting counts them.
        val rows = listOf(transaction("split", "Split", "20261105"))

        assertEquals(0, uncategorizedThisMonth(rows, today))
    }

    @Test
    fun `transfers, off-budget rows and categorized rows are not counted`() {
        val rows = listOf(
            transaction("transfer", "", "20261105", type = Type.TRANSFER),
            transaction("off-budget", "", "20261105", accountOffBudget = true),
            transaction("categorized", "Groceries", "20261105"),
        )

        assertEquals(0, uncategorizedThisMonth(rows, today))
    }

    @Test
    fun `agrees with the Budget banner for the same month`() {
        val rows = listOf(
            transaction("a", "Uncategorized", "20261101"),
            transaction("b", "Uncategorized", "20261002"),
            transaction("c", "Split", "20261103"),
            transaction("d", "", "20261104", type = Type.TRANSFER),
            transaction("e", "Uncategorized", "20261114"),
        )

        assertEquals(
            uncategorizedTransactionsForBudgetMonth(rows, "2026-11").size,
            uncategorizedThisMonth(rows, today),
        )
    }

    private fun transaction(
        id: String,
        category: String,
        date: String,
        type: Type = Type.EXPENSE,
        accountOffBudget: Boolean = false,
    ) = Transaction(
        id = id,
        date = date,
        payee = "Store",
        category = category,
        account = "Checking",
        amount = -10,
        cleared = true,
        type = type,
        accountOffBudget = accountOffBudget,
    )
}
