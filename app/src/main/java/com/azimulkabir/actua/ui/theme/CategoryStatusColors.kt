package com.azimulkabir.actua.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.azimulkabir.actua.data.preferences.CategoryStatusColorPreferences
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.model.BudgetProgressState

/**
 * Holds the user's configured overrides for the five [BudgetProgressState] colors, backed by
 * [CategoryStatusColorPreferences]. This is the single source of truth the status dot and the
 * category progress bar both read through [categoryStatusColor], so a color change is reflected
 * everywhere it's shown without those surfaces needing to know about each other.
 */
@Stable
class CategoryStatusColorState(context: Context) {
    private val preferences = CategoryStatusColorPreferences(context.applicationContext)
    private val displayPreferences = DisplayPreferences(context.applicationContext)
    private val overrides = mutableStateMapOf<BudgetProgressState, Color>().apply {
        BudgetProgressState.entries.forEach { status ->
            preferences.colorOverride(status)?.let { put(status, Color(it)) }
        }
    }

    var showDots by mutableStateOf(displayPreferences.showCategoryStatusDots)
        private set

    fun overrideOrNull(status: BudgetProgressState): Color? = overrides[status]

    fun setOverride(status: BudgetProgressState, color: Color) {
        overrides[status] = color
        preferences.setColorOverride(status, color.toArgb())
    }

    fun resetToDefaults() {
        overrides.clear()
        preferences.resetToDefaults()
    }

    fun updateShowDots(show: Boolean) {
        showDots = show
        displayPreferences.showCategoryStatusDots = show
    }
}

// Null (rather than a Context-backed default) so previews and Compose UI tests that render a
// screen without wrapping the app root can still resolve status colors — they simply fall back
// to the theme defaults below instead of reading a real override.
val LocalCategoryStatusColors = staticCompositionLocalOf<CategoryStatusColorState?> { null }

/** Material You-semantic default, used when the user hasn't overridden a status color. */
@Composable
fun defaultCategoryStatusColor(status: BudgetProgressState): Color {
    val colors = MaterialTheme.colorScheme
    return when (status) {
        BudgetProgressState.OVERSPENT -> colors.error
        BudgetProgressState.SPENT -> colors.warning
        BudgetProgressState.SPENDING -> colors.primary
        BudgetProgressState.FUNDED -> colors.success
        BudgetProgressState.UNASSIGNED -> colors.onSurfaceVariant
    }
}

/**
 * The shared color resolver: the status dot and category progress bar both call this so a
 * configured color reads identically wherever a category's status is shown.
 */
@Composable
fun categoryStatusColor(status: BudgetProgressState): Color =
    LocalCategoryStatusColors.current?.overrideOrNull(status) ?: defaultCategoryStatusColor(status)
