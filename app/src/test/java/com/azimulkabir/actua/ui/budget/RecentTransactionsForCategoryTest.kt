package com.azimulkabir.actua.ui.budget

import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentTransactionsForCategoryTest {
    @Test
    fun `returns the newest three transactions in the category`() {
        val all = listOf(
            transaction("a", "Groceries", "20260901"),
            transaction("b", "Groceries", "20260915"),
            transaction("c", "Dining", "20260920"),
            transaction("d", "Groceries", "20260910"),
            transaction("e", "Groceries", "20260905"),
        )

        val result = recentTransactionsForCategory(all, "Groceries")

        assertEquals(listOf("b", "d", "e"), result.map { it.id })
    }

    @Test
    fun `is empty when the list has no transaction in the category`() {
        // A "no category" filter on the Transactions tab leaves only uncategorized rows; the
        // details sheet must be fed the unfiltered list instead (#1207).
        val filtered = listOf(transaction("a", "Uncategorized", "20260901"))

        assertEquals(emptyList<String>(), recentTransactionsForCategory(filtered, "Groceries").map { it.id })
    }

    private fun transaction(id: String, category: String, date: String) = Transaction(
        id = id,
        date = date,
        payee = "Store",
        category = category,
        account = "Checking",
        amount = -10,
        cleared = true,
        type = Type.EXPENSE,
    )
}
