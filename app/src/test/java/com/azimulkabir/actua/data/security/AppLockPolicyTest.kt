package com.azimulkabir.actua.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    private var now = 1_000L
    private val policy = AppLockPolicy { now }

    private fun foreground(enabled: Boolean = true, timeout: AppLockTimeout = AppLockTimeout.FIVE_MINUTES) =
        policy.onForeground(enabled, timeout)

    @Test
    fun aDisabledLockNeverLocks() {
        foreground(enabled = false)
        assertFalse(policy.locked)
        policy.onBackground()
        now += 24 * 60 * 60_000
        foreground(enabled = false)
        assertFalse(policy.locked)
    }

    @Test
    fun aNewProcessStartsLockedWhenEnabled() {
        // A process that was killed and restored has no memory of having been unlocked.
        foreground()
        assertTrue(policy.locked)
    }

    @Test
    fun unlockingOpensTheAppUntilItLeavesTheForeground() {
        foreground()
        policy.unlock()
        assertFalse(policy.locked)
        foreground() // e.g. onStart again with no stop in between
        assertFalse(policy.locked)
    }

    @Test
    fun returningWithinTheTimeoutStaysUnlocked() {
        foreground()
        policy.unlock()
        policy.onBackground()
        now += 4 * 60_000 + 59_000
        foreground(timeout = AppLockTimeout.FIVE_MINUTES)
        assertFalse(policy.locked)
    }

    @Test
    fun returningAtOrAfterTheTimeoutLocks() {
        foreground()
        policy.unlock()
        policy.onBackground()
        now += 5 * 60_000
        foreground(timeout = AppLockTimeout.FIVE_MINUTES)
        assertTrue(policy.locked)
    }

    @Test
    fun immediatelyLocksOnEveryReturnEvenWithoutTimeToPass() {
        foreground(timeout = AppLockTimeout.IMMEDIATELY)
        policy.unlock()
        policy.onBackground()
        foreground(timeout = AppLockTimeout.IMMEDIATELY)
        assertTrue(policy.locked)
    }

    @Test
    fun aRecreatedActivityThatNeverBackgroundedStaysUnlocked() {
        // Rotation: the activity stops without calling onBackground, then starts again.
        foreground(timeout = AppLockTimeout.IMMEDIATELY)
        policy.unlock()
        foreground(timeout = AppLockTimeout.IMMEDIATELY)
        assertFalse(policy.locked)
    }

    @Test
    fun aLockedAppStaysLockedUntilAuthenticationSucceeds() {
        foreground()
        policy.onBackground()
        now += 10 * 60_000
        foreground()
        assertTrue(policy.locked)
        policy.onBackground()
        foreground() // back again quickly: still locked
        assertTrue(policy.locked)
        policy.unlock()
        assertFalse(policy.locked)
    }

    @Test
    fun theTimeoutIsMeasuredFromTheLatestBackgrounding() {
        foreground()
        policy.unlock()
        policy.onBackground()
        now += 4 * 60_000
        foreground()
        assertFalse(policy.locked)
        policy.onBackground() // a fresh clock
        now += 4 * 60_000
        foreground()
        assertFalse(policy.locked)
    }

    @Test
    fun changingTheSettingLeavesTheAppUnlockedAndRestartsTheClock() {
        foreground(enabled = false)
        // The user just authenticated to turn it on.
        policy.onSettingChanged()
        assertFalse(policy.locked)
        foreground() // onStart again after the prompt: must not lock
        assertFalse(policy.locked)
        policy.onBackground()
        now += 6 * 60_000
        foreground()
        assertTrue(policy.locked)
    }

    @Test
    fun turningTheLockOffLeavesTheAppUnlockedForGood() {
        foreground()
        policy.unlock()
        policy.onSettingChanged() // disabled after authenticating
        policy.onBackground()
        now += 60 * 60_000
        foreground(enabled = false)
        assertFalse(policy.locked)
    }

    @Test
    fun storedTimeoutsParseAndUnknownValuesFallBackToTheStrictestSetting() {
        AppLockTimeout.entries.forEach { assertEquals(it, AppLockTimeout.fromStored(it.name)) }
        assertEquals(AppLockTimeout.IMMEDIATELY, AppLockTimeout.fromStored(null))
        assertEquals(AppLockTimeout.IMMEDIATELY, AppLockTimeout.fromStored("NEVER"))
        assertEquals(AppLockTimeout.IMMEDIATELY, AppLockTimeout.fromStored(""))
    }

    @Test
    fun timeoutsAreOrderedFromStrictestToMostRelaxed() {
        assertEquals(listOf(0L, 60_000L, 300_000L, 900_000L), AppLockTimeout.entries.map { it.millis })
    }
}
