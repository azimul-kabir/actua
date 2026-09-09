package com.azimulkabir.actua.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    @Test fun formatsSyncAndBackupAgeCompactly() {
        val now = 1_000_000L
        assertEquals("just now", relativeTime(now - 5_000, now))
        assertEquals("2 min ago", relativeTime(now - 120_000, now))
        assertEquals("2 hr ago", relativeTime(now - 7_200_000, now))
    }
}
