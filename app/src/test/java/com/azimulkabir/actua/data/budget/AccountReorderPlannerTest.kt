package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountReorderPlannerTest {
    private fun account(id: String, sortOrder: Double) =
        ActualAccount(id, id, ActualAccountType.CHECKING, offBudget = false, closed = false, sortOrder = sortOrder, balanceCents = 0)

    private fun accounts() = listOf(
        account("checking", 1.0),
        account("savings", 2.0),
        account("cash", 3.0),
    )

    @Test fun moveAccountReordersToBeforeTarget() {
        val (updated, move) = AccountReorderPlanner.moveAccount(accounts(), "cash", "checking")!!
        assertEquals(AccountReorderPlanner.AccountMove("cash", "checking"), move)
        assertEquals(listOf("cash", "checking", "savings"), updated.map { it.id })
    }

    @Test fun moveAccountToEndWhenTargetIsNull() {
        val (updated, move) = AccountReorderPlanner.moveAccount(accounts(), "checking", null)!!
        assertEquals(AccountReorderPlanner.AccountMove("checking", null), move)
        assertEquals(listOf("savings", "cash", "checking"), updated.map { it.id })
    }

    @Test fun moveAccountIsNullWhenMovingToItself() {
        assertNull(AccountReorderPlanner.moveAccount(accounts(), "checking", "checking"))
    }

    @Test fun moveAccountIsNullWhenAccountIsMissing() {
        assertNull(AccountReorderPlanner.moveAccount(accounts(), "missing", "checking"))
    }

    @Test fun moveAccountUpSwapsWithThePreviousAccount() {
        val (updated, move) = AccountReorderPlanner.moveAccountUp(accounts(), "savings")!!
        assertEquals(AccountReorderPlanner.AccountMove("savings", "checking"), move)
        assertEquals(listOf("savings", "checking", "cash"), updated.map { it.id })
    }

    @Test fun moveAccountUpIsNullForTheFirstAccount() {
        assertNull(AccountReorderPlanner.moveAccountUp(accounts(), "checking"))
    }

    @Test fun moveAccountDownSwapsWithTheNextAccount() {
        val (updated, move) = AccountReorderPlanner.moveAccountDown(accounts(), "checking")!!
        assertEquals(AccountReorderPlanner.AccountMove("checking", "cash"), move)
        assertEquals(listOf("savings", "checking", "cash"), updated.map { it.id })
    }

    @Test fun moveAccountDownIsNullForTheLastAccount() {
        assertNull(AccountReorderPlanner.moveAccountDown(accounts(), "cash"))
    }

    @Test fun moveAccountDownInSectionMovesBetweenGroupsWithinSection() {
        val acc1 = account("acc1", 1.0).copy(groupId = "grp-a")
        val acc2 = account("acc2", 2.0).copy(groupId = "grp-a")
        val acc3 = account("acc3", 3.0).copy(groupId = "grp-b")
        val off1 = account("off1", 4.0).copy(offBudget = true)
        val chunks = listOf(
            ReorderChunk("On budget", "Household", listOf(acc1, acc2), groupId = "grp-a"),
            ReorderChunk("On budget", "Savings", listOf(acc3), groupId = "grp-b"),
            ReorderChunk("Off budget", null, listOf(off1), groupId = null),
        )

        // acc2 is at bottom of Household group -> moving down should enter Savings group
        val (stepped, move) = AccountReorderPlanner.moveAccountDownInSection(chunks, "acc2")!!
        assertEquals(AccountReorderPlanner.AccountMove("acc2", "acc3", targetGroupId = "grp-b", updateGroupId = true), move)
        val onBudgetGroupB = stepped.first { it.groupId == "grp-b" }
        assertEquals(listOf("acc2", "acc3"), onBudgetGroupB.accounts.map { it.id })
        assertEquals("grp-b", onBudgetGroupB.accounts.first { it.id == "acc2" }.groupId)

        // acc3 is now at bottom of On budget section -> moving down must NOT cross into Off budget
        val cannotCross = AccountReorderPlanner.moveAccountDownInSection(stepped, "acc3")
        assertNull(cannotCross)
    }

    @Test fun moveAccountUpInSectionMovesBetweenGroupsWithinSection() {
        val acc1 = account("acc1", 1.0).copy(groupId = "grp-a")
        val acc2 = account("acc2", 2.0).copy(groupId = "grp-b")
        val off1 = account("off1", 3.0).copy(offBudget = true)
        val chunks = listOf(
            ReorderChunk("On budget", "Household", listOf(acc1), groupId = "grp-a"),
            ReorderChunk("On budget", "Savings", listOf(acc2), groupId = "grp-b"),
            ReorderChunk("Off budget", null, listOf(off1), groupId = null),
        )

        // acc2 is at top of Savings group -> moving up should enter Household group at the end
        val (stepped, move) = AccountReorderPlanner.moveAccountUpInSection(chunks, "acc2")!!
        assertEquals(AccountReorderPlanner.AccountMove("acc2", "off1", targetGroupId = "grp-a", updateGroupId = true), move)
        val onBudgetGroupA = stepped.first { it.groupId == "grp-a" }
        assertEquals(listOf("acc1", "acc2"), onBudgetGroupA.accounts.map { it.id })
        assertEquals("grp-a", onBudgetGroupA.accounts.first { it.id == "acc2" }.groupId)

        // off1 is at top of Off budget section -> moving up must NOT cross into On budget
        val cannotCross = AccountReorderPlanner.moveAccountUpInSection(chunks, "off1")
        assertNull(cannotCross)
    }
}
