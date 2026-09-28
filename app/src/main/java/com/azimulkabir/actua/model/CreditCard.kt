package com.azimulkabir.actua.model

import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.ui.components.formatDate
import java.time.LocalDate
import java.util.Locale

data class CreditCardConfig(
    val statementDay: Int,
    val dueOffsetDays: Int = CreditCardCycle.DEFAULT_DUE_OFFSET_DAYS,
    val limitCents: Long? = null,
    val dueDay: Int? = null,
)

data class CreditCardStatus(
    val accountId: String,
    val accountName: String,
    val balanceCents: Long,
    val config: CreditCardConfig,
    val cycleSpendCents: Long,
    val availableCreditCents: Long?,
    val closed: Boolean,
    /** Recent statement dues, oldest first; null when statement data wasn't loaded. */
    val statementDues: List<CreditCardCycle.StatementDue>? = null,
) {
    val cycle: CreditCardCycle get() = CreditCardCycle(config.statementDay, config.paymentDue)

    /** The statement still awaiting payment, shared by every screen that shows the card. */
    fun pendingStatementDue(today: DayDate = DayDate.today()): CreditCardCycle.StatementDue? =
        statementDues?.let { CreditCardCycle.pendingStatementDue(it, today) }
}

/** Unpaid cards first by nearest due date, then paid cards, with a stable name tie-break. */
fun Iterable<CreditCardStatus>.sortedForPaymentPriority(today: DayDate = DayDate.today()) =
    sortedWith(compareBy<CreditCardStatus>(
        { if (it.balanceCents < 0) 0 else 1 },
        { it.cycle.daysUntilDue(today) },
        { it.accountName.lowercase(Locale.ROOT) },
        { it.accountId },
    ))

