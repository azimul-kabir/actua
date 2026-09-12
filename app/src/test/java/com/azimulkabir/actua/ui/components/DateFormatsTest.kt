package com.azimulkabir.actua.ui.components

import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormatsTest {
    @After fun reset() { DateDisplay.format = "System default" }

    @Test fun formatsSupportedExplicitDatePatterns() {
        val date = LocalDate.of(2026, 9, 12)
        DateDisplay.format = "DD/MM/YYYY"
        assertEquals("12/09/2026", formatDate(date))
        DateDisplay.format = "MM/DD/YYYY"
        assertEquals("09/12/2026", formatDate(date))
        DateDisplay.format = "YYYY-MM-DD"
        assertEquals("2026-09-12", formatDate(date))
    }

    @Test fun storageFormatNeverChanges() {
        DateDisplay.format = "MM/DD/YYYY"
        assertEquals("20260912", storageDate(LocalDate.of(2026, 9, 12)))
    }
}
