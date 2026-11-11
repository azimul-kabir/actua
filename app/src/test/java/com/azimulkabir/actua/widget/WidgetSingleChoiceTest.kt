package com.azimulkabir.actua.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSingleChoiceTest {
    private data class Item(val id: String, val name: String)

    private val items = listOf(Item("a", "Checking"), Item("b", "Savings"), Item("c", "Credit card"))

    private fun resolve(selected: String?) = resolveSingle(items, selected) { it.id }

    @Test
    fun theChosenItemIsShown() {
        assertEquals(SingleChoice.Found(items[1]), resolve("b"))
    }

    @Test
    fun skippingSetupShowsTheFirstItem() {
        assertEquals(SingleChoice.Found(items[0]), resolve(null))
    }

    @Test
    fun aRemovedItemIsNotFoundRatherThanReplacedByAnother() {
        assertEquals(SingleChoice.NotFound, resolve("gone"))
        // Not even when the budget still has items, and the first one is not silently shown instead.
        assertEquals(SingleChoice.NotFound, resolveSingle(listOf(Item("a", "Checking")), "b") { it.id })
    }

    @Test
    fun nothingToShowIsEmpty() {
        assertEquals(SingleChoice.Empty, resolveSingle(emptyList<Item>(), null) { it.id })
    }

    @Test
    fun aChosenItemInAnEmptyBudgetIsNotFound() {
        assertEquals(SingleChoice.NotFound, resolveSingle(emptyList<Item>(), "a") { it.id })
    }
}
