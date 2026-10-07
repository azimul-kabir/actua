package com.azimulkabir.actua.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Actual's `transferAvailable` / `coverOverbudgeted` limits for the To Budget sheet (#907). */
class BudgetSummaryMoveAmountTest {
    @Test fun fundingFromToBudgetIsLimitedToToBudget() {
        assertEquals(4_000L, budgetSummaryMoveAmount(enteredCents = 4_000, toBudgetCents = 10_000, sourceBalanceCents = null))
        assertEquals(10_000L, budgetSummaryMoveAmount(enteredCents = 25_000, toBudgetCents = 10_000, sourceBalanceCents = null))
        assertNull(budgetSummaryMoveAmount(enteredCents = 5_000, toBudgetCents = 0, sourceBalanceCents = null))
        assertNull(budgetSummaryMoveAmount(enteredCents = 0, toBudgetCents = 10_000, sourceBalanceCents = null))
    }

    @Test fun coveringOverbudgetedIsLimitedToTheSourceBalance() {
        assertEquals(3_000L, budgetSummaryMoveAmount(enteredCents = 3_000, toBudgetCents = -8_000, sourceBalanceCents = 5_000))
        assertEquals(5_000L, budgetSummaryMoveAmount(enteredCents = 8_000, toBudgetCents = -8_000, sourceBalanceCents = 5_000))
        // Upstream covers whatever amount was asked for, even beyond the overbudgeted amount.
        assertEquals(9_000L, budgetSummaryMoveAmount(enteredCents = 9_000, toBudgetCents = -8_000, sourceBalanceCents = 20_000))
        assertNull(budgetSummaryMoveAmount(enteredCents = 3_000, toBudgetCents = -8_000, sourceBalanceCents = -100))
        assertNull(budgetSummaryMoveAmount(enteredCents = 3_000, toBudgetCents = -8_000, sourceBalanceCents = null))
    }
}
