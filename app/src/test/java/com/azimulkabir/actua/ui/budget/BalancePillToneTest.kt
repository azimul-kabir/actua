package com.azimulkabir.actua.ui.budget

import com.azimulkabir.actua.model.BudgetProgressState
import org.junit.Assert.assertEquals
import org.junit.Test

class BalancePillToneTest {
    @Test
    fun `only a negative balance is an alert`() {
        assertEquals(BalancePillTone.ALERT, balancePillTone(-3_000L, BudgetProgressState.OVERSPENT))
        BudgetProgressState.entries.forEach { status ->
            assertEquals(BalancePillTone.NEUTRAL, balancePillTone(18_475L, status))
        }
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
        assertEquals(BalancePillTone.NEUTRAL, balancePillTone(18_475L, BudgetProgressState.FUNDED, false))
    }
}
