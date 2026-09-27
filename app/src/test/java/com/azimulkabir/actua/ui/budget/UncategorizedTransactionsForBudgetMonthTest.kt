package com.azimulkabir.actua.ui.budget

import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Test

class UncategorizedTransactionsForBudgetMonthTest {
    @Test
    fun `includes on-budget transaction with no category in the given month`() {
        val transaction = transaction(id = "uncategorized", category = "Uncategorized", date = "20260915")

        val result = uncategorizedTransactionsForBudgetMonth(listOf(transaction), month = "2026-09")

        assertEquals(listOf("uncategorized"), result.map { it.id })
    }

    @Test
    fun `excludes a categorized transaction`() {
        val transaction = transaction(id = "categorized", category = "Groceries", date = "20260915")

        val result = uncategorizedTransactionsForBudgetMonth(listOf(transaction), month = "2026-09")

        assertEquals(emptyList<String>(), result.map { it.id })
    }

    @Test
    fun `excludes an off-budget account's uncategorized transaction`() {
        // ActuaRepository never falls back to the literal "Uncategorized" label for an
        // off-budget account (it leaves category blank instead), so this is already excluded.
        val transaction = transaction(id = "off-budget", category = "", date = "20260915", accountOffBudget = true)

        val result = uncategorizedTransactionsForBudgetMonth(listOf(transaction), month = "2026-09")

        assertEquals(emptyList<String>(), result.map { it.id })
    }

    @Test
    fun `excludes a transfer even without a category`() {
        val transaction = transaction(id = "transfer", category = "", date = "20260915", type = Type.TRANSFER)

        val result = uncategorizedTransactionsForBudgetMonth(listOf(transaction), month = "2026-09")

        assertEquals(emptyList<String>(), result.map { it.id })
    }

    @Test
    fun `excludes an uncategorized transaction from a different month`() {
        val transaction = transaction(id = "other-month", category = "Uncategorized", date = "20260815")

        val result = uncategorizedTransactionsForBudgetMonth(listOf(transaction), month = "2026-09")

        assertEquals(emptyList<String>(), result.map { it.id })
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
