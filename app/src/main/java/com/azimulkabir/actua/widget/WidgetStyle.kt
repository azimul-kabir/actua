package com.azimulkabir.actua.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb

/** The colors a non-default [WidgetStyle] gives every widget (ARGB ints, as RemoteViews takes them). */
class WidgetStyleColors(
    val background: Int,
    val onBackground: Int,
    val secondary: Int,
    val primary: Int,
    val button: Int,
    val buttonText: Int,
    val progressTrack: Int,
    /** The tonal surface behind amount pills and the budget tiles. */
    val tile: Int,
)

/**
 * The global home-screen widget look, a supporter extra on the Play build. [Default] leaves the
 * widgets exactly as they are (including Material You device colors); the others override the
 * background and every text color so each stays readable.
 */
enum class WidgetStyle {
    Default,
    Translucent,
    SolidDark,
    SolidLight,

    /** Tinted with the app's accent color; needs Android 12 to tint the rounded background. */
    AccentTinted,
    ;

    /**
     * The colors for this style, or null for [Default], which changes nothing. [night] is the device's
     * dark mode, which [Translucent] follows; [accent] is only used by [AccentTinted].
     */
    fun colors(night: Boolean, accent: ColorScheme?): WidgetStyleColors? = when (this) {
        Default -> null
        // A scrim in the device theme: light with dark text, or dark with light text. Either reads
        // on both light and dark wallpapers because the scrim is mostly opaque.
        Translucent -> if (night) {
            WidgetStyleColors(
                background = 0xCC101014.toInt(), onBackground = 0xFFFFFFFF.toInt(), secondary = 0xFFE3DFE8.toInt(),
                primary = 0xFFD0D8FF.toInt(), button = 0x26FFFFFF, buttonText = 0xFFD0D8FF.toInt(),
                progressTrack = 0x40FFFFFF, tile = 0x26FFFFFF,
            )
        } else {
            WidgetStyleColors(
                background = 0xD9F5F2FA.toInt(), onBackground = 0xFF1C1B20.toInt(), secondary = 0xFF4A4753.toInt(),
                primary = 0xFF33427A.toInt(), button = 0x24485786, buttonText = 0xFF33427A.toInt(),
                progressTrack = 0x40485786, tile = 0x24485786,
            )
        }
        SolidDark -> WidgetStyleColors(
            background = 0xFF1E1B22.toInt(), onBackground = 0xFFE7E1E9.toInt(), secondary = 0xFFC9C4CE.toInt(),
            primary = 0xFFBAC6FF.toInt(), button = 0xFF3D4666.toInt(), buttonText = 0xFFBAC6FF.toInt(),
            progressTrack = 0xFF3D4666.toInt(), tile = 0xFF3D4666.toInt(),
        )
        SolidLight -> WidgetStyleColors(
            background = 0xFFF5F2FA.toInt(), onBackground = 0xFF1C1B20.toInt(), secondary = 0xFF625F68.toInt(),
            primary = 0xFF485786.toInt(), button = 0xFFDFE4FF.toInt(), buttonText = 0xFF485786.toInt(),
            progressTrack = 0xFFDFE4FF.toInt(), tile = 0xFFDFE4FF.toInt(),
        )
        AccentTinted -> accent?.let { scheme ->
            val background = scheme.primaryContainer.toArgb()
            val onBackground = scheme.onPrimaryContainer.toArgb()
            WidgetStyleColors(
                background = background,
                onBackground = onBackground,
                secondary = blendArgb(onBackground, background, 0.97f),
                primary = onBackground,
                button = scheme.primary.toArgb(),
                buttonText = scheme.onPrimary.toArgb(),
                progressTrack = blendArgb(onBackground, background, 0.25f),
                tile = blendArgb(onBackground, background, 0.08f),
            )
        }
    }

    companion object {
        fun fromName(name: String?): WidgetStyle = entries.firstOrNull { it.name == name } ?: Default
    }
}

/** [foreground] over [background] at [ratio] (0 = all background, 1 = all foreground), opaque. */
internal fun blendArgb(foreground: Int, background: Int, ratio: Float): Int {
    fun channel(shift: Int): Int {
        val f = (foreground shr shift) and 0xFF
        val b = (background shr shift) and 0xFF
        return (b + (f - b) * ratio).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
