package com.azimulkabir.actua.model

/**
 * How a category is named between screens (Home, Budget, transactions, automations, widgets): its id,
 * or its name for a category without one. Names repeat across category groups, so a name alone cannot say
 * which category was meant; an id can.
 */
val BudgetCategory.categoryKey: String get() = id ?: name

/**
 * The group and category that [key] names in [groups], or null when there is none. An id is matched first;
 * a key that is only a name (from a screen state saved before keys, or a category without an id) falls back
 * to the first category with that name.
 */
fun findCategory(groups: List<BudgetGroup>, key: String): Pair<BudgetGroup, BudgetCategory>? {
    groups.forEach { group -> group.categories.firstOrNull { it.id == key }?.let { return group to it } }
    groups.forEach { group -> group.categories.firstOrNull { it.name == key }?.let { return group to it } }
    return null
}

/** Whether [first] and [second] name the same category in [groups]: the same key, or two keys for one category. */
fun sameCategory(groups: List<BudgetGroup>, first: String?, second: String?): Boolean {
    if (first == second) return true
    if (first == null || second == null) return false
    val a = findCategory(groups, first)?.second ?: return false
    val b = findCategory(groups, second)?.second ?: return false
    return a.categoryKey == b.categoryKey
}
