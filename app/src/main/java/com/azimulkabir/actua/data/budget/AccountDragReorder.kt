package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualAccount

/**
 * Pure helpers for the Reorder Accounts drag gesture. Stepping reuses
 * [AccountReorderPlanner.moveAccountUp]/[AccountReorderPlanner.moveAccountDown]
 * and [AccountReorderPlanner.moveAccountUpInSection]/[AccountReorderPlanner.moveAccountDownInSection].
 */
object AccountDragReorder {
    /** The move that would persist [accountId]'s current position in the full account order. */
    fun finalMove(accounts: List<ActualAccount>, accountId: String): AccountReorderPlanner.AccountMove? {
        val index = accounts.indexOfFirst { it.id == accountId }
        if (index < 0) return null
        val nextId = accounts.getOrNull(index + 1)?.id
        return AccountReorderPlanner.AccountMove(accountId, nextId)
    }

    /** The move that would persist [accountId]'s position across chunked groups and sections. */
    fun finalMove(
        chunks: List<ReorderChunk>,
        accountId: String,
        originalGroupId: String?,
    ): AccountReorderPlanner.AccountMove? {
        val fullList = chunks.flatMap { it.accounts }
        val index = fullList.indexOfFirst { it.id == accountId }
        if (index < 0) return null
        val nextId = fullList.getOrNull(index + 1)?.id
        val currentChunk = chunks.firstOrNull { c -> c.accounts.any { it.id == accountId } }
        val newGroupId = currentChunk?.groupId
        val updateGroupId = newGroupId != originalGroupId
        return AccountReorderPlanner.AccountMove(
            accountId = accountId,
            beforeAccountId = nextId,
            targetGroupId = newGroupId,
            updateGroupId = updateGroupId,
        )
    }

    /** Whether [accountId] sits at a different index in [current] than in [original]. */
    fun hasMoved(original: List<ActualAccount>, current: List<ActualAccount>, accountId: String): Boolean =
        original.indexOfFirst { it.id == accountId } != current.indexOfFirst { it.id == accountId }

    /** Whether [accountId] has moved in position or changed account group across chunks. */
    @JvmName("hasMovedChunks")
    fun hasMoved(
        originalChunks: List<ReorderChunk>,
        currentChunks: List<ReorderChunk>,
        accountId: String,
    ): Boolean {
        val origList = originalChunks.flatMap { it.accounts }
        val currList = currentChunks.flatMap { it.accounts }
        val origIndex = origList.indexOfFirst { it.id == accountId }
        val currIndex = currList.indexOfFirst { it.id == accountId }
        if (origIndex != currIndex) return true
        val origGroup = originalChunks.firstOrNull { c -> c.accounts.any { it.id == accountId } }?.groupId
        val currGroup = currentChunks.firstOrNull { c -> c.accounts.any { it.id == accountId } }?.groupId
        return origGroup != currGroup
    }
}
