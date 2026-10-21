package com.azimulkabir.actua.data.loans

import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.model.LoanConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanPlannerTest {
    private val start = DayDate(2022, 12, 1)

    /** The $22,000 / 6% APR loan from YNAB's simulator, with the $365 minimum. */
    private val loan = LoanConfig(2_200_000, 6.0, 36_500)
    private val balance = -2_200_000L

    @Test
    fun planAtTheMinimumSavesNothing() {
        val plan = LoanPlanner.plan(loan, balance, 36_500, 0, start)
        assertTrue(plan.isAtMinimum)
        assertNull(plan.savings)
        assertEquals(plan.minimum, plan.chosen)
        assertEquals(72, plan.chosen!!.paymentCount)
        assertEquals(2_200_000L, plan.owedCents)
        assertEquals(2_200_000L + plan.chosen!!.totalInterest, plan.totalRemainingCents)
        assertEquals(plan.chosen!!.totalInterest, plan.interestRemainingCents)
    }

    @Test
    fun higherPaymentShortensTheLoanAndReportsSavings() {
        val plan = LoanPlanner.plan(loan, balance, 50_000, 0, start)
        assertFalse(plan.isAtMinimum)
        val savings = plan.savings!!
        assertTrue(savings.months > 0 && savings.interestCents > 0)
        assertEquals(plan.minimum!!.paymentCount - plan.chosen!!.paymentCount, savings.months)
        assertEquals(plan.minimum!!.totalInterest - plan.chosen!!.totalInterest, savings.interestCents)
    }

    @Test
    fun oneOffExtraLandsInTheFirstMonthAndCountsAsASavingsPlan() {
        val plan = LoanPlanner.plan(loan, balance, 36_500, 500_000, start)
        assertFalse(plan.isAtMinimum)
        assertEquals(36_500L + 500_000, plan.chosen!!.entries.first().payment)
        assertEquals(36_500L, plan.chosen!!.entries[1].payment)
        assertNotNull(plan.savings)
    }

    @Test
    fun slowerThanMinimumReportsNoSavings() {
        val plan = LoanPlanner.plan(loan, balance, 20_000, 0, start)
        assertNull(plan.savings)
        assertTrue(plan.chosen!!.paymentCount > plan.minimum!!.paymentCount)
    }

    @Test
    fun paymentBelowInterestNeverClears() {
        val plan = LoanPlanner.plan(loan, balance, 10_000, 0, start)
        assertNull(plan.chosen)
        assertNull(plan.savings)
        assertNull(plan.totalRemainingCents)
        assertNotNull(plan.minimum)
    }

    @Test
    fun paymentForMonthsIsTheSmallestThatMeetsTheTerm() {
        val payment = LoanPlanner.paymentForMonths(loan, balance, 48, start)!!
        assertTrue(LoanPlanner.plan(loan, balance, payment, 0, start).chosen!!.paymentCount <= 48)
        assertTrue(LoanPlanner.plan(loan, balance, payment - 1, 0, start).chosen!!.paymentCount > 48)
        assertNull(LoanPlanner.paymentForMonths(loan, balance, 0, start))
        assertNull(LoanPlanner.paymentForMonths(loan, balance, 601, start))
    }

    @Test
    fun paymentForMonthsAccountsForEscrow() {
        val withEscrow = loan.copy(escrowOrFeesCents = 10_000)
        val without = LoanPlanner.paymentForMonths(loan, balance, 48, start)!!
        assertEquals(without + 10_000, LoanPlanner.paymentForMonths(withEscrow, balance, 48, start))
    }

    @Test
    fun clearedLoanHasAnEmptyPlan() {
        val plan = LoanPlanner.plan(loan, 5_000, 36_500, 0, start)
        assertEquals(0L, plan.owedCents)
        assertTrue(plan.chosen!!.entries.isEmpty())
        assertEquals(0L, plan.totalRemainingCents)
    }

    @Test
    fun balancePointsStartAtTheOpeningBalanceAndEndAtZero() {
        val schedule = LoanPlanner.plan(loan, balance, 36_500, 0, start).chosen!!
        val points = LoanPlanner.balancePoints(schedule, 2_200_000)
        assertEquals(schedule.paymentCount + 1, points.size)
        assertEquals(2_200_000L, points.first())
        assertEquals(0L, points.last())
        assertEquals(points.sortedDescending(), points)
    }

    @Test
    fun periodsStartTheMonthBeforeTheFirstPayment() {
        assertEquals(listOf("2022-11-01", "2022-12-01", "2023-01-01"), LoanPlanner.periods(start, 3))
    }

    @Test
    fun monthsSplitIntoYearsAndRemainder() {
        assertEquals(1 to 3, LoanPlanner.years(15))
        assertEquals(6 to 0, LoanPlanner.years(72))
        assertEquals(0 to 7, LoanPlanner.years(7))
    }
}
