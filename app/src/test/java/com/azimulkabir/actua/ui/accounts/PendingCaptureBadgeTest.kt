package com.azimulkabir.actua.ui.accounts

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingCaptureBadgeTest {
    @Test fun badgeShowsTheCountUpToNinetyNine() {
        assertEquals("1", pendingCaptureBadge(1))
        assertEquals("99", pendingCaptureBadge(99))
        assertEquals("99+", pendingCaptureBadge(100))
    }
}
