package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup

/**
 * Pure helpers for the Manage Categories drag gesture. Unlike [CategoryReorderPlanner.moveCategoryUp]/
 * [CategoryReorderPlanner.moveCategoryDown], [step] never crosses into a neighboring group — the Manage
 * Categories drag only reorders a category within its own group; moving it to a different group is a
 * separate, explicit "Move to group" action.
 */
object CategoryDragReorder {
    /** Moves [categoryId] one place in [direction] (-1 up, +1 down) within its own group, or null at that edge. */
    fun step(groups: List<ActualCategoryGroup>, categoryId: String, direction: Int): List<ActualCategoryGroup>? {
        val source = groups.firstOrNull { g -> g.categories.any { it.id == categoryId } } ?: return null
        val index = source.categories.indexOfFirst { it.id == categoryId }
        return if (direction < 0) {
            if (index <= 0) null
            else CategoryReorderPlanner.moveCategory(groups, categoryId, source.id, source.categories[index - 1].id)?.first
        } else {
            if (index < 0 || index >= source.categories.size - 1) null
            else {
                val targetId = source.categories.getOrNull(index + 2)?.id
                CategoryReorderPlanner.moveCategory(groups, categoryId, source.id, targetId)?.first
            }
        }
    }

    /** The move that would persist [categoryId]'s current position within its own group. */
    fun finalMove(groups: List<ActualCategoryGroup>, categoryId: String): CategoryReorderPlanner.CategoryMove? {
        val source = groups.firstOrNull { g -> g.categories.any { it.id == categoryId } } ?: return null
        val index = source.categories.indexOfFirst { it.id == categoryId }
        val nextId = source.categories.getOrNull(index + 1)?.id
        return CategoryReorderPlanner.CategoryMove(categoryId, source.id, nextId)
    }

    /** Whether [categoryId] sits at a different index within its group in [current] than in [original]. */
    fun hasMoved(original: List<ActualCategoryGroup>, current: List<ActualCategoryGroup>, categoryId: String): Boolean {
        fun indexOf(groups: List<ActualCategoryGroup>): Int {
            val g = groups.firstOrNull { it.categories.any { c -> c.id == categoryId } } ?: return -1
            return g.categories.indexOfFirst { it.id == categoryId }
        }
        return indexOf(original) != indexOf(current)
    }
}
