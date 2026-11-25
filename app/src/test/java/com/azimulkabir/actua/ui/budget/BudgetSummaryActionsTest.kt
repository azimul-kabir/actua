package com.azimulkabir.actua.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which To Budget actions are offered, as Actual's ToBudgetMenu decides them (#1774). */
class BudgetSummaryActionsTest {
    @Test fun positiveToBudgetOffersMoveAndHold() {
        val actions = budgetSummaryActions(toBudgetCents = 5_000, manualBufferedCents = 0, autoBufferedCents = 0)
        assertEquals(BudgetSummaryActions(move = true, cover = false, hold = true, resetManualHold = false, disableAutoHold = false), actions)
    }

    @Test fun negativeToBudgetOffersOnlyCover() {
        val actions = budgetSummaryActions(toBudgetCents = -5_000, manualBufferedCents = 0, autoBufferedCents = 0)
        assertEquals(BudgetSummaryActions(move = false, cover = true, hold = false, resetManualHold = false, disableAutoHold = false), actions)
    }

    @Test fun zeroToBudgetOffersNothing() {
        assertFalse(budgetSummaryActions(0, 0, 0).any)
    }

    @Test fun anAutomaticHoldHidesHoldAndOffersToDisableIt() {
        val actions = budgetSummaryActions(toBudgetCents = 5_000, manualBufferedCents = 0, autoBufferedCents = 3_000)
        assertFalse(actions.hold)
        assertTrue(actions.disableAutoHold)
        assertFalse(actions.resetManualHold)
    }

    @Test fun aManualHoldOffersReset() {
        val actions = budgetSummaryActions(toBudgetCents = 0, manualBufferedCents = 3_000, autoBufferedCents = 0)
        assertTrue(actions.resetManualHold)
        assertFalse(actions.disableAutoHold)
    }
}
