package com.azimulkabir.actua.data.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeSummaryPeriodTest {
    private fun period(today: LocalDate, startDay: Int) = HomeSummaryPeriod.containing(today, startDay)

    @Test
    fun startDayOneIsTheCalendarMonth() {
        assertEquals(
            HomeSummaryPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
            period(LocalDate.of(2026, 10, 17), 1),
        )
    }

    @Test
    fun beforeTheStartDayThePeriodBeganLastMonth() {
        assertEquals(
            HomeSummaryPeriod(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 26)),
            period(LocalDate.of(2026, 10, 5), 27),
        )
    }

    @Test
    fun onAndAfterTheStartDayThePeriodBeginsThisMonth() {
        val expected = HomeSummaryPeriod(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 26))
        assertEquals(expected, period(LocalDate.of(2026, 10, 27), 27))
        assertEquals(expected, period(LocalDate.of(2026, 10, 31), 27))
        assertEquals(expected, period(LocalDate.of(2026, 11, 26), 27))
    }

    @Test
    fun periodsCrossTheYearBoundary() {
        assertEquals(
            HomeSummaryPeriod(LocalDate.of(2025, 12, 27), LocalDate.of(2026, 1, 26)),
            period(LocalDate.of(2026, 1, 5), 27),
        )
    }

    @Test
    fun startDayThirtyOneClampsToShortMonths() {
        assertEquals(
            HomeSummaryPeriod(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 30)),
            period(LocalDate.of(2026, 3, 15), 31),
        )
        assertEquals(
            HomeSummaryPeriod(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 29)),
            period(LocalDate.of(2026, 3, 31), 31),
        )
        assertEquals(
            HomeSummaryPeriod(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 30)),
            period(LocalDate.of(2026, 5, 10), 31),
        )
    }

    @Test
    fun februaryAndLeapYearsClampTheStartDay() {
        // 2028 is a leap year: the 29th exists in February, so the period runs 29 Feb – 28 Mar.
        assertEquals(
            HomeSummaryPeriod(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 28)),
            period(LocalDate.of(2028, 3, 10), 29),
        )
        // 2027 is not: day 29 clamps to Feb 28, and the next start is Mar 29.
        assertEquals(
            HomeSummaryPeriod(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 28)),
            period(LocalDate.of(2027, 3, 10), 29),
        )
        assertEquals(
            HomeSummaryPeriod(LocalDate.of(2027, 1, 30), LocalDate.of(2027, 2, 27)),
            period(LocalDate.of(2027, 2, 10), 30),
        )
    }

    @Test
    fun consecutivePeriodsNeitherGapNorOverlapForEveryStartDay() {
        for (startDay in 1..31) {
            var day = LocalDate.of(2027, 12, 31)
            var previous = period(day, startDay)
            while (day > LocalDate.of(2027, 1, 1)) {
                day = day.minusDays(1)
                val current = period(day, startDay)
                if (current != previous) assertEquals(
                    "start day $startDay on $day", current.endInclusive.plusDays(1), previous.start,
                )
                assertTrue(day >= current.start && day <= current.endInclusive)
                previous = current
            }
        }
    }

    @Test
    fun containsUsesActualsDayIntegers() {
        val period = HomeSummaryPeriod(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 26))
        assertTrue(20260927 in period)
        assertTrue(20261026 in period)
        assertFalse(20260926 in period)
        assertFalse(20261027 in period)
    }

    @Test
    fun outOfRangeStartDaysAreCoerced() {
        assertEquals(period(LocalDate.of(2026, 10, 5), 31), period(LocalDate.of(2026, 10, 5), 99))
        assertEquals(period(LocalDate.of(2026, 10, 5), 1), period(LocalDate.of(2026, 10, 5), 0))
    }
}
