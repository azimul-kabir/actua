package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** Ported from loot-core `budget/actions.test.ts` "set budget average" and "copyUntilYearEnd". */
class BudgetAveragesTest {
    private val current = YearMonth.of(2024, 2)

    // Upstream fixture: cat1 spends 3.00, 6.00, 9.00, 30.00, 12.00 in Nov 2023 – Mar 2024.
    private val activity = mapOf(
        YearMonth.of(2023, 11) to -300L, YearMonth.of(2023, 12) to -600L, YearMonth.of(2024, 1) to -900L,
        YearMonth.of(2024, 2) to -3000L, YearMonth.of(2024, 3) to -1200L,
    )

    private fun budget(month: YearMonth, n: Int, firstActivity: YearMonth?, sums: Map<YearMonth, Long> = activity): Long {
        val months = BudgetAverages.months(month, n, firstActivity, current)
        return BudgetAverages.budgetAmount(BudgetAverages.average(months.map { sums[it] ?: 0L }), isIncome = false)
    }

    @Test
    fun futureMonthAveragesTheMonthsBeforeTheCurrentOne() {
        // April with current month February: starts at January, not March.
        assertEquals(
            listOf(YearMonth.of(2024, 1), YearMonth.of(2023, 12), YearMonth.of(2023, 11)),
            BudgetAverages.months(YearMonth.of(2024, 4), 3, YearMonth.of(2023, 11), current),
        )
        assertEquals(600L, budget(YearMonth.of(2024, 4), 3, YearMonth.of(2023, 11)))
    }

    @Test
    fun pastMonthStartsLastMonth() {
        assertEquals(YearMonth.of(2023, 12), BudgetAverages.startMonth(YearMonth.of(2024, 1), current))
        assertEquals(YearMonth.of(2024, 1), BudgetAverages.startMonth(YearMonth.of(2024, 2), current))
    }

    @Test
    fun stopsAtTheFirstActivityMonth() {
        // Only December and January count, so 15.00 / 2.
        assertEquals(
            listOf(YearMonth.of(2024, 1), YearMonth.of(2023, 12)),
            BudgetAverages.months(YearMonth.of(2024, 4), 12, YearMonth.of(2023, 12), current),
        )
        assertEquals(750L, budget(YearMonth.of(2024, 4), 12, YearMonth.of(2023, 12)))
        // No activity at all: every month counts.
        assertEquals(12, BudgetAverages.months(YearMonth.of(2024, 4), 12, null, current).size)
        // First activity after the window: no months, average 0.
        assertEquals(0L, budget(YearMonth.of(2024, 4), 3, YearMonth.of(2024, 3)))
    }

    @Test
    fun roundsLikeMathRound() {
        // An extra -1.00 in December: -19.00 / 3 rounds to -6.33.
        val sums = activity + (YearMonth.of(2023, 12) to -700L)
        assertEquals(633L, budget(YearMonth.of(2024, 4), 3, YearMonth.of(2023, 11), sums))
        // Halves round toward positive infinity, as JavaScript's Math.round.
        assertEquals(-1L, BudgetAverages.average(listOf(-1L, -2L)))
        assertEquals(2L, BudgetAverages.average(listOf(1L, 2L)))
        assertEquals(0L, BudgetAverages.average(emptyList()))
    }

    @Test
    fun incomeAveragesKeepTheirSign() {
        assertEquals(250_000L, BudgetAverages.budgetAmount(250_000L, isIncome = true))
        assertEquals(-250_000L, BudgetAverages.budgetAmount(250_000L, isIncome = false))
    }

    @Test
    fun copyUntilYearEndStopsInDecemberAndAtCreatedMonths() {
        assertEquals(
            listOf(YearMonth.of(2024, 12)),
            BudgetAverages.monthsUntilYearEnd(YearMonth.of(2024, 11), current),
        )
        assertEquals(emptyList<YearMonth>(), BudgetAverages.monthsUntilYearEnd(YearMonth.of(2024, 12), current))
        assertEquals(10, BudgetAverages.monthsUntilYearEnd(YearMonth.of(2024, 2), current).size)
        // Budgets only exist up to a year after the current month.
        assertEquals(
            listOf(YearMonth.of(2025, 2)),
            BudgetAverages.monthsUntilYearEnd(YearMonth.of(2025, 1), current),
        )
    }
}
