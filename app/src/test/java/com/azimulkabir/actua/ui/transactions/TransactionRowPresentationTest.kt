package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionRowPresentationTest {
    @Test
    fun `all accounts shows ordinary transaction account`() {
        val row = transactionRowPresentation(transaction(account = "Cash in Hand"), showAccount = true)

        assertEquals("Cash in Hand", row.accountLabel)
    }

    @Test
    fun `individual account hides ordinary transaction account`() {
        val row = transactionRowPresentation(transaction(account = "Cash in Hand"), showAccount = false)

        assertNull(row.accountLabel)
    }

    @Test
    fun `all accounts names both sides of outgoing transfer`() {
        val row = transactionRowPresentation(
            transaction(account = "Bhaiya", amountCents = -200_000, transferAccount = "Cash in Hand"),
            showAccount = true,
        )

        assertEquals("Transfer to Cash in Hand", row.title)
        assertEquals("From Bhaiya", row.transferContext)
        assertEquals("Transfer", row.categoryLabel)
    }

    @Test
    fun `individual account transfer only names other account`() {
        val row = transactionRowPresentation(
            transaction(account = "Cash in Hand", amountCents = 200_000, transferAccount = "Bhaiya"),
            showAccount = false,
        )

        assertEquals("Transfer from Bhaiya", row.title)
        assertNull(row.transferContext)
    }

    private fun transaction(
        account: String,
        amountCents: Long = -50_000,
        transferAccount: String? = null,
    ) = Transaction(
        id = "transaction-id",
        date = "20260831",
        payee = "Family Expense",
        category = "Transportation",
        account = account,
        amount = (amountCents / 100).toInt(),
        cleared = true,
        amountCents = amountCents,
        type = if (transferAccount == null) Type.EXPENSE else Type.TRANSFER,
        transferAccount = transferAccount,
        notes = "For School Transport",
    )
}
