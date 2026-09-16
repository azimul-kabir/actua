package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDragReorderTest {
    private fun category(id: String, groupId: String, sortOrder: Double) =
        ActualCategory(id, id, groupId, isIncome = false, hidden = false, sortOrder = sortOrder)

    private fun groups() = listOf(
        ActualCategoryGroup(
            "bills", "Bills", isIncome = false, hidden = false, sortOrder = 1.0,
            categories = listOf(category("rent", "bills", 1.0), category("electric", "bills", 2.0), category("water", "bills", 3.0)),
        ),
        ActualCategoryGroup(
            "fun", "Fun", isIncome = false, hidden = false, sortOrder = 2.0,
            categories = listOf(category("dining", "fun", 1.0)),
        ),
    )

    @Test fun stepDownReordersWithinTheSameGroup() {
        val updated = CategoryDragReorder.step(groups(), "rent", +1)!!
        assertEquals(listOf("electric", "rent", "water"), updated.first { it.id == "bills" }.categories.map { it.id })
    }

    @Test fun stepUpReordersWithinTheSameGroup() {
        val updated = CategoryDragReorder.step(groups(), "water", -1)!!
        assertEquals(listOf("rent", "water", "electric"), updated.first { it.id == "bills" }.categories.map { it.id })
    }

    @Test fun stepNeverCrossesIntoTheNextGroup() {
        assertNull(CategoryDragReorder.step(groups(), "water", +1))
    }

    @Test fun stepNeverCrossesIntoThePreviousGroup() {
        assertNull(CategoryDragReorder.step(groups(), "rent", -1))
    }

    @Test fun stepOnASingleCategoryGroupIsNoop() {
        assertNull(CategoryDragReorder.step(groups(), "dining", +1))
        assertNull(CategoryDragReorder.step(groups(), "dining", -1))
    }

    @Test fun finalMoveTargetsTheCategoryCurrentlyAfterIt() {
        val move = CategoryDragReorder.finalMove(groups(), "rent")!!
        assertEquals(CategoryReorderPlanner.CategoryMove("rent", "bills", "electric"), move)
    }

    @Test fun finalMoveIsNullBeforeIdForTheLastCategoryInAGroup() {
        val move = CategoryDragReorder.finalMove(groups(), "water")!!
        assertEquals(CategoryReorderPlanner.CategoryMove("water", "bills", null), move)
    }

    @Test fun hasMovedIsFalseWhenPositionWithinGroupIsUnchanged() {
        assertFalse(CategoryDragReorder.hasMoved(groups(), groups(), "electric"))
    }

    @Test fun hasMovedIsTrueAfterAStep() {
        val stepped = CategoryDragReorder.step(groups(), "rent", +1)!!
        assertTrue(CategoryDragReorder.hasMoved(groups(), stepped, "rent"))
    }
}
