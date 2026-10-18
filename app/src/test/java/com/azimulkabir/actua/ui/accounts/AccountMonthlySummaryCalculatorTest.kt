package com.azimulkabir.actua.ui.accounts

import com.azimulkabir.actua.model.SplitLine
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountMonthlySummaryCalculatorTest {
    @Test fun `uses category classification and excludes transfers and uncategorized rows`() {
        val summary = AccountMonthlySummaryCalculator.calculate(listOf(
            transaction(50_000, categoryIsIncome = true),
            transaction(-12_000, categoryIsIncome = false),
            transaction(-8_000, type = Type.TRANSFER),
            transaction(8_000, type = Type.TRANSFER),
            transaction(90_000),
            transaction(-4_000),
        ))

        assertEquals(50_000, summary.incomeCents)
        assertEquals(12_000, summary.expenseCents)
        assertEquals(38_000, summary.netCents)
    }

    @Test fun `classifies split portions without counting the parent`() {
        val summary = AccountMonthlySummaryCalculator.calculate(listOf(
            transaction(-11_000, categoryIsIncome = null).copy(splits = listOf(
                SplitLine(amountCents = 10_000, categoryIsIncome = false),
                SplitLine(amountCents = 3_000, categoryIsIncome = false),
                SplitLine(amountCents = 2_000, isOpposite = true, categoryIsIncome = true),
            )),
        ))

        assertEquals(2_000, summary.incomeCents)
        assertEquals(13_000, summary.expenseCents)
        assertEquals(-11_000, summary.netCents)
    }

    @Test fun `refunds and negative income reduce their category totals`() {
        val summary = AccountMonthlySummaryCalculator.calculate(listOf(
            transaction(2_500, categoryIsIncome = false),
            transaction(-1_500, categoryIsIncome = true),
        ))

        assertEquals(-1_500, summary.incomeCents)
        assertEquals(-2_500, summary.expenseCents)
        assertEquals(1_000, summary.netCents)
    }

    @Test fun `on-budget leg of a transfer into an off-budget account counts as an expense`() {
        val summary = AccountMonthlySummaryCalculator.calculate(
            listOf(transaction(-20_000, type = Type.TRANSFER, account = "Checking", transferAccount = "DPS Savings")),
            offBudgetAccountNames = setOf("DPS Savings"),
        )

        assertEquals(0, summary.incomeCents)
        assertEquals(20_000, summary.expenseCents)
        assertEquals(-20_000, summary.netCents)
    }

    @Test fun `on-budget leg of a transfer from an off-budget account counts as income`() {
        val summary = AccountMonthlySummaryCalculator.calculate(
            listOf(transaction(20_000, type = Type.TRANSFER, account = "Checking", transferAccount = "DPS Savings")),
            offBudgetAccountNames = setOf("DPS Savings"),
        )

        assertEquals(20_000, summary.incomeCents)
        assertEquals(0, summary.expenseCents)
        assertEquals(20_000, summary.netCents)
    }

    @Test fun `off-budget leg of a cross-boundary transfer is excluded to avoid double counting`() {
        val summary = AccountMonthlySummaryCalculator.calculate(
            listOf(transaction(20_000, type = Type.TRANSFER, account = "DPS Savings", transferAccount = "Checking")),
            offBudgetAccountNames = setOf("DPS Savings"),
        )

        assertEquals(0, summary.incomeCents)
        assertEquals(0, summary.expenseCents)
    }

    @Test fun `transfer between two off-budget accounts is excluded`() {
        val summary = AccountMonthlySummaryCalculator.calculate(
            listOf(transaction(-5_000, type = Type.TRANSFER, account = "DPS Savings", transferAccount = "Investment")),
            offBudgetAccountNames = setOf("DPS Savings", "Investment"),
        )

        assertEquals(0, summary.incomeCents)
        assertEquals(0, summary.expenseCents)
    }

    @Test fun `filterTransactionsForSummary with calendar month start day filters by current calendar month`() {
        val txOct1 = transaction(10_000, date = "2026-10-01")
        val txOct15 = transaction(20_000, date = "20261015")
        val txSep30 = transaction(30_000, date = "2026-09-30")
        val txNov1 = transaction(40_000, date = "2026-11-01")

        val (filtered, period) = filterTransactionsForSummary(
            transactions = listOf(txOct1, txOct15, txSep30, txNov1),
            summaryStartDay = 1,
            today = java.time.LocalDate.of(2026, 10, 18),
        )

        assertEquals(null, period)
        assertEquals(listOf(txOct1, txOct15), filtered)
    }

    @Test fun `filterTransactionsForSummary with custom start day filters by pay cycle period`() {
        // Today is Oct 18, start day is 25 -> period is Sep 25 to Oct 24
        val txBefore = transaction(10_000, date = "2026-09-24")
        val txStart = transaction(20_000, date = "2026-09-25")
        val txInside = transaction(30_000, date = "2026-10-10")
        val txEnd = transaction(40_000, date = "2026-10-24")
        val txAfter = transaction(50_000, date = "2026-10-25")

        val (filtered, period) = filterTransactionsForSummary(
            transactions = listOf(txBefore, txStart, txInside, txEnd, txAfter),
            summaryStartDay = 25,
            today = java.time.LocalDate.of(2026, 10, 18),
        )

        val expectedPeriod = com.azimulkabir.actua.data.home.HomeSummaryPeriod(
            start = java.time.LocalDate.of(2026, 9, 25),
            endInclusive = java.time.LocalDate.of(2026, 10, 24),
        )
        assertEquals(expectedPeriod, period)
        assertEquals(listOf(txStart, txInside, txEnd), filtered)
    }

    private fun transaction(
        amountCents: Long,
        categoryIsIncome: Boolean? = null,
        type: Type = if (amountCents >= 0) Type.INCOME else Type.EXPENSE,
        account: String = "Checking",
        transferAccount: String? = null,
        date: String = "20260913",
    ) = Transaction(
        id = java.util.UUID.randomUUID().toString(), date = date, payee = "", category = "",
        account = account, amount = 0, cleared = true, amountCents = amountCents,
        type = type, categoryIsIncome = categoryIsIncome, transferAccount = transferAccount,
    )
}
