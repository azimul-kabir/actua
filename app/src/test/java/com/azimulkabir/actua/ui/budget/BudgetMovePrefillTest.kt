package com.azimulkabir.actua.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Test

/** The move sheet's opening amount, as Actual's TransferMenu / CoverMenu prefill it (#1773). */
class BudgetMovePrefillTest {
    @Test fun transferStartsAtTheCategoryBalance() {
        assertEquals(12_500L, budgetMovePrefillCents(categoryBalanceCents = 12_500, sourceBalanceCents = 0))
    }

    @Test fun coverStartsAtTheOverspentAmount() {
        assertEquals(4_000L, budgetMovePrefillCents(categoryBalanceCents = -4_000, sourceBalanceCents = 10_000))
    }

    @Test fun coverIsHeldToWhatTheSourceCanFund() {
        assertEquals(2_500L, budgetMovePrefillCents(categoryBalanceCents = -4_000, sourceBalanceCents = 2_500))
        assertEquals(0L, budgetMovePrefillCents(categoryBalanceCents = -4_000, sourceBalanceCents = -300))
    }

    @Test fun aZeroBalanceStartsEmpty() {
        assertEquals(0L, budgetMovePrefillCents(categoryBalanceCents = 0, sourceBalanceCents = 5_000))
    }
}
