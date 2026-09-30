package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingTransactionsTest {
    private val date = DayDate(2026, 10, 5)

    private fun item(id: String, status: ScheduleStatus, nextDate: DayDate? = date) = ScheduleListItem(
        ActualScheduleSummary(id, id, null, nextDate, null, null, "account", null, null,
            ScheduleAmountOp.APPROXIMATE, null, null, false, false, null, null,
            false, null, null, null),
        status, "Checking", null,
    )

    @Test fun projectsDueUpcomingAndMissedButNotPaidCompletedOrScheduled() {
        val rows = upcomingTransactionsFrom(ScheduleStatus.entries.map { item(it.name, it) })

        assertEquals(
            listOf(ScheduleStatus.DUE, ScheduleStatus.UPCOMING, ScheduleStatus.MISSED).map { it.name },
            rows.map { it.scheduleId },
        )
        assertTrue(rows.all { it.isUpcoming && !it.cleared })
    }

    @Test fun missedRowsAreMarkedAndLabelledSeparatelyFromUpcomingOnes() {
        val rows = upcomingTransactionsFrom(listOf(
            item("late", ScheduleStatus.MISSED, DayDate(2026, 9, 1)),
            item("soon", ScheduleStatus.UPCOMING),
            item("today", ScheduleStatus.DUE),
        )).associateBy { it.scheduleId }

        assertTrue(rows.getValue("late").isMissed)
        assertEquals("Missed", rows.getValue("late").category)
        assertEquals("20260901", rows.getValue("late").date)
        assertFalse(rows.getValue("soon").isMissed)
        assertEquals("Upcoming", rows.getValue("soon").category)
        assertFalse(rows.getValue("today").isMissed)
    }

    @Test fun skipsSchedulesWithoutANextDate() {
        assertEquals(emptyList<String>(), upcomingTransactionsFrom(listOf(item("x", ScheduleStatus.MISSED, null))).map { it.id })
    }
}
