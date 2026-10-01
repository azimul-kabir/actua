package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

class ActualTransactionFormPlanTest {
    @Test
    fun transferLabelsNameAnAccount() {
        assertEquals(true, ActualTransactionFormService.isTransferLabel("Transfer: Savings"))
        assertEquals(true, ActualTransactionFormService.isTransferLabel("  Transfer: Savings "))
        assertEquals(false, ActualTransactionFormService.isTransferLabel("Transfer: "))
        assertEquals(false, ActualTransactionFormService.isTransferLabel("Transfers R Us"))
        assertEquals(false, ActualTransactionFormService.isTransferLabel("Store"))
    }

    @Test
    fun aReconciledRowStaysClearedUnlessItMovesAccount() {
        val reconciled = row(cleared = true, reconciled = true)
        fun guard(updated: ActualTransaction) = ActualTransactionFormService.keepReconciledInvariant(reconciled, updated)

        val uncleared = guard(reconciled.copy(cleared = false, amountCents = -500))
        assertEquals(true to true, uncleared.cleared to uncleared.reconciled)
        assertEquals(-500L, uncleared.amountCents)

        val moved = guard(reconciled.copy(accountId = "savings", cleared = false))
        assertEquals(false to false, moved.cleared to moved.reconciled)

        val ordinary = row(cleared = true, reconciled = false)
        val unclearedOrdinary = ordinary.copy(cleared = false)
        assertEquals(unclearedOrdinary, ActualTransactionFormService.keepReconciledInvariant(ordinary, unclearedOrdinary))
    }

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

    private fun row(cleared: Boolean, reconciled: Boolean) = ActualTransaction(
        id = "row", accountId = "checking", date = 20260901, amountCents = -1000,
        payeeId = null, payeeName = null, categoryId = null, categoryName = null,
        notes = null, cleared = cleared, reconciled = reconciled, transferId = null,
        isParent = false, parentId = null, tombstone = false, sortOrder = null,
        importedPayee = null, scheduleId = null, transferAccountId = null,
    )
}
