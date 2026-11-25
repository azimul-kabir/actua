package com.azimulkabir.actua.ui.budget

import com.azimulkabir.actua.model.BudgetProgressState
import org.junit.Assert.assertEquals
import org.junit.Test

class BalancePillToneTest {
    @Test
    fun `positive and negative balances are tinted by sign`() {
        assertEquals(BalancePillTone.ALERT, balancePillTone(-3_000L, BudgetProgressState.OVERSPENT))
        BudgetProgressState.entries.forEach { status ->
            assertEquals(BalancePillTone.ALERT, balancePillTone(18_475L, status))
        }
    }

    @Test
    fun `without color balances the color follows the sign`() {
        BudgetProgressState.entries.forEach { status ->
            assertEquals(BudgetProgressState.FUNDED, balancePillStatus(18_475L, status))
            assertEquals(BudgetProgressState.OVERSPENT, balancePillStatus(-3_000L, status))
        }
        assertEquals(BudgetProgressState.SPENT, balancePillStatus(0L, BudgetProgressState.SPENT))
    }

    @Test
    fun `with color balances the color follows the category status`() {
        assertEquals(BudgetProgressState.GOAL_IN_PROGRESS, balancePillStatus(18_475L, BudgetProgressState.GOAL_IN_PROGRESS, true))
        assertEquals(BudgetProgressState.OVERSPENT, balancePillStatus(-3_000L, BudgetProgressState.GOAL_IN_PROGRESS, true))
    }

    @Test
    fun `fully spent zero keeps its pill but unassigned zero is empty`() {
        assertEquals(BalancePillTone.NEUTRAL, balancePillTone(0L, BudgetProgressState.SPENT))
        assertEquals(BalancePillTone.EMPTY, balancePillTone(0L, BudgetProgressState.UNASSIGNED))
    }

    @Test fun colorByStatusTintsEveryBalanceExceptUnassignedZero() {
        assertEquals(BalancePillTone.ALERT, balancePillTone(18_475L, BudgetProgressState.GOAL_OVERFUNDED, true))
        assertEquals(BalancePillTone.ALERT, balancePillTone(0L, BudgetProgressState.SPENT, true))
        assertEquals(BalancePillTone.EMPTY, balancePillTone(0L, BudgetProgressState.UNASSIGNED, true))
        assertEquals(BalancePillTone.NEUTRAL, balancePillTone(0L, BudgetProgressState.FUNDED, false))
    }
}
