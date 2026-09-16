package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCategoryViewTest {
    @Test fun `all matches everything`() {
        assertTrue(BudgetCategoryView.ALL.matches(category(balanceCents = -500)))
        assertTrue(BudgetCategoryView.ALL.matches(category(balanceCents = 500)))
        assertTrue(BudgetCategoryView.ALL.matches(category(balanceCents = 0)))
    }

    @Test fun `overspent is a negative balance`() {
        assertTrue(BudgetCategoryView.OVERSPENT.matches(category(balanceCents = -1)))
        assertFalse(BudgetCategoryView.OVERSPENT.matches(category(balanceCents = 0)))
        assertFalse(BudgetCategoryView.OVERSPENT.matches(category(balanceCents = 100)))
    }

    @Test fun `money available is a positive balance`() {
        assertTrue(BudgetCategoryView.MONEY_AVAILABLE.matches(category(balanceCents = 1)))
        assertFalse(BudgetCategoryView.MONEY_AVAILABLE.matches(category(balanceCents = 0)))
        assertFalse(BudgetCategoryView.MONEY_AVAILABLE.matches(category(balanceCents = -1)))
    }

    @Test fun `underfunded needs a goal not yet reached`() {
        assertTrue(BudgetCategoryView.UNDERFUNDED.matches(category(balanceCents = 5_000, goalCents = 20_000)))
        assertFalse(BudgetCategoryView.UNDERFUNDED.matches(category(balanceCents = 20_000, goalCents = 20_000)))
        assertFalse(BudgetCategoryView.UNDERFUNDED.matches(category(balanceCents = 5_000, goalCents = null)))
    }

    @Test fun `overfunded needs a goal already exceeded`() {
        assertTrue(BudgetCategoryView.OVERFUNDED.matches(category(balanceCents = 25_000, goalCents = 20_000)))
        assertFalse(BudgetCategoryView.OVERFUNDED.matches(category(balanceCents = 20_000, goalCents = 20_000)))
        assertFalse(BudgetCategoryView.OVERFUNDED.matches(category(balanceCents = 25_000, goalCents = null)))
    }

    @Test fun `fromLabel round trips and falls back to all`() {
        BudgetCategoryView.entries.forEach { assertEquals(it, BudgetCategoryView.fromLabel(it.label)) }
        assertEquals(BudgetCategoryView.ALL, BudgetCategoryView.fromLabel("unknown"))
    }

    private fun category(balanceCents: Long, goalCents: Long? = null) = BudgetCategory(
        name = "Groceries", assigned = 0, spent = 0,
        availableCents = balanceCents, goalCents = goalCents,
    )
}
