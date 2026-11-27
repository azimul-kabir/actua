package com.azimulkabir.actua.widget

import com.azimulkabir.actua.ui.theme.CatppuccinStatusColors
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb

/** The colors a non-default [WidgetStyle] gives every widget (ARGB ints, as RemoteViews takes them). */
data class WidgetStyleColors(
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
    /**
     * The color of the usual progress bar. It is [primary] except in [WidgetStyle.AccentTinted], which
     * uses the accent here at the 3:1 that a graphic needs. Chart bars stay on [primary], because the earlier months'
     * softer bars are blended from it and have to remain a clear shape.
     */
    val accent: Int = primary,
    /**
     * Where a [WidgetStyle.GradientAccent] background ends: [background] darkened by the gradient drawable's shade. Every
     * other style is flat, so it is [background]. Text has to read on both ends.
     */
    val backgroundEnd: Int = background,
) {
    /** The color a category's progress bar is filled with for [status]. */
    internal fun progressFill(status: ProgressStatus): Int = when (status) {
        ProgressStatus.Normal -> accent
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

    /** A diagonal gradient of the app's accent color with light text; needs Android 12. */
    GradientAccent,

    /** The Translucent scrim with an outline, following the device theme; needs Android 12. */
    Outline,

    /** Pure black for AMOLED screens, with soft violet values; needs Android 12. */
    Midnight,
    ;

    /** Whether this style is drawn from the app's accent, so a new accent has to reach the widgets. */
    val followsAccent: Boolean get() = this != Default

    /** Styles that tint the widget's background, which RemoteViews can only do from Android 12. */
    val needsAndroid12: Boolean get() = this == AccentTinted || this == GradientAccent || this == Outline || this == Midnight

    /**
     * The colors for this style, or null for [Default], which changes nothing. [night] is the device's
     * dark mode, which [Translucent] follows. [accent] supplies the foreground for every styled widget. [status] is the
     * Catppuccin theme's warning and overspent colors when one is on, so the progress bars match the app's Budget tab.
     */
    fun colors(night: Boolean, accent: ColorScheme?, status: CatppuccinStatusColors? = null): WidgetStyleColors? = when (this) {
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
        Outline -> Translucent.colors(night, null)
        Midnight -> WidgetStyleColors(
            background = 0xFF000000.toInt(), onBackground = 0xFFF2EEF7.toInt(), secondary = 0xFFB9B3C2.toInt(),
            primary = 0xFFC9B8FF.toInt(), button = 0xFF2A2238.toInt(), buttonText = 0xFFC9B8FF.toInt(),
            progressTrack = 0xFF2A2238.toInt(), tile = 0xFF17131F.toInt(),
            warning = DARK_WARNING, danger = DARK_DANGER,
        )
        // Always drawn from the light scheme's primary, a saturated tone, in both device themes: the gradient
        // only darkens it, so the text is chosen to read on both ends.
        GradientAccent -> accent?.let { scheme -> gradientColors(scheme.primary.toArgb(), scheme.onPrimary.toArgb()) }
        AccentTinted -> accent?.let { scheme ->
            val background = scheme.primaryContainer.toArgb()
            val onBackground = scheme.onPrimaryContainer.toArgb()
            val tile = blendArgb(onBackground, background, 0.08f)
            // The accent for values, progress bars and chart bars. Where it does not read at 4.5:1 on both the
            // background and the tiles (a pale accent on a light widget), it is moved towards the text color only as
            // far as needed, so it keeps its hue. The buttons keep the exact accent.
            val accentArgb = scheme.primary.toArgb()
            val towardsText = (0..10).map { blendArgb(onBackground, accentArgb, it / 10f) }
            val accentOrText = towardsText
                .firstOrNull { contrast(it, background) >= ACCENT_MIN_CONTRAST && contrast(it, tile) >= ACCENT_MIN_CONTRAST }
                ?: onBackground
            val warningTone = status?.warning?.toArgb()?.let { statusTone(it, it, background) }
                ?: statusTone(LIGHT_WARNING, DARK_WARNING, background)
            val dangerTone = status?.danger?.toArgb()?.let { statusTone(it, it, background) }
                ?: statusTone(LIGHT_DANGER, DARK_DANGER, background)
            // The progress bar is a graphic, which needs 3:1 rather than the 4.5:1 of text, so the accent keeps more of
            // its hue. It must also stay apart from the warning and overspent bars (a Peach or Red accent would not).
            val readable = towardsText.filter { contrast(it, background) >= ACCENT_MIN_GRAPHIC_CONTRAST }
            fun apartFromStatus(color: Int) = minOf(contrast(color, warningTone), contrast(color, dangerTone))
            // The first tone apart from both status bars; failing that, the readable one furthest from them.
            val accentGraphic = readable.firstOrNull { apartFromStatus(it) >= APART_FROM_STATUS }
                ?: readable.maxByOrNull { apartFromStatus(it) } ?: onBackground
            WidgetStyleColors(
                background = background,
                onBackground = onBackground,
                secondary = secondaryTone(onBackground, background, tile),
                primary = accentOrText,
                accent = accentGraphic,
                button = scheme.primary.toArgb(),
                buttonText = scheme.onPrimary.toArgb(),
                progressTrack = blendArgb(onBackground, background, 0.25f),
                tile = tile,
                // Catppuccin's Peach and Red, moved only as far as needed to stay 3:1 on this widget's background.
                warning = warningTone,
                danger = dangerTone,
            )
        }
    }?.let { colors ->
        if (accent == null || this == AccentTinted) colors else colors.withForegroundAccent(accent.primary.toArgb())
    }

    companion object {
        fun fromName(name: String?): WidgetStyle = entries.firstOrNull { it.name == name } ?: Default
    }
}

/** Tune the accent against the look's surfaces, including translucent scrims over light or dark wallpaper. */
private fun WidgetStyleColors.withForegroundAccent(seed: Int): WidgetStyleColors {
    val backgrounds = listOf(background, backgroundEnd).distinct().flatMap { surface ->
        listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()).map { overWithOpacity(surface, 100, it) }
    }.distinct()
    val tiles = backgrounds.map { overWithOpacity(tile, 100, it) }
    val buttons = backgrounds.map { overWithOpacity(button, 100, it) }
    val values = accentTone(seed, onBackground, backgrounds + tiles)
    return copy(
        primary = values,
        accent = values,
        buttonText = accentTone(seed, buttonText, buttons),
    )
}

