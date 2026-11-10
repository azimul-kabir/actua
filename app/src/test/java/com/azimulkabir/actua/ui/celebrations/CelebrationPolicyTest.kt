package com.azimulkabir.actua.ui.celebrations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationPolicyTest {

    private val month = "2026-11"

    @Test
    fun celebratesWhenReadyToBudgetReachesZeroFromAnAmountInTheSameMonth() {
        assertTrue(CelebrationPolicy.readyToBudgetReachedZero(month, 120_000L, month, 0L))
        // Covering an overspent month back to exactly zero counts too.
        assertTrue(CelebrationPolicy.readyToBudgetReachedZero(month, -4_500L, month, 0L))
    }

    @Test
    fun doesNotCelebrateWithoutAChangeToZero() {
        assertFalse("already zero", CelebrationPolicy.readyToBudgetReachedZero(month, 0L, month, 0L))
        assertFalse("still above zero", CelebrationPolicy.readyToBudgetReachedZero(month, 120_000L, month, 50_000L))
        assertFalse("went negative", CelebrationPolicy.readyToBudgetReachedZero(month, 1_000L, month, -1L))
        assertFalse("zero to non-zero", CelebrationPolicy.readyToBudgetReachedZero(month, 0L, month, 1_000L))
    }

    @Test
    fun aMonthChangeNeverCelebrates() {
        assertFalse(CelebrationPolicy.readyToBudgetReachedZero("2026-10", 120_000L, month, 0L))
        assertFalse(CelebrationPolicy.readyToBudgetReachedZero(null, 120_000L, month, 0L))
    }

    @Test
    fun aBudgetThatTracksInsteadOfEnvelopesNeverCelebrates() {
        assertFalse(CelebrationPolicy.readyToBudgetReachedZero(month, null, month, 0L))
        assertFalse(CelebrationPolicy.readyToBudgetReachedZero(month, 120_000L, month, null))
    }

    @Test
    fun playsOnlyForASupporterWithTheToggleOnAndAnimationsEnabled() {
        assertTrue(CelebrationPolicy.shouldPlay(available = true, isSupporter = true, enabled = true, animationScale = 1f))
        assertTrue(CelebrationPolicy.shouldPlay(available = true, isSupporter = true, enabled = true, animationScale = 0.5f))
    }

    @Test
    fun staysQuietWhenAnyConditionFails() {
        assertFalse("fdroid build", CelebrationPolicy.shouldPlay(false, true, true, 1f))
        assertFalse("not a supporter", CelebrationPolicy.shouldPlay(true, false, true, 1f))
        assertFalse("toggle off", CelebrationPolicy.shouldPlay(true, true, false, 1f))
        assertFalse("animations off", CelebrationPolicy.shouldPlay(true, true, true, 0f))
    }
}
