package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import java.time.LocalDate
import java.time.YearMonth

/** One month of the Income and spending widget, in integer cents. */
data class IncomeSpendingMonth(
    val month: YearMonth,
    val incomeCents: Long,
    val spentCents: Long,
    val current: Boolean,
) {
    /** What is left: income minus spending, negative when more was spent than came in. */
    val leftCents: Long get() = incomeCents - spentCents

    /** What is left as a whole percent of income, or null when there is no income to compare with. */
    val savingsRatePercent: Int? get() =
        if (incomeCents > 0) Math.round(leftCents * 100.0 / incomeCents).toInt() else null
}

/** The last few months of income and spending, oldest first, ending with the current month. */
data class IncomeSpending(val months: List<IncomeSpendingMonth>) {
    val current: IncomeSpendingMonth? get() = months.lastOrNull()

    /** True when there was neither income nor spending in any month, which the widget shows as an empty state. */
    val isEmpty: Boolean get() = months.all { it.incomeCents == 0L && it.spentCents == 0L }
}

object IncomeSpendingBuilder {
    const val DEFAULT_MONTHS = 6

    /**
     * Income and spending for the [months] months up to and including the month of [today], each counted
     * by [SavedReportEngine.monthlyCashFlow], the code behind the Income vs expenses report, so the widget
     * and Reports always agree. The current month is the amounts so far, and is flagged for highlighting.
     */
    fun build(
        transactions: List<ActualTransaction>,
        accounts: List<ActualAccount>,
        groups: List<ActualCategoryGroup>,
        today: LocalDate,
        months: Int = DEFAULT_MONTHS,
    ): IncomeSpending {
        require(months >= 1) { "A series needs at least one month" }
        val aggregator = ReportAggregator(accounts, groups)
        val currentMonth = YearMonth.from(today)
        return IncomeSpending(
            (months - 1 downTo 0).map { back ->
                val month = currentMonth.minusMonths(back.toLong())
                val (income, spent) = SavedReportEngine.monthlyCashFlow(transactions, month, aggregator)
                IncomeSpendingMonth(month, income, spent, current = back == 0)
            },
        )
    }
}
