package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeSpendingTest {
    private val today = LocalDate.of(2026, 11, 11)

    private val accounts = listOf(account("checking", offBudget = false), account("savings", offBudget = false), account("mortgage", offBudget = true))
    private val groups = listOf(group("usual", income = false, "food"), group("income", income = true, "salary"))

    private fun tx(
        id: String, date: Int, amount: Long, account: String = "checking", category: String? = "food",
        transfer: String? = null, tombstone: Boolean = false,
    ) = ActualTransaction(id, account, date, amount, null, null, category, null, null, false, false, null,
        false, transfer, tombstone, null, null, null, null)

    private fun series(rows: List<ActualTransaction>, months: Int = 6) =
        IncomeSpendingBuilder.build(rows, accounts, groups, today, months)

    @Test
    fun coversTheLastSixMonthsOldestFirstEndingWithTheFlaggedCurrentOne() {
        val series = series(emptyList())
        assertEquals(
            listOf("2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11").map(YearMonth::parse),
            series.months.map { it.month },
        )
        assertEquals(listOf(false, false, false, false, false, true), series.months.map { it.current })
        assertEquals(series.months.last(), series.current)
    }

    @Test
    fun crossesAYearBoundary() {
        val january = IncomeSpendingBuilder.build(emptyList(), accounts, groups, LocalDate.of(2027, 1, 5))
        assertEquals(YearMonth.of(2026, 8), january.months.first().month)
        assertEquals(YearMonth.of(2027, 1), january.months.last().month)
    }

    @Test
    fun sumsEachMonthsIncomeAndSpendingAndWhatIsLeft() {
        val series = series(listOf(
            tx("pay1", 20261001, 500_000, category = "salary"),
            tx("food1", 20261015, -120_000),
            tx("food2", 20261030, -30_000),
            tx("pay2", 20261105, 520_000, category = "salary"),
            tx("food3", 20261110, -2_599),
        ))
        val october = series.months[4]
        assertEquals(500_000L, october.incomeCents)
        assertEquals(150_000L, october.spentCents)
        assertEquals(350_000L, october.leftCents)
        val november = series.months[5]
        assertEquals(520_000L, november.incomeCents)
        assertEquals(2_599L, november.spentCents)
        assertEquals(517_401L, november.leftCents)
        assertTrue(november.current)
        assertFalse(october.current)
        assertEquals(0L, series.months[0].incomeCents)
    }

    @Test
    fun theSavingsRateIsWhatIsLeftAsAShareOfIncome() {
        val month = IncomeSpendingMonth(YearMonth.of(2026, 11), incomeCents = 400_000, spentCents = 300_000, current = true)
        assertEquals(25, month.savingsRatePercent)
        // Rounds to the nearest percent, and is negative when more was spent than came in.
        assertEquals(33, IncomeSpendingMonth(YearMonth.of(2026, 11), 300, 200, false).savingsRatePercent)
        assertEquals(-50, IncomeSpendingMonth(YearMonth.of(2026, 11), 200, 300, false).savingsRatePercent)
    }

    @Test
    fun noSavingsRateWithoutIncome() {
        assertNull(IncomeSpendingMonth(YearMonth.of(2026, 11), 0, 5_000, true).savingsRatePercent)
        assertEquals(-5_000L, IncomeSpendingMonth(YearMonth.of(2026, 11), 0, 5_000, true).leftCents)
    }

    @Test
    fun countsAMonthTheWayIncomeVsExpensesDoes() {
        val rows = listOf(
            tx("pay", 20261102, 500_000, category = "salary"),
            tx("spent", 20261103, -1_000),
            tx("refund", 20261104, 250),
            tx("incomeRefund", 20261105, -700, category = "salary"),
            tx("offBudget", 20261106, -80_000, account = "mortgage"),
            tx("deleted", 20261107, -9_000, tombstone = true),
            tx("transfer", 20261108, -40_000, account = "checking", category = null, transfer = "savings"),
            tx("transferIn", 20261108, 40_000, account = "savings", category = null, transfer = "checking"),
            tx("older", 20250101, -4_000),
        )
        val november = series(rows).months.last()
        // Income categories against everything else; an income-category outflow reduces income and a refund
        // reduces spending; off-budget, deleted and same-status transfer rows stay out.
        assertEquals(499_300L, november.incomeCents)
        assertEquals(750L, november.spentCents)
        val shared = SavedReportEngine.monthlyCashFlow(rows, YearMonth.of(2026, 11), ReportAggregator(accounts, groups))
        assertEquals(november.incomeCents to november.spentCents, shared)
    }

    @Test
    fun aMonthIsBoundedByItsFirstAndLastDay() {
        val rows = listOf(
            tx("lastOfOctober", 20261031, -100),
            tx("firstOfNovember", 20261101, -200),
            tx("lastOfNovember", 20261130, -400),
            tx("firstOfDecember", 20261201, -800),
        )
        val series = series(rows)
        assertEquals(100L, series.months[4].spentCents)
        assertEquals(600L, series.months[5].spentCents)
    }

    @Test
    fun isEmptyOnlyWhenThereWasNeitherIncomeNorSpendingInAnyMonth() {
        assertTrue(series(emptyList()).isEmpty)
        assertFalse(series(listOf(tx("pay", 20260601, 1, category = "salary"))).isEmpty)
        assertFalse(series(listOf(tx("spent", 20261101, -1))).isEmpty)
    }

    @Test
    fun aShorterSeriesForTheSmallestSize() {
        val series = series(listOf(tx("1", 20261015, -300), tx("2", 20261101, -200)), months = 1)
        assertEquals(listOf(YearMonth.of(2026, 11)), series.months.map { it.month })
        assertEquals(200L, series.months.single().spentCents)
    }

    private fun account(id: String, offBudget: Boolean) = ActualAccount(id, id, ActualAccountType.CHECKING, offBudget, false, 0.0, 0L)

    private fun group(id: String, income: Boolean, vararg categories: String) = ActualCategoryGroup(
        id, id, income, false, 0.0, categories.map { ActualCategory(it, it, id, income, false, 0.0) },
    )
}
