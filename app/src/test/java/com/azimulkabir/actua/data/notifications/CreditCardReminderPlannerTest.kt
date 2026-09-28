package com.azimulkabir.actua.data.notifications

import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.model.CreditCardConfig
import com.azimulkabir.actua.model.CreditCardCycle
import com.azimulkabir.actua.model.CreditCardStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CreditCardReminderPlannerTest {
    private val utc = ZoneId.of("UTC")
    private fun card(balance: Long = -7_500, closed: Boolean = false) = CreditCardStatus(
        "card1", "Visa", balance, CreditCardConfig(statementDay = 15), 0, null, closed,
    )

    @Test fun `unpaid card gets future reminders at 9am`() {
        val now = ZonedDateTime.of(2026, 2, 20, 8, 0, 0, 0, utc)
        val reminders = CreditCardReminderPlanner.plan(listOf(card()), now)

        assertEquals(listOf(7, 5, 3, 1), reminders.map { it.offsetDays })
        assertTrue(reminders.all { it.triggerAt.hour == 9 && it.triggerAt.minute == 0 })
        assertEquals(listOf(23, 25, 27, 1), reminders.map { it.triggerAt.dayOfMonth })
    }

    @Test fun `past reminder dates are omitted`() {
        val now = ZonedDateTime.of(2026, 2, 26, 10, 0, 0, 0, utc)
        assertEquals(listOf(3, 1), CreditCardReminderPlanner.plan(listOf(card()), now).map { it.offsetDays })
    }

    @Test fun `paid and closed cards get no reminders`() {
        val now = ZonedDateTime.of(2026, 2, 20, 8, 0, 0, 0, utc)
        assertTrue(CreditCardReminderPlanner.plan(listOf(card(0), card(closed = true)), now).isEmpty())
    }

    private fun due(dueDate: DayDate, remaining: Long, statement: Long = 25_000L) =
        CreditCardCycle.StatementDue(statement, statement - remaining, remaining, dueDate)

    private fun cardWithDues(vararg dues: CreditCardCycle.StatementDue) = CreditCardStatus(
        "card1", "Visa", -30_000, CreditCardConfig(statementDay = 15, dueOffsetDays = 45), 0, null, false,
        dues.toList(),
    )

    @Test fun `paid statement gets no reminders despite new spending`() {
        val now = ZonedDateTime.of(2026, 3, 20, 8, 0, 0, 0, utc)
        val card = cardWithDues(due(DayDate(2026, 4, 1), remaining = 0), due(DayDate(2026, 4, 29), remaining = 0, statement = 0))

        assertTrue(CreditCardReminderPlanner.plan(listOf(card), now).isEmpty())
        assertNull(CreditCardReminderPlanner.reminderDue(card, DayDate(2026, 3, 20)))
    }

    @Test fun `reminders target the first unpaid statement`() {
        val now = ZonedDateTime.of(2026, 3, 20, 8, 0, 0, 0, utc)
        val unpaid = due(DayDate(2026, 4, 29), remaining = 20_000)
        val card = cardWithDues(due(DayDate(2026, 4, 1), remaining = 0), unpaid)

        val reminders = CreditCardReminderPlanner.plan(listOf(card), now)
        assertEquals(listOf(7, 5, 3, 1), reminders.map { it.offsetDays })
        assertTrue(reminders.all { it.dueDate == DayDate(2026, 4, 29) })
        assertEquals(unpaid, CreditCardReminderPlanner.reminderDue(card, DayDate(2026, 3, 20))?.statementDue)
    }

    @Test fun `body names the statement due, else the live balance`() {
        val format = { cents: Long -> "$" + cents }
        assertEquals("Statement due $20000. Payment due 29 Apr.",
            CreditCardReminderPlanner.body(due(DayDate(2026, 4, 29), remaining = 20_000), -30_000, "29 Apr", format))
        assertEquals("Current balance $30000. Payment due 29 Apr.",
            CreditCardReminderPlanner.body(null, -30_000, "29 Apr", format))
    }
}
