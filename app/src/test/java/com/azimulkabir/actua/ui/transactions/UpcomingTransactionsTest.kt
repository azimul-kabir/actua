package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.RecurConfig
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingTransactionsTest {
    private val date = DayDate(2026, 10, 5)

    private fun item(
        id: String, status: ScheduleStatus, nextDate: DayDate? = date,
        dateCondition: ScheduleDateCondition? = null, customUpcomingLength: String? = null,
        accountName: String? = "Checking",
    ) = ScheduleListItem(
        ActualScheduleSummary(id, id, null, nextDate, null, null, "account", null, null,
            ScheduleAmountOp.APPROXIMATE, null, dateCondition, false, false, customUpcomingLength, null,
            false, null, null, null),
        status, accountName, null,
    )

    private fun weekly(start: DayDate) =
        ScheduleDateCondition.Recurring(RecurConfig(RecurConfig.Frequency.WEEKLY, 1, start))

    @Test fun projectsDueUpcomingAndMissedButNotPaidCompletedOrScheduled() {
        val rows = upcomingTransactionsFrom(ScheduleStatus.entries.map { item(it.name, it) }, today = date)

        // A paid one-off schedule has no occurrence left after its paid next date.
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
        ), today = date).associateBy { it.scheduleId }

        assertTrue(rows.getValue("late").isMissed)
        assertEquals("Missed", rows.getValue("late").category)
        assertEquals("20260901", rows.getValue("late").date)
        assertFalse(rows.getValue("soon").isMissed)
        assertEquals("Upcoming", rows.getValue("soon").category)
        assertFalse(rows.getValue("today").isMissed)
    }

    @Test fun skipsSchedulesWithoutANextDate() {
        assertEquals(
            emptyList<String>(),
            upcomingTransactionsFrom(listOf(item("x", ScheduleStatus.MISSED, null)), today = date).map { it.id },
        )
    }

    @Test fun recurringScheduleListsEveryOccurrenceInItsUpcomingWindow() {
        val rows = upcomingTransactionsFrom(listOf(item("w", ScheduleStatus.DUE, dateCondition = weekly(date))), today = date)

        // The default window is 7 days: 10-05 through 10-12.
        assertEquals(listOf("20261012", "20261005"), rows.map { it.date })
        assertEquals(listOf("upcoming-w-20261012", "upcoming-w-20261005"), rows.map { it.id })
    }

    @Test fun customUpcomingLengthOverridesTheDefaultWindow() {
        val custom = item("w", ScheduleStatus.DUE, dateCondition = weekly(date), customUpcomingLength = "2-week")
        assertEquals(
            listOf("20261019", "20261012", "20261005"),
            upcomingTransactionsFrom(listOf(custom), today = date).map { it.date },
        )
        val global = item("w", ScheduleStatus.DUE, dateCondition = weekly(date))
        assertEquals(
            listOf("20261019", "20261012", "20261005"),
            upcomingTransactionsFrom(listOf(global), today = date, defaultUpcomingLength = "2-week").map { it.date },
        )
        // A schedule's own length wins over the budget-wide one.
        val both = item("w", ScheduleStatus.DUE, dateCondition = weekly(date), customUpcomingLength = "7")
        assertEquals(
            listOf("20261012", "20261005"),
            upcomingTransactionsFrom(listOf(both), today = date, defaultUpcomingLength = "2-week").map { it.date },
        )
    }

    @Test fun paidRecurringScheduleLeavesOutItsPaidNextDate() {
        val rows = upcomingTransactionsFrom(listOf(item("w", ScheduleStatus.PAID, dateCondition = weekly(date))), today = date)

        assertEquals(listOf("20261012"), rows.map { it.date })
        assertFalse(rows.single().isMissed)
    }

    @Test fun onlyTheMissedNextDateOfARecurringScheduleIsMarkedMissed() {
        val last = DayDate(2026, 9, 28)
        val rows = upcomingTransactionsFrom(listOf(item("w", ScheduleStatus.MISSED, last, weekly(last))), today = date)

        assertEquals(listOf("20261012", "20261005", "20260928"), rows.map { it.date })
        assertEquals(listOf(false, false, true), rows.map { it.isMissed })
    }

    @Test fun includesSchedulesWithoutAnAccount() {
        val row = upcomingTransactionsFrom(listOf(item("x", ScheduleStatus.UPCOMING, accountName = null)), today = date).single()

        assertEquals("", row.account)
        assertEquals("x", row.scheduleId)
    }

    @Test fun sortsNewestFirst() {
        val rows = upcomingTransactionsFrom(listOf(
            item("a", ScheduleStatus.DUE), item("b", ScheduleStatus.UPCOMING, DayDate(2026, 10, 7)),
        ), today = date)

        assertEquals(listOf("b", "a"), rows.map { it.scheduleId })
    }
}
