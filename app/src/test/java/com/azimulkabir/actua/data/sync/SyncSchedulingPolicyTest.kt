package com.azimulkabir.actua.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSchedulingPolicyTest {
    @Test
    fun recentSuccessIsReusedOnlyForSameBudgetInsideWindow() {
        assertTrue(SyncCoalescingPolicy.shouldReuse(true, "budget", "budget", 1_000L, 6_000L))
        assertFalse(SyncCoalescingPolicy.shouldReuse(true, "budget", "other", 1_000L, 1_001L))
        assertFalse(SyncCoalescingPolicy.shouldReuse(true, "budget", "budget", 1_000L, 6_001L))
        assertFalse(SyncCoalescingPolicy.shouldReuse(true, "budget", "budget", 2_000L, 1_999L))
    }

    @Test
    fun mutationSyncNeverReusesPreviousResult() {
        assertFalse(allowsRecentSuccess(ActualSyncWorker.REASON_MUTATION))
        assertTrue(allowsRecentSuccess(ActualSyncWorker.REASON_PERIODIC))
        assertTrue(allowsRecentSuccess(ActualSyncWorker.REASON_FOREGROUND))
        assertEquals("After change", syncTriggerLabel(ActualSyncWorker.REASON_MUTATION))
        assertEquals("App open", syncTriggerLabel(ActualSyncWorker.REASON_FOREGROUND))
        assertEquals("Background", syncTriggerLabel(ActualSyncWorker.REASON_PERIODIC))
    }
}
