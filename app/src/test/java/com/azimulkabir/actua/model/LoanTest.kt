package com.azimulkabir.actua.model

import com.azimulkabir.actua.data.schedules.DayDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanTest {
    private val config = LoanConfig(originalBalanceCents = 1_000_000, annualRatePercent = 5.0, minimumPaymentCents = 20_000)

    @Test
    fun progressIsMeasuredAgainstTheOriginalBalance() {
        assertEquals(0L, config.paidOffCents(-1_000_000))
        assertEquals(250_000L, config.paidOffCents(-750_000))
        assertEquals(250, config.paidOffPermille(-750_000))
        assertEquals(1_000_000L, config.paidOffCents(0))
        assertEquals(1000, config.paidOffPermille(0))
    }

    @Test
    fun progressIsClampedWhenBalanceGrewOrWasOverpaid() {
        assertEquals(0L, config.paidOffCents(-1_200_000))
        assertEquals(1_000_000L, config.paidOffCents(50_000))
        assertEquals(0L, LoanConfig.owedCents(50_000))
        assertEquals(333, LoanConfig(300, 0.0, 1).paidOffPermille(-200))
    }

    @Test
    fun statusReportsPaidOffOnlyWhenNothingIsOwed() {
        fun status(balance: Long) = LoanStatus("a", "Car", balance, config, closed = false)
        assertFalse(status(-1).isPaidOff)
        assertTrue(status(0).isPaidOff)
        assertEquals(1_000L, status(-1_000).owedCents)
    }

    @Test
    fun configRejectsUnusableTerms() {
        assertThrows(IllegalArgumentException::class.java) { LoanConfig(0, 5.0, 100) }
        assertThrows(IllegalArgumentException::class.java) { LoanConfig(100, 5.0, 0) }
        assertThrows(IllegalArgumentException::class.java) { LoanConfig(100, -0.1, 100) }
        assertThrows(IllegalArgumentException::class.java) { LoanConfig(100, Double.NaN, 100) }
        assertThrows(IllegalArgumentException::class.java) { LoanConfig(100, 1.0, 100, termMonths = 0) }
        assertThrows(IllegalArgumentException::class.java) { LoanConfig(100, 1.0, 100, termMonths = 601) }
        assertThrows(IllegalArgumentException::class.java) { LoanConfig(100, 1.0, 100, paymentDay = 32) }
        assertThrows(IllegalArgumentException::class.java) { LoanConfig(100, 1.0, 100, startDate = 20261340) }
        LoanConfig(100, 1.0, 100, termMonths = 600, paymentDay = 31, startDate = 20240229)
    }

    @Test
    fun amountsParseToIntegerCentsWithoutFloatingPoint() {
        assertEquals(12_345L, parsePositiveCents("123.45", 2))
        assertEquals(12_345L, parsePositiveCents("123,45", 2))
        assertEquals(1L, parsePositiveCents("0.005", 2))
        assertEquals(12_300L, parsePositiveCents("123", 2))
        assertEquals(123L, parsePositiveCents("123", 0))
        assertNull(parsePositiveCents("", 2))
        assertNull(parsePositiveCents("0", 2))
        assertNull(parsePositiveCents("-5", 2))
        assertNull(parsePositiveCents("1.2.3", 2))
    }

    @Test
    fun ratesParseAndRejectInvalidInput() {
        assertEquals(5.25, parseRatePercent("5.25")!!, 0.0)
        assertEquals(5.25, parseRatePercent("5,25")!!, 0.0)
        assertEquals(0.0, parseRatePercent("0")!!, 0.0)
        assertNull(parseRatePercent("-1"))
        assertNull(parseRatePercent("abc"))
        assertNull(parseRatePercent("NaN"))
    }

    private fun account(id: String, type: String, offBudget: Boolean = true, closed: Boolean = false) =
        Account(name = id, balance = 0, type = type, offBudget = offBudget, closed = closed, id = id)

    @Test
    fun candidatesAreOpenOffBudgetUntrackedDebtFirst() {
        val result = loanCandidates(listOf(
            account("savings", "savings"), account("visa", "credit"), account("mortgage", "mortgage"),
            account("on-budget", "debt", offBudget = false), account("closed", "debt", closed = true),
            account("tracked", "debt"), account("auto", "debt"),
        ), configuredIds = setOf("tracked"))
        assertEquals(listOf("auto", "mortgage", "savings", "visa"), result.map { it.id })
    }

    @Test
    fun nextPaymentDateIsThePaymentDayOnOrAfterToday() {
        val loan = LoanConfig(1_000, 5.0, 100, paymentDay = 15)
        assertEquals(DayDate(2026, 10, 15), loan.nextPaymentDate(DayDate(2026, 10, 10)))
        assertEquals(DayDate(2026, 10, 15), loan.nextPaymentDate(DayDate(2026, 10, 15)))
        assertEquals(DayDate(2026, 11, 15), loan.nextPaymentDate(DayDate(2026, 10, 16)))
        assertEquals(DayDate(2027, 1, 15), loan.nextPaymentDate(DayDate(2026, 12, 20)))
        assertNull(LoanConfig(1_000, 5.0, 100).nextPaymentDate(DayDate(2026, 10, 10)))
    }

    @Test
    fun nextPaymentDateClampsToShortMonths() {
        val loan = LoanConfig(1_000, 5.0, 100, paymentDay = 31)
        assertEquals(DayDate(2026, 11, 30), loan.nextPaymentDate(DayDate(2026, 11, 1)))
        assertEquals(DayDate(2027, 2, 28), loan.nextPaymentDate(DayDate(2027, 1, 31).addingDays(1)))
        assertEquals(DayDate(2028, 2, 29), loan.nextPaymentDate(DayDate(2028, 1, 31).addingDays(1)))
        // On the 30th of a 30-day month the clamped due day is today.
        assertEquals(DayDate(2026, 11, 30), loan.nextPaymentDate(DayDate(2026, 11, 30)))
    }

    @Test
    fun paymentSplitChargesInterestAndEscrowInFull() {
        val loan = LoanConfig(2_200_000, 6.0, 36_500, escrowOrFeesCents = 10_000)
        val split = loan.paymentSplit(-2_200_000, 36_500)
        assertEquals(LoanPaymentSplit(11_000, 10_000, 15_500), split)
        // Underpaying grows the loan: the shortfall shows as negative principal, charges are not shrunk.
        assertEquals(LoanPaymentSplit(11_000, 10_000, -6_000), loan.paymentSplit(-2_200_000, 15_000))
        assertEquals(LoanPaymentSplit(0, 10_000, 5_000), loan.paymentSplit(0, 15_000))
    }

    @Test
    fun suggestedPaymentIsCappedAtThePayoffAmount() {
        val loan = LoanConfig(2_200_000, 6.0, 36_500, escrowOrFeesCents = 10_000)
        assertEquals(36_500L, loan.suggestedPaymentCents(-2_200_000))
        // 20,000.00 owed: payoff = 20,000.00 + 100.00 interest + 100.00 escrow.
        assertEquals(2_020_000L, loan.payoffAmountCents(-2_000_000))
        // Last payment: 200.00 owed + 1.00 interest + 100.00 escrow = 301.00, below the 365.00 minimum.
        assertEquals(30_100L, loan.suggestedPaymentCents(-20_000))
        assertEquals(0L, loan.payoffAmountCents(0))
        assertEquals(0L, loan.payoffAmountCents(5_000))
    }

    @Test
    fun nonNegativeCentsAllowBlankAndZeroButNotNegatives() {
        assertEquals(0L, parseNonNegativeCents("", 2))
        assertEquals(0L, parseNonNegativeCents("0", 2))
        assertEquals(1_050L, parseNonNegativeCents("10,5", 2))
        assertNull(parseNonNegativeCents("-1", 2))
        assertNull(parseNonNegativeCents("x", 2))
    }

    @Test
    fun targetSnoozeIsASingleMonthThatExpiresOnItsOwn() {
        val snoozed = LoanConfig(1_000, 5.0, 100, targetSnoozedMonth = "2026-10")
        assertTrue(snoozed.targetIsSnoozed("2026-10"))
        assertFalse(snoozed.targetIsSnoozed("2026-11"))
        assertFalse(LoanConfig(1_000, 5.0, 100).targetIsSnoozed("2026-10"))
    }
}
