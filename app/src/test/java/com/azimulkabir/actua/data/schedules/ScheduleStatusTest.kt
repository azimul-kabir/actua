package com.azimulkabir.actua.data.schedules

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleStatusTest {
    private val today = DayDate(2026, 9, 5)

    @Test fun statusBranchOrderMatchesIos() {
        assertEquals(ScheduleStatus.COMPLETED, ScheduleStatusCalculator.status(today, true, true, null, today))
        assertEquals(ScheduleStatus.PAID, ScheduleStatusCalculator.status(today.addingDays(-3), false, true, null, today))
        assertEquals(ScheduleStatus.DUE, ScheduleStatusCalculator.status(today, false, false, null, today))
        assertEquals(ScheduleStatus.UPCOMING, ScheduleStatusCalculator.status(today.addingDays(7), false, false, null, today))
        assertEquals(ScheduleStatus.MISSED, ScheduleStatusCalculator.status(today.addingDays(-1), false, false, null, today))
        assertEquals(ScheduleStatus.SCHEDULED, ScheduleStatusCalculator.status(today.addingDays(8), false, false, null, today))
    }

    @Test fun upcomingWindowSemanticsMatchIos() {
        assertEquals(25, ScheduleUpcomingLength.days("currentMonth", today))
        assertEquals(30, ScheduleUpcomingLength.days("oneMonth", today))
        assertEquals(14, ScheduleUpcomingLength.days("2-week", today))
        assertEquals(7, ScheduleUpcomingLength.days("garbage", today))
    }

    /** #937: a schedule's own length wins, then the budget-wide preference, then 7 days. */
    @Test fun effectiveUpcomingLengthFallsBackToTheBudgetPreference() {
        assertEquals("oneMonth", ScheduleUpcomingLength.effective("oneMonth", "2-week"))
        assertEquals("2-week", ScheduleUpcomingLength.effective(null, "2-week"))
        assertEquals("2-week", ScheduleUpcomingLength.effective("", "2-week"))
        assertEquals(null, ScheduleUpcomingLength.effective(null, null))

        val tenDaysOut = today.addingDays(10)
        assertEquals(
            ScheduleStatus.SCHEDULED,
            ScheduleStatusCalculator.status(tenDaysOut, false, false, ScheduleUpcomingLength.effective(null, null), today),
        )
        assertEquals(
            ScheduleStatus.UPCOMING,
            ScheduleStatusCalculator.status(tenDaysOut, false, false, ScheduleUpcomingLength.effective(null, "2-week"), today),
        )
    }

    @Test fun calendarMathClampsMonthsAndIgnoresDst() {
        assertEquals(DayDate(2025, 2, 28), DayDate(2025, 1, 31).addingMonths(1))
        assertEquals(2, DayDate(2026, 3, 7).daysUntil(DayDate(2026, 3, 9)))
        assertEquals(1, DayDate(2026, 9, 6).weekday)
    }

    @Test fun occurrenceLookbackMatchesIos() {
        assertEquals(today, ScheduleStatusCalculator.occurrenceMatchStartDate(today, "is", false))
        assertEquals(today, ScheduleStatusCalculator.occurrenceMatchStartDate(today, "isapprox", true))
        assertEquals(today.addingDays(-2), ScheduleStatusCalculator.occurrenceMatchStartDate(today, "isapprox", false))
    }

    @Test fun scheduleDisplayOrderUsesManualOrderThenDateThenStableTitle() {
        fun item(id: String, name: String, date: DayDate?, order: Double?) = ScheduleListItem(
            ActualScheduleSummary(id, name, null, date, null, null, null, null, null,
                ScheduleAmountOp.APPROXIMATE, null, null, false, false, null, order,
                false, null, null, null),
            ScheduleStatus.SCHEDULED, null, null,
        )
        val values = listOf(
            item("no-date", "Zeta", null, null),
            item("later", "Later", today.addingDays(2), null),
            item("manual", "Manual", today.addingDays(20), 1.0),
            item("alpha-b", "Alpha", today, null),
            item("alpha-a", "Alpha", today, null),
        )

        assertEquals(listOf("manual", "alpha-a", "alpha-b", "later", "no-date"),
            values.sortedForDisplay().map { it.schedule.id })
    }

    /** #1296: a posted occurrence takes the shown date only while it still counts as paying the stored one. */
    @Test fun postedDateFollowsTheShownDateWhileTheOccurrenceStaysPaid() {
        val stored = DayDate(2030, 1, 25)
        val shown = DayDate(2030, 1, 24)
        // An approximate date matches from two days before the stored date, so Thursday is still paid.
        assertEquals(shown, ScheduleStatusCalculator.postedDate(stored, shown, "isapprox", false))
        assertEquals(shown, ScheduleStatusCalculator.postedDate(stored, shown, null, false))
        assertEquals(DayDate(2030, 1, 23), ScheduleStatusCalculator.postedDate(stored, DayDate(2030, 1, 23), "isapprox", false))
        // Three days early is outside the window, so the stored date is kept.
        assertEquals(stored, ScheduleStatusCalculator.postedDate(stored, DayDate(2030, 1, 22), "isapprox", false))
    }

    @Test fun postedDateKeepsTheStoredDateForExactAndAutoPostingSchedules() {
        val stored = DayDate(2030, 1, 25)
        val shown = DayDate(2030, 1, 24)
        assertEquals(stored, ScheduleStatusCalculator.postedDate(stored, shown, "is", false))
        assertEquals(stored, ScheduleStatusCalculator.postedDate(stored, shown, "isapprox", true))
        // A shown date on or after the stored date is inside the window for them too.
        assertEquals(DayDate(2030, 1, 26), ScheduleStatusCalculator.postedDate(stored, DayDate(2030, 1, 26), "is", false))
    }

    @Test fun postedDateWithoutAShownDateIsTheStoredDate() {
        val stored = DayDate(2030, 1, 25)
        assertEquals(stored, ScheduleStatusCalculator.postedDate(stored, null, "isapprox", false))
    }
}
