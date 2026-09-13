package com.azimulkabir.actua.data.importing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialMessageParserTest {
    @Test
    fun `parses debit alert without mistaking card digits for amount`() {
        val result = FinancialMessageParser.parse(
            "Your card ****9876 was debited BDT 450.25 at Coffee House on 13/09/2026. Trx ID ABC12345",
            "Bank app", LocalDate.of(2026, 9, 14),
        )
        val candidate = result.candidates.single()
        assertEquals(-45_025L, candidate.amountCents)
        assertEquals(20260913, candidate.date)
        assertEquals("Coffee House", candidate.payee)
        assertEquals("9876", candidate.accountHint)
        assertEquals(ImportConfidence.HIGH, candidate.confidence)
        assertTrue("9876" !in candidate.notes)
    }

    @Test
    fun `parses credit using today when alert omits date`() {
        val candidate = FinancialMessageParser.parse("BDT 1,200.00 credited to account 1234 from Employer",
            today = LocalDate.of(2026, 9, 13)).candidates.single()
        assertEquals(120_000L, candidate.amountCents)
        assertEquals(20260913, candidate.date)
        assertEquals("Employer", candidate.payee)
    }

    @Test
    fun `non financial messages fail without candidates`() {
        val result = FinancialMessageParser.parse("Your verification code is 123456")
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.problems.isNotEmpty())
    }

    @Test
    fun `custom bank wording is supported by parser profile`() {
        val profile = FinancialMessageProfile(setOf("used"), setOf("loaded"))
        val candidate = FinancialMessageParser.parse("Card used BDT 50.00 at Shop", profile = profile)
            .candidates.single()
        assertEquals(-5_000L, candidate.amountCents)
    }
}
