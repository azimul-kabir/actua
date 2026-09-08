package com.azimulkabir.actua.data.notifications

import com.azimulkabir.actua.model.CreditCardConfig
import com.azimulkabir.actua.model.CreditCardStatus
import org.junit.Assert.assertEquals
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
}
