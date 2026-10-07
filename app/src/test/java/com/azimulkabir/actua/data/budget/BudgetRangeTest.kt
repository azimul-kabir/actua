package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** loot-core `budget/base.ts` `getBudgetRange`, as `createAllBudgets` calls it (#911). */
class BudgetRangeTest {
    private val current = YearMonth.of(2026, 10)

    @Test
    fun startsThreeMonthsBeforeTheEarliestTransaction() {
        assertEquals(YearMonth.of(2024, 10)..YearMonth.of(2027, 10), BudgetRange.of(YearMonth.of(2025, 1), current))
    }

    @Test
    fun withoutTransactionsStartsThreeMonthsBeforeTheCurrentMonth() {
        assertEquals(YearMonth.of(2026, 7)..YearMonth.of(2027, 10), BudgetRange.of(null, current))
    }

    @Test
    fun aFutureEarliestTransactionDoesNotMoveTheStartPastTheCurrentMonth() {
        assertEquals(YearMonth.of(2026, 7)..YearMonth.of(2027, 10), BudgetRange.of(YearMonth.of(2027, 3), current))
    }
}
