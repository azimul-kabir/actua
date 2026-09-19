package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritePreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() {
        context.getSharedPreferences("favorite_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun favoritesPersistAndAreScopedToBudgetAndType() {
        val preferences = FavoritePreferences(context)
        preferences.set("budget-a", FavoritePreferences.Type.CATEGORY, "groceries", true)

        assertTrue(FavoritePreferences(context).contains("budget-a", FavoritePreferences.Type.CATEGORY, "groceries"))
        assertFalse(preferences.contains("budget-b", FavoritePreferences.Type.CATEGORY, "groceries"))
        assertFalse(preferences.contains("budget-a", FavoritePreferences.Type.ACCOUNT, "groceries"))

        preferences.set("budget-a", FavoritePreferences.Type.CATEGORY, "groceries", false)
        assertFalse(preferences.contains("budget-a", FavoritePreferences.Type.CATEGORY, "groceries"))
    }

    @Test fun replaceRemovesDeselectedFavorites() {
        val preferences = FavoritePreferences(context)
        preferences.set("budget-a", FavoritePreferences.Type.CATEGORY, "groceries", true)
        preferences.set("budget-a", FavoritePreferences.Type.CATEGORY, "rent", true)

        preferences.replace("budget-a", FavoritePreferences.Type.CATEGORY, setOf("rent"))

        assertFalse(preferences.contains("budget-a", FavoritePreferences.Type.CATEGORY, "groceries"))
        assertTrue(preferences.contains("budget-a", FavoritePreferences.Type.CATEGORY, "rent"))
    }
}
