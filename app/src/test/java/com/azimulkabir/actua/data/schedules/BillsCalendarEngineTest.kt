package com.azimulkabir.actua.data.schedules

import com.azimulkabir.actua.model.CreditCardConfig
import com.azimulkabir.actua.model.CreditCardCycle
import com.azimulkabir.actua.model.CreditCardStatus
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

    @Test fun earlyTodayPostMarksOccurrencePaidAfterScheduleAdvances() {
        val schedule = schedule(
            nextDate = DayDate(2026, 10, 5),
            condition = ScheduleDateCondition.Recurring(
                RecurConfig(RecurConfig.Frequency.MONTHLY, 1, DayDate(2026, 1, 5)),
            ),
            status = ScheduleStatus.UPCOMING,
            postsTransaction = true,
        )

        val items = BillsCalendarEngine.itemsForSchedules(
            listOf(schedule),
            mapOf("schedule" to setOf(DayDate(2026, 9, 1))),
            emptyMap(),
            2026,
            9,
            today,
        )

        assertEquals(listOf(DayDate(2026, 9, 5)), items.map(BillCalendarItem::date))
        assertEquals(listOf(ScheduleStatus.PAID), items.map(BillCalendarItem::status))
        assertTrue(items.none(BillCalendarItem::isCurrentOccurrence))
    }

    @Test fun oldPaymentDoesNotMarkAdvancedOccurrencePaid() {
        val schedule = schedule(
            nextDate = DayDate(2026, 10, 5),
            condition = ScheduleDateCondition.Recurring(
                RecurConfig(RecurConfig.Frequency.MONTHLY, 1, DayDate(2026, 1, 5)),
            ),
            status = ScheduleStatus.UPCOMING,
            postsTransaction = true,
        )

        val items = BillsCalendarEngine.itemsForSchedules(
            listOf(schedule),
            mapOf("schedule" to setOf(DayDate(2026, 8, 5))),
            emptyMap(),
            2026,
            9,
            today,
        )

        assertEquals(listOf(ScheduleStatus.MISSED), items.map(BillCalendarItem::status))
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
        postsTransaction: Boolean = false,
    ) = ScheduleListItem(
        ActualScheduleSummary(
            "schedule", "Rent", null, nextDate, null, null, "account", null,
            ScheduledAmount.Fixed(-125_000), ScheduleAmountOp.APPROXIMATE, "isapprox", condition,
            postsTransaction, false, null, null, false, null, null, "category",
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

    private fun cardBill(
        dues: List<CreditCardCycle.StatementDue>?, today: DayDate, balance: Long = -5_000,
    ): BillCalendarItem {
        val card = CreditCardStatus("card1", "Visa", balance, CreditCardConfig(statementDay = 15, dueOffsetDays = 25),
            0, null, false, dues)
        return BillsCalendarEngine.itemsForCreditCards(listOf(card), 2026, 4, today).single()
    }

    private fun statement(remaining: Long, balance: Long = 25_000L) =
        CreditCardCycle.StatementDue(balance, balance - remaining, remaining, DayDate(2026, 4, 9))

    @Test fun creditCardBillUsesTheStatementDueInsteadOfTheLiveBalance() {
        val paid = cardBill(listOf(statement(remaining = 0)), DayDate(2026, 4, 5))
        assertEquals(DayDate(2026, 4, 9), paid.date)
        assertEquals(ScheduleStatus.PAID, paid.status)
        assertEquals(-25_000L, paid.amountCents)

        val zero = cardBill(listOf(statement(remaining = 0, balance = 0)), DayDate(2026, 4, 5))
        assertEquals(ScheduleStatus.PAID, zero.status)
        assertEquals(0L, zero.amountCents)

        val upcoming = cardBill(listOf(statement(remaining = 10_000)), DayDate(2026, 4, 5))
        assertEquals(ScheduleStatus.UPCOMING, upcoming.status)
        assertEquals(-10_000L, upcoming.amountCents)

        assertEquals(ScheduleStatus.MISSED, cardBill(listOf(statement(remaining = 10_000)), DayDate(2026, 4, 10)).status)
    }

    @Test fun creditCardBillFallsBackToTheLiveBalanceWithoutStatementData() {
        val bill = cardBill(null, DayDate(2026, 4, 5))
        assertEquals(ScheduleStatus.UPCOMING, bill.status)
        assertEquals(-5_000L, bill.amountCents)
        assertEquals(ScheduleStatus.PAID, cardBill(null, DayDate(2026, 4, 5), balance = 0).status)
    }
}
