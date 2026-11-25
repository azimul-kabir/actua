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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.azimulkabir.actua.data.preferences.CategoryStatusColorPreferences
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.model.BudgetProgressState

/**
 * Holds the user's configured overrides for the [BudgetProgressState] colors, backed by
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

    /**
     * Opt-in "Use accent color": progress bars, status dots and balance pills take the accent for the
     * on-track statuses (see [accentStatusColor]), and balances are colored by status rather than sign.
     */
    var colorBudgetedAmounts by mutableStateOf(displayPreferences.colorBudgetedAmounts)
        private set

    fun updateColorBudgetedAmounts(enabled: Boolean) {
        colorBudgetedAmounts = enabled
        displayPreferences.colorBudgetedAmounts = enabled
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

// Budget status colors are fixed semantic hues (green, amber, gray, red, blue), not the accent, so a
// status reads the same under every accent theme. The goal-in-progress amber is deeper than
// [warning] so it holds 3:1 on a white surface.
private val GoalAmberLight = Color(0xFFD97706)
private val GoalAmberDark = Color(0xFFFBBF24)
private val NeutralStatusLight = Color(0xFF6B7280)
private val NeutralStatusDark = Color(0xFF9CA3AF)
private val PastGoalBlueLight = Color(0xFF0284C7)
private val PastGoalBlueDark = Color(0xFF38BDF8)

/** Hues from here round through red to [WarningHueEnd] read as red, pink-red or amber. */
private const val WarningHueStart = 330f
private const val WarningHueEnd = 60f

/** Below this saturation an accent reads as gray, like the neutral statuses. */
private const val MinAccentSaturation = 0.25f

/**
 * Whether [color] can stand for an on-track status without looking like a warning or a neutral
 * status: not red, pink-red or amber (Rose, the gold preset, a custom red or orange) and not gray.
 */
internal fun accentReadsAsOnTrack(color: Color): Boolean {
    val (hue, saturation) = CustomAccent.toHsv(color.toArgb())
    return saturation >= MinAccentSaturation && hue > WarningHueEnd && hue < WarningHueStart
}

/**
 * The status color "Use accent color" gives [status], or null to keep the semantic default: the
 * on-track statuses take [primary], Funded past goal takes [tertiary] so it still differs from Funded.
 * Overspent, Goal in progress, Fully spent and No money assigned always keep red, amber and gray, and
 * an accent that reads as a warning or gray keeps every default.
 */
internal fun accentStatusColor(status: BudgetProgressState, primary: Color, tertiary: Color): Color? {
    if (!accentReadsAsOnTrack(primary)) return null
    return when (status) {
        BudgetProgressState.FUNDED, BudgetProgressState.SPENDING, BudgetProgressState.GOAL_REACHED -> primary
        BudgetProgressState.GOAL_OVERFUNDED -> tertiary.takeIf(::accentReadsAsOnTrack)
        BudgetProgressState.OVERSPENT, BudgetProgressState.GOAL_IN_PROGRESS,
        BudgetProgressState.SPENT, BudgetProgressState.UNASSIGNED -> null
    }
}

/**
 * The default for [status] when the user hasn't overridden its color: the semantic hue, or the
 * accent where "Use accent color" is on (see [accentStatusColor]).
 */
@Composable
fun defaultCategoryStatusColor(status: BudgetProgressState): Color {
    val colors = MaterialTheme.colorScheme
    if (LocalCategoryStatusColors.current?.colorBudgetedAmounts == true) {
        accentStatusColor(status, colors.primary, colors.tertiary)?.let { return it }
    }
    val light = colors.background.luminance() > 0.5f
    val catppuccin = LocalCatppuccin.current
    val amber = if (catppuccin != null) colors.warning else if (light) GoalAmberLight else GoalAmberDark
    val neutral = if (catppuccin != null) colors.onSurfaceVariant
        else if (light) NeutralStatusLight else NeutralStatusDark
    val blue = catppuccin?.flavor(!light)?.palette?.accents?.get(CatppuccinAccent.Blue)
        ?: if (light) PastGoalBlueLight else PastGoalBlueDark
    return when (status) {
        BudgetProgressState.OVERSPENT -> colors.danger
        BudgetProgressState.SPENT -> neutral
        BudgetProgressState.SPENDING -> colors.success
        BudgetProgressState.FUNDED -> colors.success
        BudgetProgressState.UNASSIGNED -> neutral
        BudgetProgressState.GOAL_IN_PROGRESS -> amber
        BudgetProgressState.GOAL_REACHED -> colors.success
        BudgetProgressState.GOAL_OVERFUNDED -> blue
    }
}

/**
 * The shared color resolver: the status dot and category progress bar both call this so a
 * configured color reads identically wherever a category's status is shown.
 */
@Composable
fun categoryStatusColor(status: BudgetProgressState): Color =
    LocalCategoryStatusColors.current?.overrideOrNull(status) ?: defaultCategoryStatusColor(status)

private const val StatusTintAlpha = 0.16f
private const val MinTextContrast = 4.5

/**
 * A readable version of a status [fill] for text: [fill] itself when it already reaches 4.5:1, else
 * the fill pulled toward black or white until it does. The balance pill tints its background with the
 * fill at 16%, so [tinted] measures against that. Saved overrides and Catppuccin tones go through this too.
 */
@Composable
fun statusTextColor(fill: Color, tinted: Boolean = true): Color {
    val surface = MaterialTheme.colorScheme.surface
    val background = if (tinted) fill.copy(alpha = StatusTintAlpha).compositeOver(surface) else surface
    if (contrastRatio(fill, background) >= MinTextContrast) return fill
    val target = if (surface.luminance() > 0.5f) Color.Black else Color.White
    for (step in 1..20) {
        val candidate = lerp(fill, target, step / 20f)
        if (contrastRatio(candidate, background) >= MinTextContrast) return candidate
    }
    return target
}

/**
 * The color of a balance that is shown without a status (sign only): green when positive, red when
 * negative, null for zero. Reads the Funded and Overspent colors, so a saved override applies.
 */
@Composable
fun signedBalanceColor(amountCents: Long): Color? = when {
    amountCents < 0L -> categoryStatusColor(BudgetProgressState.OVERSPENT)
    amountCents > 0L -> categoryStatusColor(BudgetProgressState.FUNDED)
    else -> null
}
