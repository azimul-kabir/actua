package com.azimulkabir.actua.model

import com.azimulkabir.actua.data.schedules.DayDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CreditCardPriorityTest {
    private val today = DayDate(2026, 2, 20)

    private fun card(id: String, name: String, balance: Long, statementDay: Int) = CreditCardStatus(
        id, name, balance, CreditCardConfig(statementDay), 0, null, false,
    )

    @Test fun `unpaid cards sort by urgency before paid cards`() {
        val cards = listOf(
            card("paid-soon", "Paid Soon", 0, 15),
            card("unpaid-later", "Unpaid Later", -5_000, 25),
            card("unpaid-soon", "Unpaid Soon", -10_000, 15),
            card("paid-later", "Paid Later", 500, 25),
        )

        assertEquals(
            listOf("Unpaid Soon", "Unpaid Later", "Paid Soon", "Paid Later"),
            cards.sortedForPaymentPriority(today).map { it.accountName },
        )
    }

    @Test fun `name and id make due-date ties stable`() {
        val cards = listOf(
            card("z", "Zeta", -100, 15),
            card("b", "Alpha", -100, 15),
            card("a", "Alpha", -100, 15),
        )
        assertEquals(listOf("a", "b", "z"), cards.sortedForPaymentPriority(today).map { it.accountId })
    }
}
