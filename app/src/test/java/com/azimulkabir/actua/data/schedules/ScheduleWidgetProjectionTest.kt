package com.azimulkabir.actua.data.schedules

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleWidgetProjectionTest {
    private val today = DayDate(2026, 9, 5)

    private fun item(
        id: String,
        date: DayDate?,
        completed: Boolean = false,
        hasTransaction: Boolean = false,
    ) = ScheduleListItem(
        ActualScheduleSummary(id, id, null, date, null, null, null, null, null,
            ScheduleAmountOp.APPROXIMATE, null, null, false, completed, null, null,
            false, null, null, null),
        ScheduleStatusCalculator.status(date, completed, hasTransaction, null, today),
        null, null,
    )

    @Test fun excludesCompletedPaidAndDatelessSchedules() {
        val values = listOf(
            item("completed", today, completed = true),
            item("paid", today, hasTransaction = true),
            item("no-date", null),
            item("due", today),
        )

        assertEquals(listOf("due"), ScheduleWidgetProjection.upcoming(values, today, 7).map { it.item.schedule.id })
    }

    @Test fun excludesEntriesBeyondTheConfiguredPeriod() {
        val values = listOf(item("in-window", today.addingDays(7)), item("out-of-window", today.addingDays(8)))

        assertEquals(listOf("in-window"), ScheduleWidgetProjection.upcoming(values, today, 7).map { it.item.schedule.id })
    }

    @Test fun includesOverdueRegardlessOfHowFarPast() {
        val values = listOf(item("very-overdue", today.addingDays(-30)))

        assertEquals(listOf("very-overdue"), ScheduleWidgetProjection.upcoming(values, today, 7).map { it.item.schedule.id })
    }

    @Test fun sortsOverdueFirstThenNearestDueDate() {
        val values = listOf(
            item("future", today.addingDays(5)),
            item("today", today),
            item("overdue", today.addingDays(-2)),
        )

        assertEquals(listOf("overdue", "today", "future"),
            ScheduleWidgetProjection.upcoming(values, today, 7).map { it.item.schedule.id })
    }

    @Test fun flagsOverdueEntries() {
        val values = listOf(item("overdue", today.addingDays(-1)), item("due", today), item("future", today.addingDays(1)))

        val byId = ScheduleWidgetProjection.upcoming(values, today, 7).associateBy { it.item.schedule.id }
        assertEquals(true, byId["overdue"]?.overdue)
        assertEquals(false, byId["due"]?.overdue)
        assertEquals(false, byId["future"]?.overdue)
    }

    @Test fun relativeLabelsMatchExpectedWording() {
        assertEquals("Today", ScheduleWidgetProjection.relativeDueLabel(today, today))
        assertEquals("Tomorrow", ScheduleWidgetProjection.relativeDueLabel(today, today.addingDays(1)))
        assertEquals("In 5 days", ScheduleWidgetProjection.relativeDueLabel(today, today.addingDays(5)))
        assertEquals("1 day overdue", ScheduleWidgetProjection.relativeDueLabel(today, today.addingDays(-1)))
        assertEquals("3 days overdue", ScheduleWidgetProjection.relativeDueLabel(today, today.addingDays(-3)))
    }
}
