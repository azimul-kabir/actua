package com.azimulkabir.actua.data.loans

import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.model.LoanConfig
import com.azimulkabir.actua.model.LoanPaymentFrequency

/**
 * Periodic amortization for loan accounts, ported from Actuali iOS `LoanAmortization` (which models
 * YNAB's loan accounts): interest is the annual rate / periods per year on the current balance, rounded
 * to the cent each period, and each payment covers interest and any escrow before reducing principal.
 * All amounts are integer cents. The result is a projection, not a lender's own schedule.
 */
object LoanAmortization {
    /** Longest projection run in months; bounds the walk so a payment that never clears the loan returns null. */
    const val MAX_MONTHS = 600

    data class Entry(
        val month: DayDate,
        /** Regular payment plus any one-off extra, or only what is left to clear on the final month. */
        val payment: Long,
        val interest: Long,
        val escrow: Long,
        /** Part of [payment] that reduced the balance; negative when it did not cover interest and escrow. */
        val principal: Long,
        /** Still owed after this month's payment. */
        val balance: Long,
    )

    data class Schedule(val entries: List<Entry>) {
        /** Date or month the final payment lands, or null for a balance already clear. */
        val payoffDate: DayDate? get() = entries.lastOrNull()?.month
        val paymentCount: Int get() = entries.size
        val totalInterest: Long get() = entries.sumOf { it.interest }
    }

    /** What paying more than the minimum buys. [months] is the duration saved in calendar months. */
    data class Savings(val interestCents: Long, val months: Int, val periods: Int = months)

    /** Interest for one period on [balanceCents]: rate / periods per year x balance, HALF_UP to the cent. */
    fun periodicInterest(
        balanceCents: Long,
        annualRatePercent: Double,
        frequency: LoanPaymentFrequency = LoanPaymentFrequency.MONTHLY,
    ): Long {
        if (balanceCents <= 0 || annualRatePercent <= 0) return 0
        val divisor = 100.0 * frequency.periodsPerYear
        return Math.round(balanceCents.toDouble() * annualRatePercent / divisor)
    }

    /** Interest for one month on [balanceCents]: rate / 12 x balance, HALF_UP to the cent. */
    fun monthlyInterest(balanceCents: Long, annualRatePercent: Double): Long =
        periodicInterest(balanceCents, annualRatePercent, LoanPaymentFrequency.MONTHLY)

    /** Advances [current] to the next payment date according to [frequency]. */
    internal fun advanceDate(
        current: DayDate,
        frequency: LoanPaymentFrequency,
        secondPaymentDay: Int? = null,
    ): DayDate = when (frequency) {
        LoanPaymentFrequency.MONTHLY -> current.addingMonths(1)
        LoanPaymentFrequency.BIWEEKLY -> current.addingDays(14)
        LoanPaymentFrequency.WEEKLY -> current.addingDays(7)
        LoanPaymentFrequency.SEMI_MONTHLY -> {
            val d1 = current.day
            val d2 = secondPaymentDay ?: minOf(d1 + 14, 28)
            val firstDay = minOf(d1, d2)
            val secondDay = maxOf(d1, d2)
            val last = DayDate.lastDay(current.year, current.month)
            val cand1 = DayDate(current.year, current.month, minOf(firstDay, last))
            val cand2 = DayDate(current.year, current.month, minOf(secondDay, last))
            when {
                current < cand1 -> cand1
                current < cand2 -> cand2
                else -> {
                    val nextMonth = current.addingMonths(1)
                    val nextLast = DayDate.lastDay(nextMonth.year, nextMonth.month)
                    DayDate(nextMonth.year, nextMonth.month, minOf(firstDay, nextLast))
                }
            }
        }
    }

