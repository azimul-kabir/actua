package com.azimulkabir.actua.data.schedules

import com.azimulkabir.actua.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePrefillTest {
    private fun tx(amountCents: Long, transfer: String? = null, upcoming: Boolean = false) = Transaction(
        id = "t1", date = "2026-11-05", payee = "Landlord", category = "Rent", account = "Checking",
        amount = (amountCents / 100).toInt(), cleared = true, amountCents = amountCents,
        transferAccount = transfer, isUpcoming = upcoming,
    )

    @Test
    fun copiesPayeeAccountAmountAndDate() {
        val prefill = SchedulePrefill.from(tx(-123_456))!!
        assertEquals("Landlord", prefill.payeeName)
        assertEquals("Checking", prefill.accountName)
        assertEquals(-123_456L, prefill.amountCents)
        assertEquals(DayDate(2026, 11, 5), prefill.startDate)
    }

    @Test
    fun keepsIncomePositive() {
        assertEquals(250_000L, SchedulePrefill.from(tx(250_000))!!.amountCents)
    }

    @Test
    fun transfersAndUpcomingRowsAreNotAvailable() {
        assertFalse(SchedulePrefill.isAvailable(tx(-5_000, transfer = "Savings")))
        assertNull(SchedulePrefill.from(tx(-5_000, upcoming = true)))
        assertNull(SchedulePrefill.from(tx(0)))
        assertTrue(SchedulePrefill.isAvailable(tx(-5_000)))
    }
}
