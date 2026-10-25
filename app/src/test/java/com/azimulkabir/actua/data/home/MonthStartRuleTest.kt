package com.azimulkabir.actua.data.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class MonthStartRuleTest {
    private val bangladeshWeekend = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    private fun rule(day: Int, adjustment: StartDayAdjustment, weekend: Set<DayOfWeek> = bangladeshWeekend) =
        MonthStartRule(day, adjustment, weekend)
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun period(from: LocalDate, to: LocalDate) = HomeSummaryPeriod(from, to)

    @Test
    fun theDefaultRuleIsTheCalendarMonthAndAnyDeviationIsNot() {
        assertTrue(MonthStartRule.CALENDAR_MONTH.isCalendarMonth)
        assertFalse(MonthStartRule(25).isCalendarMonth)
        assertFalse(MonthStartRule(1, StartDayAdjustment.PREVIOUS_WORKING_DAY).isCalendarMonth)
    }

    @Test
    fun aSalaryDayOnAFridayOrSaturdayMovesToThursdayWithAFridaySaturdayWeekend() {
        val previous = rule(25, StartDayAdjustment.PREVIOUS_WORKING_DAY)
        // 2026-04-25 is a Friday and 2026-07-25 a Saturday; 2026-05-25 is a Monday.
        assertEquals(d(2026, 4, 23), previous.startFor(java.time.YearMonth.of(2026, 4)))
        assertEquals(d(2026, 7, 23), previous.startFor(java.time.YearMonth.of(2026, 7)))
        assertEquals(d(2026, 5, 25), previous.startFor(java.time.YearMonth.of(2026, 5)))
    }

    @Test
    fun nextWorkingDayMovesPastTheWeekend() {
        val next = rule(25, StartDayAdjustment.NEXT_WORKING_DAY)
        assertEquals(d(2026, 4, 26), next.startFor(java.time.YearMonth.of(2026, 4)))
        assertEquals(d(2026, 7, 26), next.startFor(java.time.YearMonth.of(2026, 7)))
        assertEquals(d(2026, 5, 25), next.startFor(java.time.YearMonth.of(2026, 5)))
    }

    @Test
    fun theDefaultWeekendIsSaturdayAndSunday() {
        val previous = MonthStartRule(25, StartDayAdjustment.PREVIOUS_WORKING_DAY)
        // 2026-04-25 is a Saturday under Sat-Sun only if it were; it is a Friday, so it stays.
        assertEquals(d(2026, 4, 25), previous.startFor(java.time.YearMonth.of(2026, 4)))
        // 2026-10-25 is a Sunday, so it moves back to Friday the 23rd.
        assertEquals(d(2026, 10, 23), previous.startFor(java.time.YearMonth.of(2026, 10)))
    }

    @Test
    fun noneKeepsTheDayEvenOnAWeekend() {
        assertEquals(d(2026, 4, 25), rule(25, StartDayAdjustment.NONE).startFor(java.time.YearMonth.of(2026, 4)))
    }

    @Test
    fun anAllWeekWeekendOrAnEmptyOneLeavesTheDayAlone() {
        val everyDay = DayOfWeek.values().toSet()
        assertEquals(d(2026, 4, 25), rule(25, StartDayAdjustment.PREVIOUS_WORKING_DAY, everyDay).startFor(java.time.YearMonth.of(2026, 4)))
        assertEquals(d(2026, 4, 25), rule(25, StartDayAdjustment.PREVIOUS_WORKING_DAY, emptySet()).startFor(java.time.YearMonth.of(2026, 4)))
    }

    @Test
    fun theAdjustedPeriodsRunFromPayDayToTheDayBeforeTheNextOne() {
        val rule = rule(25, StartDayAdjustment.PREVIOUS_WORKING_DAY)
        // The April payday is Thursday the 23rd (the 25th is a Friday); May's is Monday the 25th.
        assertEquals(period(d(2026, 4, 23), d(2026, 5, 24)), HomeSummaryPeriod.containing(d(2026, 4, 24), rule))
        assertEquals(period(d(2026, 4, 23), d(2026, 5, 24)), HomeSummaryPeriod.containing(d(2026, 4, 23), rule))
        assertEquals(period(d(2026, 3, 25), d(2026, 4, 22)), HomeSummaryPeriod.containing(d(2026, 4, 22), rule))
    }

    @Test
    fun aStartPulledIntoThePreviousMonthIsFoundFromTheLastDaysOfThatMonth() {
        // Aug 1 2026 is a Saturday, so with day 1 the period starts Thursday Jul 30.
        val rule = rule(1, StartDayAdjustment.PREVIOUS_WORKING_DAY)
        assertEquals(period(d(2026, 7, 30), d(2026, 8, 31)), HomeSummaryPeriod.containing(d(2026, 7, 31), rule))
        assertEquals(period(d(2026, 7, 1), d(2026, 7, 29)), HomeSummaryPeriod.containing(d(2026, 7, 29), rule))
    }

    @Test
    fun aStartPushedPastTheMonthEndStillFindsThePreviousPeriod() {
        // Jan 31 2026 is a Saturday and Feb 28 a Saturday: the January and February starts are Feb 2 and Mar 2.
        val rule = MonthStartRule(31, StartDayAdjustment.NEXT_WORKING_DAY)
        assertEquals(period(d(2025, 12, 31), d(2026, 2, 1)), HomeSummaryPeriod.containing(d(2026, 2, 1), rule))
        assertEquals(period(d(2026, 2, 2), d(2026, 3, 1)), HomeSummaryPeriod.containing(d(2026, 2, 2), rule))
    }

    @Test
    fun shiftingMovesByPayPeriodNotByCalendarDate() {
        val rule = rule(25, StartDayAdjustment.PREVIOUS_WORKING_DAY)
        val april = HomeSummaryPeriod.containing(d(2026, 4, 24), rule)
        assertEquals(period(d(2026, 5, 25), d(2026, 6, 24)), april.shiftedByMonths(rule, 1))
        // Plain date arithmetic would land on Mar 23, inside the period before this one.
        assertEquals(period(d(2026, 3, 25), d(2026, 4, 22)), april.shiftedByMonths(rule, -1))
        assertEquals(april, april.shiftedByMonths(rule, 0))
    }

    @Test
    fun shiftingAPlainStartDayKeepsTheDay() {
        val rule = MonthStartRule(31)
        val february = HomeSummaryPeriod.containing(d(2026, 2, 28), rule)
        assertEquals(period(d(2026, 1, 31), d(2026, 2, 27)), february.shiftedByMonths(rule, -1))
        assertEquals(period(d(2026, 3, 31), d(2026, 4, 29)), february.shiftedByMonths(rule, 1))
    }

    @Test
    fun everyDayFallsInExactlyOnePeriodAndPeriodsAreContiguousForEveryRule() {
        val rules = listOf(
            MonthStartRule(25),
            rule(25, StartDayAdjustment.PREVIOUS_WORKING_DAY),
            rule(25, StartDayAdjustment.NEXT_WORKING_DAY),
            rule(1, StartDayAdjustment.PREVIOUS_WORKING_DAY),
            rule(31, StartDayAdjustment.NEXT_WORKING_DAY),
            MonthStartRule(31, StartDayAdjustment.PREVIOUS_WORKING_DAY),
        )
        for (rule in rules) {
            var day = d(2025, 12, 1)
            while (day < d(2028, 3, 1)) {
                val period = HomeSummaryPeriod.containing(day, rule)
                assertTrue("$rule $day in $period", day >= period.start && day <= period.endInclusive)
                val next = HomeSummaryPeriod.containing(period.endInclusive.plusDays(1), rule)
                assertEquals("$rule after $period", period.endInclusive.plusDays(1), next.start)
                day = day.plusDays(1)
            }
        }
    }
}
