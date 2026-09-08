package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Test

class ActualTransactionFormPlanTest {
    @Test
    fun centsRoundsHalfAwayFromZero() {
        assertEquals(820L, ActualTransactionFormService.cents("8.20"))
        assertEquals(1L, ActualTransactionFormService.cents("0.005"))
        assertEquals(-1L, ActualTransactionFormService.cents("-0.005"))
        assertEquals(null, ActualTransactionFormService.cents("hello"))
    }

    @Test
    fun offBudgetStandardTransactionClearsCategory() {
        val form = transactionForm(categoryId = "category")

        val normalized = ActualTransactionFormService.enforceOffBudgetCategoryPolicy(form, setOf("account"))

        assertEquals(null, normalized.categoryId)
    }

    @Test
    fun offBudgetSplitClearsEveryChildCategory() {
        val form = transactionForm(
            categoryId = "parent-category",
            splits = listOf(
                ActualSplitLineForm(categoryId = "one", amount = "4.00"),
                ActualSplitLineForm(categoryId = "two", amount = "6.00"),
            ),
        )

        val normalized = ActualTransactionFormService.enforceOffBudgetCategoryPolicy(form, setOf("account"))

        assertEquals(null, normalized.categoryId)
        assertEquals(listOf(null, null), normalized.splits.map { it.categoryId })
    }

    @Test
    fun onBudgetTransactionRetainsCategories() {
        val form = transactionForm(
            categoryId = "parent-category",
            splits = listOf(ActualSplitLineForm(categoryId = "child-category", amount = "10.00")),
        )

        val normalized = ActualTransactionFormService.enforceOffBudgetCategoryPolicy(form, emptySet())

        assertEquals(form, normalized)
    }

    private fun transactionForm(
        categoryId: String?,
        splits: List<ActualSplitLineForm> = emptyList(),
    ) = ActualTransactionForm(
        accountId = "account",
        type = ActualTransactionType.EXPENSE,
        amount = "10.00",
        categoryId = categoryId,
        date = 20260908,
        splits = splits,
    )

}
