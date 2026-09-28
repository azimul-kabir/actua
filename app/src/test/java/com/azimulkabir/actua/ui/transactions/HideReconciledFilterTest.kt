package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.TransactionStatusFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideReconciledFilterTest {
    @Test
    fun `hides reconciled rows when the account preference is on`() {
        assertTrue(hiddenAsReconciled(transaction(reconciled = true), TransactionStatusFilter.ALL, hideReconciled = true))
    }

    @Test
    fun `keeps unreconciled rows when the account preference is on`() {
        assertFalse(hiddenAsReconciled(transaction(reconciled = false), TransactionStatusFilter.ALL, hideReconciled = true))
    }

    @Test
    fun `shows reconciled rows when the account preference is off`() {
        assertFalse(hiddenAsReconciled(transaction(reconciled = true), TransactionStatusFilter.ALL, hideReconciled = false))
    }

    @Test
    fun `reconciled status chip supersedes the account preference`() {
        assertFalse(hiddenAsReconciled(transaction(reconciled = true), TransactionStatusFilter.RECONCILED, hideReconciled = true))
    }

    private fun transaction(reconciled: Boolean) = Transaction(
        id = "transaction-id",
        date = "20260831",
        payee = "Store",
        category = "Groceries",
        account = "Checking",
        amount = -10,
        cleared = reconciled,
        reconciled = reconciled,
    )
}
