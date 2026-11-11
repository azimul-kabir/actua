package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import java.time.LocalDate
import java.time.YearMonth

/** One month of the Spending trend widget: what was spent, in integer cents. */
data class SpendingTrendMonth(val month: YearMonth, val spentCents: Long, val current: Boolean)

/** The last few months of spending, oldest first, ending with the current month. */
data class SpendingTrend(val months: List<SpendingTrendMonth>) {
    val currentCents: Long get() = months.lastOrNull()?.spentCents ?: 0L

    /** The month before the current one, or 0 when there is none. */
    val previousCents: Long get() = months.getOrNull(months.size - 2)?.spentCents ?: 0L

    val totalCents: Long get() = months.sumOf { it.spentCents }

    /** True when nothing was spent in any month, which the widget shows as an empty state. */
    val isEmpty: Boolean get() = months.all { it.spentCents == 0L }
}

object SpendingTrendBuilder {
    const val DEFAULT_MONTHS = 6

    /**
     * The spending trend for the [months] months up to and including the month of [today]. It counts
     * spending exactly as the Spending dashboard widget does ([CoreReportEngine.spendingTransactions] and
     * [CoreReportEngine.spentInMonth]), so the widget and Reports always agree. The current month is the
     * spending so far, and is flagged so the widget can highlight it.
     */
    fun build(
        transactions: List<ActualTransaction>,
        offBudgetAccountIds: Set<String>,
        incomeCategoryIds: Set<String>,
        today: LocalDate,
        months: Int = DEFAULT_MONTHS,
    ): SpendingTrend {
        require(months >= 1) { "A trend needs at least one month" }
        val spending = CoreReportEngine.spendingTransactions(transactions, offBudgetAccountIds, incomeCategoryIds)
        val currentMonth = YearMonth.from(today)
        return SpendingTrend(
            (months - 1 downTo 0).map { back ->
                val month = currentMonth.minusMonths(back.toLong())
                SpendingTrendMonth(month, CoreReportEngine.spentInMonth(spending, month), current = back == 0)
            },
        )
    }
}
