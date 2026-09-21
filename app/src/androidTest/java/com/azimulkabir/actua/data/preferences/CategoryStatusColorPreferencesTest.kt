package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.model.BudgetProgressState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryStatusColorPreferencesTest {
    @Test
    fun missingValuesFallBackToNullAndOverridesSurviveRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("category_status_color_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()

        val fresh = CategoryStatusColorPreferences(context)
        BudgetProgressState.entries.forEach { status ->
            assertNull(fresh.colorOverride(status))
        }

        fresh.setColorOverride(BudgetProgressState.OVERSPENT, 0xFFAA0000.toInt())
        fresh.setColorOverride(BudgetProgressState.FUNDED, 0xFF00AA00.toInt())

        // Simulate a restart by reading back through a new instance over the same store.
        val reloaded = CategoryStatusColorPreferences(context)
        assertEquals(0xFFAA0000.toInt(), reloaded.colorOverride(BudgetProgressState.OVERSPENT))
        assertEquals(0xFF00AA00.toInt(), reloaded.colorOverride(BudgetProgressState.FUNDED))
        assertNull(reloaded.colorOverride(BudgetProgressState.SPENDING))
    }

    @Test
    fun resetToDefaultsClearsAllOverrides() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("category_status_color_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()

        val preferences = CategoryStatusColorPreferences(context)
        BudgetProgressState.entries.forEach { status ->
            preferences.setColorOverride(status, 0xFF123456.toInt())
        }
        preferences.resetToDefaults()

        BudgetProgressState.entries.forEach { status ->
            assertNull(preferences.colorOverride(status))
        }
    }
}
