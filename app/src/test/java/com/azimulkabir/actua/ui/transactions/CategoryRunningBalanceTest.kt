package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.SplitLine
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRunningBalanceTest {
    @Test fun `plain transactions accumulate oldest to newest from newest-first input`() {
        val rows = listOf(
            transaction("3", category = "Groceries", amountCents = -1_500),
            transaction("2", category = "Groceries", amountCents = -2_450),
            transaction("1", category = "Groceries", amountCents = -1_000),
        )

        val balances = categoryRunningBalances(rows, "Groceries")

        assertEquals(-1_000L, balances["1"])
        assertEquals(-3_450L, balances["2"])
        assertEquals(-4_950L, balances["3"])
    }

    @Test fun `the last row equals the sum of the visible transactions in exact cents`() {
        val cents = listOf(-199L, -1L, -33L, 7L, -4_001L)
        val rows = cents.mapIndexed { index, amount -> transaction("${cents.size - index}", "Dining", amount) }

        val balances = categoryRunningBalances(rows, "Dining")

        assertEquals(cents.sum(), balances[rows.first().id])
    }

    @Test fun `income and expense in one category net out`() {
        val rows = listOf(
            transaction("3", "Refunds", -500),
            transaction("2", "Refunds", 2_000),
            transaction("1", "Refunds", -750),
        )

        val balances = categoryRunningBalances(rows, "Refunds")

        assertEquals(-750L, balances["1"])
        assertEquals(1_250L, balances["2"])
        assertEquals(750L, balances["3"])
    }

    @Test fun `other categories and uncategorized transfers add nothing and get no figure`() {
        val rows = listOf(
            transaction("3", "Groceries", -300),
            transaction("2", "Dining", -9_999),
            transaction("t", "", -5_000, type = Type.TRANSFER, transferAccount = "Savings"),
            transaction("1", "Groceries", -100),
        )

        val balances = categoryRunningBalances(rows, "Groceries")

        assertEquals(setOf("1", "3"), balances.keys)
        assertEquals(-400L, balances["3"])
    }

    @Test fun `a split contributes only its lines in the category`() {
        // A -10.00 expense split into Groceries 6.00 and Dining 4.00.
        val split = transaction(
            "s", "Split", -1_000,
            splits = listOf(
                SplitLine(category = "Groceries", amountCents = 600),
                SplitLine(category = "Dining", amountCents = 400),
            ),
        )
        val rows = listOf(split, transaction("p", "Groceries", -200))

        val groceries = categoryRunningBalances(rows, "Groceries")
        val dining = categoryRunningBalances(rows, "Dining")

        assertEquals(-200L, groceries["p"])
        assertEquals(-800L, groceries["s"])
        assertEquals(mapOf("s" to -400L), dining)
    }

    @Test fun `several split lines in the same category are added together`() {
        val split = transaction(
            "s", "Split", -1_000,
            splits = listOf(
                SplitLine(category = "Groceries", amountCents = 300),
                SplitLine(category = "Groceries", amountCents = 200),
                SplitLine(category = "Dining", amountCents = 500),
            ),
        )

        assertEquals(-500L, categoryRunningBalances(listOf(split), "Groceries")["s"])
    }

    @Test fun `an opposite split line counts against the parent direction`() {
        // Income of 10.00 as 12.00 in Salary and an opposite 2.00 line in Fees.
        val split = transaction(
            "s", "Split", 1_000,
            splits = listOf(
                SplitLine(category = "Salary", amountCents = 1_200),
                SplitLine(category = "Fees", amountCents = 200, isOpposite = true),
            ),
        )

        assertEquals(1_200L, categoryRunningBalances(listOf(split), "Salary")["s"])
        assertEquals(-200L, categoryRunningBalances(listOf(split), "Fees")["s"])
    }

    @Test fun `upcoming schedule rows never count`() {
        val rows = listOf(
            transaction("u", "Rent", -90_000, isUpcoming = true),
            transaction("1", "Rent", -90_000),
        )

        val balances = categoryRunningBalances(rows, "Rent")

        assertNull(balances["u"])
        assertEquals(-90_000L, balances["1"])
    }

    @Test fun `only the rows passed in are summed so a narrower list gives a narrower total`() {
        val all = listOf(
            transaction("3", "Groceries", -300, account = "Cash"),
            transaction("2", "Groceries", -200, account = "Card"),
            transaction("1", "Groceries", -100, account = "Cash"),
        )
        val cashOnly = all.filter { it.account == "Cash" }

        assertEquals(-400L, categoryRunningBalances(cashOnly, "Groceries")["3"])
        assertEquals(-600L, categoryRunningBalances(all, "Groceries")["3"])
    }

    @Test fun `an empty list or a category with no rows gives no figures`() {
        assertTrue(categoryRunningBalances(emptyList(), "Groceries").isEmpty())
        assertTrue(categoryRunningBalances(listOf(transaction("1", "Dining", -1)), "Groceries").isEmpty())
    }

    @Test fun `a split with a line in the category belongs in that category's list`() {
        val split = transaction(
            "s", "Split", -1_000,
            splits = listOf(SplitLine(category = "Groceries", amountCents = 1_000)),
        )

        assertTrue(inCategory(split, "Groceries"))
        assertFalse(inCategory(split, "Dining"))
        assertTrue(inCategory(transaction("1", "Dining", -1), "Dining"))
        assertFalse(inCategory(transaction("2", "", -1, type = Type.TRANSFER), "Dining"))
    }

    @Test fun `the account running balance is unchanged`() {
        val rows = listOf(
            transaction("2", "Groceries", -300, account = "Cash"),
            transaction("1", "Dining", -100, account = "Cash"),
        )

        val balances = accountRunningBalances(rows, "Cash")

        assertEquals(-100L, balances["1"])
        assertEquals(-400L, balances["2"])
    }

    private fun transaction(
        id: String,
        category: String,
        amountCents: Long,
        account: String = "Everyday",
        type: Type = if (amountCents >= 0) Type.INCOME else Type.EXPENSE,
        transferAccount: String? = null,
        splits: List<SplitLine> = emptyList(),
        isUpcoming: Boolean = false,
    ) = Transaction(
        id = id,
        date = "20260831",
        payee = "Payee",
        category = category,
        account = account,
        amount = (amountCents / 100).toInt(),
        cleared = true,
        amountCents = amountCents,
        type = type,
        transferAccount = transferAccount,
        splits = splits,
        isUpcoming = isUpcoming,
    )
}
