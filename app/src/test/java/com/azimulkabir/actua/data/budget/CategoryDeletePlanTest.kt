package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import org.junit.Assert.assertEquals
import org.junit.Test

/** The transfer choices Actual's `confirm-category-delete` offers (actua#926). */
class CategoryDeletePlanTest {
    private fun category(id: String, group: String, income: Boolean = false, hidden: Boolean = false) =
        ActualCategory(id, id, group, income, hidden, 0.0)

    private val groups = listOf(
        ActualCategoryGroup("bills", "Bills", false, false, 1.0, listOf(category("rent", "bills"), category("power", "bills"))),
        ActualCategoryGroup("old", "Old", false, true, 2.0, listOf(category("gym", "old"))),
        ActualCategoryGroup("food", "Food", false, false, 3.0, listOf(category("grocery", "food"), category("snacks", "food", hidden = true))),
        ActualCategoryGroup("only", "Only", false, false, 4.0, listOf(category("lonely", "only"))),
        ActualCategoryGroup("income", "Income", true, false, 5.0, listOf(category("salary", "income", true), category("bonus", "income", true))),
    )

    private fun targets(id: String) = CategoryDeletePlan.targets(groups, id).map { it.id to it.categories.map(ActualCategory::id) }

    @Test
    fun expenseTargetsAreVisibleExpenseCategoriesWithoutTheDeletedOne() {
        assertEquals(
            listOf("bills" to listOf("power"), "food" to listOf("grocery"), "only" to listOf("lonely")),
            targets("rent"),
        )
        // A group left empty by removing the deleted category is dropped.
        assertEquals(
            listOf("bills" to listOf("rent", "power"), "food" to listOf("grocery")),
            targets("lonely"),
        )
    }

    @Test
    fun incomeTargetsAreOtherIncomeCategories() {
        assertEquals(listOf("income" to listOf("bonus")), targets("salary"))
    }

    @Test
    fun anUnknownCategoryHasNoTargets() {
        assertEquals(emptyList<Pair<String, List<String>>>(), targets("missing"))
    }
}
