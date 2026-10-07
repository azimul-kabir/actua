package com.azimulkabir.actua.data.budget

import java.time.YearMonth

/**
 * Actual's budget averages (loot-core `budget/actions.ts` `getCategoryAverage`): the average of a
 * category's monthly activity over up to N months ending last month, or ending the month before the
 * current one when [month] is in the future, stopping at the category's first activity.
 */
object BudgetAverages {
    /** Upstream `getAverageStartMonth`. */
    fun startMonth(month: YearMonth, current: YearMonth): YearMonth {
        val previous = month.minusMonths(1)
        return if (previous >= current) current.minusMonths(1) else previous
    }

    /** Upstream `getAverageMonths`: newest first, never before [firstActivity] when there is one. */
    fun months(month: YearMonth, maxMonths: Int, firstActivity: YearMonth?, current: YearMonth): List<YearMonth> {
        val months = mutableListOf<YearMonth>()
        var candidate = startMonth(month, current)
        repeat(maxMonths) {
            if (firstActivity != null && candidate < firstActivity) return months
            months += candidate
            candidate = candidate.minusMonths(1)
        }
        return months
    }

    /** `Math.round(sum / months)`: halves round toward positive infinity; no months is 0. */
    fun average(sums: List<Long>): Long {
        if (sums.isEmpty()) return 0
        val n = sums.size.toLong()
        return Math.floorDiv(2 * sums.sum() + n, 2 * n)
    }

    /** The budget amount upstream writes: expense activity is negative, so its average is negated. */
    fun budgetAmount(average: Long, isIncome: Boolean): Long = if (isIncome) average else -average

    /** Upstream `copyUntilYearEnd`'s months: after [month] through December, within created months. */
    fun monthsUntilYearEnd(month: YearMonth, current: YearMonth): List<YearMonth> {
        val end = minOf(YearMonth.of(month.year, 12), current.plusMonths(12))
        return generateSequence(month.plusMonths(1)) { it.plusMonths(1) }.takeWhile { it <= end }.toList()
    }
}
