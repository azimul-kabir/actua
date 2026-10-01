package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergeSelectionTest {
    @Test
    fun twoPostedRowsInOneAccountWithOneAmountCanMergeInSelectionOrder() {
        val first = row("first")
        val second = row("second")

        assertEquals(first to second, mergeablePair(listOf(first, second)))
        assertEquals(second to first, mergeablePair(listOf(second, first)))
    }

    @Test
    fun mergeNeedsExactlyTwoRows() {
        assertNull(mergeablePair(listOf(row("a"))))
        assertNull(mergeablePair(listOf(row("a"), row("b"), row("c"))))
    }

    @Test
    fun differentAccountsOrAmountsCannotMerge() {
        assertNull(mergeablePair(listOf(row("a"), row("b", accountId = "savings", account = "Savings"))))
        assertNull(mergeablePair(listOf(row("a"), row("b", amountCents = -999))))
    }

    @Test
    fun accountsWithTheSameNameAreToldApartById() {
        assertNull(mergeablePair(listOf(row("a"), row("b", accountId = "checking-2"))))
    }

    @Test
    fun upcomingRowsCannotMerge() {
        assertNull(mergeablePair(listOf(row("a"), row("b", isUpcoming = true))))
    }

    @Test
    fun twoTransfersMergeOnlyToTheSameAccount() {
        val toSavings = row("a", transferAccountId = "savings", transferAccount = "Savings")
        assertEquals(
            "a",
            mergeablePair(listOf(toSavings, row("b", transferAccountId = "savings", transferAccount = "Savings")))?.first?.id,
        )
        assertNull(mergeablePair(listOf(toSavings, row("b", transferAccountId = "brokerage", transferAccount = "Brokerage"))))
        // A transfer and an ordinary row can merge.
        assertEquals("a", mergeablePair(listOf(toSavings, row("b")))?.first?.id)
    }

    private fun row(
        id: String,
        accountId: String = "checking",
        account: String = "Checking",
        amountCents: Long = -1000,
        isUpcoming: Boolean = false,
        transferAccountId: String? = null,
        transferAccount: String? = null,
    ) = Transaction(
        id = id, date = "2026-09-01", payee = "Store", category = "Groceries", account = account,
        amount = 0, cleared = false, amountCents = amountCents,
        type = if (transferAccountId != null) Type.TRANSFER else Type.EXPENSE,
        transferAccount = transferAccount, isUpcoming = isUpcoming,
        accountId = accountId, transferAccountId = transferAccountId,
    )
}
