package com.azimulkabir.actua.data.budget

import kotlin.math.floor

/** Exact port of Actual's shoveSortOrders placement rules. */
object SortOrder {
    const val INCREMENT = 16_384.0
    data class Position(val id: String, val sortOrder: Double)
    data class Placement(val sortOrder: Double, val moved: List<Position>)

    fun shove(items: List<Position>, before: String?): Placement {
        val target = items.indexOfFirst { it.id == before }
        if (target < 0) return Placement((items.lastOrNull()?.sortOrder ?: 0.0) + INCREMENT, emptyList())
        val preceding = if (target > 0) items[target - 1].sortOrder else 0.0
        val moved = mutableListOf<Position>()
        if (items[target].sortOrder - preceding <= 2) {
            var index = target; var order = floor(items[target].sortOrder) + INCREMENT
            while (index < items.size && order > items[index].sortOrder) {
                moved += Position(items[index].id, order); index++; order += INCREMENT
            }
        }
        val above = items[target].sortOrder
        return Placement(if (target > 0) (items[target - 1].sortOrder + above) / 2 else above / 2, moved)
    }
}
