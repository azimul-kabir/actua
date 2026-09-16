package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetCategoryProgressTest {
    @Test fun `no goal uses spent of assigned`() {
        assertEquals(0.5f, category(assignedCents = 10_000, spentCents = 5_000).progressFraction)
    }

    @Test fun `no goal with nothing assigned is zero`() {
        assertEquals(0f, category(assignedCents = 0, spentCents = 0).progressFraction)
    }

    @Test fun `goal underfunded tracks balance toward goal`() {
        val category = category(assignedCents = 5_000, spentCents = 0, balanceCents = 5_000, goalCents = 20_000)
        assertEquals(0.25f, category.progressFraction)
    }

    @Test fun `goal fully funded before anything is spent reads full`() {
        val category = category(assignedCents = 20_000, spentCents = 0, balanceCents = 20_000, goalCents = 20_000)
        assertEquals(1f, category.progressFraction)
    }

    @Test fun `goal partially spent back down reduces progress`() {
        val category = category(assignedCents = 20_000, spentCents = 15_000, balanceCents = 5_000, goalCents = 20_000)
        assertEquals(0.25f, category.progressFraction)
    }

    @Test fun `goal overfunded clamps to full`() {
        val category = category(assignedCents = 25_000, spentCents = 0, balanceCents = 25_000, goalCents = 20_000)
        assertEquals(1f, category.progressFraction)
    }

    @Test fun `zero or negative goal falls back to spent of assigned`() {
        val zeroGoal = category(assignedCents = 10_000, spentCents = 5_000, balanceCents = 5_000, goalCents = 0)
        assertEquals(0.5f, zeroGoal.progressFraction)

        val negativeGoal = category(assignedCents = 10_000, spentCents = 5_000, balanceCents = 5_000, goalCents = -100)
        assertEquals(0.5f, negativeGoal.progressFraction)
    }

    @Test fun `by-date long-term target drives progress before server goal is synced`() {
        val target = BudgetTarget(BudgetTarget.Type.BY_DATE, amountCents = 40_000, targetMonth = "2026-12")
        val category = category(
            assignedCents = 5_000, spentCents = 0, balanceCents = 10_000, goalCents = null,
        ).copy(target = target)
        assertEquals(0.25f, category.progressFraction)
    }

    @Test fun `goal-only target drives progress before server goal is synced`() {
        val target = BudgetTarget(BudgetTarget.Type.GOAL, amountCents = 50_000)
        val category = category(
            assignedCents = 0, spentCents = 0, balanceCents = 25_000, goalCents = null,
        ).copy(target = target)
        assertEquals(0.5f, category.progressFraction)
    }

    @Test fun `server goal wins over a locally computed long-term target`() {
        val target = BudgetTarget(BudgetTarget.Type.BY_DATE, amountCents = 40_000, targetMonth = "2026-12")
        val category = category(
            assignedCents = 5_000, spentCents = 0, balanceCents = 10_000, goalCents = 20_000,
        ).copy(target = target)
        assertEquals(0.5f, category.progressFraction)
    }

    @Test fun `schedule-linked target without a server goal falls back to spend-down progress`() {
        val target = BudgetTarget(BudgetTarget.Type.SCHEDULE, scheduleId = "bill-1")
        val category = category(
            assignedCents = 10_000, spentCents = 5_000, balanceCents = 5_000, goalCents = null,
        ).copy(target = target)
        assertEquals(0.5f, category.progressFraction)
    }

    private fun category(
        assignedCents: Long,
        spentCents: Long,
        balanceCents: Long = assignedCents - spentCents,
        goalCents: Long? = null,
    ) = BudgetCategory(
        name = "Groceries", assigned = 0, spent = 0,
        actualAssignedCents = assignedCents, spentCents = spentCents,
        availableCents = balanceCents, goalCents = goalCents,
    )
}
