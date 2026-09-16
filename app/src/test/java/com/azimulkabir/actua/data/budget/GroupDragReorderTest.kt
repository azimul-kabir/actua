package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupDragReorderTest {
    private fun groups() = listOf(
        ActualCategoryGroup("bills", "Bills", isIncome = false, hidden = false, sortOrder = 1.0, categories = emptyList<ActualCategory>()),
        ActualCategoryGroup("fun", "Fun", isIncome = false, hidden = false, sortOrder = 2.0, categories = emptyList()),
        ActualCategoryGroup("income", "Income", isIncome = true, hidden = false, sortOrder = 3.0, categories = emptyList()),
    )

    @Test fun finalMoveTargetsTheGroupCurrentlyAfterIt() {
        val move = GroupDragReorder.finalMove(groups(), "bills")!!
        assertEquals(CategoryReorderPlanner.GroupMove("bills", "fun"), move)
    }

    @Test fun finalMoveIsNullBeforeIdForTheLastGroup() {
        val move = GroupDragReorder.finalMove(groups(), "income")!!
        assertEquals(CategoryReorderPlanner.GroupMove("income", null), move)
    }

    @Test fun finalMoveIsNullWhenGroupIsMissing() {
        assertNull(GroupDragReorder.finalMove(groups(), "missing"))
    }

    @Test fun hasMovedIsFalseWhenOrderIsUnchanged() {
        assertFalse(GroupDragReorder.hasMoved(groups(), groups(), "fun"))
    }

    @Test fun hasMovedIsTrueAfterAStep() {
        val stepped = CategoryReorderPlanner.moveGroupDown(groups(), "bills")!!.first
        assertTrue(GroupDragReorder.hasMoved(groups(), stepped, "bills"))
    }
}
