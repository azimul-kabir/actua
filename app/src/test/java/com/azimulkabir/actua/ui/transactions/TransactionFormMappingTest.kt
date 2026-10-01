package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.SplitLine
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The single-page Add transaction form ↔ the save model (#759). */
class TransactionFormMappingTest {
    private fun save(payee: String, account: String = "Checking", incoming: Boolean, amount: Long = 1_250) =
        transactionFromForm(
            id = "", date = "20260920", payee = payee, category = "Dining", account = account,
            incoming = incoming, amountCents = amount, cleared = false, notes = "",
            splits = listOf(SplitLine(category = "Dining", amountCents = amount)),
            rulesApplied = false, categoryIsExplicit = true,
        )

    @Test
    fun signGivesExpenseOrIncome() {
        val expense = save("Cafe", incoming = false)
        assertEquals(Type.EXPENSE, expense.type)
        assertEquals(-1_250L, expense.amountCents)
        assertEquals("Cafe", expense.payee)
        assertNull(expense.transferAccount)

        val income = save("Employer", incoming = true)
        assertEquals(Type.INCOME, income.type)
        assertEquals(1_250L, income.amountCents)
    }

    @Test
    fun transferPayeeMakesATransferFromTheSendingAccount() {
        val out = save("Transfer: Savings", incoming = false)
        assertEquals(Type.TRANSFER, out.type)
        assertEquals("Checking", out.account)
        assertEquals("Savings", out.transferAccount)
        assertEquals(-1_250L, out.amountCents)
        assertEquals("", out.payee)
        assertTrue(out.splits.isEmpty())

        val into = save("Transfer: Savings", incoming = true)
        assertEquals("Savings", into.account)
        assertEquals("Checking", into.transferAccount)
        assertEquals(-1_250L, into.amountCents)
    }

    @Test
    fun editingEitherSideOfATransferRoundTripsWithoutReversingIt() {
        val outgoing = Transaction(
            id = "out", date = "20260920", payee = "", category = "", account = "Checking", amount = -12,
            cleared = false, amountCents = -1_250, type = Type.TRANSFER, transferAccount = "Savings",
        )
        val incoming = outgoing.copy(id = "in", account = "Savings", transferAccount = "Checking", amountCents = 1_250, amount = 12)

        for (leg in listOf(outgoing, incoming)) {
            val start = transactionFormStart(leg, Type.EXPENSE)
            assertEquals("Transfer: ${leg.transferAccount}", start.payee)
            val saved = save(start.payee, account = leg.account, incoming = start.incoming)
            assertEquals("Checking", saved.account)
            assertEquals("Savings", saved.transferAccount)
        }
    }

    @Test
    fun newFormsStartFromTheDefaultType() {
        assertEquals(TransactionFormStart("", incoming = false), transactionFormStart(null, Type.EXPENSE))
        assertEquals(TransactionFormStart("", incoming = true), transactionFormStart(null, Type.INCOME))
        val income = Transaction(id = "i", date = "20260920", payee = "Employer", category = "Salary",
            account = "Checking", amount = 10, cleared = false)
        assertEquals(TransactionFormStart("Employer", incoming = true), transactionFormStart(income, Type.EXPENSE))
    }

    @Test
    fun onlyTransferPickerEntriesAreTransfers() {
        assertEquals("Savings", transferTargetOf("Transfer: Savings"))
        assertNull(transferTargetOf("Transfer: "))
        assertNull(transferTargetOf("Transfer Savings"))
        assertNull(transferTargetOf("Cafe"))
    }

    @Test
    fun splitLinePayeeOptionsLeaveOutTransfers() {
        assertEquals(
            listOf("Store", "Transfer: "),
            splitLinePayeeOptions(listOf("Store", "Transfer: Savings", "Transfer: ", "Transfer: Checking")),
        )
    }
}
