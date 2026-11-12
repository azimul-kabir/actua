package com.azimulkabir.actua.ui.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class RootListDataTest {
    @Test
    fun emptyDataHasNoRowsOrChoices() {
        val empty = RootListData.Empty
        assertTrue(empty.accounts.isEmpty())
        assertTrue(empty.hideReconciledAccountIds.isEmpty())
        assertTrue(empty.categoryNames.isEmpty())
        assertTrue(empty.categoryChoices.labels.isEmpty())
        assertTrue(empty.payeeNames.isEmpty())
        assertTrue(empty.suggestedPayees.isEmpty())
        assertTrue(empty.creditCards.isEmpty())
        assertTrue(empty.loans.isEmpty())
        assertTrue(empty.loanSnoozedMonths.isEmpty())
    }
}
