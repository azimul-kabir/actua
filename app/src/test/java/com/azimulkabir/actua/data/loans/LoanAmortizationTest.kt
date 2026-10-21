package com.azimulkabir.actua.data.loans

import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.model.LoanConfig
import com.azimulkabir.actua.model.LoanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanAmortizationTest {
    private val start = DayDate(2022, 12, 1)

    /** The loan from YNAB's Loan Payoff Simulator: $22,000 owed at 6% APR. */
    private fun carLoan(payment: Long, escrow: Long = 0, extras: Map<DayDate, Long> = emptyMap()) =
        LoanAmortization.schedule(2_200_000, 6.0, payment, start, escrow, extras)

    @Test
    fun monthlyInterestIsTheAnnualRateOverTwelve() {
        assertEquals(11_000L, LoanAmortization.monthlyInterest(2_200_000, 6.0))
        assertEquals(0L, LoanAmortization.monthlyInterest(2_200_000, 0.0))
        assertEquals(0L, LoanAmortization.monthlyInterest(0, 6.0))
        assertEquals(0L, LoanAmortization.monthlyInterest(-5_000, 6.0))
        // 100.01 at 1% -> 0.0833 cents... rounds to the cent: 10001 * 1 / 1200 = 8.33 -> 8
        assertEquals(8L, LoanAmortization.monthlyInterest(10_001, 1.0))
    }

    @Test
    fun matchesActualiIosAndYnabSimulatorFigures() {
        val schedule = carLoan(36_500)!!
        // Same numbers as Actuali's LoanAmortizationTests; YNAB reports $4,245.66 of interest.
        assertEquals(72, schedule.paymentCount)
        assertEquals(424_573L, schedule.totalInterest)
        assertTrue(kotlin.math.abs(schedule.totalInterest - 424_566) <= 10)
        assertEquals(DayDate(2028, 11, 1), schedule.payoffDate)
    }

    @Test
    fun finalPaymentClearsTheBalanceExactly() {
        val last = carLoan(36_500)!!.entries.last()
        assertEquals(0L, last.balance)
        assertEquals(33_073L, last.payment)
        assertTrue(last.payment < 36_500)
    }

    @Test
    fun principalPlusInterestAccountsForEveryPaymentAndBalanceFallsToZero() {
        val schedule = carLoan(36_500, escrow = 20_000)!!
        schedule.entries.forEach { assertEquals(it.payment, it.principal + it.interest + it.escrow) }
        assertEquals(2_200_000L, schedule.entries.sumOf { it.principal })
        assertTrue(schedule.entries.first().interest > schedule.entries.last().interest)
    }

    @Test
    fun paymentBelowInterestNeverClears() {
        assertNull(carLoan(11_000))
        assertNull(carLoan(5_000))
        // Escrow alone can swallow a payment that would otherwise clear.
        assertNull(carLoan(36_500, escrow = 36_500))
    }

    @Test
    fun zeroInterestLoanDividesEvenly() {
        val schedule = LoanAmortization.schedule(100_000, 0.0, 25_000, start)!!
        assertEquals(4, schedule.paymentCount)
        assertEquals(0L, schedule.totalInterest)
        assertEquals(DayDate(2023, 3, 1), schedule.payoffDate)
    }

    @Test
    fun clearBalanceHasAnEmptySchedule() {
        val schedule = LoanAmortization.schedule(0, 6.0, 100, start)!!
        assertTrue(schedule.entries.isEmpty())
        assertNull(schedule.payoffDate)
        assertEquals(0L, schedule.totalInterest)
    }

    @Test
    fun extraPaymentsShortenTheLoanAndSaveInterest() {
        val minimum = carLoan(36_500)!!
        val withExtra = carLoan(36_500, extras = mapOf(DayDate(2023, 1, 1) to 500_000L))!!
        assertTrue(withExtra.paymentCount < minimum.paymentCount)
        val savings = LoanAmortization.savings(minimum, withExtra)
        assertEquals(minimum.paymentCount - withExtra.paymentCount, savings.months)
        assertEquals(minimum.totalInterest - withExtra.totalInterest, savings.interestCents)
        assertTrue(savings.interestCents > 0)
        // The extra lands in January regardless of which day of the month the key carries.
        val sameMonth = carLoan(36_500, extras = mapOf(DayDate(2023, 1, 20) to 500_000L))!!
        assertEquals(withExtra, sameMonth)
        assertEquals(0L, LoanAmortization.savings(minimum, minimum).interestCents)
    }

    @Test
    fun requiredPaymentIsTheSmallestThatMeetsTheTerm() {
        val payment = LoanAmortization.requiredPayment(2_200_000, 6.0, 72, start)!!
        assertTrue(carLoan(payment)!!.paymentCount <= 72)
        assertTrue(carLoan(payment - 1)!!.paymentCount > 72)
        assertEquals(2_200_000L + 11_000L, LoanAmortization.requiredPayment(2_200_000, 6.0, 1, start))
    }

    @Test
    fun requiredPaymentRejectsOutOfRangeTerms() {
        assertNull(LoanAmortization.requiredPayment(2_200_000, 6.0, 0, start))
        assertNull(LoanAmortization.requiredPayment(2_200_000, 6.0, 601, start))
        assertEquals(0L, LoanAmortization.requiredPayment(0, 6.0, 12, start))
        assertNotNull(LoanAmortization.requiredPayment(2_200_000, 6.0, 600, start))
    }

    @Test
    fun configProjectionUsesTheOwedBalanceAndEscrow() {
        val config = LoanConfig(2_200_000, 6.0, 36_500, escrowOrFeesCents = 10_000)
        val schedule = config.schedule(-2_200_000, start)!!
        assertEquals(10_000L, schedule.entries.first().escrow)
        assertEquals(2_200_000L, schedule.entries.sumOf { it.principal })
        // An overpaid (positive) balance owes nothing.
        assertTrue(config.schedule(5_000, start)!!.entries.isEmpty())
    }

    @Test
    fun statusPayoffMonthIsNullWhenPaidOffOrNeverClears() {
        val status = { balance: Long, payment: Long ->
            LoanStatus("a", "Car", balance, LoanConfig(2_200_000, 6.0, payment), closed = false)
        }
        assertEquals(DayDate(2028, 11, 1), status(-2_200_000, 36_500).payoffMonth(start))
        assertNull(status(0, 36_500).payoffMonth(start))
        assertNull(status(-2_200_000, 5_000).payoffMonth(start))
    }
}
