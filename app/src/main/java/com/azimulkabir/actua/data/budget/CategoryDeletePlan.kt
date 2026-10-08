package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup

/**
 * What deleting a category needs, as Actual decides it: whether its transactions and budget
 * amounts must move to another category first (`must-category-transfer`), and the categories
 * `confirm-category-delete` lets them move to.
 */
data class CategoryDeletePlan(
    val requiresTransfer: Boolean,
    val isIncome: Boolean,
    /** Visible same-type categories under their visible groups, in budget order, without the deleted one. */
    val targets: List<ActualCategoryGroup>,
) {
    companion object {
        /**
         * The transfer choices for [categoryId]: categories of the same type (income or expense),
         * leaving out hidden categories and hidden groups as Actual's category autocomplete does.
         */
        fun targets(groups: List<ActualCategoryGroup>, categoryId: String): List<ActualCategoryGroup> {
            val income = groups.firstNotNullOfOrNull { group -> group.categories.firstOrNull { it.id == categoryId } }
                ?.isIncome ?: return emptyList()
            return groups.filter { it.isIncome == income && !it.hidden }
                .map { group -> group.copy(categories = group.categories.filter { !it.hidden && it.id != categoryId }) }
                .filter { it.categories.isNotEmpty() }
        }
    }
}
