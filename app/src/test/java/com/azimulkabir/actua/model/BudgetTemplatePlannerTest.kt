package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetTemplatePlannerTest {
    @Test fun previewsOnlyChangedSupportedTargets() {
        val groups = listOf(BudgetGroup("Living", listOf(
            category("rent", "Rent", assigned = 80_000, target = BudgetTarget(
                BudgetTarget.Type.MONTHLY_SAVINGS, 100_000,
            )),
            category("food", "Food", assigned = 30_000, target = BudgetTarget(
                BudgetTarget.Type.MONTHLY_SAVINGS, 30_000,
            )),
            category("advanced", "Advanced", assigned = 0, unsupported = true),
        )))

        val preview = BudgetTemplatePlanner.preview(groups, "2026-09")

        assertEquals(1, preview.changes.size)
        assertEquals("rent", preview.changes.single().categoryId)
        assertEquals(20_000, preview.netBudgetChangeCents)
        assertEquals(1, preview.unchangedCount)
        assertEquals(listOf("Living · Advanced"), preview.unsupportedCategories)
    }

    @Test fun repeatedApplicationProducesAnEmptyPreview() {
        val original = category("rent", "Rent", assigned = 80_000, target = BudgetTarget(
            BudgetTarget.Type.MONTHLY_SAVINGS, 100_000,
        ))
        val first = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Living", listOf(original))), "2026-09")
        val applied = category(
            "rent", "Rent", first.changes.single().proposedCents,
            BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 100_000),
        )

        val second = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Living", listOf(applied))), "2026-09")

        assertEquals(emptyList<BudgetTemplateChange>(), second.changes)
        assertEquals(1, second.unchangedCount)
        assertEquals(0, second.netBudgetChangeCents)
    }

    private fun category(
        id: String,
        name: String,
        assigned: Long,
        target: BudgetTarget? = null,
        unsupported: Boolean = false,
    ) = BudgetCategory(
        name = name,
        assigned = (assigned / 100).toInt(),
        spent = 0,
        actualAssignedCents = assigned,
        id = id,
        target = target,
        hasUnsupportedTarget = unsupported,
    )
}
