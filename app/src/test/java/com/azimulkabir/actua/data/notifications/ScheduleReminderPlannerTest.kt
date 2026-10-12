package com.azimulkabir.actua.data.notifications

import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.RecurConfig
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val today = DayDate(2026, 10, 13)
    private val morning = ZonedDateTime.of(2026, 10, 13, 8, 0, 0, 0, zone)

    private fun item(
        id: String,
        nextDate: DayDate?,
        status: ScheduleStatus = ScheduleStatus.UPCOMING,
        recurring: Boolean = false,
    ) = ScheduleListItem(
        ActualScheduleSummary(
            id, "Rent $id", null, nextDate, null, null, "account", null, ScheduledAmount.Fixed(-120_000),
            ScheduleAmountOp.EXACT, null,
            if (recurring) ScheduleDateCondition.Recurring(RecurConfig(RecurConfig.Frequency.MONTHLY, 1, today)) else null,
            false, status == ScheduleStatus.COMPLETED, null, null, false, null, null, null,
        ),
        status, "Checking", null,
    )

    @Test fun plansTheLeadTimeAndDueDateRemindersAtNine() {
        val reminders = ScheduleReminderPlanner.plan(listOf(item("a", DayDate(2026, 10, 20))), leadDays = 3, now = morning)

        assertEquals(listOf(3, 0), reminders.map { it.offsetDays })
        assertEquals(ZonedDateTime.of(2026, 10, 17, 9, 0, 0, 0, zone), reminders[0].triggerAt)
        assertEquals(ZonedDateTime.of(2026, 10, 20, 9, 0, 0, 0, zone), reminders[1].triggerAt)
        assertTrue(reminders.all { it.dueDate == DayDate(2026, 10, 20) })
    }

    @Test fun sameDayLeadTimePlansOneReminder() {
        val reminders = ScheduleReminderPlanner.plan(listOf(item("a", DayDate(2026, 10, 20))), leadDays = 0, now = morning)

        assertEquals(listOf(0), reminders.map { it.offsetDays })
    }

    @Test fun skipsRemindersWhoseTimeHasPassed() {
        val afternoon = morning.withHour(15)
        // Due tomorrow with a 3-day lead: the lead reminder was two days ago; only the due-date one remains.
        val tomorrow = ScheduleReminderPlanner.plan(listOf(item("a", DayDate(2026, 10, 14))), 3, afternoon)
        assertEquals(listOf(0), tomorrow.map { it.offsetDays })
        // Due today after 9 AM, or already missed: nothing left to plan.
        assertTrue(ScheduleReminderPlanner.plan(listOf(item("a", today, ScheduleStatus.DUE)), 1, afternoon).isEmpty())
        assertTrue(ScheduleReminderPlanner.plan(listOf(item("a", DayDate(2026, 10, 1), ScheduleStatus.MISSED)), 1, morning).isEmpty())
    }

    @Test fun paidCompletedAndUndatedSchedulesDoNotRemind() {
        val schedules = listOf(
            item("paid", DayDate(2026, 10, 20), ScheduleStatus.PAID),
            item("done", DayDate(2026, 10, 20), ScheduleStatus.COMPLETED),
            item("undated", null, ScheduleStatus.SCHEDULED),
        )

        assertTrue(ScheduleReminderPlanner.plan(schedules, 1, morning).isEmpty())
    }

    @Test fun workNamesIdentifyEachOccurrenceAndOffset() {
        val reminders = ScheduleReminderPlanner.plan(listOf(item("a", DayDate(2026, 10, 20))), 1, morning)
        val again = ScheduleReminderPlanner.plan(listOf(item("a", DayDate(2026, 10, 20))), 1, morning.plusHours(1))

        assertEquals(reminders.map { it.workName }, again.map { it.workName })
        assertEquals(2, reminders.map { it.workName }.toSet().size)
    }

    @Test fun postingOrSkippingARecurringScheduleMovesRemindersToTheNewNextDate() {
        val before = ScheduleReminderPlanner.plan(listOf(item("a", DayDate(2026, 10, 20), recurring = true)), 1, morning)
        // Actual advances next_date when the occurrence is posted or skipped.
        val after = ScheduleReminderPlanner.plan(listOf(item("a", DayDate(2026, 11, 20), recurring = true)), 1, morning)

        assertTrue(after.all { it.dueDate == DayDate(2026, 11, 20) })
        assertTrue(before.map { it.workName }.intersect(after.map { it.workName }.toSet()).isEmpty())
    }

    @Test fun stillDueRechecksTheOccurrenceWhenTheReminderFires() {
        val due = DayDate(2026, 10, 14)
        val pending = item("a", due)

        assertTrue(ScheduleReminderPlanner.stillDue(pending, due, offset = 1, leadDays = 1, today = today))
        // Matched to a transaction, posted/skipped to a new date, or deleted since planning.
        assertFalse(ScheduleReminderPlanner.stillDue(item("a", due, ScheduleStatus.PAID), due, 1, 1, today))
        assertFalse(ScheduleReminderPlanner.stillDue(item("a", DayDate(2026, 11, 14)), due, 1, 1, today))
        assertFalse(ScheduleReminderPlanner.stillDue(null, due, 1, 1, today))
        // Lead time changed since planning, or the reminder fires on the wrong day.
        assertFalse(ScheduleReminderPlanner.stillDue(pending, due, offset = 1, leadDays = 3, today = today))
        assertFalse(ScheduleReminderPlanner.stillDue(pending, due, offset = 0, leadDays = 1, today = today))
    }

    @Test fun settledOccurrencesAreWithdrawn() {
        val posted = setOf(
            ScheduleReminderPlanner.occurrenceKey("pending", DayDate(2026, 10, 14)),
            ScheduleReminderPlanner.occurrenceKey("paid", DayDate(2026, 10, 14)),
            ScheduleReminderPlanner.occurrenceKey("advanced", DayDate(2026, 10, 14)),
            ScheduleReminderPlanner.occurrenceKey("deleted", DayDate(2026, 10, 14)),
        )
        val schedules = listOf(
            item("pending", DayDate(2026, 10, 14)),
            item("paid", DayDate(2026, 10, 14), ScheduleStatus.PAID),
            item("advanced", DayDate(2026, 11, 14), recurring = true),
        )

        assertEquals(
            setOf("paid", "advanced", "deleted"),
            ScheduleReminderPlanner.settled(posted, schedules).map(ScheduleReminderPlanner::scheduleIdOf).toSet(),
        )
    }

    @Test fun postingReplacesTheSchedulesEarlierOccurrence() {
        val posted = setOf(
            ScheduleReminderPlanner.occurrenceKey("a", DayDate(2026, 9, 14)),
            ScheduleReminderPlanner.occurrenceKey("b", DayDate(2026, 10, 14)),
        )

        assertEquals(
            setOf(
                ScheduleReminderPlanner.occurrenceKey("a", DayDate(2026, 10, 14)),
                ScheduleReminderPlanner.occurrenceKey("b", DayDate(2026, 10, 14)),
            ),
            ScheduleReminderPlanner.afterPosting(posted, "a", DayDate(2026, 10, 14)),
        )
    }

    @Test fun scheduleIdsContainingTheSeparatorRoundTrip() {
        val key = ScheduleReminderPlanner.occurrenceKey("odd|id", today)
        assertEquals("odd|id", ScheduleReminderPlanner.scheduleIdOf(key))
    }

    @Test fun titleAndBodyDescribeTheOccurrence() {
        assertEquals("Rent is due today", ScheduleReminderPlanner.title("Rent", today, today))
        assertEquals("Rent is due tomorrow", ScheduleReminderPlanner.title("Rent", today.addingDays(1), today))
        assertEquals("Rent is due in 7 days", ScheduleReminderPlanner.title("Rent", today.addingDays(7), today))
        assertEquals("−1,200.00 · Checking · Due 10/20/2026",
            ScheduleReminderPlanner.body("−1,200.00", "Checking", "10/20/2026"))
        // Hidden balances drop the amount; a schedule without an account drops that too.
        assertEquals("Due 10/20/2026", ScheduleReminderPlanner.body(null, null, "10/20/2026"))
    }

    @Test fun tappedReminderOpensTheScheduleOnlyWhileItExists() {
        val schedules = listOf(item("a", today))

        assertEquals("a", ScheduleReminderLaunch.scheduleId(schedules, "a"))
        assertNull(ScheduleReminderLaunch.scheduleId(schedules, "deleted"))
        assertNull(ScheduleReminderLaunch.scheduleId(schedules, null))
    }
}
