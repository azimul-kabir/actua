package com.azimulkabir.actua.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingPromptTest {

    @Test
    fun waitsWhileTheSupportPromptIsStillDue() {
        assertTrue(supportPromptPending(supportPromptDue = true, isSupporter = false))
    }

    @Test
    fun doesNotWaitOnceTheSupportPromptWasShownOrIsNotDue() {
        assertFalse(supportPromptPending(supportPromptDue = false, isSupporter = false))
    }

    @Test
    fun aSupporterIsNeverHeldBackBecauseTheSupportPromptNeverShowsForThem() {
        assertFalse(supportPromptPending(supportPromptDue = true, isSupporter = true))
    }
}
