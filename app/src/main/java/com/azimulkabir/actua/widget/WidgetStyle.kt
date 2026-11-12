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
    /** A category's progress bar from 85% spent, readable on [background]. */
    val warning: Int,
    /** A category's progress bar once it is overspent, readable on [background]. */
    val danger: Int,
) {
    /** The color a category's progress bar is filled with for [status]. */
    internal fun progressFill(status: ProgressStatus): Int = when (status) {
        ProgressStatus.Normal -> primary
        ProgressStatus.Warning -> warning
        ProgressStatus.Overspent -> danger
    }
}

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

    /** Whether this style is drawn from the app's accent, so a new accent has to reach the widgets. */
    val followsAccent: Boolean get() = this == AccentTinted

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
                warning = DARK_WARNING, danger = DARK_DANGER,
            )
        } else {
            WidgetStyleColors(
                background = 0xD9F5F2FA.toInt(), onBackground = 0xFF1C1B20.toInt(), secondary = 0xFF4A4753.toInt(),
                primary = 0xFF33427A.toInt(), button = 0x24485786, buttonText = 0xFF33427A.toInt(),
                progressTrack = 0x40485786, tile = 0x24485786,
                warning = LIGHT_WARNING, danger = LIGHT_DANGER,
            )
        }
        SolidDark -> WidgetStyleColors(
            background = 0xFF1E1B22.toInt(), onBackground = 0xFFE7E1E9.toInt(), secondary = 0xFFC9C4CE.toInt(),
            primary = 0xFFBAC6FF.toInt(), button = 0xFF3D4666.toInt(), buttonText = 0xFFBAC6FF.toInt(),
            progressTrack = 0xFF3D4666.toInt(), tile = 0xFF3D4666.toInt(),
            warning = DARK_WARNING, danger = DARK_DANGER,
        )
        SolidLight -> WidgetStyleColors(
            background = 0xFFF5F2FA.toInt(), onBackground = 0xFF1C1B20.toInt(), secondary = 0xFF625F68.toInt(),
            primary = 0xFF485786.toInt(), button = 0xFFDFE4FF.toInt(), buttonText = 0xFF485786.toInt(),
            progressTrack = 0xFFDFE4FF.toInt(), tile = 0xFFDFE4FF.toInt(),
            warning = LIGHT_WARNING, danger = LIGHT_DANGER,
        )
        AccentTinted -> accent?.let { scheme ->
            val background = scheme.primaryContainer.toArgb()
            val onBackground = scheme.onPrimaryContainer.toArgb()
            val tile = blendArgb(onBackground, background, 0.08f)
            WidgetStyleColors(
                background = background,
                onBackground = onBackground,
                secondary = secondaryTone(onBackground, background, tile),
                primary = onBackground,
                button = scheme.primary.toArgb(),
                buttonText = scheme.onPrimary.toArgb(),
                progressTrack = blendArgb(onBackground, background, 0.25f),
                tile = tile,
                warning = statusTone(LIGHT_WARNING, DARK_WARNING, background),
                danger = statusTone(LIGHT_DANGER, DARK_DANGER, background),
            )
        }
    }

    companion object {
        fun fromName(name: String?): WidgetStyle = entries.firstOrNull { it.name == name } ?: Default
    }
}

// Progress bar tones for the amber and red states: the deeper pair for light backgrounds, the lighter for dark.
// The light pair is deep enough to keep 3:1 even on the translucent light scrim over a black wallpaper.
private const val LIGHT_WARNING = 0xFF8A5400.toInt()
private const val LIGHT_DANGER = 0xFFB3261E.toInt()
private const val DARK_WARNING = 0xFFFFB74D.toInt()
private const val DARK_DANGER = 0xFFFF8A80.toInt()

/** The contrast a graphic needs against its background to stay a clear shape (WCAG's 3:1). */
private const val MIN_STATUS_CONTRAST = 3.0

/**
 * The status color for a progress bar on [background]: whichever of [light] and [dark] stands out more, moved
 * toward white or black only if that is still short of 3:1, as it can be on a mid-tone accent container.
 */
internal fun statusTone(light: Int, dark: Int, background: Int): Int {
    val tone = if (contrast(light, background) >= contrast(dark, background)) light else dark
    if (contrast(tone, background) >= MIN_STATUS_CONTRAST) return tone
    val target = if (contrast(0xFFFFFFFF.toInt(), background) >= contrast(0xFF000000.toInt(), background)) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    for (step in 1..10) {
        val moved = blendArgb(target, tone, step / 10f)
        if (contrast(moved, background) >= MIN_STATUS_CONTRAST) return moved
    }
    return target
}

/** How much of the main text color the secondary text keeps, at its softest. */
private const val SECONDARY_STRENGTH = 0.78f

/** The contrast text needs against its background (WCAG AA for normal text). */
private const val MIN_TEXT_CONTRAST = 4.5

/**
 * The secondary text color on an accent container: the main text color softened toward the background to
 * [SECONDARY_STRENGTH], so labels read as a step below the main text, and firmed up only as far as it takes
 * to stay readable (4.5:1) on both the [background] and a [tile] drawn on it. A container with little contrast
 * to begin with ends up at the main text color itself.
 */
internal fun secondaryTone(onBackground: Int, background: Int, tile: Int): Int {
    var strength = SECONDARY_STRENGTH
    while (strength < 1f) {
        val tone = blendArgb(onBackground, background, strength)
        if (contrast(tone, background) >= MIN_TEXT_CONTRAST && contrast(tone, tile) >= MIN_TEXT_CONTRAST) return tone
        strength += 0.02f
    }
    return onBackground
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
