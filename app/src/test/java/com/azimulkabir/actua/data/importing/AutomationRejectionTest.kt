package com.azimulkabir.actua.data.importing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRejectionTest {
    private val today = LocalDate.of(2026, 9, 30)
    private val token = "0123456789abcdef"

    private fun handle(vararg extras: Pair<String, String?>, enabled: Boolean = true, expected: String? = token) =
        handleAutomationExtras(enabled, expected, extras.toMap(), today)

    @Test
    fun `a valid broadcast queues and records no rejection`() {
        val result = handle("token" to token, "amount" to "-12.34", "payee" to "Coffee")
        assertEquals(-1_234L, result.candidates.single().amountCents)
        assertNull(result.rejection)
    }

    @Test
    fun `extras without key names are reported as a missing token`() {
        val result = handle("amount" to "-12.34")
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.rejection!!.startsWith("No token extra"))
        assertTrue("key:value" in result.rejection!!)
    }

    @Test
    fun `a blank token counts as missing`() {
        assertTrue(handle("token" to "  ", "amount" to "1").rejection!!.startsWith("No token extra"))
    }

    @Test
    fun `a wrong token is reported without echoing it`() {
        val result = handle("token" to "guess-1234", "amount" to "-12.34", "payee" to "Coffee")
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.rejection!!.startsWith("Wrong token"))
        assertFalse("guess-1234" in result.rejection!!)
    }

    @Test
    fun `broadcasts while the integration is off are only reported as off`() {
        listOf(handle("token" to token, "amount" to "1", enabled = false), handle("amount" to "1", expected = null))
            .forEach {
                assertTrue(it.candidates.isEmpty())
                assertEquals("Received while Accept transactions from Tasker was off", it.rejection)
            }
    }

    @Test
    fun `invalid fields report the parser's reason without the value`() {
        val result = handle("token" to token, "amount" to "$25.00", "payee" to "Coffee")
        assertTrue(result.candidates.isEmpty())
        assertEquals("Amount must be a number with at most two decimals", result.rejection)
        assertFalse("Coffee" in result.rejection!!)
        assertEquals("Date must use YYYY-MM-DD", handle("token" to token, "amount" to "1", "date" to "30/09/2026").rejection)
        assertEquals("No amount or text was provided", handle("token" to token).rejection)
    }
}
