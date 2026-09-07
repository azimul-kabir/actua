package com.azimulkabir.actua.model

import com.azimulkabir.actua.data.schedules.DayDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CreditCardConfig(
    val statementDay: Int,
    val dueOffsetDays: Int = CreditCardCycle.DEFAULT_DUE_OFFSET_DAYS,
    val limitCents: Long? = null,
)

data class CreditCardStatus(
    val accountId: String,
    val accountName: String,
    val balanceCents: Long,
    val config: CreditCardConfig,
    val cycleSpendCents: Long,
    val availableCreditCents: Long?,
    val closed: Boolean,
) {
    val cycle: CreditCardCycle get() = CreditCardCycle(config.statementDay, config.dueOffsetDays)
}

/** Billing-cycle calculations matching Actuali iOS CreditCardCycle. */
data class CreditCardCycle(val statementDay: Int, val dueOffsetDays: Int = DEFAULT_DUE_OFFSET_DAYS) {
    init {
        require(statementDay in 1..31)
        require(dueOffsetDays in 1..MAX_DUE_OFFSET_DAYS)
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

    fun upcomingDueDate(today: DayDate = DayDate.today()): DayDate {
        var due = cycleRange(today).second.addingDays(dueOffsetDays)
        var statement = previousStatementDate(today)
        repeat(dueOffsetDays / 28 + 2) {
            val statementDue = statement.addingDays(dueOffsetDays)
            if (today > statementDue) return due
            due = statementDue
            statement = previousStatementDate(statement)
        }
        return due
    }

    fun daysRemainingInCycle(today: DayDate = DayDate.today()) =
        maxOf(0, today.daysUntil(cycleRange(today).second))

    fun daysUntilDue(today: DayDate = DayDate.today()) = maxOf(0, today.daysUntil(upcomingDueDate(today)))

    fun dueSummary(today: DayDate = DayDate.today()): String = when (val days = daysUntilDue(today)) {
        0 -> "Due today"
        1 -> "Due tomorrow"
        else -> {
            val due = upcomingDueDate(today)
            val formatted = java.time.LocalDate.of(due.year, due.month, due.day)
                .format(DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH))
            "Due $formatted (${days}d)"
        }
    }

    fun dueShortSummary(today: DayDate = DayDate.today()): String {
        val days = daysUntilDue(today)
        return if (days <= 1) dueSummary(today) else "Due in ${days}d"
    }

    companion object {
        const val DEFAULT_DUE_OFFSET_DAYS = 15
        const val MAX_DUE_OFFSET_DAYS = 60
    }
}
