package com.azimulkabir.actua.ui.navigation

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
}
