package com.azimulkabir.actua.data.loans

import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.model.LoanConfig

/**
 * Monthly amortization for loan accounts, ported from Actuali iOS `LoanAmortization` (which models
 * YNAB's loan accounts): interest is the annual rate / 12 on the current balance, rounded to the
 * cent each month, and each payment covers interest and any escrow before reducing principal.
 * All amounts are integer cents. The result is a projection, not a lender's own schedule.
 */
object LoanAmortization {
    /** Longest projection run; bounds the walk so a payment that never clears the loan returns null. */
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
        /** Month the final payment lands, or null for a balance already clear. */
        val payoffDate: DayDate? get() = entries.lastOrNull()?.month
        val paymentCount: Int get() = entries.size
        val totalInterest: Long get() = entries.sumOf { it.interest }
    }

    /** What paying more than the minimum buys. */
    data class Savings(val interestCents: Long, val months: Int)

    /** Interest for one month on [balanceCents]: rate / 12 x balance, HALF_UP to the cent. */
    fun monthlyInterest(balanceCents: Long, annualRatePercent: Double): Long {
        if (balanceCents <= 0 || annualRatePercent <= 0) return 0
        // 1200 = 100 (the rate is a percentage) x 12 (months).
        return Math.round(balanceCents.toDouble() * annualRatePercent / 1200)
    }

    /**
     * Projects the loan to payoff, or null when [paymentCents] cannot clear it within [MAX_MONTHS]
     * (what a payment below the monthly interest looks like). [extraPayments] are one-off amounts
     * keyed by the (year, month) they land in, paid on top of the regular payment.
     */
    fun schedule(
        balanceCents: Long,
        annualRatePercent: Double,
        paymentCents: Long,
        startingMonth: DayDate,
        escrowOrFeesCents: Long = 0,
        extraPayments: Map<DayDate, Long> = emptyMap(),
    ): Schedule? {
        if (balanceCents <= 0) return Schedule(emptyList())
        val extras = extraPayments.entries.groupingBy { it.key.year * 100 + it.key.month }
            .fold(0L) { total, entry -> total + entry.value }
        var remaining = balanceCents
        var month = startingMonth
        val entries = ArrayList<Entry>()
        repeat(MAX_MONTHS) {
            val interest = monthlyInterest(remaining, annualRatePercent)
            val due = paymentCents + (extras[month.year * 100 + month.month] ?: 0L)
            // Capping at what is left makes the final month a part payment that clears the loan.
            val paid = minOf(due, remaining + interest + escrowOrFeesCents)
            val principal = paid - interest - escrowOrFeesCents
            remaining -= principal
            entries += Entry(month, paid, interest, escrowOrFeesCents, principal, remaining)
            if (remaining <= 0) return Schedule(entries)
            month = month.addingMonths(1)
        }
        return null
    }

    /**
     * Smallest monthly payment that clears [balanceCents] within [months], or null when the term is
     * out of range. Searched against [schedule] rather than the annuity formula, since monthly
     * rounding can make a closed form miss its own term by a cent; payoff speed only rises with
     * payment size, so a binary search is valid.
     */
    fun requiredPayment(
        balanceCents: Long,
        annualRatePercent: Double,
        months: Int,
        startingMonth: DayDate,
        escrowOrFeesCents: Long = 0,
    ): Long? {
        if (balanceCents <= 0) return 0
        if (months <= 0 || months > MAX_MONTHS) return null
        var low = 1L
        // Clearing balance, first month's interest and escrow at once fits any term of one month or more.
        var high = balanceCents + monthlyInterest(balanceCents, annualRatePercent) + escrowOrFeesCents
        while (low < high) {
            val mid = low + (high - low) / 2
            val clears = schedule(balanceCents, annualRatePercent, mid, startingMonth, escrowOrFeesCents)
                ?.let { it.paymentCount <= months } ?: false
            if (clears) high = mid else low = mid + 1
        }
        return low
    }

    /** Interest and time saved by following [target] instead of [minimum]. */
    fun savings(minimum: Schedule, target: Schedule) = Savings(
        interestCents = maxOf(0L, minimum.totalInterest - target.totalInterest),
        months = maxOf(0, minimum.paymentCount - target.paymentCount),
    )
}

/** Projection for this loan against [accountBalanceCents] at its monthly payment; null when it never clears. */
fun LoanConfig.schedule(
    accountBalanceCents: Long,
    from: DayDate = DayDate.today(),
    extraPayments: Map<DayDate, Long> = emptyMap(),
): LoanAmortization.Schedule? = LoanAmortization.schedule(
    LoanConfig.owedCents(accountBalanceCents), annualRatePercent, minimumPaymentCents, from,
    escrowOrFeesCents ?: 0, extraPayments,
)
