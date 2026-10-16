package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.azimulkabir.actua.ui.theme.danger
import com.azimulkabir.actua.ui.theme.success
import com.azimulkabir.actua.ui.theme.warning
import kotlin.math.max
import kotlin.math.min

/**
 * Semantic colors for financial charts (income, expense, overspending, balance, forecast).
 */
data class ChartSemanticColors(
    val income: Color,
    val expense: Color,
    val overspending: Color,
    val balance: Color,
    val forecast: Color,
)

/**
 * Container holding the complete chart palette: semantic colors and a 12-color categorical set.
 */
data class ChartPaletteColors(
    val semantic: ChartSemanticColors,
    val categorical: List<Color>,
)

/**
 * Chart color palette providing semantic colors and a 12-color categorical palette checked for
 * contrast in light and dark themes as well as Material You dynamic coloring.
 */
object ChartPalette {
    val LightSemantic = ChartSemanticColors(
        income = Color(0xFF16A34A),
        expense = Color(0xFFDC2626),
        overspending = Color(0xFFD97706),
        balance = Color(0xFF4F46E5),
        forecast = Color(0xFF7C3AED),
    )

    val DarkSemantic = ChartSemanticColors(
        income = Color(0xFF4ADE80),
        expense = Color(0xFFF87171),
        overspending = Color(0xFFFBBF24),
        balance = Color(0xFF818CF8),
        forecast = Color(0xFFA78BFA),
    )

    /**
     * 12 distinct categorical colors designed for high contrast and readability on light surfaces.
     */
    val LightCategorical: List<Color> = listOf(
        Color(0xFF4338CA), // Indigo
        Color(0xFF047857), // Emerald
        Color(0xFFB45309), // Amber
        Color(0xFFBE123C), // Rose
        Color(0xFF0E7490), // Cyan
        Color(0xFF6D28D9), // Violet
        Color(0xFFC2410C), // Orange
        Color(0xFF0F766E), // Teal
        Color(0xFFBE185D), // Pink
        Color(0xFF1D4ED8), // Blue
        Color(0xFF4D7C0F), // Lime
        Color(0xFF334155), // Slate
    )

    /**
     * 12 distinct categorical colors designed for high contrast and readability on dark surfaces.
     */
    val DarkCategorical: List<Color> = listOf(
        Color(0xFF818CF8), // Indigo
        Color(0xFF34D399), // Emerald
        Color(0xFFFBBF24), // Amber
        Color(0xFFFB7185), // Rose
        Color(0xFF22D3EE), // Cyan
        Color(0xFFA78BFA), // Violet
        Color(0xFFFB923C), // Orange
        Color(0xFF2DD4BF), // Teal
        Color(0xFFF472B6), // Pink
        Color(0xFF60A5FA), // Blue
        Color(0xFFA3E635), // Lime
        Color(0xFF94A3B8), // Slate
    )

    fun light(): ChartPaletteColors = ChartPaletteColors(LightSemantic, LightCategorical)
    fun dark(): ChartPaletteColors = ChartPaletteColors(DarkSemantic, DarkCategorical)

    fun categoricalColor(index: Int, isDark: Boolean = false): Color {
        val list = if (isDark) DarkCategorical else LightCategorical
        return list[Math.floorMod(index, list.size)]
    }
}

/**
 * Calculates WCAG contrast ratio between [foreground] and [background].
 * Range is from 1.0 (no contrast) to 21.0 (black on white).
 */
fun calculateContrastRatio(foreground: Color, background: Color): Float {
    val l1 = max(foreground.luminance(), background.luminance())
    val l2 = min(foreground.luminance(), background.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
}

/**
 * Resolves the chart palette appropriate for current theme and dynamic color preferences.
 */
@Composable
fun rememberChartPalette(
    isDark: Boolean = MaterialTheme.colorScheme.background.luminance() <= 0.5f,
    useDynamicColor: Boolean = false,
): ChartPaletteColors {
    val colors = MaterialTheme.colorScheme
    val dynamicSuccess = colors.success
    val dynamicDanger = colors.danger
    val dynamicWarning = colors.warning
    return remember(isDark, useDynamicColor, colors, dynamicSuccess, dynamicDanger, dynamicWarning) {
        val baseSemantic = if (isDark) ChartPalette.DarkSemantic else ChartPalette.LightSemantic
        val semantic = if (useDynamicColor) {
            ChartSemanticColors(
                income = dynamicSuccess,
                expense = dynamicDanger,
                overspending = dynamicWarning,
                balance = colors.primary,
                forecast = colors.tertiary,
            )
        } else {
            baseSemantic
        }
        val categorical = if (isDark) ChartPalette.DarkCategorical else ChartPalette.LightCategorical
        ChartPaletteColors(semantic, categorical)
    }
}
