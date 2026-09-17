package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.SplitLine
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountRunningBalanceTest {
    @Test
    fun `accumulates in chronological order from newest-first input`() {
        // Newest first, as the register receives it.
        val transactions = listOf(
            transaction("3", account = "Everyday", amountCents = -1_500),
            transaction("2", account = "Everyday", amountCents = 72_000),
            transaction("1", account = "Everyday", amountCents = -2_450),
        )

        val balances = accountRunningBalances(transactions, "Everyday")

        assertEquals(-2_450L, balances["1"])
        assertEquals(69_550L, balances["2"])
        assertEquals(68_050L, balances["3"])
    }

    @Test
    fun `starting balance transaction seeds the running total`() {
        // Actual represents the opening balance as an ordinary, earliest-dated transaction row.
        val transactions = listOf(
            transaction("2", account = "Everyday", amountCents = -2_450),
            transaction("1", account = "Everyday", amountCents = 100_000),
        )

        val balances = accountRunningBalances(transactions, "Everyday")

        assertEquals(100_000L, balances["1"])
        assertEquals(97_550L, balances["2"])
    }

    @Test
    fun `only the current account's side of a transfer is counted`() {
        val transactions = listOf(
            transaction("2", account = "Credit card", amountCents = 5_000, transferAccount = "Everyday",
                type = Type.TRANSFER),
            transaction("1", account = "Everyday", amountCents = -5_000, transferAccount = "Credit card",
                type = Type.TRANSFER),
        )

        val everydayBalances = accountRunningBalances(transactions, "Everyday")
        val creditCardBalances = accountRunningBalances(transactions, "Credit card")

        assertEquals(-5_000L, everydayBalances["1"])
        assertNull(everydayBalances["2"])
        assertEquals(5_000L, creditCardBalances["2"])
        assertNull(creditCardBalances["1"])
    }

    @Test
    fun `split transaction contributes its already-summed total once`() {
        val transactions = listOf(
            transaction("2", account = "Everyday", amountCents = -300,
                splits = listOf(
                    SplitLine(category = "Groceries", amountCents = 200),
                    SplitLine(category = "Transport", amountCents = 100),
                )),
            transaction("1", account = "Everyday", amountCents = 1_000),
        )

        val balances = accountRunningBalances(transactions, "Everyday")

        assertEquals(1_000L, balances["1"])
        assertEquals(700L, balances["2"])
    }

    @Test
    fun `other accounts and a long history do not affect the running total`() {
        val history = (1..50).map { index ->
            transaction(index.toString(), account = "Everyday", amountCents = 100)
        }.reversed() + transaction("other", account = "Savings", amountCents = 999_999)

        val balances = accountRunningBalances(history, "Everyday")

        assertEquals(100L, balances["1"])
        assertEquals(5_000L, balances["50"])
        assertNull(balances["other"])
    }

    private fun transaction(
        id: String,
        account: String,
        amountCents: Long,
        transferAccount: String? = null,
        type: Type = Type.EXPENSE,
        splits: List<SplitLine> = emptyList(),
    ) = Transaction(
        id = id,
        date = "20260831",
        payee = "Payee",
        category = "Category",
        account = account,
        amount = (amountCents / 100).toInt(),
        cleared = true,
        amountCents = amountCents,
        type = type,
        transferAccount = transferAccount,
        splits = splits,
    )
}
