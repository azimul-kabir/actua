package com.azimulkabir.actua.data.budget

import java.time.YearMonth

/**
 * Actual's budget months (loot-core `budget/base.ts` `getBudgetRange` / `createAllBudgets`): from
 * 3 months before the earliest non-child transaction (or the current month when there is none, or
 * it is later) to 12 months after the current month. Actual has no budget outside this range.
 */
object BudgetRange {
    fun of(earliestTransactionMonth: YearMonth?, current: YearMonth): ClosedRange<YearMonth> {
        val start = minOf(earliestTransactionMonth ?: current, current)
        return start.minusMonths(3)..current.plusMonths(12)
    }
}
