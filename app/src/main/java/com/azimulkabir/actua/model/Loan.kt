package com.azimulkabir.actua.model

import com.azimulkabir.actua.data.loans.schedule
import com.azimulkabir.actua.data.schedules.DayDate

/**
 * Synced loan terms, stored in Actual's `preferences` table under `actuali:loan:<accountId>`
 * with the same JSON field names as Actuali iOS `LoanConfig`. All amounts are integer cents.
 */
data class LoanConfig(
    /** What was owed when the loan was added, always positive; payoff progress is measured against it. */
    val originalBalanceCents: Long,
    /** Nominal annual rate as a percentage: 5.25 means 5.25% APR. */
    val annualRatePercent: Double,
    val minimumPaymentCents: Long,
    /** Escrow or fees bundled into the monthly payment. */
    val escrowOrFeesCents: Long? = null,
    /** Budget category the loan's payments are assigned to, if any. */
    val categoryId: String? = null,
    /** Month (`YYYY-MM`) the payment target is skipped for, if any. */
    val targetSnoozedMonth: String? = null,
    /** Original loan term in months; optional and unread by Actuali iOS, which ignores unknown keys. */
    val termMonths: Int? = null,
    /** Day of the month the payment is due (1..31). */
    val paymentDay: Int? = null,
    /** When the loan started, as an Actual YYYYMMDD day integer. */
    val startDate: Int? = null,
) {
    init {
        require(termMonths == null || termMonths in 1..MAX_TERM_MONTHS) { "Term must be 1..$MAX_TERM_MONTHS months" }
        require(paymentDay == null || paymentDay in 1..31) { "Payment day must be 1..31" }
        require(startDate == null || DayDate.fromYyyymmdd(startDate) != null) { "Start date must be a valid YYYYMMDD day" }
        require(originalBalanceCents > 0) { "Original balance must be positive" }
        require(minimumPaymentCents > 0) { "Monthly payment must be positive" }
        require(annualRatePercent.isFinite() && annualRatePercent >= 0) { "Interest rate must not be negative" }
    }

    /** Principal repaid so far, clamped between nothing and the original balance. */
    fun paidOffCents(accountBalanceCents: Long): Long =
        (originalBalanceCents - owedCents(accountBalanceCents)).coerceIn(0L, originalBalanceCents)

    /**
     * Next due date for [paymentDay] on or after [today], clamped to short months; null without a
     * payment day. A paid-off loan has no next payment either way, which callers decide.
     */
    fun nextPaymentDate(today: DayDate = DayDate.today()): DayDate? {
        val day = paymentDay ?: return null
        fun inMonth(month: DayDate) = DayDate(month.year, month.month, minOf(day, DayDate.lastDay(month.year, month.month)))
        val thisMonth = inMonth(today)
        return if (thisMonth >= today) thisMonth else inMonth(today.addingMonths(1).let { DayDate(it.year, it.month, 1) })
    }

    /** Share of the original balance repaid in tenths of a percent (0..1000), rounded down. */
    fun paidOffPermille(accountBalanceCents: Long): Int =
        (paidOffCents(accountBalanceCents) * 1000 / originalBalanceCents).toInt()

    companion object {
        /** Matches the 600-month (50-year) bound Actuali's amortization engine uses. */
        const val MAX_TERM_MONTHS = 600

        /** What is still owed, positive: Actual holds a loan balance negative while money is owed. */
        fun owedCents(accountBalanceCents: Long): Long = maxOf(0L, -accountBalanceCents)
    }
}

/** How one payment divides between the lender's charges and the balance. */
data class LoanPaymentSplit(val interestCents: Long, val escrowCents: Long, val principalCents: Long)

/**
 * Split for a payment of [paymentCents] against [accountBalanceCents]. Unlike the schedule, this
 * charges interest and escrow in full whatever was paid, as a lender bills them: underpaying moves
 * the shortfall into a negative principal (the loan grew) instead of shrinking the charge.
 */
fun LoanConfig.paymentSplit(accountBalanceCents: Long, paymentCents: Long): LoanPaymentSplit {
    val interest = com.azimulkabir.actua.data.loans.LoanAmortization.monthlyInterest(
        LoanConfig.owedCents(accountBalanceCents), annualRatePercent,
    )
    val escrow = escrowOrFeesCents ?: 0L
    return LoanPaymentSplit(interest, escrow, paymentCents - interest - escrow)
}

/** What clearing the loan outright this month takes: the balance plus its interest and escrow. */
fun LoanConfig.payoffAmountCents(accountBalanceCents: Long): Long {
    val owed = LoanConfig.owedCents(accountBalanceCents)
    if (owed == 0L) return 0
    return owed + com.azimulkabir.actua.data.loans.LoanAmortization.monthlyInterest(owed, annualRatePercent) +
        (escrowOrFeesCents ?: 0L)
}

/** The amount to offer: the monthly payment, except on the last one where it would overshoot. */
fun LoanConfig.suggestedPaymentCents(accountBalanceCents: Long): Long =
    minOf(minimumPaymentCents, payoffAmountCents(accountBalanceCents))

data class LoanStatus(
    val accountId: String,
    val accountName: String,
    val balanceCents: Long,
    val config: LoanConfig,
    val closed: Boolean,
) {
    val owedCents: Long get() = LoanConfig.owedCents(balanceCents)
    val paidOffCents: Long get() = config.paidOffCents(balanceCents)
    val paidOffPermille: Int get() = config.paidOffPermille(balanceCents)
    val isPaidOff: Boolean get() = owedCents == 0L

    /** Month the loan is projected to clear, or null when paid off or the payment never clears it. */
    fun payoffMonth(from: DayDate = projectionStart()): DayDate? =
        if (isPaidOff) null else config.schedule(balanceCents, from)?.payoffDate

    /** First projected payment month: the next payment day when set, otherwise the current month. */
    fun projectionStart(today: DayDate = DayDate.today()): DayDate = config.nextPaymentDate(today) ?: today
}

/** Open, off-budget accounts not yet tracked as loans: debt-type accounts first, then by name. */
fun loanCandidates(accounts: List<Account>, configuredIds: Set<String>): List<Account> =
    accounts.filter { !it.closed && it.offBudget && it.id !in configuredIds }.sortedWith(compareBy(
        { when (it.type.lowercase(java.util.Locale.ROOT)) { "mortgage", "debt" -> 0; "credit" -> 2; else -> 1 } },
        { it.name.lowercase(java.util.Locale.ROOT) },
        { it.id },
    ))

/** Parses a typed amount to cents with HALF_UP rounding; null when blank, malformed or not positive. */
fun parsePositiveCents(text: String, decimalPlaces: Int): Long? = runCatching {
    java.math.BigDecimal(text.trim().replace(',', '.')).movePointRight(decimalPlaces)
        .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact().takeIf { it > 0 }
}.getOrNull()

/** Parses a typed annual rate percentage; null when malformed, negative or not finite. */
fun parseRatePercent(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

/** Parses a typed amount to cents (HALF_UP); blank means zero. Null when malformed or negative. */
fun parseNonNegativeCents(text: String, decimalPlaces: Int): Long? =
    if (text.isBlank()) 0L else runCatching {
        java.math.BigDecimal(text.trim().replace(',', '.')).movePointRight(decimalPlaces)
            .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact().takeIf { it >= 0 }
    }.getOrNull()
