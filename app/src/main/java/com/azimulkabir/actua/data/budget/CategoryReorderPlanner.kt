package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup

/**
 * Pure reordering logic shared by drag-and-drop and the up/down accessible controls in the
 * category management UI. Mirrors [ActualEntityWriter.moveCategory] and
 * [ActualEntityWriter.moveCategoryGroup]: a `null` before-id always means "move to the end", and
 * the income category group's position is fixed (its categories still reorder freely).
 */
object CategoryReorderPlanner {
    data class CategoryMove(val categoryId: String, val groupId: String, val beforeCategoryId: String?)
    data class GroupMove(val groupId: String, val beforeGroupId: String?)

    /** Moves [categoryId] to just before [targetCategoryId] inside [targetGroupId] (end when null). */
    fun moveCategory(
        groups: List<ActualCategoryGroup>,
        categoryId: String,
        targetGroupId: String,
        targetCategoryId: String?,
    ): Pair<List<ActualCategoryGroup>, CategoryMove>? {
        if (categoryId == targetCategoryId) return null
        val source = groups.firstOrNull { g -> g.categories.any { it.id == categoryId } } ?: return null
        val category = source.categories.first { it.id == categoryId }
        val target = groups.firstOrNull { it.id == targetGroupId } ?: return null
        val withoutCategory = groups.map { g ->
            if (g.id == source.id) g.copy(categories = g.categories.filterNot { it.id == categoryId }) else g
        }
        val updated = withoutCategory.map { g ->
            if (g.id != targetGroupId) return@map g
            val moved = category.copy(groupId = target.id, isIncome = target.isIncome, hidden = target.hidden)
            val insertAt = targetCategoryId?.let { id -> g.categories.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
                ?: g.categories.size
            g.copy(categories = g.categories.toMutableList().apply { add(insertAt, moved) })
        }
        return updated to CategoryMove(categoryId, targetGroupId, targetCategoryId)
    }

    /** Moves [categoryId] one place up its flattened group order, crossing group boundaries at the edges. */
    fun moveCategoryUp(groups: List<ActualCategoryGroup>, categoryId: String): Pair<List<ActualCategoryGroup>, CategoryMove>? {
        val source = groups.firstOrNull { g -> g.categories.any { it.id == categoryId } } ?: return null
        val index = source.categories.indexOfFirst { it.id == categoryId }
        return if (index > 0) {
            moveCategory(groups, categoryId, source.id, source.categories[index - 1].id)
        } else {
            val previous = previousGroup(groups, source.id) ?: return null
            moveCategory(groups, categoryId, previous.id, null)
        }
    }

    /** Moves [categoryId] one place down its flattened group order, crossing group boundaries at the edges. */
    fun moveCategoryDown(groups: List<ActualCategoryGroup>, categoryId: String): Pair<List<ActualCategoryGroup>, CategoryMove>? {
        val source = groups.firstOrNull { g -> g.categories.any { it.id == categoryId } } ?: return null
        val index = source.categories.indexOfFirst { it.id == categoryId }
        return if (index < source.categories.size - 1) {
            moveCategory(groups, categoryId, source.id, source.categories.getOrNull(index + 2)?.id)
        } else {
            val next = nextGroup(groups, source.id) ?: return null
            moveCategory(groups, categoryId, next.id, next.categories.firstOrNull()?.id)
        }
    }

    /** Moves [groupId] before [targetGroupId] (end when null). The income group cannot be moved. */
    fun moveGroup(
        groups: List<ActualCategoryGroup>,
        groupId: String,
        targetGroupId: String?,
    ): Pair<List<ActualCategoryGroup>, GroupMove>? {
        if (groupId == targetGroupId) return null
        val moving = groups.firstOrNull { it.id == groupId } ?: return null
        if (moving.isIncome) return null
        val rest = groups.filterNot { it.id == groupId }
        val insertAt = targetGroupId?.let { id -> rest.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: rest.size
        val updated = rest.toMutableList().apply { add(insertAt, moving) }
        return updated to GroupMove(groupId, targetGroupId)
    }

    /** Moves the reorderable (non-income) group containing [groupId] up one position. */
    fun moveGroupUp(groups: List<ActualCategoryGroup>, groupId: String): Pair<List<ActualCategoryGroup>, GroupMove>? {
        val reorderable = groups.filterNot { it.isIncome }
        val index = reorderable.indexOfFirst { it.id == groupId }
        if (index <= 0) return null
        return moveGroup(groups, groupId, reorderable[index - 1].id)
    }

    /** Moves the reorderable (non-income) group containing [groupId] down one position. */
    fun moveGroupDown(groups: List<ActualCategoryGroup>, groupId: String): Pair<List<ActualCategoryGroup>, GroupMove>? {
        val reorderable = groups.filterNot { it.isIncome }
        val index = reorderable.indexOfFirst { it.id == groupId }
        if (index < 0 || index >= reorderable.size - 1) return null
        // Falling off the end of the reorderable neighbors must land just before the fixed-position
        // income group, not at the true end of [groups] (moveGroup's null target), or the group would
        // jump past the income group's position instead of merely swapping with its last neighbor.
        val targetId = reorderable.getOrNull(index + 2)?.id ?: groups.firstOrNull { it.isIncome }?.id
        return moveGroup(groups, groupId, targetId)
    }

    private fun previousGroup(groups: List<ActualCategoryGroup>, groupId: String): ActualCategoryGroup? {
        val reorderable = groups.filterNot { it.isIncome }
        val index = reorderable.indexOfFirst { it.id == groupId }
        return if (index > 0) reorderable[index - 1] else null
    }

    private fun nextGroup(groups: List<ActualCategoryGroup>, groupId: String): ActualCategoryGroup? {
        val reorderable = groups.filterNot { it.isIncome }
        val index = reorderable.indexOfFirst { it.id == groupId }
        return if (index in 0 until reorderable.size - 1) reorderable[index + 1] else null
    }
}
