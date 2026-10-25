package com.azimulkabir.actua.data.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class NonWorkingDaysTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun theDefaultIsSaturdayAndSundayWithNoHolidays() {
        assertTrue(NonWorkingDays().isDefault)
        assertFalse(NonWorkingDays(weekend = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)).isDefault)
        assertFalse(NonWorkingDays(holidays = setOf(d(2026, 5, 25))).isDefault)
    }

    @Test
    fun weekendDaysAndHolidaysAreNonWorking() {
        val days = NonWorkingDays(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), setOf(d(2026, 5, 25)))
        assertTrue(days.isNonWorking(d(2026, 4, 24)))   // Friday
        assertTrue(days.isNonWorking(d(2026, 4, 25)))   // Saturday
        assertFalse(days.isNonWorking(d(2026, 4, 26)))  // Sunday is a working day here
        assertTrue(days.isNonWorking(d(2026, 5, 25)))   // Monday holiday
        assertFalse(days.isNonWorking(d(2026, 5, 26)))
    }

    @Test
    fun steppingSkipsAWeekendAndAdjoiningHolidays() {
        val days = NonWorkingDays(holidays = setOf(d(2026, 5, 22), d(2026, 5, 25)))
        // Sat 23, Sun 24 and Mon 25 (holiday) are off; Fri 22 is a holiday too, so earlier goes to Thu 21.
        assertEquals(d(2026, 5, 21), days.workingDayFrom(d(2026, 5, 23), -1))
        assertEquals(d(2026, 5, 26), days.workingDayFrom(d(2026, 5, 23), 1))
        assertEquals(d(2026, 5, 21), days.workingDayFrom(d(2026, 5, 21), 1))
    }

    @Test
    fun withNoWorkingDayToReachTheDateStaysPut() {
        val everyDay = NonWorkingDays(weekend = DayOfWeek.values().toSet())
        assertEquals(d(2026, 5, 23), everyDay.workingDayFrom(d(2026, 5, 23), 1))
        // A run of holidays longer than a year is given up on rather than looped over.
        val longRun = generateSequence(d(2026, 1, 1)) { it.plusDays(1) }.take(500).toSet()
        val holidays = NonWorkingDays(weekend = emptySet(), holidays = longRun)
        assertEquals(d(2026, 1, 1), holidays.workingDayFrom(d(2026, 1, 1), 1))
    }

    @Test
    fun anEmptyWeekendAndNoHolidaysMovesNothing() {
        assertEquals(d(2026, 5, 23), NonWorkingDays(weekend = emptySet()).workingDayFrom(d(2026, 5, 23), -1))
    }
}
