package com.azimulkabir.actua.model

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
}
