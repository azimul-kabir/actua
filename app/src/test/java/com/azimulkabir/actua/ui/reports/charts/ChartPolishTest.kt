package com.azimulkabir.actua.ui.reports.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartPolishTest {
    @Test fun animationsFollowTheSystemDurationScale() {
        assertFalse(chartAnimationsEnabled(0f))
        assertTrue(chartAnimationsEnabled(1f))
        assertTrue(chartAnimationsEnabled(0.5f))
        assertTrue(chartAnimationsEnabled(10f))
    }

    @Test fun nextAndPreviousPeriodStayInsideTheChart() {
        assertEquals(0, stepPeriodIndex(null, 1, 6))
        assertEquals(5, stepPeriodIndex(null, -1, 6))
        assertEquals(3, stepPeriodIndex(2, 1, 6))
        assertEquals(1, stepPeriodIndex(2, -1, 6))
        assertEquals(5, stepPeriodIndex(5, 1, 6))
        assertEquals(0, stepPeriodIndex(0, -1, 6))
        assertEquals(0, stepPeriodIndex(null, 1, 0))
    }

    @Test fun summaryNamesTheStartEndHighAndLow() {
        val summary = seriesSummary("Net worth", listOf("2026-04-30", "2026-05-31", "2026-06-30"), listOf(100, 300, 200)) { "$$it" }
        assertEquals(
            "Net worth: 3 periods, from 2026-04-30 \$100 to 2026-06-30 \$200, highest \$300 in 2026-05-31, lowest \$100 in 2026-04-30.",
            summary,
        )
        assertEquals("Empty: no data.", seriesSummary("Empty", emptyList(), emptyList()) { "$it" })
    }
}
