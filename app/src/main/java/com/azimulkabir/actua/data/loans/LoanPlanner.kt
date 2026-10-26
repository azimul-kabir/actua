package com.azimulkabir.actua.data.loans

import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.model.LoanConfig
import com.azimulkabir.actua.model.LoanPaymentFrequency

/**
 * "What if" payoff projections for a loan, ported from Actuali iOS `LoanPayoffPlannerView`: the
 * chosen payment (and an optional one-off extra in the first period) against the required
 * minimum. Nothing here is saved; it only shows what would happen.
 */
object LoanPlanner {
    data class Plan(
        val owedCents: Long,
        val minimum: LoanAmortization.Schedule?,
        /** Null when the chosen payment can't clear the loan (it doesn't cover the periodic interest). */
        val chosen: LoanAmortization.Schedule?,
        val isAtMinimum: Boolean,
        val frequency: LoanPaymentFrequency = LoanPaymentFrequency.MONTHLY,
    ) {
        val interestRemainingCents: Long? get() = chosen?.totalInterest
        val totalRemainingCents: Long? get() = chosen?.let { owedCents + it.totalInterest }

        /** What the chosen plan saves over the minimum; null when it saves nothing worth reporting. */
        val savings: LoanAmortization.Savings?
            get() {
                val min = minimum ?: return null
                val target = chosen ?: return null
                if (isAtMinimum) return null
                return LoanAmortization.savings(min, target, frequency).takeIf { it.interestCents > 0 || it.months > 0 }
            }
    }

    /** Both schedules start at [start]; [extraCents] is a one-off paid on top of the payment in that period. */
    fun plan(
        config: LoanConfig,
        accountBalanceCents: Long,
        paymentCents: Long,
        extraCents: Long,
        start: DayDate,
    ): Plan {
        val owed = LoanConfig.owedCents(accountBalanceCents)
        fun schedule(payment: Long, extra: Long) = LoanAmortization.schedule(
            owed, config.annualRatePercent, payment, start, config.escrowOrFeesCents ?: 0,
            if (extra > 0) mapOf(start to extra) else emptyMap(),
            frequency = config.paymentFrequency,
            secondPaymentDay = config.secondPaymentDay,
        )
        return Plan(
            owedCents = owed,
            minimum = schedule(config.minimumPaymentCents, 0),
            chosen = schedule(paymentCents, extraCents),
            isAtMinimum = paymentCents == config.minimumPaymentCents && extraCents == 0L,
            frequency = config.paymentFrequency,
        )
    }

    /** The periodic payment that clears the loan in [months] months, or null when out of range. */
    fun paymentForMonths(config: LoanConfig, accountBalanceCents: Long, months: Int, start: DayDate): Long? {
        val periods = (months * config.paymentFrequency.periodsPerYear + 6) / 12
        return LoanAmortization.requiredPayment(
            LoanConfig.owedCents(accountBalanceCents), config.annualRatePercent, periods, start,
            config.escrowOrFeesCents ?: 0,
            frequency = config.paymentFrequency,
            secondPaymentDay = config.secondPaymentDay,
        )
    }

    /** Balances for a burndown chart: the opening balance, then after each payment of this schedule. */
    fun balancePoints(schedule: LoanAmortization.Schedule, openingCents: Long): List<Long> =
        listOf(openingCents) + schedule.entries.map { it.balance }

    /** One chart period label per point: the period before [start] (opening), then each payment period. */
    fun periods(
        start: DayDate,
        points: Int,
        frequency: LoanPaymentFrequency = LoanPaymentFrequency.MONTHLY,
    ): List<String> = (0 until points).map { index ->
        when (frequency) {
            LoanPaymentFrequency.MONTHLY -> start.addingMonths(index - 1).iso
            LoanPaymentFrequency.BIWEEKLY -> start.addingDays(14 * (index - 1)).iso
            LoanPaymentFrequency.WEEKLY -> start.addingDays(7 * (index - 1)).iso
            LoanPaymentFrequency.SEMI_MONTHLY -> start.addingDays(15 * (index - 1)).iso
        }
    }

    /** Splits a month count into whole years and remaining months. */
    fun years(months: Int): Pair<Int, Int> = months / 12 to months % 12
}