    /**
     * Projects the loan to payoff, or null when [paymentCents] cannot clear it within 50 years
     * (what a payment below the periodic interest looks like). [extraPayments] are one-off amounts.
     */
    fun schedule(
        balanceCents: Long,
        annualRatePercent: Double,
        paymentCents: Long,
        startingMonth: DayDate,
        escrowOrFeesCents: Long = 0,
        extraPayments: Map<DayDate, Long> = emptyMap(),
        frequency: LoanPaymentFrequency = LoanPaymentFrequency.MONTHLY,
        secondPaymentDay: Int? = null,
    ): Schedule? {
        if (balanceCents <= 0) return Schedule(emptyList())
        val extrasByMonth = extraPayments.entries.groupingBy { it.key.year * 100 + it.key.month }
            .fold(0L) { total, entry -> total + entry.value }
        var remaining = balanceCents
        var date = startingMonth
        val entries = ArrayList<Entry>()
        val maxPeriods = 50 * frequency.periodsPerYear
        repeat(maxPeriods) {
            val interest = periodicInterest(remaining, annualRatePercent, frequency)
            val extra = if (frequency == LoanPaymentFrequency.MONTHLY) {
                extrasByMonth[date.year * 100 + date.month] ?: 0L
            } else {
                extraPayments[date] ?: if (entries.isEmpty()) (extrasByMonth[date.year * 100 + date.month] ?: 0L) else 0L
            }
            val due = paymentCents + extra
            // Capping at what is left makes the final period a part payment that clears the loan.
            val paid = minOf(due, remaining + interest + escrowOrFeesCents)
            val principal = paid - interest - escrowOrFeesCents
            remaining -= principal
            entries += Entry(date, paid, interest, escrowOrFeesCents, principal, remaining)
            if (remaining <= 0) return Schedule(entries)
            date = advanceDate(date, frequency, secondPaymentDay)
        }
        return null
    }

    /**
     * Smallest periodic payment that clears [balanceCents] within [periods], or null when out of range.
     */
    fun requiredPayment(
        balanceCents: Long,
        annualRatePercent: Double,
        periods: Int,
        startingMonth: DayDate,
        escrowOrFeesCents: Long = 0,
        frequency: LoanPaymentFrequency = LoanPaymentFrequency.MONTHLY,
        secondPaymentDay: Int? = null,
    ): Long? {
        if (balanceCents <= 0) return 0
        val maxPeriods = 50 * frequency.periodsPerYear
        if (periods <= 0 || periods > maxPeriods) return null
        var low = 1L
        // Clearing balance, first period's interest and escrow at once fits any term of one period or more.
        var high = balanceCents + periodicInterest(balanceCents, annualRatePercent, frequency) + escrowOrFeesCents
        while (low < high) {
            val mid = low + (high - low) / 2
            val clears = schedule(
                balanceCents, annualRatePercent, mid, startingMonth, escrowOrFeesCents,
                frequency = frequency, secondPaymentDay = secondPaymentDay,
            )?.let { it.paymentCount <= periods } ?: false
            if (clears) high = mid else low = mid + 1
        }
        return low
    }

    /** Interest and time saved by following [target] instead of [minimum]. */
    fun savings(
        minimum: Schedule,
        target: Schedule,
        frequency: LoanPaymentFrequency = LoanPaymentFrequency.MONTHLY,
    ): Savings {
        val periods = maxOf(0, minimum.paymentCount - target.paymentCount)
        val months = (periods * 12 + frequency.periodsPerYear - 1) / frequency.periodsPerYear
        return Savings(
            interestCents = maxOf(0L, minimum.totalInterest - target.totalInterest),
            months = months,
            periods = periods,
        )
    }
}

/** Projection for this loan against [accountBalanceCents] at its payment; null when it never clears. */
fun LoanConfig.schedule(
    accountBalanceCents: Long,
    from: DayDate = DayDate.today(),
    extraPayments: Map<DayDate, Long> = emptyMap(),
): LoanAmortization.Schedule? = LoanAmortization.schedule(
    LoanConfig.owedCents(accountBalanceCents), annualRatePercent, minimumPaymentCents, from,
    escrowOrFeesCents ?: 0, extraPayments,
    frequency = paymentFrequency,
    secondPaymentDay = secondPaymentDay,
)
