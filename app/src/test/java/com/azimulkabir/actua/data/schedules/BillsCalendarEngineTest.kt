package com.azimulkabir.actua.data.schedules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillsCalendarEngineTest {
    private val today = DayDate(2026, 9, 10)

    @Test fun calendarUsesMondayFirstAndIncludesEveryDay() {
        assertEquals(1, BillsCalendarEngine.leadingEmptyDays(2026, 9))
        assertEquals(DayDate(2026, 9, 1), BillsCalendarEngine.daysInMonth(2026, 9).first())
        assertEquals(DayDate(2026, 9, 30), BillsCalendarEngine.daysInMonth(2026, 9).last())
    }

    @Test fun recurringSchedulesProjectAllMonthlyOccurrencesAndPayments() {
        val schedule = schedule(
            nextDate = DayDate(2026, 9, 5),
            condition = ScheduleDateCondition.Recurring(
                RecurConfig(
                    RecurConfig.Frequency.MONTHLY,
                    1,
                    DayDate(2026, 9, 5),
                    patterns = listOf(RecurConfig.Pattern("day", 5), RecurConfig.Pattern("day", 20)),
                ),
            ),
            status = ScheduleStatus.PAID,
        )

        val items = BillsCalendarEngine.itemsForSchedules(
            listOf(schedule),
            mapOf("schedule" to setOf(DayDate(2026, 9, 4))),
            mapOf("category" to "Subscriptions"),
            2026,
            9,
            today,
        )

        assertEquals(listOf(5, 20), items.map { it.date.day })
        assertEquals(listOf(ScheduleStatus.PAID, ScheduleStatus.UPCOMING), items.map(BillCalendarItem::status))
        assertTrue(items.all(BillCalendarItem::isRecurring))
        assertEquals("Subscriptions", items.first().categoryName)
    }

    @Test fun summaryAndFiltersSeparateUpcomingOverdueAndPaid() {
        val items = listOf(
            item("future", 20, -1_000, ScheduleStatus.UPCOMING),
            item("late", 2, -2_000, ScheduleStatus.MISSED),
            item("paid", 5, -3_000, ScheduleStatus.PAID),
        )

        val summary = BillsCalendarEngine.summarize(items)

        assertEquals(1_000, summary.upcomingCents)
        assertEquals(2_000, summary.overdueCents)
        assertEquals(3_000, summary.paidCents)
        assertEquals(1, summary.clearedCount)
        assertEquals(listOf("late"), BillsCalendarEngine.filter(items, BillFilter.OVERDUE, null).map { it.id })
        assertEquals(listOf("future"),
            BillsCalendarEngine.filter(items, BillFilter.ALL, DayDate(2026, 9, 20)).map { it.id })
    }

    private fun schedule(
        nextDate: DayDate,
        condition: ScheduleDateCondition,
        status: ScheduleStatus,
    ) = ScheduleListItem(
        ActualScheduleSummary(
            "schedule", "Rent", null, nextDate, null, null, "account", null,
            ScheduledAmount.Fixed(-125_000), ScheduleAmountOp.APPROXIMATE, "isapprox", condition,
            false, false, null, null, false, null, null, "category",
        ),
        status,
        "Checking",
        null,
    )

    private fun item(id: String, day: Int, amount: Long, status: ScheduleStatus) = BillCalendarItem(
        id,
        DayDate(2026, 9, day),
        id,
        amount,
        null,
        null,
        status,
    )
}
