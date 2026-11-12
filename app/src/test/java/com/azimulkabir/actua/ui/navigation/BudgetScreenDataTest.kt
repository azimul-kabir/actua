package com.azimulkabir.actua.ui.navigation

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetScreenDataTest {
    @Test
    fun emptyDataCarriesTheRequestedMonth() {
        val empty = BudgetMonthData.empty("2026-03")
        assertEquals("2026-03", empty.month)
        assertTrue(empty.groups.isEmpty())
        assertTrue(empty.scheduleFunding.isEmpty())
        assertEquals("", empty.note)
        assertNull(empty.monthRange)
        assertNull(empty.overview.toBudgetCents)
    }

    @Test
    fun defaultSettingsMatchTheEnvelopeBudgetBeforeLoading() {
        assertEquals(ActualBudgetDatabase.BUDGET_TYPE_ENVELOPE, BudgetSettings.Default.type)
        assertEquals(false, BudgetSettings.Default.hidesFraction)
        assertEquals(2, BudgetSettings.Default.decimalPlaces)
    }
}
