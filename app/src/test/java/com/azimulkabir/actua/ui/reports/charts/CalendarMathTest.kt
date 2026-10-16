package com.azimulkabir.actua.ui.reports.charts

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarMathTest {
    @Test
    fun swipeLeftGoesForwardRightGoesBackShortDragStays() {
        assertEquals(1, calendarSwipeStep(-200f, 100f))
        assertEquals(-1, calendarSwipeStep(200f, 100f))
        assertEquals(0, calendarSwipeStep(-40f, 100f))
        assertEquals(0, calendarSwipeStep(99f, 100f))
    }

    @Test
    fun monthIndexStaysInRange() {
        assertEquals(0, clampMonthIndex(0, -1, 3))
        assertEquals(3, clampMonthIndex(3, 1, 3))
        assertEquals(2, clampMonthIndex(1, 1, 3))
        assertEquals(0, clampMonthIndex(0, 1, 0))
    }

    @Test
    fun barsScaleToTheLargestDayAndStayVisible() {
        assertEquals(1f, calendarBarFraction(500, 500), 0.0001f)
        assertEquals(0.5f, calendarBarFraction(250, 500), 0.0001f)
        assertEquals(0f, calendarBarFraction(0, 500), 0f)
        assertEquals(0f, calendarBarFraction(100, 0), 0f)
        assertEquals(0.08f, calendarBarFraction(1, 100_000), 0.0001f)
    }
}
