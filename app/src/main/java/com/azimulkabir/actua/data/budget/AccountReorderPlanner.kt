package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualAccount

/** One reorderable bucket of accounts on the Reorder Accounts screen. */
data class ReorderChunk(
    val sectionTitle: String,
    val groupName: String?,
    val accounts: List<ActualAccount>,
    val groupId: String? = null,
) {
    val key: String get() = "$sectionTitle|${groupId ?: groupName ?: ""}"
}

/**
 * Pure reordering logic for the drag-to-reorder accounts screen. Accounts are a single flat
 * list (unlike category groups, there is no fixed-position entry), mirroring
 * [ActualEntityWriter.moveAccount]: a `null` before-id always means "move to the end".
 */
object AccountReorderPlanner {
    data class AccountMove(
        val accountId: String,
        val beforeAccountId: String?,
        val targetGroupId: String? = null,
        val updateGroupId: Boolean = false,
    )

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

    /**
     * Moves [accountId] up within its section across groups, without crossing into other sections.
     * Returns the updated chunk list and the resulting [AccountMove], or null if already at the top of the section.
     */
    fun moveAccountUpInSection(
        chunks: List<ReorderChunk>,
        accountId: String,
    ): Pair<List<ReorderChunk>, AccountMove>? {
        val chunkIndex = chunks.indexOfFirst { it.accounts.any { acc -> acc.id == accountId } }
        if (chunkIndex < 0) return null
        val chunk = chunks[chunkIndex]
        val sectionTitle = chunk.sectionTitle
        val accountIndex = chunk.accounts.indexOfFirst { it.id == accountId }
        val movingAccount = chunk.accounts[accountIndex]

        val updatedChunks = chunks.toMutableList()
        val originalGroupId = movingAccount.groupId

        if (accountIndex > 0) {
            val newAccounts = chunk.accounts.toMutableList()
            java.util.Collections.swap(newAccounts, accountIndex, accountIndex - 1)
            updatedChunks[chunkIndex] = chunk.copy(accounts = newAccounts)
        } else {
            val prevChunkIndex = (chunkIndex - 1 downTo 0).firstOrNull { chunks[it].sectionTitle == sectionTitle }
                ?: return null
            val prevChunk = chunks[prevChunkIndex]

            val newCurrentAccounts = chunk.accounts.toMutableList().apply { removeAt(accountIndex) }
            val newPrevAccounts = prevChunk.accounts.toMutableList().apply {
                add(movingAccount.copy(groupId = prevChunk.groupId))
            }
            updatedChunks[chunkIndex] = chunk.copy(accounts = newCurrentAccounts)
            updatedChunks[prevChunkIndex] = prevChunk.copy(accounts = newPrevAccounts)
        }

        val move = AccountDragReorder.finalMove(updatedChunks, accountId, originalGroupId) ?: return null
        return updatedChunks to move
    }

    /**
     * Moves [accountId] down within its section across groups, without crossing into other sections.
     * Returns the updated chunk list and the resulting [AccountMove], or null if already at the bottom of the section.
     */
    fun moveAccountDownInSection(
        chunks: List<ReorderChunk>,
        accountId: String,
    ): Pair<List<ReorderChunk>, AccountMove>? {
        val chunkIndex = chunks.indexOfFirst { it.accounts.any { acc -> acc.id == accountId } }
        if (chunkIndex < 0) return null
        val chunk = chunks[chunkIndex]
        val sectionTitle = chunk.sectionTitle
        val accountIndex = chunk.accounts.indexOfFirst { it.id == accountId }
        val movingAccount = chunk.accounts[accountIndex]

        val updatedChunks = chunks.toMutableList()
        val originalGroupId = movingAccount.groupId

        if (accountIndex < chunk.accounts.size - 1) {
            val newAccounts = chunk.accounts.toMutableList()
            java.util.Collections.swap(newAccounts, accountIndex, accountIndex + 1)
            updatedChunks[chunkIndex] = chunk.copy(accounts = newAccounts)
        } else {
            val nextChunkIndex = (chunkIndex + 1 until chunks.size).firstOrNull { chunks[it].sectionTitle == sectionTitle }
                ?: return null
            val nextChunk = chunks[nextChunkIndex]

            val newCurrentAccounts = chunk.accounts.toMutableList().apply { removeAt(accountIndex) }
            val newNextAccounts = nextChunk.accounts.toMutableList().apply {
                add(0, movingAccount.copy(groupId = nextChunk.groupId))
            }
            updatedChunks[chunkIndex] = chunk.copy(accounts = newCurrentAccounts)
            updatedChunks[nextChunkIndex] = nextChunk.copy(accounts = newNextAccounts)
        }

        val move = AccountDragReorder.finalMove(updatedChunks, accountId, originalGroupId) ?: return null
        return updatedChunks to move
    }
}