private fun accentTone(seed: Int, fallback: Int, surfaces: List<Int>): Int =
    (10 downTo 0).asSequence().map { blendArgb(seed, fallback, it / 10f) }
        .firstOrNull { tone -> surfaces.all { contrast(tone, it) >= MIN_TEXT_CONTRAST } }
        ?: fallback

/**
 * How far a [WidgetStyle.GradientAccent] background darkens from corner to corner: the grey its drawable ends on, out of
 * 255, which the launcher multiplies with the accent. Kept in step with `widget_gradient_*.xml` by WidgetLookTest.
 */
internal const val GRADIENT_SHADE = 0xB8

/** [color] multiplied by [GRADIENT_SHADE], as the launcher draws the far end of the gradient. */
internal fun gradientEnd(color: Int): Int {
    fun channel(shift: Int) = (((color shr shift) and 0xFF) * GRADIENT_SHADE / 255) shl shift
    return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
}

/** The Gradient accent colors on [primary]: text that reads on both ends of the gradient, with tiles and bars from it. */
internal fun gradientColors(primary: Int, onPrimary: Int): WidgetStyleColors {
    val start = primary or (0xFF shl 24)
    val end = gradientEnd(start)
    fun readsOnBoth(color: Int, min: Double) = contrast(color, start) >= min && contrast(color, end) >= min
    val text = listOf(onPrimary, 0xFFFFFFFF.toInt(), 0xFF000000.toInt()).firstOrNull { readsOnBoth(it, MIN_TEXT_CONTRAST) }
        ?: listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()).maxBy { minOf(contrast(it, start), contrast(it, end)) }
    val lightText = contrast(text, 0xFF000000.toInt()) > contrast(text, 0xFFFFFFFF.toInt())
    // Tiles and buttons are a veil away from the text color (darker under light text, lighter under dark text), so
    // text on them only gains contrast.
    val veil = if (lightText) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    val tile = blendArgb(veil, start, 0.18f)
    val tileEnd = blendArgb(veil, end, 0.18f)
    var strength = SECONDARY_STRENGTH
    var secondary = text
    while (strength < 1f) {
        val tone = blendArgb(text, start, strength)
        if (listOf(start, end, tile, tileEnd).all { contrast(tone, it) >= MIN_TEXT_CONTRAST }) { secondary = tone; break }
        strength += 0.02f
    }
    val warning = statusTone(LIGHT_WARNING, DARK_WARNING, end).let { if (contrast(it, start) >= MIN_STATUS_CONTRAST) it else statusTone(LIGHT_WARNING, DARK_WARNING, start) }
    val danger = statusTone(LIGHT_DANGER, DARK_DANGER, end).let { if (contrast(it, start) >= MIN_STATUS_CONTRAST) it else statusTone(LIGHT_DANGER, DARK_DANGER, start) }
    return WidgetStyleColors(
        background = start, onBackground = text, secondary = secondary, primary = text,
        button = tile, buttonText = text,
        progressTrack = blendArgb(text, start, if (lightText) 0.3f else 0.25f), tile = tile,
        warning = warning, danger = danger, backgroundEnd = end,
    )
}

// Progress bar tones for the amber and red states: the deeper pair for light backgrounds, the lighter for dark.
// The light pair is deep enough to keep 3:1 even on the translucent light scrim over a black wallpaper.
private const val LIGHT_WARNING = 0xFF8A5400.toInt()
private const val LIGHT_DANGER = 0xFFB3261E.toInt()
private const val DARK_WARNING = 0xFFFFB74D.toInt()
private const val DARK_DANGER = 0xFFFF8A80.toInt()

/** The contrast a graphic needs against its background to stay a clear shape (WCAG's 3:1). */
private const val MIN_STATUS_CONTRAST = 3.0

/** How much contrast the accent needs on the widget to be used for values, which are text. */
private const val ACCENT_MIN_CONTRAST = 4.5

/** How much contrast the accent needs on the widget to be used for bars and progress, which are graphics. */
private const val ACCENT_MIN_GRAPHIC_CONTRAST = 3.0

/** How far (as a contrast ratio) the usual progress bar must sit from the warning and overspent bars. */
private const val APART_FROM_STATUS = 1.15

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
