package com.azimulkabir.actua.ui.navigation

import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_AFTER_CHANGE
import com.azimulkabir.actua.data.sync.SyncStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundSyncPolicyTest {
    @Test
    fun compositionDoesNotSyncUntilActivityStarts() {
        assertFalse(shouldRequestForegroundSync(0))
        assertTrue(shouldRequestForegroundSync(1))
        assertTrue(shouldRequestForegroundSync(2))
    }

    @Test
    fun bannerIsSuppressedForMutationUploadsButShownForRefreshTriggers() {
        // An unset trigger is a legacy/unknown state, not a known mutation upload, so it fails
        // safe by showing the banner rather than silently hiding a running sync.
        assertTrue(shouldShowSyncBanner(runningStatus(activeTrigger = null)))
        assertFalse(shouldShowSyncBanner(runningStatus(activeTrigger = SYNC_TRIGGER_AFTER_CHANGE)))
        assertTrue(shouldShowSyncBanner(runningStatus(activeTrigger = "App open")))
        assertTrue(shouldShowSyncBanner(runningStatus(activeTrigger = "Background")))
        assertTrue(shouldShowSyncBanner(runningStatus(activeTrigger = "Manual")))
        assertFalse(shouldShowSyncBanner(runningStatus(activeTrigger = "App open", running = false)))
    }

    private fun runningStatus(activeTrigger: String?, running: Boolean = true) = SyncStatus(
        running = running,
        lastAttemptMillis = 0,
        lastSuccessMillis = 0,
        sentMessages = 0,
        receivedMessages = 0,
        error = null,
        lastBackgroundRefreshMillis = 0,
        activeTrigger = activeTrigger,
        lastDurationMillis = 0,
        lastForegroundRefreshMillis = 0,
    )
}
