package com.azimulkabir.actua.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryIconTest {
    @Test fun parsesAndRoundTripsPrefixedKeys() {
        val emoji = CategoryIcon.parse("emoji:🍕")
        assertEquals(CategoryIcon("emoji", "🍕"), emoji)
        assertEquals("emoji:🍕", emoji.toString())
        assertEquals(CategoryIcon("lucide", "pizza"), CategoryIcon.parse("lucide:pizza"))
        listOf("", "🍕", ":🍕", "emoji:", "emoji: ").forEach { assertNull(CategoryIcon.parse(it)) }
    }

    @Test fun reservesSpaceOnlyWhenAnOfferedRowHasAnIcon() {
        val icons = mapOf("dining" to "🍕")
        assertFalse(reservesCategoryIconSlot(emptyMap(), sequenceOf("dining")))
        assertFalse(reservesCategoryIconSlot(icons, sequenceOf("rent", null)))
        assertFalse(reservesCategoryIconSlot(icons, emptySequence()))
        assertTrue(reservesCategoryIconSlot(icons, sequenceOf("rent", "dining")))
    }
}