/** Billing-cycle calculations matching Actuali iOS CreditCardCycle. */
data class CreditCardCycle(
    val statementDay: Int,
    val paymentDue: PaymentDue = PaymentDue.DaysAfter(DEFAULT_DUE_OFFSET_DAYS),
) {
    sealed interface PaymentDue {
        data class DaysAfter(val days: Int) : PaymentDue
        data class DayOfMonth(val day: Int) : PaymentDue
    }

    constructor(statementDay: Int, dueOffsetDays: Int) :
        this(statementDay, PaymentDue.DaysAfter(dueOffsetDays))

    val dueOffsetDays: Int get() = when (val due = paymentDue) {
        is PaymentDue.DaysAfter -> due.days
        is PaymentDue.DayOfMonth -> DEFAULT_DUE_OFFSET_DAYS
    }

    init {
        require(statementDay in 1..31)
        when (val due = paymentDue) {
            is PaymentDue.DaysAfter -> require(due.days in 1..MAX_DUE_OFFSET_DAYS)
            is PaymentDue.DayOfMonth -> require(due.day in 1..31)
        }
    }

    fun cycleRange(today: DayDate = DayDate.today()): Pair<DayDate, DayDate> {
        val close = minOf(statementDay, DayDate.lastDay(today.year, today.month))
        return if (today.day > close) {
            val next = today.addingMonths(1)
            DayDate(today.year, today.month, close).addingDays(1) to
                DayDate(next.year, next.month, minOf(statementDay, DayDate.lastDay(next.year, next.month)))
        } else {
            val previous = today.addingMonths(-1)
            DayDate(previous.year, previous.month,
                minOf(statementDay, DayDate.lastDay(previous.year, previous.month))).addingDays(1) to
                DayDate(today.year, today.month, close)
        }
    }

    fun previousStatementDate(today: DayDate = DayDate.today()) = cycleRange(today).first.addingDays(-1)

    fun dueDate(statement: DayDate): DayDate = when (val due = paymentDue) {
        is PaymentDue.DaysAfter -> statement.addingDays(due.days)
        is PaymentDue.DayOfMonth -> {
            val month = if (due.day > statementDay) statement else statement.addingMonths(1)
            DayDate(month.year, month.month, minOf(due.day, DayDate.lastDay(month.year, month.month)))
        }
    }

    /**
     * The statement closing date whose payment is next due. Walks back through closed
     * statements rather than assuming only the most recent one is pending, in case the
     * offset exceeds a monthly cycle.
     */
    fun upcomingStatementDate(today: DayDate = DayDate.today()): DayDate {
        var pending = cycleRange(today).second
        var statement = previousStatementDate(today)
        repeat(dueOffsetDays / 28 + 2) {
            if (today > dueDate(statement)) return pending
            pending = statement
            statement = previousStatementDate(statement)
        }
        return pending
    }

    fun upcomingDueDate(today: DayDate = DayDate.today()): DayDate = dueDate(upcomingStatementDate(today))

    fun daysRemainingInCycle(today: DayDate = DayDate.today()) =
        maxOf(0, today.daysUntil(cycleRange(today).second))

    /** Days until [dueDate], or until the next calculated due date when it is null. */
    fun daysUntilDue(today: DayDate = DayDate.today(), dueDate: DayDate? = null) =
        maxOf(0, today.daysUntil(dueDate ?: upcomingDueDate(today)))

    /** The last three closed billing statement cycles, ordered newest to oldest. */
    fun recentStatementCycles(today: DayDate = DayDate.today()): List<StatementCycle> {
        val cycles = mutableListOf<StatementCycle>()
        var currentEnd = previousStatementDate(today)
        repeat(3) {
            val prevEnd = previousStatementDate(currentEnd)
            val start = prevEnd.addingDays(1)
            val due = dueDate(currentEnd)
            cycles += StatementCycle(start, currentEnd, due)
            currentEnd = prevEnd
        }
        return cycles
    }

    /** "Due today", "Due tomorrow", or "Due <date> (Nd)" in the user's date format. */
    fun dueSummary(
        today: DayDate = DayDate.today(),
        dueDate: DayDate? = null,
        formatDueDate: (LocalDate) -> String = ::formatDate,
    ): String = when (val days = daysUntilDue(today, dueDate)) {
        0 -> "Due today"
        1 -> "Due tomorrow"
        else -> {
            val due = dueDate ?: upcomingDueDate(today)
            "Due ${formatDueDate(LocalDate.of(due.year, due.month, due.day))} (${days}d)"
        }
    }

    fun dueShortSummary(today: DayDate = DayDate.today(), dueDate: DayDate? = null): String {
        val days = daysUntilDue(today, dueDate)
        return if (days <= 1) dueSummary(today, dueDate) else "Due in ${days}d"
    }

    /**
     * Statements whose dues cards load, oldest first: the recent closed statements (the
     * 60-day maximum offset means these cover every one still pending), plus the current
     * cycle's statement when none of them is still due.
     */
    fun statementDueCycles(today: DayDate = DayDate.today()): List<StatementCycle> {
        val recent = recentStatementCycles(today).reversed()
        if (recent.any { today <= it.dueDate }) return recent
        val (start, end) = cycleRange(today)
        return recent + StatementCycle(start, end, dueDate(end))
    }

    /** A closed billing cycle's date range and the payment due date for its statement. */
    data class StatementCycle(val start: DayDate, val end: DayDate, val dueDate: DayDate)

    /** Status of the payment due for a credit card statement. */
    data class StatementDue(
        /** Balance in cents owed when the statement closed (positive). */
        val statementBalance: Long,
        /** Payments/credits in cents received since the statement closed (positive). */
        val paymentsSince: Long,
        /** Remaining balance in cents to pay for this statement (positive). */
        val remainingDue: Long,
        /** Payment due date for this statement. */
        val dueDate: DayDate,
    ) {
        val isPaid: Boolean get() = remainingDue == 0L && statementBalance > 0L
    }

    /** Record of a closed credit card billing statement with spend, due, and transaction metrics. */
    data class StatementRecord(
        val startDate: DayDate,
        val endDate: DayDate,
        val dueDate: DayDate,
        val statementBalance: Long,
        val paymentsSince: Long,
        val remainingDue: Long,
        /** Outflow spend in cents during the billing cycle (positive). */
        val totalSpend: Long,
    ) {
        val id: Int get() = endDate.yyyymmdd
        val isPaid: Boolean get() = remainingDue == 0L && statementBalance > 0L
    }

    companion object {
        const val DEFAULT_DUE_OFFSET_DAYS = 15
        const val MAX_DUE_OFFSET_DAYS = 60

        /**
         * The statement still awaiting payment: the first unpaid one that isn't past due,
         * else the first not-yet-due one even if already paid. [dues] are oldest first.
         */
        fun pendingStatementDue(dues: List<StatementDue>, today: DayDate = DayDate.today()): StatementDue? =
            dues.firstOrNull { today <= it.dueDate && it.remainingDue > 0 }
                ?: dues.firstOrNull { today <= it.dueDate }

        /** Cards-row due pill: "$342.18 · Due in 27d", or the summary alone once settled. */
        fun duePillText(amount: String, summary: String, remainingDue: Long): String =
            if (remainingDue > 0) "$amount · $summary" else summary

        /** Computes the statement payment status given raw balances and payments. */
        fun calculateStatementDue(
            statementRawBalance: Long,
            paymentsSince: Long,
            liveBalance: Long,
            dueDate: DayDate,
        ): StatementDue {
            val statementOwed = maxOf(0L, -statementRawBalance)
            val unpaid = maxOf(0L, statementOwed - paymentsSince)
            val remaining = minOf(unpaid, maxOf(0L, -liveBalance))
            return StatementDue(statementOwed, paymentsSince, remaining, dueDate)
        }
    }
}

val CreditCardConfig.paymentDue: CreditCardCycle.PaymentDue
    get() = dueDay?.let(CreditCardCycle.PaymentDue::DayOfMonth)
        ?: CreditCardCycle.PaymentDue.DaysAfter(dueOffsetDays)
