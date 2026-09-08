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
}
