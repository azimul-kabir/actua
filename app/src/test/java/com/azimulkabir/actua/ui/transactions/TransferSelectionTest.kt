package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.SplitLine
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferSelectionTest {
    private val payment = row("payment")
    private val received = row("received", accountId = "card", account = "Card", amountCents = 2500)

    @Test
    fun twoRowsInDifferentAccountsWithCancellingAmountsCanBecomeATransfer() {
        assertEquals(payment to received, transferablePair(listOf(payment, received)))
        assertEquals(received to payment, transferablePair(listOf(received, payment)))
    }

    @Test
    fun makeTransferNeedsExactlyTwoRows() {
        assertNull(transferablePair(listOf(payment)))
        assertNull(transferablePair(listOf(payment, received, row("other", accountId = "savings", account = "Savings"))))
    }

    @Test
    fun sameAccountOrAmountsThatDoNotCancelAreDisabled() {
        assertNull(transferablePair(listOf(payment, received.copy(accountId = "checking", account = "Checking"))))
        assertNull(transferablePair(listOf(payment, received.copy(amountCents = 2499))))
        assertNull(transferablePair(listOf(payment, received.copy(amountCents = -2500))))
    }

    @Test
    fun accountsWithTheSameNameAreToldApartById() {
        assertEquals(
            payment to received.copy(account = "Checking"),
            transferablePair(listOf(payment, received.copy(account = "Checking"))),
        )
        assertNull(transferablePair(listOf(payment, received.copy(accountId = "checking", account = "Other"))))
    }

    @Test
    fun transfersSplitsAndUpcomingRowsAreDisabled() {
        assertNull(transferablePair(listOf(payment, received.copy(type = Type.TRANSFER, transferAccount = "Savings"))))
        assertNull(transferablePair(listOf(payment.copy(splits = listOf(SplitLine(amountCents = -2500))), received)))
        assertNull(transferablePair(listOf(payment, received.copy(isUpcoming = true))))
    }

    private fun row(id: String, accountId: String = "checking", account: String = "Checking", amountCents: Long = -2500) =
        Transaction(
            id = id, date = "2026-09-01", payee = "Payment", category = "", account = account,
            amount = 0, cleared = false, amountCents = amountCents,
            type = if (amountCents >= 0) Type.INCOME else Type.EXPENSE, accountId = accountId,
        )
}
