package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryKeyTest {
    private fun category(name: String, id: String?) = BudgetCategory(name = name, assigned = 0, spent = 0, id = id)

    private val wardrobeInGroup1 = category("Wardrobe", "wardrobe-1")
    private val wardrobeInGroup3 = category("Wardrobe", "wardrobe-3")
    private val rent = category("Rent", "rent")
    private val groups = listOf(
        BudgetGroup("Category Group 1", listOf(rent, wardrobeInGroup1)),
        BudgetGroup("Category Group 3", listOf(wardrobeInGroup3)),
    )

    @Test
    fun aCategoryIsKeyedByItsIdOrElseItsName() {
        assertEquals("wardrobe-3", wardrobeInGroup3.categoryKey)
        assertEquals("No id", category("No id", null).categoryKey)
    }

    @Test
    fun aFavoriteInTheSecondGroupOpensThatGroupAndNotTheFirstWithTheSameName() {
        val (group, found) = findCategory(groups, wardrobeInGroup3.categoryKey)!!
        assertEquals("Category Group 3", group.name)
        assertEquals(wardrobeInGroup3, found)

        val (firstGroup, first) = findCategory(groups, wardrobeInGroup1.categoryKey)!!
        assertEquals("Category Group 1", firstGroup.name)
        assertEquals(wardrobeInGroup1, first)
    }

    @Test
    fun aKeyThatIsOnlyANameFindsTheFirstCategoryWithThatName() {
        // A screen state saved before keys, or a category without an id.
        val (group, found) = findCategory(groups, "Wardrobe")!!
        assertEquals("Category Group 1", group.name)
        assertEquals(wardrobeInGroup1, found)
        assertEquals(rent, findCategory(groups, "Rent")?.second)
    }

    @Test
    fun aCategoryWithoutAnIdIsFoundByItsName() {
        val noId = category("Gifts", null)
        val withNoId = listOf(BudgetGroup("Fun", listOf(noId)))
        assertEquals(noId, findCategory(withNoId, noId.categoryKey)?.second)
    }

    @Test
    fun anIdIsMatchedBeforeAnyName() {
        // Another category happens to be named like this one's id: the id still wins.
        val tricky = listOf(
            BudgetGroup("A", listOf(category("wardrobe-3", "other"))),
            BudgetGroup("B", listOf(wardrobeInGroup3)),
        )
        assertEquals("B", findCategory(tricky, "wardrobe-3")?.first?.name)
    }

    @Test
    fun anUnknownKeyFindsNothing() {
        assertNull(findCategory(groups, "gone"))
        assertNull(findCategory(emptyList(), "Wardrobe"))
    }

    @Test
    fun twoKeysForOneCategoryAreTheSameAndTwoDuplicatesAreNot() {
        assertTrue(sameCategory(groups, "rent", "Rent"))
        assertTrue(sameCategory(groups, "wardrobe-3", "wardrobe-3"))
        assertFalse("same name, different groups", sameCategory(groups, "wardrobe-1", "wardrobe-3"))
        assertFalse(sameCategory(groups, "rent", "wardrobe-1"))
    }

    @Test
    fun noKeyIsOnlyTheSameAsNoKey() {
        assertTrue(sameCategory(groups, null, null))
        assertFalse(sameCategory(groups, null, "rent"))
        assertFalse(sameCategory(groups, "rent", null))
        assertFalse("a key that names nothing", sameCategory(groups, "gone", "rent"))
    }
}
