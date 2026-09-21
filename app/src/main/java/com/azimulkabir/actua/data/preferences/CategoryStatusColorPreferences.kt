package com.azimulkabir.actua.data.preferences

import android.content.Context
import com.azimulkabir.actua.model.BudgetProgressState

/**
 * Local/device-level overrides for the five category status colors (status dot and progress
 * bar). Stored as ARGB ints; a missing or invalid entry falls back to Actua's theme-derived
 * default for that status rather than a fixed value, so the picker only needs to hold what the
 * user actually customized.
 */
class CategoryStatusColorPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "category_status_color_preferences",
        Context.MODE_PRIVATE,
    )

    fun colorOverride(status: BudgetProgressState): Int? {
        val key = keyFor(status)
        return if (preferences.contains(key)) preferences.getInt(key, 0) else null
    }

    fun setColorOverride(status: BudgetProgressState, argb: Int) {
        preferences.edit().putInt(keyFor(status), argb).apply()
    }

    fun resetToDefaults() {
        preferences.edit().clear().apply()
    }

    private fun keyFor(status: BudgetProgressState): String = "status_color_${status.name}"
}
