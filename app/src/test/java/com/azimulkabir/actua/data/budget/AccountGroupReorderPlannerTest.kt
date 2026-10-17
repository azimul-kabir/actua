package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualAccountGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountGroupReorderPlannerTest {
    private fun group(id: String, sortOrder: Double) =
        ActualAccountGroup(id, id, sortOrder = sortOrder)

    private fun groups() = listOf(
        group("personal", 1.0),
        group("business", 2.0),
        group("savings", 3.0),
    )

    @Test fun moveGroupReordersToBeforeTarget() {
        val (updated, move) = AccountGroupReorderPlanner.moveGroup(groups(), "savings", "personal")!!
        assertEquals(AccountGroupReorderPlanner.GroupMove("savings", "personal"), move)
        assertEquals(listOf("savings", "personal", "business"), updated.map { it.id })
    }

    @Test fun moveGroupToEndWhenTargetIsNull() {
        val (updated, move) = AccountGroupReorderPlanner.moveGroup(groups(), "personal", null)!!
        assertEquals(AccountGroupReorderPlanner.GroupMove("personal", null), move)
        assertEquals(listOf("business", "savings", "personal"), updated.map { it.id })
    }

    @Test fun moveGroupIsNullWhenMovingToItself() {
        assertNull(AccountGroupReorderPlanner.moveGroup(groups(), "personal", "personal"))
    }

    @Test fun moveGroupIsNullWhenGroupIsMissing() {
        assertNull(AccountGroupReorderPlanner.moveGroup(groups(), "missing", "personal"))
    }

    @Test fun moveGroupUpSwapsWithPreviousGroup() {
        val (updated, move) = AccountGroupReorderPlanner.moveGroupUp(groups(), "business")!!
        assertEquals(AccountGroupReorderPlanner.GroupMove("business", "personal"), move)
        assertEquals(listOf("business", "personal", "savings"), updated.map { it.id })
    }

    @Test fun moveGroupUpIsNullForFirstGroup() {
        assertNull(AccountGroupReorderPlanner.moveGroupUp(groups(), "personal"))
    }

    @Test fun moveGroupDownSwapsWithNextGroup() {
        val (updated, move) = AccountGroupReorderPlanner.moveGroupDown(groups(), "personal")!!
        assertEquals(AccountGroupReorderPlanner.GroupMove("personal", "savings"), move)
        assertEquals(listOf("business", "personal", "savings"), updated.map { it.id })
    }

    @Test fun moveGroupDownIsNullForLastGroup() {
        assertNull(AccountGroupReorderPlanner.moveGroupDown(groups(), "savings"))
    }
}
