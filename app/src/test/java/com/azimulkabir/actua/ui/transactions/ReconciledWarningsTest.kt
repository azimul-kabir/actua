package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Actual's `confirm-transaction-edit` reasons for reconciled rows (#746). */
class ReconciledWarningsTest {
    private fun row(reconciled: Boolean = false, transferReconciled: Boolean = false) = Transaction(
        id = "t", date = "20260901", payee = "Store", category = "Groceries", account = "Checking",
        amount = -10, cleared = true, reconciled = reconciled, transferReconciled = transferReconciled,
    )

    @Test
    fun unreconciledRowsNeedNoConfirmation() {
        ReconciledAction.entries.forEach { assertNull(reconciledWarning(listOf(row(), row()), it)) }
        assertNull(reconciledWarning(emptyList(), ReconciledAction.DELETE))
    }

    @Test
    fun aReconciledRowWarnsForEveryAction() {
        val rows = listOf(row(), row(reconciled = true))
        assertEquals(
            "Saving your changes to this reconciled transaction may bring your reconciliation out of balance.",
            reconciledWarning(listOf(row(reconciled = true)), ReconciledAction.EDIT),
        )
        assertEquals(
            "Editing reconciled transactions may bring your reconciliation out of balance.",
            reconciledWarning(rows, ReconciledAction.BULK_EDIT),
        )
        assertEquals(
            "Deleting reconciled transactions may bring your reconciliation out of balance.",
            reconciledWarning(rows, ReconciledAction.DELETE),
        )
    }

    @Test
    fun aReconciledOtherTransferLegWarnsAboutThatAccount() {
        val rows = listOf(row(transferReconciled = true))
        assertEquals(
            "This transfer has a linked transaction in another account that is reconciled. " +
                "Editing it may bring that account's reconciliation out of balance.",
            reconciledWarning(rows, ReconciledAction.EDIT),
        )
        assertEquals(
            "This transfer has a linked transaction in another account that is reconciled. " +
                "Deleting it may bring that account's reconciliation out of balance.",
            reconciledWarning(rows, ReconciledAction.DELETE),
        )
        // A reconciled row of its own takes precedence, as in Actual.
        assertEquals(
            "Deleting reconciled transactions may bring your reconciliation out of balance.",
            reconciledWarning(rows + row(reconciled = true), ReconciledAction.DELETE),
        )
    }

    @Test
    fun bulkClearedEditsSkipReconciledRows() {
        val uncleared = row().copy(id = "u", cleared = false)
        val cleared = row().copy(id = "c")
        val reconciled = row(reconciled = true).copy(id = "r")
        val rows = listOf(uncleared, cleared, reconciled)
        assertEquals(listOf("u"), bulkClearedTargets(rows, cleared = true).map { it.id })
        assertEquals(listOf("c"), bulkClearedTargets(rows, cleared = false).map { it.id })
    }

    @Test
    fun unlockUsesActualsUnlockReconciledText() {
        assertEquals(
            "Unlocking this transaction means you won't be warned about changes that can impact your " +
                "reconciled balance. (Changes to amount, account, payee, etc).",
            UNLOCK_RECONCILED_WARNING,
        )
    }
}
