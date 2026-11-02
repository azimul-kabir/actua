package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Actual 26.11's envelope balance menu: "Fund goal from To Budget" moves `min(goal - budgeted, To Budget)` (#1418). */
class GoalFundAmountTest {
    private fun category(budgeted: Long, goal: Long?, longGoal: Boolean = false) = BudgetCategory(
        name = "Vacation", assigned = 0, spent = 0, actualAssignedCents = budgeted, goalCents = goal, longGoal = longGoal,
    )

    @Test fun `funds the whole shortfall when To Budget covers it`() {
        assertEquals(30_000L, category(budgeted = 20_000, goal = 50_000).goalFundAmountCents(toBudgetCents = 100_000))
        assertEquals(50_000L, category(budgeted = 0, goal = 50_000).goalFundAmountCents(toBudgetCents = 50_000))
    }

    @Test fun `is limited to what is left in To Budget`() {
        assertEquals(10_000L, category(budgeted = 20_000, goal = 50_000).goalFundAmountCents(toBudgetCents = 10_000))
        assertEquals(1L, category(budgeted = 0, goal = 50_000).goalFundAmountCents(toBudgetCents = 1))
    }

    @Test fun `offers nothing when there is nothing to fund or nothing to fund it with`() {
        assertNull(category(budgeted = 0, goal = 50_000).goalFundAmountCents(toBudgetCents = 0))
        assertNull(category(budgeted = 0, goal = 50_000).goalFundAmountCents(toBudgetCents = -5_000))
        assertNull(category(budgeted = 50_000, goal = 50_000).goalFundAmountCents(toBudgetCents = 100_000))
        assertNull(category(budgeted = 60_000, goal = 50_000).goalFundAmountCents(toBudgetCents = 100_000))
    }

    @Test fun `a category with no goal or a long-term goal is not offered`() {
        assertNull(category(budgeted = 0, goal = null).goalFundAmountCents(toBudgetCents = 100_000))
        assertNull(category(budgeted = 0, goal = 50_000, longGoal = true).goalFundAmountCents(toBudgetCents = 100_000))
    }
}
