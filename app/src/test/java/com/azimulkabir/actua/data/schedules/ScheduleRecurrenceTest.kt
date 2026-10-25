package com.azimulkabir.actua.data.schedules

import com.azimulkabir.actua.data.home.MonthStartRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

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

    @Test fun upcomingDatesContinuePastABeforeSolvedWeekendOccurrence() {
        // 2026-10-25 is a Sunday, solved back to Friday 2026-10-23; the walk must not stop there.
        val before = RecurConfig(RecurConfig.Frequency.MONTHLY, 1, DayDate(2025,10,25),
            skipWeekend = true, weekendSolveMode = "before")
        assertEquals(listOf("2026-10-23", "2026-11-25", "2026-12-25", "2027-01-25"),
            ScheduleRecurrence.upcomingDates(before, 4, DayDate(2026,10,23)).map { it.iso })
        assertEquals("2026-11-25", ScheduleRecurrence.previousOccurrence(before, DayDate(2026,12,1))?.iso)
    }

    @Test fun upcomingDatesStopAtTheRecurrenceEnd() {
        val count = RecurConfig(RecurConfig.Frequency.MONTHLY, 1, DayDate(2026,8,25),
            endMode = "after_n_occurrences", endOccurrences = 3)
        assertEquals(listOf("2026-09-25", "2026-10-25"),
            ScheduleRecurrence.upcomingDates(count, 10, DayDate(2026,9,1)).map { it.iso })
    }

    @Test fun occursOnAndOccursApproxMatchTheRecurrence() {
        val monthly = RecurConfig(RecurConfig.Frequency.MONTHLY, 1, DayDate(2026, 1, 3))
        org.junit.Assert.assertTrue(ScheduleRecurrence.occursOn(monthly, DayDate(2026, 5, 3)))
        org.junit.Assert.assertFalse(ScheduleRecurrence.occursOn(monthly, DayDate(2026, 5, 4)))
        org.junit.Assert.assertTrue(ScheduleRecurrence.occursApprox(monthly, DayDate(2026, 5, 5)))
        org.junit.Assert.assertFalse(ScheduleRecurrence.occursApprox(monthly, DayDate(2026, 5, 6)))
    }

    @Test fun parseToleratesLongValuedNumbersFromRuleValueRoundTrip() {
        val json = org.json.JSONObject().put("start", "2026-01-03").put("frequency", "monthly")
            .put("interval", 1L).put("patterns", org.json.JSONArray()
                .put(org.json.JSONObject().put("type", "day").put("value", 3L)))
            .put("endMode", "never")
        val config = requireNotNull(RecurConfig.parse(json))
        assertEquals(1, config.interval)
        assertEquals(listOf(RecurConfig.Pattern("day", 3)), config.patterns)
    }

    private val fridaySaturday = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    private fun salary(mode: String, skipWeekend: Boolean = true) = RecurConfig(
        RecurConfig.Frequency.MONTHLY, 1, DayDate(2026, 1, 25), skipWeekend = skipWeekend, weekendSolveMode = mode,
    )
    private fun shown(config: RecurConfig, stored: DayDate, weekend: Set<DayOfWeek> = fridaySaturday) =
        ScheduleRecurrence.displayDate(config, stored, weekend)

    @Test fun aBeforeScheduleShowsThursdayWhenTheWeekendIsFridayAndSaturday() {
        // The 25th is a Saturday in April and July (Actual stores the Friday before) and a Friday in September.
        assertEquals(DayDate(2026, 4, 23), shown(salary("before"), DayDate(2026, 4, 24)))
        assertEquals(DayDate(2026, 7, 23), shown(salary("before"), DayDate(2026, 7, 24)))
        assertEquals(DayDate(2026, 9, 24), shown(salary("before"), DayDate(2026, 9, 25)))
    }

    @Test fun anAfterScheduleShowsTheSundayAfterAFridaySaturdayWeekend() {
        assertEquals(DayDate(2026, 4, 26), shown(salary("after"), DayDate(2026, 4, 27)))
        assertEquals(DayDate(2026, 9, 27), shown(salary("after"), DayDate(2026, 9, 25)))
    }

    @Test fun workingDayOccurrencesAndSaturdaySundayWeekendsAreUntouched() {
        assertEquals(DayDate(2026, 5, 25), shown(salary("before"), DayDate(2026, 5, 25)))
        assertEquals(DayDate(2026, 4, 24), shown(salary("before"), DayDate(2026, 4, 24), MonthStartRule.DEFAULT_WEEKEND_DAYS))
    }

    @Test fun aScheduleThatDoesNotSkipWeekendsIsNeverMoved() {
        assertEquals(DayDate(2026, 4, 25), shown(salary("before", skipWeekend = false), DayDate(2026, 4, 25)))
    }

    @Test fun aStoredDateThatIsNoOccurrenceIsLeftAlone() {
        assertEquals(DayDate(2026, 4, 10), shown(salary("before"), DayDate(2026, 4, 10)))
    }

    @Test fun anEmptyOrFullWeekendNeverMovesAnything() {
        assertEquals(DayDate(2026, 4, 25), shown(salary("before"), DayDate(2026, 4, 24), emptySet()))
        assertEquals(DayDate(2026, 4, 25), shown(salary("before"), DayDate(2026, 4, 24), DayOfWeek.values().toSet()))
    }

    @Test fun displayingNeverChangesTheStoredRecurrence() {
        val config = salary("before")
        assertEquals("2026-04-24", next(config, "2026-04-01"))
    }
}
