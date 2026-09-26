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
}
