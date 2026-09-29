package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TransferDraftTest {
    private fun transfer(account: String, other: String, cents: Long) = Transaction(
        id = "leg", date = "2026-09-10", payee = "", category = "", account = account,
        amount = (cents / 100).toInt(), cleared = false, amountCents = cents,
        type = Type.TRANSFER, transferAccount = other,
    )

    @Test
    fun incomingLegBecomesAFromToDraft() {
        val draft = transfer("Savings", "Checking", 2_500).asTransferDraft()
        assertEquals("Checking", draft.account)
        assertEquals("Savings", draft.transferAccount)
        assertEquals(-2_500L, draft.amountCents)
    }

    @Test
    fun zeroAmountLegIsTreatedAsIncomingLikeTheEditor() {
        val draft = transfer("Savings", "Checking", 0).asTransferDraft()
        assertEquals("Checking", draft.account)
        assertEquals("Savings", draft.transferAccount)
    }

    @Test
    fun outgoingLegsAndNonTransfersAreUnchanged() {
        val outgoing = transfer("Checking", "Savings", -2_500)
        assertSame(outgoing, outgoing.asTransferDraft())
        val income = Transaction(id = "i", date = "2026-09-10", payee = "Pay", category = "Salary",
            account = "Checking", amount = 10, cleared = false)
        assertSame(income, income.asTransferDraft())
    }
}
