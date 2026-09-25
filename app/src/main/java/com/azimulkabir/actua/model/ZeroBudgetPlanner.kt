package com.azimulkabir.actua.model

/**
 * Preview-first planner for Actual's "Set budgets to zero" month action. Faithful reference:
 * `packages/loot-core/src/server/budget/actions.ts` `setZero({ month })`, also ported by our
 * portable reference client Actuali (MattFaz/actuali#467). Every non-income category is
 * proposed for zero, including hidden ones, matching upstream's inclusion of hidden categories.
 */
object ZeroBudgetPlanner {
    fun preview(groups: List<BudgetGroup>, month: String): BudgetTemplatePreview {
        val changes = mutableListOf<BudgetTemplateChange>()
        val unsupported = mutableListOf<String>()
        var unchanged = 0
        groups.filterNot { it.isIncome }.forEach { group ->
            group.categories.filterNot { it.isIncome }.forEach { category ->
                if (category.assignedCents == 0L) {
                    unchanged++
                    return@forEach
                }
                val id = category.id
                if (id == null) {
                    unsupported += "${group.name} · ${category.name}"
                    return@forEach
                }
                changes += BudgetTemplateChange(group.name, id, category.name, category.assignedCents, 0L)
            }
        }
        return BudgetTemplatePreview(
            month = month,
            changes = changes,
            unchangedCount = unchanged,
            unsupportedCategories = unsupported.distinct(),
        )
    }
}
