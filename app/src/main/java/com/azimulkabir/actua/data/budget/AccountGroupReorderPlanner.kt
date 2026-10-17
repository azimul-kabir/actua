package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualAccountGroup

/**
 * Pure reordering logic for custom account groups, mirroring [ActualEntityWriter.moveAccountGroup]:
 * a `null` before-id always means "move to the end".
 */
object AccountGroupReorderPlanner {
    data class GroupMove(val groupId: String, val beforeGroupId: String?)

    /** Moves [groupId] before [targetGroupId] (end when null). */
    fun moveGroup(
        groups: List<ActualAccountGroup>,
        groupId: String,
        targetGroupId: String?,
    ): Pair<List<ActualAccountGroup>, GroupMove>? {
        if (groupId == targetGroupId) return null
        if (groups.none { it.id == groupId }) return null
        val moving = groups.first { it.id == groupId }
        val rest = groups.filterNot { it.id == groupId }
        val insertAt = targetGroupId?.let { id -> rest.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: rest.size
        val updated = rest.toMutableList().apply { add(insertAt, moving) }
        return updated to GroupMove(groupId, targetGroupId)
    }

    /** Moves [groupId] one place up the list. */
    fun moveGroupUp(groups: List<ActualAccountGroup>, groupId: String): Pair<List<ActualAccountGroup>, GroupMove>? {
        val index = groups.indexOfFirst { it.id == groupId }
        if (index <= 0) return null
        return moveGroup(groups, groupId, groups[index - 1].id)
    }

    /** Moves [groupId] one place down the list. */
    fun moveGroupDown(groups: List<ActualAccountGroup>, groupId: String): Pair<List<ActualAccountGroup>, GroupMove>? {
        val index = groups.indexOfFirst { it.id == groupId }
        if (index < 0 || index >= groups.size - 1) return null
        return moveGroup(groups, groupId, groups.getOrNull(index + 2)?.id)
    }
}
