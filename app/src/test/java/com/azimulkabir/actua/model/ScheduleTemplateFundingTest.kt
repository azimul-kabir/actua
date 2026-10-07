package com.azimulkabir.actua.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTemplateFundingTest {
    private val quarterly = BudgetScheduleFunding(
        id = "insurance", name = "Insurance", amountCents = 30_000,
        occurrencesInMonth = 0, monthsUntilNextOccurrence = 2,
        frequency = "monthly", interval = 3, nextDate = LocalDate.of(2026, 10, 20),
    )

    @Test fun quarterlyScheduleIsASinkingFund() {
        // Regression for #857: Actual spreads a schedule due in 3 months over the months until it.
        assertEquals(10_000L, request(quarterly, carryover = 0))
    }

    @Test fun sinkingFundDeductsLastMonthsBalance() {
        assertEquals(3_333L, request(quarterly, carryover = 20_000))
    }

    @Test fun coveredBalanceOnlyAddsTheMonthlyBaseContribution() {
        assertEquals(10_000L, request(quarterly, carryover = 40_000))
    }

    @Test fun frequentWeeklyScheduleIsPaidInItsMonth() {
        val weekly = BudgetScheduleFunding(
            id = "lessons", name = "Lessons", amountCents = 2_500,
            occurrencesInMonth = 3, monthsUntilNextOccurrence = 0,
            frequency = "weekly", interval = 2, nextDate = LocalDate.of(2026, 8, 5),
            occurrencesInDueMonth = 3,
        )
        assertEquals(7_500L, request(weekly, carryover = 0))
    }

    @Test fun plannerUsesTheSinkingFundRequest() {
        val category = BudgetCategory(
            id = "insurance", name = "Insurance", assigned = 0, spent = 0, actualAvailable = 0,
            actualAssignedCents = 0, availableCents = 0,
            automations = listOf(BudgetTarget(BudgetTarget.Type.SCHEDULE, priority = 1, scheduleId = "insurance")),
        )
        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Bills", listOf(category))),
            "2026-08",
            schedules = listOf(quarterly),
        )
        assertEquals(listOf(10_000L), preview.changes.map { it.proposedCents })
    }

    private fun request(funding: BudgetScheduleFunding, carryover: Long): Long {
        val category = BudgetCategory(
            name = "Bills", assigned = 0, spent = 0, actualAvailable = carryover.toInt(),
            actualAssignedCents = 0, availableCents = carryover,
        )
        return ScheduleTemplateFunding.request(
            targets = listOf(BudgetTarget(BudgetTarget.Type.SCHEDULE, scheduleId = funding.id)),
            schedules = listOf(funding),
            category = category,
            month = "2026-08",
            balance = carryover,
            lastMonthBalance = carryover,
            previousMonthGoal = 0L,
        )
    }
}
