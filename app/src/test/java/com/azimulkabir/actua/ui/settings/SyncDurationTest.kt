package com.azimulkabir.actua.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncDurationTest {
    @Test
    fun formatsSubsecondAndLongerSyncDurations() {
        assertEquals("850 ms", formatSyncDuration(850L))
        assertEquals("1.5 s", formatSyncDuration(1_500L))
    }
}
