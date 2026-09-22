package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetCategoryProgressTest {
    @Test fun `no goal measures spending capacity`() {
        assertEquals(0.5f, category(assignedCents = 10_000, spentCents = 5_000).progressFraction())
    }

    @Test fun `no goal with nothing assigned is zero`() {
        assertEquals(0f, category(assignedCents = 0, spentCents = 0).progressFraction())
    }

    @Test fun `ordinary spending includes carryover and overspending fills the bar`() {
        // Actuali's spending bar uses all available funds, including carryover.
        val fullySpentWithCarryover = category(assignedCents = 3_524, spentCents = 3_695, balanceCents = 1_305)
        assertEquals(0.739f, fullySpentWithCarryover.progressFraction(), 0.0001f)

        val overspentNegativeBalance = category(assignedCents = 5_000, spentCents = 6_000, balanceCents = -1_000)
        assertEquals(1f, overspentNegativeBalance.progressFraction())
    }

    @Test fun `goal underfunded tracks balance toward goal`() {
        val category = category(assignedCents = 5_000, spentCents = 0, balanceCents = 5_000, goalCents = 20_000)
        assertEquals(0.25f, category.progressFraction())
    }

    @Test fun `goal fully funded before anything is spent reads full`() {
        val category = category(assignedCents = 20_000, spentCents = 0, balanceCents = 20_000, goalCents = 20_000)
        assertEquals(1f, category.progressFraction())
    }

    @Test fun `goal partially spent back down reduces progress`() {
        val category = category(assignedCents = 20_000, spentCents = 15_000, balanceCents = 5_000, goalCents = 20_000)
        assertEquals(0.25f, category.progressFraction())
    }

    @Test fun `goal overfunded clamps to full`() {
        val category = category(assignedCents = 25_000, spentCents = 0, balanceCents = 25_000, goalCents = 20_000)
        assertEquals(1f, category.progressFraction())
    }

    @Test fun `zero or negative goal falls back to spending capacity`() {
        val zeroGoal = category(assignedCents = 10_000, spentCents = 5_000, balanceCents = 5_000, goalCents = 0)
        assertEquals(0.5f, zeroGoal.progressFraction())

        val negativeGoal = category(assignedCents = 10_000, spentCents = 5_000, balanceCents = 5_000, goalCents = -100)
        assertEquals(0.5f, negativeGoal.progressFraction())
    }

    @Test fun `by-date long-term target drives progress before server goal is synced`() {
        val target = BudgetTarget(BudgetTarget.Type.BY_DATE, amountCents = 40_000, targetMonth = "2026-12")
        val category = category(
            assignedCents = 5_000, spentCents = 0, balanceCents = 10_000, goalCents = null,
        ).copy(target = target)
        assertEquals(0.25f, category.progressFraction())
    }

    @Test fun `goal-only target drives progress before server goal is synced`() {
        val target = BudgetTarget(BudgetTarget.Type.GOAL, amountCents = 50_000)
        val category = category(
            assignedCents = 0, spentCents = 0, balanceCents = 25_000, goalCents = null,
        ).copy(target = target)
        assertEquals(0.5f, category.progressFraction())
    }

    @Test fun `locally computed by-date target wins over a stale server goal`() {
        // goalCents = 20,000 mimics a stale "monthly installment" value left over from before
        // an automation was last applied; the by-date target's own amount (40,000) is the
        // true end goal and must win, matching issue #305.
        val target = BudgetTarget(BudgetTarget.Type.BY_DATE, amountCents = 40_000, targetMonth = "2026-12")
        val category = category(
            assignedCents = 5_000, spentCents = 0, balanceCents = 10_000, goalCents = 20_000,
        ).copy(target = target)
        assertEquals(0.25f, category.progressFraction())
    }

    @Test fun `schedule-linked target without resolvable schedule data falls back to balance-of-assigned progress`() {
        val target = BudgetTarget(BudgetTarget.Type.SCHEDULE, scheduleId = "bill-1")
        val category = category(
            assignedCents = 10_000, spentCents = 5_000, balanceCents = 5_000, goalCents = null,
        ).copy(target = target)
        assertEquals(0.5f, category.progressFraction())
    }

    @Test fun `schedule-linked target resolves the full occurrence amount over a stale server goal`() {
        // The reported scenario: an annual "cover scheduled transaction" goal of 630.00, with
        // 262.50 saved and 52.50/month assigned. A stale server goal of 52.50 (this month's
        // installment) must not win once the schedule's true amount can be resolved.
        val target = BudgetTarget(BudgetTarget.Type.SCHEDULE, scheduleId = "bill-1")
        val category = category(
            assignedCents = 5_250, spentCents = 0, balanceCents = 26_250, goalCents = 5_250,
        ).copy(target = target)
        val schedules = listOf(
            BudgetScheduleFunding(
                id = "bill-1", name = "Auto insurance", amountCents = 63_000,
                occurrencesInMonth = 0, monthsUntilNextOccurrence = 7,
            ),
        )
        assertEquals(0.4166667f, category.progressFraction(schedules), 0.0001f)
    }

    @Test fun `ordinary monthly goal does not replace spending capacity`() {
        val groceries = category(60_000, 30_200, 139_000, 60_000).copy(
            longGoal = false, target = BudgetTarget(BudgetTarget.Type.FIXED, 60_000),
        )
        assertEquals(30_200f / 169_200f, groceries.progressFraction(), 0.0001f)
        assertEquals(false, groceries.usesGoalProgress)
        assertEquals(BudgetProgressState.SPENDING, groceries.progressState)
        assertEquals(true, groceries.showsProgressBar)
    }

    @Test fun `demo utilities measures spent share of total available funds`() {
        assertEquals(90f / 195f, category(12_000, 9_000, 10_500).progressFraction(), 0.0001f)
    }

    @Test fun `carryover spending works without monthly allocation`() {
        val category = category(0, 5_000, 5_000)
        assertEquals(0.5f, category.progressFraction())
        assertEquals(true, category.showsProgressBar)
    }

    @Test fun `ordinary category states and visibility match spending`() {
        val funded = category(10_000, 0)
        assertEquals(0f, funded.progressFraction())
        assertEquals(BudgetProgressState.FUNDED, funded.progressState)
        assertEquals(true, funded.showsProgressBar)
        val spent = category(10_000, 10_000)
        assertEquals(1f, spent.progressFraction())
        assertEquals(BudgetProgressState.SPENT, spent.progressState)
        val overspent = category(0, 3_000)
        assertEquals(1f, overspent.progressFraction())
        assertEquals(BudgetProgressState.OVERSPENT, overspent.progressState)
        val empty = category(0, 0)
        assertEquals(false, empty.showsProgressBar)
        assertEquals(BudgetProgressState.UNASSIGNED, empty.progressState)
        assertEquals(false, category(0, 0, 5_000).showsProgressBar)
    }

    @Test fun `net inflow uses absolute activity matching Actuali`() {
        assertEquals(0.2f, category(0, -1_000, 4_000).progressFraction())
    }

    @Test fun `large amounts do not overflow presentation capacity`() {
        assertEquals(0.5f, category(0, Long.MIN_VALUE, Long.MAX_VALUE).progressFraction())
    }

    @Test fun `long-term target is visible without allocation or spending`() {
        val category = category(0, 0, 120_000).copy(
            target = BudgetTarget(BudgetTarget.Type.BY_DATE, 180_000, "2027-03"),
        )
        assertEquals(true, category.usesGoalProgress)
        assertEquals(true, category.showsProgressBar)
        assertEquals(2f / 3f, category.progressFraction(), 0.0001f)
    }

    @Test fun `ordinary category bar state matches its status`() {
        val category = category(assignedCents = 10_000, spentCents = 5_000)
        assertEquals(BudgetProgressState.SPENDING, category.progressBarState())
    }

    @Test fun `by-date target funded for this month but not in full is still in progress`() {
        // Issue #491: this month's installment is funded, but the bar only reads as reached
        // once the whole target balance is.
        val target = BudgetTarget(BudgetTarget.Type.BY_DATE, amountCents = 40_000, targetMonth = "2026-12")
        val category = category(assignedCents = 5_000, spentCents = 0, balanceCents = 10_000)
            .copy(target = target)
        assertEquals(BudgetProgressState.GOAL_IN_PROGRESS, category.progressBarState())
    }

    @Test fun `goal fully funded reads as reached`() {
        val category = category(assignedCents = 20_000, spentCents = 0, balanceCents = 20_000, goalCents = 20_000)
        assertEquals(BudgetProgressState.GOAL_REACHED, category.progressBarState())
    }

    @Test fun `goal overfunded reads as reached`() {
        val category = category(assignedCents = 25_000, spentCents = 0, balanceCents = 25_000, goalCents = 20_000)
        assertEquals(BudgetProgressState.GOAL_REACHED, category.progressBarState())
    }

    @Test fun `goal with negative balance reads as overspent`() {
        val category = category(assignedCents = 5_000, spentCents = 6_000, balanceCents = -1_000, goalCents = 20_000)
        assertEquals(BudgetProgressState.OVERSPENT, category.progressBarState())
    }

    @Test fun `unresolved schedule target with nothing assigned is in progress`() {
        val target = BudgetTarget(BudgetTarget.Type.SCHEDULE, scheduleId = "bill-1")
        val category = category(assignedCents = 0, spentCents = 0, balanceCents = 0).copy(target = target)
        assertEquals(BudgetProgressState.GOAL_IN_PROGRESS, category.progressBarState())
    }

    @Test fun `goal bar states never drive the category status dot`() {
        val category = category(assignedCents = 5_000, spentCents = 0, balanceCents = 5_000, goalCents = 20_000)
        assertEquals(BudgetProgressState.FUNDED, category.progressState)
    }

    private fun category(
        assignedCents: Long,
        spentCents: Long,
        balanceCents: Long = assignedCents - spentCents,
        goalCents: Long? = null,
    ) = BudgetCategory(
        name = "Groceries", assigned = 0, spent = 0,
        actualAssignedCents = assignedCents, spentCents = spentCents,
        availableCents = balanceCents, goalCents = goalCents, longGoal = goalCents != null,
    )
}
