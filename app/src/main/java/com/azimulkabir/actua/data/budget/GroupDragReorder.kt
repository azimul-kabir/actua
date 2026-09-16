package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup

/**
 * Pure helpers for the Reorder Groups drag gesture. Stepping reuses [CategoryReorderPlanner.moveGroupUp]/
 * [CategoryReorderPlanner.moveGroupDown], which already keep the income group's position fixed.
 */
object GroupDragReorder {
    /** The move that would persist [groupId]'s current position in the full group order. */
    fun finalMove(groups: List<ActualCategoryGroup>, groupId: String): CategoryReorderPlanner.GroupMove? {
        val index = groups.indexOfFirst { it.id == groupId }
        if (index < 0) return null
        val nextId = groups.getOrNull(index + 1)?.id
        return CategoryReorderPlanner.GroupMove(groupId, nextId)
    }

    /** Whether [groupId] sits at a different index in [current] than in [original]. */
    fun hasMoved(original: List<ActualCategoryGroup>, current: List<ActualCategoryGroup>, groupId: String): Boolean =
        original.indexOfFirst { it.id == groupId } != current.indexOfFirst { it.id == groupId }
}
