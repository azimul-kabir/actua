package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Actual's month and group totals and the tracking savings headline (#909). */
class BudgetOverviewTotalsTest {
    private fun category(name: String, hidden: Boolean) = BudgetCategory(
        name, 0, 0, actualAssignedCents = 100, id = name, availableCents = 100, hidden = hidden,
    )

    @Test fun envelopeGroupsTotalHiddenCategoriesTrackingGroupsDont() {
        val group = BudgetGroup("Bills", listOf(category("Rent", false), category("Old", true)))
        assertEquals(listOf("Rent", "Old"), budgetTotalCategories(group, tracking = false).map { it.name })
        assertEquals(listOf("Rent"), budgetTotalCategories(group, tracking = true).map { it.name })
    }

    @Test fun envelopeLeadsWithToBudget() {
        assertEquals("To budget" to 1_200L, BudgetOverview(1_200, 0, 0, 0).lead())
        assertEquals("Ready to Budget" to -50L, BudgetOverview(-50, 0, 0, 0).lead("Ready to Budget"))
    }

    @Test fun trackingLeadsWithSavingsLikeActualsMobileBudget() {
        assertEquals("Projected savings" to 300L, BudgetOverview(null, 0, 0, 0, savedCents = 300, projectedSavings = true).lead())
        assertEquals("Saved" to 300L, BudgetOverview(null, 0, 0, 0, savedCents = 300).lead())
        assertEquals("Overspent" to -20L, BudgetOverview(null, 0, 0, 0, savedCents = -20).lead())
        assertEquals("To budget" to null, BudgetOverview(null, 0, 0, 0).lead())
    }
}
