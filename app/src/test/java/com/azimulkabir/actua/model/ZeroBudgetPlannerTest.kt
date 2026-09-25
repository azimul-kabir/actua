package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ZeroBudgetPlannerTest {
    private fun category(id: String, name: String, assignedCents: Long, hidden: Boolean = false) = BudgetCategory(
        name = name,
        assigned = 0,
        spent = 0,
        id = id,
        actualAssignedCents = assignedCents,
        hidden = hidden,
    )

    @Test fun proposesZeroForEveryBudgetedCategory() {
        val groups = listOf(
            BudgetGroup("Living", listOf(
                category("rent", "Rent", assignedCents = 100_000),
                category("food", "Food", assignedCents = 0),
            )),
        )

        val preview = ZeroBudgetPlanner.preview(groups, "2026-09")

        assertEquals(1, preview.changes.size)
        assertEquals("rent", preview.changes.single().categoryId)
        assertEquals(0L, preview.changes.single().proposedCents)
        assertEquals(1, preview.unchangedCount)
        assertEquals(-100_000L, preview.netBudgetChangeCents)
    }

    @Test fun includesHiddenCategoriesButSkipsIncome() {
        val groups = listOf(
            BudgetGroup("Bills", listOf(category("hidden", "Old subscription", assignedCents = 5_000, hidden = true))),
            BudgetGroup("Income", listOf(category("paycheck", "Paycheck", assignedCents = 200_000)), isIncome = true),
        )

        val preview = ZeroBudgetPlanner.preview(groups, "2026-09")

        assertEquals(listOf("hidden"), preview.changes.map { it.categoryId })
    }

    @Test fun repeatedApplicationProducesAnEmptyPreview() {
        val zeroed = category("rent", "Rent", assignedCents = 0)

        val preview = ZeroBudgetPlanner.preview(listOf(BudgetGroup("Living", listOf(zeroed))), "2026-09")

        assertEquals(emptyList<BudgetTemplateChange>(), preview.changes)
        assertEquals(1, preview.unchangedCount)
    }
}
