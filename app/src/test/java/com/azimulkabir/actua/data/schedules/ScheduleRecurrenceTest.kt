package com.azimulkabir.actua.data.schedules

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleRecurrenceTest {
    private fun next(config: RecurConfig, day: String) = ScheduleRecurrence.nextOccurrence(config, requireNotNull(DayDate.fromIso(day)))?.iso

    @Test fun dailyWeeklyAndYearlyIntervals() {
        assertEquals("2026-03-15", next(RecurConfig(RecurConfig.Frequency.DAILY, 1, DayDate(2026,1,1)), "2026-03-15"))
        assertEquals("2026-01-15", next(RecurConfig(RecurConfig.Frequency.WEEKLY, 2, DayDate(2026,1,1)), "2026-01-14"))
        assertEquals("2028-02-29", next(RecurConfig(RecurConfig.Frequency.YEARLY, 1, DayDate(2024,2,29)), "2025-01-01"))
    }

    @Test fun monthlyPositiveNegativeAndNthWeekdayPatternsUnion() {
        val day = RecurConfig(RecurConfig.Frequency.MONTHLY, 1, DayDate(2026,1,1),
            listOf(RecurConfig.Pattern("day",-1), RecurConfig.Pattern("day",15)))
        assertEquals("2026-02-15", next(day, "2026-02-02"))
        assertEquals("2026-02-28", next(day, "2026-02-16"))
        val weekday = RecurConfig(RecurConfig.Frequency.MONTHLY, 1, DayDate(2026,1,1),
            listOf(RecurConfig.Pattern("MO",2), RecurConfig.Pattern("FR",-1)))
        assertEquals("2026-02-09", next(weekday, "2026-02-01"))
        assertEquals("2026-02-27", next(weekday, "2026-02-10"))
    }

    @Test fun boundedSchedulesFallBackToLastOccurrence() {
        val count = RecurConfig(RecurConfig.Frequency.MONTHLY, 1, DayDate(2026,1,10),
            endMode="after_n_occurrences", endOccurrences=3)
        assertEquals("2026-03-10", next(count, "2027-01-01"))
        val date = RecurConfig(RecurConfig.Frequency.DAILY, 1, DayDate(2026,1,1),
            endMode="on_date", endDate=DayDate(2026,1,3))
        assertEquals("2026-01-03", next(date, "2026-02-01"))
    }

    @Test fun weekendSolvingMatchesBeforeAndAfterModes() {
        val before = RecurConfig(RecurConfig.Frequency.MONTHLY,1,DayDate(2026,1,5),
            listOf(RecurConfig.Pattern("day",7)), true,"before")
        assertEquals("2026-02-06", next(before, "2026-02-01"))
        val after = RecurConfig(RecurConfig.Frequency.MONTHLY,1,DayDate(2026,1,5),
            listOf(RecurConfig.Pattern("day",7)), true,"after")
        assertEquals("2026-02-09", next(after, "2026-02-01"))
    }
}
