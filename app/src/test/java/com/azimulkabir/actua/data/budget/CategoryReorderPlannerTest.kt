package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryReorderPlannerTest {
    private fun category(id: String, groupId: String, sortOrder: Double, isIncome: Boolean = false, hidden: Boolean = false) =
        ActualCategory(id, id, groupId, isIncome, hidden, sortOrder)

    private fun groups() = listOf(
        ActualCategoryGroup(
            "bills", "Bills", isIncome = false, hidden = false, sortOrder = 1.0,
            categories = listOf(category("rent", "bills", 1.0), category("electric", "bills", 2.0)),
        ),
        ActualCategoryGroup(
            "fun", "Fun", isIncome = false, hidden = false, sortOrder = 2.0,
            categories = listOf(category("dining", "fun", 1.0)),
        ),
        ActualCategoryGroup(
            "income", "Income", isIncome = true, hidden = false, sortOrder = 3.0,
            categories = listOf(category("salary", "income", 1.0, isIncome = true)),
        ),
    )

    @Test fun moveCategoryWithinSameGroupReordersOnly() {
        val (updated, move) = CategoryReorderPlanner.moveCategory(groups(), "electric", "bills", "rent")!!
        assertEquals(CategoryReorderPlanner.CategoryMove("electric", "bills", "rent"), move)
        assertEquals(listOf("electric", "rent"), updated.first { it.id == "bills" }.categories.map { it.id })
    }

    @Test fun moveCategoryAcrossGroupsReassignsGroupAndFlags() {
        val (updated, move) = CategoryReorderPlanner.moveCategory(groups(), "rent", "fun", "dining")!!
        assertEquals(CategoryReorderPlanner.CategoryMove("rent", "fun", "dining"), move)
        assertEquals(listOf("electric"), updated.first { it.id == "bills" }.categories.map { it.id })
        assertEquals(listOf("rent", "dining"), updated.first { it.id == "fun" }.categories.map { it.id })
    }

    @Test fun moveCategoryToEndOfGroupWhenBeforeIdNull() {
        val (updated, move) = CategoryReorderPlanner.moveCategory(groups(), "rent", "bills", null)!!
        assertEquals(CategoryReorderPlanner.CategoryMove("rent", "bills", null), move)
        assertEquals(listOf("electric", "rent"), updated.first { it.id == "bills" }.categories.map { it.id })
    }

    @Test fun moveCategoryUpCrossesIntoPreviousGroupAtTopOfGroup() {
        val (updated, move) = CategoryReorderPlanner.moveCategoryUp(groups(), "dining")!!
        assertEquals("bills", move.groupId)
        assertNull(move.beforeCategoryId)
        assertEquals(listOf("rent", "electric", "dining"), updated.first { it.id == "bills" }.categories.map { it.id })
        assertEquals(emptyList<String>(), updated.first { it.id == "fun" }.categories.map { it.id })
    }

    @Test fun moveCategoryUpAtVeryTopIsNoop() {
        assertNull(CategoryReorderPlanner.moveCategoryUp(groups(), "rent"))
    }

    @Test fun moveCategoryDownCrossesIntoNextGroupAtBottomOfGroup() {
        val (updated, move) = CategoryReorderPlanner.moveCategoryDown(groups(), "electric")!!
        assertEquals("fun", move.groupId)
        assertEquals("dining", move.beforeCategoryId)
        assertEquals(listOf("rent"), updated.first { it.id == "bills" }.categories.map { it.id })
        assertEquals(listOf("electric", "dining"), updated.first { it.id == "fun" }.categories.map { it.id })
    }

    @Test fun moveCategoryCannotCrossIntoIncomeGroup() {
        assertNull(CategoryReorderPlanner.moveCategoryDown(groups(), "dining"))
    }

    @Test fun moveGroupReordersExpenseGroupsOnly() {
        val (updated, move) = CategoryReorderPlanner.moveGroup(groups(), "fun", "bills")!!
        assertEquals(CategoryReorderPlanner.GroupMove("fun", "bills"), move)
        assertEquals(listOf("fun", "bills", "income"), updated.map { it.id })
    }

    @Test fun moveGroupToEndWhenTargetNull() {
        val (updated, move) = CategoryReorderPlanner.moveGroup(groups(), "bills", null)!!
        assertEquals(CategoryReorderPlanner.GroupMove("bills", null), move)
        assertEquals(listOf("fun", "income", "bills"), updated.map { it.id })
    }

    @Test fun moveGroupCannotMoveTheIncomeGroup() {
        assertNull(CategoryReorderPlanner.moveGroup(groups(), "income", "bills"))
    }

    @Test fun moveGroupUpAndDownRespectEdgesAndIncomeExclusion() {
        assertNull(CategoryReorderPlanner.moveGroupUp(groups(), "bills"))
        val (updated, move) = CategoryReorderPlanner.moveGroupDown(groups(), "bills")!!
        assertEquals(CategoryReorderPlanner.GroupMove("bills", null), move)
        assertEquals(listOf("fun", "bills", "income"), updated.map { it.id })
        assertNull(CategoryReorderPlanner.moveGroupDown(groups(), "fun"))
    }
}
