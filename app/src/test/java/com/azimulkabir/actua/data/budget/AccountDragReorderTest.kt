package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDragReorderTest {
    private fun account(id: String, sortOrder: Double) =
        ActualAccount(id, id, ActualAccountType.CHECKING, offBudget = false, closed = false, sortOrder = sortOrder, balanceCents = 0)

    private fun accounts() = listOf(account("checking", 1.0), account("savings", 2.0), account("cash", 3.0))

    @Test fun finalMoveTargetsTheAccountCurrentlyAfterIt() {
        val move = AccountDragReorder.finalMove(accounts(), "checking")!!
        assertEquals(AccountReorderPlanner.AccountMove("checking", "savings"), move)
    }

    @Test fun finalMoveIsNullBeforeIdForTheLastAccount() {
        val move = AccountDragReorder.finalMove(accounts(), "cash")!!
        assertEquals(AccountReorderPlanner.AccountMove("cash", null), move)
    }

    @Test fun finalMoveIsNullWhenAccountIsMissing() {
        assertNull(AccountDragReorder.finalMove(accounts(), "missing"))
    }

    @Test fun hasMovedIsFalseWhenOrderIsUnchanged() {
        assertFalse(AccountDragReorder.hasMoved(accounts(), accounts(), "savings"))
    }

    @Test fun hasMovedIsTrueAfterAStep() {
        val stepped = AccountReorderPlanner.moveAccountDown(accounts(), "checking")!!.first
        assertTrue(AccountDragReorder.hasMoved(accounts(), stepped, "checking"))
    }
}
