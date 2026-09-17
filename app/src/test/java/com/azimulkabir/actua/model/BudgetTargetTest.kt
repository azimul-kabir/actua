package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetTargetTest {
    @Test fun fixedMonthlyUsesFixedAmount() {
        val category = category(carryover = 12_000)
        assertEquals(
            20_000,
            BudgetTarget(BudgetTarget.Type.FIXED, 20_000, startingDate = "2026-01-01")
                .suggestedBudget(category, "2026-09"),
        )
    }

    @Test fun fixedEveryOtherMonthOnlySkipsOffCadenceMonths() {
        assertEquals(
            20_000,
            BudgetTarget(BudgetTarget.Type.FIXED, 20_000, startingDate = "2026-09-01", everyCount = 2)
                .suggestedBudget(category(), "2026-09"),
        )
        assertEquals(
            0,
            BudgetTarget(BudgetTarget.Type.FIXED, 20_000, startingDate = "2026-09-01", everyCount = 2)
                .suggestedBudget(category(), "2026-10"),
        )
    }

    @Test fun refillContributesNothingOnItsOwn() {
        // Refill has no amount of its own - it tops up to the sibling Balance cap, computed by
        // BudgetTemplatePlanner which has access to the full category document.
        val category = category(carryover = 7_500)
        assertEquals(0, BudgetTarget(BudgetTarget.Type.REFILL).suggestedBudget(category, "2026-09"))
    }

    @Test fun byDateSpreadsRemainingAmountAcrossInclusiveMonths() {
        val category = category(carryover = 10_000)
        assertEquals(30_000, BudgetTarget(BudgetTarget.Type.BY_DATE, 100_000, targetMonth = "2026-11")
            .suggestedBudget(category, "2026-09"))
    }

    @Test fun fixedWeeklyTargetCountsOccurrencesInSelectedMonth() {
        val category = category()
        assertEquals(
            25_000,
            BudgetTarget(BudgetTarget.Type.FIXED, 5_000, startingDate = "2026-09-01", period = BudgetTarget.Period.WEEK)
                .suggestedBudget(category, "2026-09"),
        )
    }

    @Test fun historicalAverageUsesRequestedRecentHistory() {
        val category = category().copy(history = listOf(
            BudgetHistory("2026-08", 0, -10_000),
            BudgetHistory("2026-07", 0, -20_000),
            BudgetHistory("2026-06", 0, -30_000),
        ))
        assertEquals(
            15_000,
            BudgetTarget(BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.AVERAGE, historicalMonths = 2)
                .suggestedBudget(category, "2026-09"),
        )
    }

    @Test fun historicalAveragePercentAdjustmentScalesTheAverage() {
        val category = category().copy(history = listOf(
            BudgetHistory("2026-08", 0, -10_000),
            BudgetHistory("2026-07", 0, -20_000),
        ))
        assertEquals(
            16_500,
            BudgetTarget(
                BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.AVERAGE, historicalMonths = 2,
                adjustmentType = BudgetTarget.AdjustmentType.PERCENT, adjustmentPercent = 10.0,
            ).suggestedBudget(category, "2026-09"),
        )
    }

    @Test fun historicalAverageFixedAdjustmentAddsToTheAverageAndClampsAtZero() {
        val category = category().copy(history = listOf(BudgetHistory("2026-08", 0, -10_000)))
        assertEquals(
            12_000,
            BudgetTarget(
                BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.AVERAGE, historicalMonths = 1,
                adjustmentType = BudgetTarget.AdjustmentType.FIXED, adjustmentAmountCents = 2_000,
            ).suggestedBudget(category, "2026-09"),
        )
        assertEquals(
            0,
            BudgetTarget(
                BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.AVERAGE, historicalMonths = 1,
                adjustmentType = BudgetTarget.AdjustmentType.FIXED, adjustmentAmountCents = -20_000,
            ).suggestedBudget(category, "2026-09"),
        )
    }

    @Test fun historicalCopyUsesAssignedBudgetFromTheRequestedPriorMonth() {
        val category = category().copy(history = listOf(
            BudgetHistory("2026-08", 42_500, -10_000),
            BudgetHistory("2026-07", 31_000, -20_000),
        ))

        assertEquals(
            42_500,
            BudgetTarget(BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.COPY, historicalMonths = 1)
                .suggestedBudget(category, "2026-09"),
        )
        assertEquals(
            31_000,
            BudgetTarget(BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.COPY, historicalMonths = 2)
                .suggestedBudget(category, "2026-09"),
        )
    }

    @Test fun historicalCopyReturnsZeroWhenPriorMonthHistoryIsMissing() {
        assertEquals(
            0,
            BudgetTarget(BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.COPY, historicalMonths = 1)
                .suggestedBudget(category(), "2026-09"),
        )
    }

    private fun category(carryover: Long = 0) = BudgetCategory(
        name = "Groceries", assigned = 0, spent = 0, actualAvailable = carryover.toInt(),
        actualAssignedCents = 0, availableCents = carryover,
    )
}
