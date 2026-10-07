package com.azimulkabir.actua.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Upstream `runSchedule` (schedule-template.ts) for one category's schedule templates at one
 * priority. Schedules due this month, "full" schedules and frequent ones (weekly up to every 4
 * weeks, daily up to every 31 days) are paid in the month they fall due ("pay month of"). The
 * rest are a sinking fund: sorted by next date, last month's balance covers the first, and each
 * gets its remaining amount spread over the months until it is due. Once the balance already
 * covers everything, each schedule only gets its monthly base contribution.
 */
object ScheduleTemplateFunding {
    private data class Entry(
        val target: Long,
        val nextDate: LocalDate?,
        val interval: Int,
        val frequency: String?,
        val numMonths: Long,
        val full: Boolean,
    )

    /**
     * The amount these schedule templates add to the category's budget this month.
     *
     * @param balance last month's balance plus what earlier templates at this priority requested.
     * @param lastMonthBalance last month's balance (`fromLastMonth`).
     * @param previousMonthGoal last month's `goal` cell, 0 when unset.
     * @param trackingBudget Actual pays every schedule in its own month in a tracking budget.
     */
    fun request(
        targets: List<BudgetTarget>,
        schedules: List<BudgetScheduleFunding>,
        category: BudgetCategory,
        month: String,
        balance: Long,
        lastMonthBalance: Long,
        previousMonthGoal: Long,
        trackingBudget: Boolean = false,
    ): Long {
        val current = runCatching { YearMonth.parse(month) }.getOrNull() ?: return 0L
        val entries = targets.filter { it.type == BudgetTarget.Type.SCHEDULE }.mapNotNull { target ->
            val reference = target.scheduleId?.takeIf(String::isNotBlank) ?: target.scheduleName?.trim().orEmpty()
            val funding = schedules.firstOrNull {
                reference in it.referenceNames && (it.categoryId == null || it.categoryId == category.id)
            } ?: return@mapNotNull null
            if (!funding.active) return@mapNotNull null
            val amount = adjusted(target, funding.amountCents.toDouble())
            val repeating = funding.frequency != null
            val nextDate = funding.nextDate
            val numMonths = nextDate?.let { ChronoUnit.MONTHS.between(current, YearMonth.from(it)) }
                ?: funding.monthsUntilNextOccurrence.toLong()
            if (numMonths < 0) return@mapNotNull null
            Entry(
                target = if (repeating) amount * funding.occurrencesInDueMonth.coerceAtLeast(1) else amount,
                nextDate = nextDate,
                interval = funding.interval.coerceAtLeast(1),
                frequency = funding.frequency,
                numMonths = numMonths,
                full = target.scheduleFull,
            )
        }
        if (entries.isEmpty()) return 0L

        fun isPayMonthOf(entry: Entry) = entry.full ||
            ((entry.frequency == "monthly" || entry.frequency == null) && entry.interval == 1 && entry.numMonths == 0L) ||
            (entry.frequency == "weekly" && entry.interval <= 4) ||
            (entry.frequency == "daily" && entry.interval <= 31) ||
            trackingBudget

        val payMonthOf = entries.filter(::isPayMonthOf)
        val sinking = entries.filterNot(::isPayMonthOf).sortedBy { it.nextDate ?: LocalDate.MAX }
        val numSubMonthly = entries.count { it.frequency == "weekly" || it.frequency == "daily" }
        val totalPayMonthOf = payMonthOf.filter { it.numMonths == 0L }.sumOf { it.target }.toDouble()
        val totalSinking = sinking.sumOf { it.target }.toDouble()
        val needed = totalSinking + totalPayMonthOf

        val coveredByBalance = balance >= needed || (
            previousMonthGoal < needed && previousMonthGoal != 0L && balance >= previousMonthGoal && numSubMonthly > 0
            )
        if (coveredByBalance) {
            return jsRound(totalPayMonthOf + sinking.sumOf(::monthlyBaseContribution))
        }
        var remainder = 0.0
        var sinkingTotal = 0.0
        sinking.forEachIndexed { index, entry ->
            remainder = if (index == 0) entry.target - lastMonthBalance.toDouble() else entry.target - remainder
            val tg = if (remainder >= 0) remainder.also { remainder = 0.0 } else 0.0.also { remainder = kotlin.math.abs(remainder) }
            sinkingTotal += tg / (entry.numMonths + 1)
        }
        return if (sinking.isEmpty()) jsRound(totalPayMonthOf + sinkingTotal) - lastMonthBalance
        else jsRound(totalPayMonthOf + sinkingTotal)
    }

    /** The schedule amount with an "increase"/"decrease" modifier, as `createScheduleList` applies it. */
    private fun adjusted(target: BudgetTarget, amount: Double): Long = when (target.adjustmentType) {
        BudgetTarget.AdjustmentType.PERCENT -> jsRound(amount * (1 + (target.adjustmentPercent ?: 0.0) / 100))
        BudgetTarget.AdjustmentType.FIXED -> jsRound(amount + (target.adjustmentAmountCents ?: 0L))
        null -> jsRound(amount)
    }

    private fun monthlyBaseContribution(entry: Entry): Double {
        val target = entry.target.toDouble()
        fun subMonthly(previous: LocalDate?): Double {
            val next = entry.nextDate ?: return target
            val months = previous?.let { ChronoUnit.MONTHS.between(YearMonth.from(it), YearMonth.from(next)) } ?: 0L
            return target / (if (months == 0L) 1L else months)
        }
        return when (entry.frequency) {
            "yearly" -> target / entry.interval / 12
            "monthly" -> target / entry.interval
            "weekly" -> subMonthly(entry.nextDate?.minusWeeks(entry.interval.toLong()))
            "daily" -> subMonthly(entry.nextDate?.minusDays(entry.interval.toLong()))
            else -> target / entry.interval
        }
    }

    /** JavaScript `Math.round` (halves round up). */
    private fun jsRound(value: Double): Long = kotlin.math.floor(value + 0.5).toLong()
}
