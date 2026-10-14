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

    @Test
    fun `notification capture requires explicit allowed app`() {
        assertTrue(shouldCaptureNotification(true, setOf("bank"), "bank", "actua"))
        assertTrue(!shouldCaptureNotification(true, emptySet(), "bank", "actua"))
        assertTrue(!shouldCaptureNotification(false, setOf("bank"), "bank", "actua"))
        assertTrue(!shouldCaptureNotification(true, setOf("actua"), "actua", "actua"))
    }

    private fun payee(text: String) = FinancialMessageParser.parse(text, "Bank app").candidates.single().payee

    @Test
    fun `money out names the merchant, not where it was paid from`() {
        assertEquals("Starbucks", payee("Paid $5.00 to Starbucks from Wallet"))
        assertEquals("Tesco", payee("You spent $12.40 at Tesco from Current Account"))
        assertEquals("Coffee House", payee("Card ending 4321 charged USD 3.50 at Coffee House"))
    }

    @Test
    fun `money in names the sender, not the receiving account`() {
        assertEquals("Alex", payee("Received $50.00 from Alex to Savings"))
        assertEquals("Employer", payee("USD 900.00 deposited to your account from Employer"))
    }

    @Test
    fun `funding sources are skipped even when they come first`() {
        assertEquals("Grocer", payee("Paid $8.00 from your wallet to Grocer"))
        assertEquals("Landlord", payee("Debited $700.00 from account XX5678 to Landlord"))
    }

    @Test
    fun `a single phrase is used whatever its direction`() {
        assertEquals("Refund Desk", payee("Refund credited $4.00 at Refund Desk"))
        assertEquals("Gym", payee("Paid $30.00 from Gym"))
    }

    @Test
    fun `at inside a word is not a phrase`() {
        // "Combat" used to read as "at Sports purchase $9".
        assertEquals("Bank app", payee("Combat Sports purchase $9.99"))
    }

    @Test
    fun `only funding phrases fall back to the source`() {
        assertEquals("Bank app", payee("Paid $5.00 from Wallet"))
    }

    @Test
    fun `a card named Credit doesn't make a purchase income`() {
        val candidate = FinancialMessageParser.parse("Fold Credit: you spent $10.69 at Cafe", "Fold").candidates.single()
        assertEquals(-1_069L, candidate.amountCents)
        assertEquals("Cafe", candidate.payee)
        assertEquals(-500L, FinancialMessageParser.parse("Your credit card was charged $5.00 at Shop", "Bank app")
            .candidates.single().amountCents)
    }

    @Test
    fun `an alert whose only direction wording is the card name is not captured`() {
        val result = FinancialMessageParser.parse("Fold Credit $10.69 Starbucks", "Fold")
        assertTrue(result.candidates.isEmpty())
        assertTrue(FinancialMessageParser.parse("New credit card statement: $10.00", "Bank app").candidates.isEmpty())
    }

    @Test
    fun `credits are still recognized`() {
        assertEquals(2_500L, FinancialMessageParser.parse("$25.00 credited to your account", "Bank app")
            .candidates.single().amountCents)
        assertEquals(500L, FinancialMessageParser.parse("Refund of $5.00 received from Shop", "Bank app")
            .candidates.single().amountCents)
        assertEquals(1_000L, FinancialMessageParser.parse("Payment of $10.00 received on your credit card", "Bank app")
            .candidates.single().amountCents)
    }

    @Test
    fun `keywords match whole words only`() {
        val profile = FinancialMessageProfile(setOf("paid"), setOf("credit"))
        assertTrue(FinancialMessageParser.parse("Discredited sender $5.00", "Bank app", profile = profile).candidates.isEmpty())
    }
}
