package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetTargetTest {
    @Test fun monthlySavingsUsesFixedAmount() {
        val category = category(carryover = 12_000)
        assertEquals(20_000, BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 20_000)
            .suggestedBudget(category, "2026-09"))
    }

    @Test fun monthlySpendingAndRefillOnlyAddWhatBalanceNeeds() {
        val category = category(carryover = 7_500)
        assertEquals(12_500, BudgetTarget(BudgetTarget.Type.MONTHLY_SPENDING, 20_000)
            .suggestedBudget(category, "2026-09"))
        assertEquals(12_500, BudgetTarget(BudgetTarget.Type.REFILL, 20_000)
            .suggestedBudget(category, "2026-09"))
    }

    @Test fun byDateSpreadsRemainingAmountAcrossInclusiveMonths() {
        val category = category(carryover = 10_000)
        assertEquals(30_000, BudgetTarget(BudgetTarget.Type.BY_DATE, 100_000, targetMonth = "2026-11")
            .suggestedBudget(category, "2026-09"))
    }

    @Test fun weeklyTargetCountsOccurrencesInSelectedMonth() {
        val category = category()
        assertEquals(25_000, BudgetTarget(BudgetTarget.Type.WEEKLY_SPENDING, 5_000,
            startingDate = "2026-09-01").suggestedBudget(category, "2026-09"))
    }

    @Test fun averageUsesRequestedRecentHistory() {
        val category = category().copy(history = listOf(
            BudgetHistory("2026-08", 0, -10_000),
            BudgetHistory("2026-07", 0, -20_000),
            BudgetHistory("2026-06", 0, -30_000),
        ))
        assertEquals(15_000, BudgetTarget(BudgetTarget.Type.AVERAGE, averageMonths = 2)
            .suggestedBudget(category, "2026-09"))
    }

    private fun category(carryover: Long = 0) = BudgetCategory(
        name = "Groceries", assigned = 0, spent = 0, actualAvailable = carryover.toInt(),
        actualAssignedCents = 0, availableCents = carryover,
    )
}
