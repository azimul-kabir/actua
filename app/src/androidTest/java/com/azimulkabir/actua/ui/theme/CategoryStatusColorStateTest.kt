package com.azimulkabir.actua.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.model.BudgetProgressState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryStatusColorStateTest {
    @Test
    fun overridesApplyImmediatelyAndSurviveRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("category_status_color_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()

        val state = CategoryStatusColorState(context)
        assertNull(state.overrideOrNull(BudgetProgressState.FUNDED))

        val chosen = Color(0xFF112233)
        state.setOverride(BudgetProgressState.FUNDED, chosen)
        assertEquals(chosen, state.overrideOrNull(BudgetProgressState.FUNDED))

        // Simulate a restart by reading the override back through a new instance over the same store.
        val reloaded = CategoryStatusColorState(context)
        assertEquals(chosen, reloaded.overrideOrNull(BudgetProgressState.FUNDED))
        assertNull(reloaded.overrideOrNull(BudgetProgressState.OVERSPENT))
    }

    @Test
    fun showDotsDefaultsOnAndTogglePersistsAcrossInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
            .edit().remove("show_category_status_dots").commit()

        val state = CategoryStatusColorState(context)
        assertEquals(true, state.showDots)

        state.updateShowDots(false)
        assertEquals(false, state.showDots)

        val reloaded = CategoryStatusColorState(context)
        assertEquals(false, reloaded.showDots)

        // Leave the shared display_preferences store as found for other tests.
        context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
            .edit().remove("show_category_status_dots").commit()
    }

    @Test
    fun resetToDefaultsClearsEveryOverride() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("category_status_color_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()

        val state = CategoryStatusColorState(context)
        BudgetProgressState.entries.forEach { state.setOverride(it, Color(0xFF445566)) }
        state.resetToDefaults()

        BudgetProgressState.entries.forEach { assertNull(state.overrideOrNull(it)) }
    }
}
