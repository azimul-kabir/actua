package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Test

/** Actual's `calcBufferedAmount`: a hold adds to the current hold (#1774). */
class HoldForNextMonthTest {
    @Test fun aHoldAddsToTheCurrentHold() {
        assertEquals(7_000L, heldAfterHold(toBudgetCents = 10_000, bufferedCents = 2_000, amountCents = 5_000))
    }

    @Test fun aHoldIsLimitedToToBudget() {
        assertEquals(12_000L, heldAfterHold(toBudgetCents = 10_000, bufferedCents = 2_000, amountCents = 50_000))
    }

    @Test fun aHoldCannotReleaseMoreThanIsHeld() {
        assertEquals(0L, heldAfterHold(toBudgetCents = 10_000, bufferedCents = 2_000, amountCents = -9_000))
    }

    @Test fun nothingIsHeldWithoutToBudget() {
        assertEquals(2_000L, heldAfterHold(toBudgetCents = -500, bufferedCents = 2_000, amountCents = 4_000))
    }
}
