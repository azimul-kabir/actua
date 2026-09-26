package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualAccount

/**
 * Pure reordering logic for the drag-to-reorder accounts screen. Accounts are a single flat
 * list (unlike category groups, there is no fixed-position entry), mirroring
 * [ActualEntityWriter.moveAccount]: a `null` before-id always means "move to the end".
 */
object AccountReorderPlanner {
    data class AccountMove(val accountId: String, val beforeAccountId: String?)

    /** Moves [accountId] before [targetAccountId] (end when null). */
    fun moveAccount(
        accounts: List<ActualAccount>,
        accountId: String,
        targetAccountId: String?,
    ): Pair<List<ActualAccount>, AccountMove>? {
        if (accountId == targetAccountId) return null
        if (accounts.none { it.id == accountId }) return null
        val moving = accounts.first { it.id == accountId }
        val rest = accounts.filterNot { it.id == accountId }
        val insertAt = targetAccountId?.let { id -> rest.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: rest.size
        val updated = rest.toMutableList().apply { add(insertAt, moving) }
        return updated to AccountMove(accountId, targetAccountId)
    }

    /** Moves [accountId] one place up the list. */
    fun moveAccountUp(accounts: List<ActualAccount>, accountId: String): Pair<List<ActualAccount>, AccountMove>? {
        val index = accounts.indexOfFirst { it.id == accountId }
        if (index <= 0) return null
        return moveAccount(accounts, accountId, accounts[index - 1].id)
    }

    /** Moves [accountId] one place down the list. */
    fun moveAccountDown(accounts: List<ActualAccount>, accountId: String): Pair<List<ActualAccount>, AccountMove>? {
        val index = accounts.indexOfFirst { it.id == accountId }
        if (index < 0 || index >= accounts.size - 1) return null
        return moveAccount(accounts, accountId, accounts.getOrNull(index + 2)?.id)
    }
}
