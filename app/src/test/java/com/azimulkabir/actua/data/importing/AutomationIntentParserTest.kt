package com.azimulkabir.actua.data.importing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationIntentParserTest {
    private val today = LocalDate.of(2026, 9, 30)

    private fun parse(vararg extras: Pair<String, String?>) = parseAutomationExtras(extras.toMap(), today)

    @Test
    fun `queues signed amount with all fields`() {
        val candidate = parse("amount" to "-12.34", "payee" to "Coffee", "date" to "2026-09-28",
            "notes" to "Latte", "reference" to "TX123", "account" to "4321", "source" to "Tasker: GPay")
            .candidates.single()
        assertEquals(-1_234L, candidate.amountCents)
        assertEquals(20260928, candidate.date)
        assertEquals("Coffee", candidate.payee)
        assertEquals("Latte", candidate.notes)
        assertEquals("TX123", candidate.reference)
        assertEquals("4321", candidate.accountHint)
        assertEquals("Tasker: GPay", candidate.sourceLabel)
    }

    @Test
    fun `type sets the sign of the amount`() {
        assertEquals(-1_234L, parse("amount" to "12.34", "type" to "debit").candidates.single().amountCents)
        assertEquals(-1_234L, parse("amount" to "-12.34", "type" to "Debit").candidates.single().amountCents)
        assertEquals(1_234L, parse("amount" to "-12.34", "type" to "credit").candidates.single().amountCents)
        assertTrue(parse("amount" to "12.34", "type" to "transfer").candidates.isEmpty())
    }

    @Test
    fun `amounts become exact integer cents`() {
        assertEquals(10L, parse("amount" to "0.10").candidates.single().amountCents)
        assertEquals(123_456L, parse("amount" to "1,234.56").candidates.single().amountCents)
        assertEquals(1_200L, parse("amount" to "12").candidates.single().amountCents)
        assertEquals(1_234L, parse("amount" to "12.340").candidates.single().amountCents)
        // Tasker may deliver a numeric extra as a Double, whose text can use an exponent.
        assertEquals(1_000_000_000L, parse("amount" to "1.0E7").candidates.single().amountCents)
    }

    @Test
    fun `rejects invalid amounts`() {
        listOf("12.345", "abc", "NaN", "1e20", "").forEach { amount ->
            assertTrue(amount, parse("amount" to amount).candidates.isEmpty())
        }
        assertTrue(parse("payee" to "Coffee").problems.isNotEmpty())
    }

    @Test
    fun `defaults date payee and source`() {
        val candidate = parse("amount" to "-5").candidates.single()
        assertEquals(20260930, candidate.date)
        assertEquals("Tasker", candidate.payee)
        assertEquals("Tasker", candidate.sourceLabel)
        assertNull(candidate.reference)
        assertNull(candidate.accountHint)
    }

    @Test
    fun `rejects invalid dates`() {
        assertTrue(parse("amount" to "-5", "date" to "30/09/2026").candidates.isEmpty())
        assertTrue(parse("amount" to "-5", "date" to "2026-02-30").candidates.isEmpty())
    }

    @Test
    fun `text falls back to the message parser`() {
        val candidate = parse("text" to "Your card 9876 was debited BDT 450.25 at Coffee House",
            "source" to "GPay").candidates.single()
        assertEquals(-45_025L, candidate.amountCents)
        assertEquals("Coffee House", candidate.payee)
        assertEquals("GPay", candidate.sourceLabel)
        assertEquals(20260930, candidate.date)
    }

    @Test
    fun `token must match while enabled`() {
        assertTrue(isAuthorizedAutomation(true, "abc123", "abc123"))
        assertTrue(isAuthorizedAutomation(true, "abc123", " abc123 "))
        assertFalse(isAuthorizedAutomation(false, "abc123", "abc123"))
        assertFalse(isAuthorizedAutomation(true, "abc123", "abc124"))
        assertFalse(isAuthorizedAutomation(true, "abc123", null))
        assertFalse(isAuthorizedAutomation(true, null, null))
        assertFalse(isAuthorizedAutomation(true, "", ""))
    }
}
