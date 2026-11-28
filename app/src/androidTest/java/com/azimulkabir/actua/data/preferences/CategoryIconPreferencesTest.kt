package com.azimulkabir.actua.data.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryIconPreferencesTest {
    @Test fun categoryIconsRoundTripIndependentlyForEachBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = IconPreferences(context)
        val firstBudget = UUID.randomUUID().toString()
        val secondBudget = UUID.randomUUID().toString()
        try {
            preferences.setCategory(firstBudget, "dining", "emoji:🍕")
            preferences.setCategory(firstBudget, "rent", "emoji:🏠")
            preferences.setCategory(secondBudget, "dining", "emoji:🍔")

            val reopened = IconPreferences(context)
            assertEquals(mapOf("dining" to "emoji:🍕", "rent" to "emoji:🏠"), reopened.categories(firstBudget))
            assertEquals(mapOf("dining" to "emoji:🍔"), reopened.categories(secondBudget))

            reopened.setCategory(firstBudget, "dining", null)
            assertEquals(mapOf("rent" to "emoji:🏠"), preferences.categories(firstBudget))
            assertEquals(mapOf("dining" to "emoji:🍔"), preferences.categories(secondBudget))
        } finally {
            preferences.setCategory(firstBudget, "dining", null)
            preferences.setCategory(firstBudget, "rent", null)
            preferences.setCategory(secondBudget, "dining", null)
        }
    }

    @Test fun accountIconsAreScopedToTheBudgetAndEntityKind() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = IconPreferences(context)
        val firstBudget = UUID.randomUUID().toString()
        val secondBudget = UUID.randomUUID().toString()
        val id = "shared-id"
        try {
            preferences.setAccount(firstBudget, id, "emoji:💳")
            preferences.setAccount(secondBudget, id, "emoji:💰")
            preferences.setCategory(firstBudget, id, "emoji:🍕")

            val reopened = IconPreferences(context)
            assertEquals(mapOf(id to "emoji:💳"), reopened.accounts(firstBudget))
            assertEquals(mapOf(id to "emoji:💰"), reopened.accounts(secondBudget))
            reopened.setAccount(firstBudget, id, null)
            assertTrue(reopened.accounts(firstBudget).isEmpty())
            assertEquals(mapOf(id to "emoji:🍕"), reopened.categories(firstBudget))
            assertEquals(mapOf(id to "emoji:💰"), reopened.accounts(secondBudget))
        } finally {
            preferences.setAccount(firstBudget, id, null)
            preferences.setAccount(secondBudget, id, null)
            preferences.setCategory(firstBudget, id, null)
        }
    }

    @Test fun acceptsExactlyOneEmojiGrapheme() {
        listOf("🍕", "✈️", "👩‍💻", "👨‍👩‍👧‍👦", "👍🏽", "🇧🇩", "1️⃣", "🏳️‍🌈").forEach {
            assertTrue("Expected a single emoji: $it", isCategoryEmoji(it))
        }
        listOf("", "a", "é", "1", "🏽", "🇧", "🍕🍔", "🍕 ", "🍕‍", "🍕️️", "☕︎", "🍕🏽", "👍🏽🏽").forEach {
            assertFalse("Expected rejection: $it", isCategoryEmoji(it))
        }
    }
}
