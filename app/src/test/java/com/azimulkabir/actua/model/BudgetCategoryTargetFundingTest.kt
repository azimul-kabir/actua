package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetCategoryTargetFundingTest {
    private fun category(balanceCents: Long, target: BudgetTarget?, goalCents: Long? = null) = BudgetCategory(
        name = "Holiday", assigned = 0, spent = 0,
        actualAssignedCents = 0, spentCents = 0, availableCents = balanceCents,
        target = target, goalCents = goalCents,
    )

    private val goal = BudgetTarget(BudgetTarget.Type.GOAL, amountCents = 20_000)

    @Test fun `no target shows no icon state`() {
        assertNull(category(5_000, target = null).targetFunding())
    }

    @Test fun `a goal moves from not started through partial to reached and overfunded`() {
        assertEquals(TargetFunding.NOT_STARTED, category(0, goal).targetFunding())
        assertEquals(TargetFunding.PARTIAL, category(5_000, goal).targetFunding())
        assertEquals(TargetFunding.PARTIAL, category(19_999, goal).targetFunding())
        assertEquals(TargetFunding.REACHED, category(20_000, goal).targetFunding())
        assertEquals(TargetFunding.OVERFUNDED, category(20_001, goal).targetFunding())
    }

    @Test fun `an overspent goal category keeps the plain icon`() {
        assertEquals(TargetFunding.PLAIN, category(-100, goal).targetFunding())
    }

    @Test fun `save by date uses the same balance rules as its progress bar`() {
        val byDate = BudgetTarget(BudgetTarget.Type.BY_DATE, amountCents = 40_000, targetMonth = "2026-12")
        assertEquals(TargetFunding.PARTIAL, category(10_000, byDate).targetFunding())
        assertEquals(TargetFunding.REACHED, category(40_000, byDate).targetFunding())
    }

    @Test fun `monthly contribution targets keep the plain icon`() {
        val fixed = BudgetTarget(BudgetTarget.Type.FIXED, amountCents = 5_000)
        assertEquals(TargetFunding.PLAIN, category(0, fixed).targetFunding())
        assertEquals(TargetFunding.PLAIN, category(5_000, fixed).targetFunding())
    }

    @Test fun `the icon state agrees with the progress bar state`() {
        listOf(0L, 5_000L, 20_000L, 25_000L).forEach { balance ->
            val c = category(balance, goal)
            val expected = when (c.progressBarState()) {
                BudgetProgressState.GOAL_REACHED -> TargetFunding.REACHED
                BudgetProgressState.GOAL_OVERFUNDED -> TargetFunding.OVERFUNDED
                else -> if (c.progressFraction() > 0f) TargetFunding.PARTIAL else TargetFunding.NOT_STARTED
            }
            assertEquals(expected, c.targetFunding())
        }
    }
}
