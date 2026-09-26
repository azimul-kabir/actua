package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualAccount

/**
 * Pure helpers for the Reorder Accounts drag gesture. Stepping reuses
 * [AccountReorderPlanner.moveAccountUp]/[AccountReorderPlanner.moveAccountDown].
 */
object AccountDragReorder {
    /** The move that would persist [accountId]'s current position in the full account order. */
    fun finalMove(accounts: List<ActualAccount>, accountId: String): AccountReorderPlanner.AccountMove? {
        val index = accounts.indexOfFirst { it.id == accountId }
        if (index < 0) return null
        val nextId = accounts.getOrNull(index + 1)?.id
        return AccountReorderPlanner.AccountMove(accountId, nextId)
    }

    /** Whether [accountId] sits at a different index in [current] than in [original]. */
    fun hasMoved(original: List<ActualAccount>, current: List<ActualAccount>, accountId: String): Boolean =
        original.indexOfFirst { it.id == accountId } != current.indexOfFirst { it.id == accountId }
}
