package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Desktop "Make transfer" (`validForTransfer`, `onSetTransfer`) at 59fe126f, case by case. */
class TransactionTransferLinkTest {
    private val payees = mapOf("checking" to "to-checking", "card" to "to-card")

    @Test
    fun twoRowsInDifferentAccountsWithCancellingAmountsAreValid() {
        assertNull(TransactionTransferLink.invalidReason(row("a"), row("b", accountId = "card", amountCents = 2500)))
    }

    @Test
    fun invalidPairsAreRejected() {
        val a = row("a")
        val b = row("b", accountId = "card", amountCents = 2500)
        assertEquals("One of the provided transactions does not exist", TransactionTransferLink.invalidReason(a, null))
        assertEquals("One of the provided transactions does not exist",
            TransactionTransferLink.invalidReason(a, b.copy(tombstone = true)))
        assertEquals("Choose two different transactions", TransactionTransferLink.invalidReason(a, a))
        assertEquals("One of the transactions is already a transfer",
            TransactionTransferLink.invalidReason(a.copy(transferId = "x"), b))
        assertEquals("One of the transactions is already a transfer",
            TransactionTransferLink.invalidReason(a, b.copy(transferId = "y")))
        assertEquals("Transfer transactions must be in different accounts",
            TransactionTransferLink.invalidReason(a, b.copy(accountId = "checking")))
        assertEquals("Transfer amounts must cancel each other out",
            TransactionTransferLink.invalidReason(a, b.copy(amountCents = 2499)))
        assertEquals("Transfer amounts must cancel each other out",
            TransactionTransferLink.invalidReason(a, b.copy(amountCents = -2500)))
        assertEquals("Split transactions can't be made into a transfer",
            TransactionTransferLink.invalidReason(a.copy(isParent = true), b))
        assertEquals("Split transactions can't be made into a transfer",
            TransactionTransferLink.invalidReason(a, b.copy(parentId = "p")))
    }

    @Test
    fun planLinksBothRowsWithEachOthersTransferPayeeAndClearsCategories() {
        val a = row("a", payeeId = "store", categoryId = "debt", notes = "card payment", cleared = true, reconciled = true,
            financialId = "bank-1", importedPayee = "PAYMENT THANK YOU")
        val b = row("b", accountId = "card", amountCents = 2500, payeeId = "bank", categoryId = "income", date = 20260903)

        val plan = TransactionTransferLink.plan(a, b, payees::get)

        assertEquals(listOf(a, b), plan.map { it.first })
        val (linkedA, linkedB) = plan.map { it.second }
        assertEquals(a.copy(payeeId = "to-card", transferId = "b", categoryId = null), linkedA)
        assertEquals(b.copy(payeeId = "to-checking", transferId = "a", categoryId = null), linkedB)
        // Only payee, link and category change: dates, cleared/reconciled and import data stay.
        assertEquals(setOf("description", "transferred_id", "category"),
            ActualTransactionWriter.changedFields(a, linkedA))
        assertEquals(setOf("description", "transferred_id", "category"),
            ActualTransactionWriter.changedFields(b, linkedB))
    }

    @Test
    fun planRefusesInvalidPairsAndMissingTransferPayees() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            TransactionTransferLink.plan(row("a"), row("b"), payees::get)
        }
        assertEquals("Transfer transactions must be in different accounts", error.message)
        assertThrows(IllegalArgumentException::class.java) {
            TransactionTransferLink.plan(row("a"), row("b", accountId = "card", amountCents = 2500)) { null }
        }
    }

    private fun row(
        id: String,
        accountId: String = "checking",
        date: Int = 20260901,
        amountCents: Long = -2500,
        payeeId: String? = null,
        categoryId: String? = null,
        notes: String? = null,
        cleared: Boolean = false,
        reconciled: Boolean = false,
        importedPayee: String? = null,
        financialId: String? = null,
    ) = ActualTransaction(
        id = id, accountId = accountId, date = date, amountCents = amountCents,
        payeeId = payeeId, payeeName = null, categoryId = categoryId, categoryName = null,
        notes = notes, cleared = cleared, reconciled = reconciled, transferId = null,
        isParent = false, parentId = null, tombstone = false, sortOrder = null,
        importedPayee = importedPayee, scheduleId = null, transferAccountId = null,
        financialId = financialId,
    )
}
