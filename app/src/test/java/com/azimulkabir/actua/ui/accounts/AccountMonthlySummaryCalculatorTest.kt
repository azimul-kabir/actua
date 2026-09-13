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

    private fun transaction(
        amountCents: Long,
        categoryIsIncome: Boolean? = null,
        type: Type = if (amountCents >= 0) Type.INCOME else Type.EXPENSE,
    ) = Transaction(
        id = amountCents.toString(), date = "20260913", payee = "", category = "",
        account = "Checking", amount = 0, cleared = true, amountCents = amountCents,
        type = type, categoryIsIncome = categoryIsIncome,
    )
}
