package com.azimulkabir.actua.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** The four Catppuccin flavors. Latte is the light one; the others are dark. */
enum class CatppuccinFlavor(val displayName: String, val isDark: Boolean) {
    Latte("Latte", false),
    Frappe("Frappé", true),
    Macchiato("Macchiato", true),
    Mocha("Mocha", true),
    ;

    internal val palette: CatppuccinPalette
        get() = when (this) {
            Latte -> CatppuccinLattePalette
            Frappe -> CatppuccinFrappePalette
            Macchiato -> CatppuccinMacchiatoPalette
            Mocha -> CatppuccinMochaPalette
        }

    companion object {
        /** The flavors a supporter can pick for dark mode. */
        val dark: List<CatppuccinFlavor> = entries.filter { it.isDark }

        fun darkFromName(name: String?): CatppuccinFlavor = dark.firstOrNull { it.name == name } ?: Mocha
    }
}

/** Budget status colors for a Catppuccin flavor: Green, Peach and Red, so the budget doesn't clash with the theme. */
class CatppuccinStatusColors(val success: Color, val warning: Color, val danger: Color)

/**
 * A supporter's Catppuccin theme (Play build): light mode is always Latte, dark mode is [darkFlavor], and
 * [accent] picks which of Catppuccin's 14 accents drives primary, secondary and tertiary. Based on the Catppuccin
 * palette; Actua is not an official Catppuccin port.
 */
data class CatppuccinTheme(
    val darkFlavor: CatppuccinFlavor = CatppuccinFlavor.Mocha,
    val accent: CatppuccinAccent = CatppuccinAccent.Mauve,
) {
    init {
        require(darkFlavor.isDark) { "darkFlavor must be a dark flavor" }
    }

    fun flavor(darkTheme: Boolean): CatppuccinFlavor = if (darkTheme) darkFlavor else CatppuccinFlavor.Latte

    /** The full Material 3 scheme for light or dark mode. */
    fun colorScheme(darkTheme: Boolean): ColorScheme = CatppuccinScheme.build(
        palette = flavor(darkTheme).palette,
        counterpart = flavor(!darkTheme).palette,
        accent = accent,
        dark = darkTheme,
    )

    fun statusColors(darkTheme: Boolean): CatppuccinStatusColors {
        val palette = flavor(darkTheme).palette
        return CatppuccinStatusColors(
            success = palette.accents.getValue(CatppuccinAccent.Green),
            warning = palette.accents.getValue(CatppuccinAccent.Peach),
            // The same tone the scheme uses for errors, so with a red accent "overspent" differs from ordinary progress.
            danger = CatppuccinScheme.errorColor(palette, accent),
        )
    }

    companion object {
        /** The id the theme registers its supporter-status reset under. */
        const val RESET_ID = "catppuccin"
    }
}

/** WCAG 2.x contrast ratio between two opaque colors. */
internal fun contrastRatio(a: Color, b: Color): Double {
    val la = wcagLuminance(a)
    val lb = wcagLuminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

private fun wcagLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** The Catppuccin-to-Material mapping. */
internal object CatppuccinScheme {
    private const val MIN_TEXT_CONTRAST = 4.5

    /**
     * What Text must reach on an accent-tinted container. Higher than [MIN_TEXT_CONTRAST] because surfaces built on a
     * container (a widget's tiles, for one) are a little lighter than it and must still read at 4.5:1.
     */
    private const val MIN_CONTAINER_TEXT_CONTRAST = 5.5

    /**
     * The first of [candidates] that reads on [background] at WCAG AA, else black or white, whichever reads better.
     * Catppuccin's own colors are tried first; black or white is only the last resort (for example on Latte Yellow).
     */
    fun readableOn(background: Color, candidates: List<Color>): Color =
        candidates.firstOrNull { contrastRatio(it, background) >= MIN_TEXT_CONTRAST }
            ?: listOf(Color.Black, Color.White).maxBy { contrastRatio(it, background) }

    /**
     * The error and overspent color: Catppuccin Red, except that with Red or Maroon as the accent, where errors would
     * be the same hue as ordinary controls, it takes a different lightness, not only a different hue (see [distinctError]).
     */
    fun errorColor(palette: CatppuccinPalette, accent: CatppuccinAccent): Color {
        val red = palette.accents.getValue(CatppuccinAccent.Red)
        return if (accent == CatppuccinAccent.Red || accent == CatppuccinAccent.Maroon) {
            distinctError(red, palette.accents.getValue(accent), palette)
        } else red
    }

    /**
     * A variant of Catppuccin [red] for errors when the accent itself is a red: of red, red lightened towards Text and
     * red darkened towards Base, the one that differs most in lightness from the [accent] while still reading on the
     * page background at 3:1.
     */
    private fun distinctError(red: Color, accent: Color, palette: CatppuccinPalette): Color =
        listOf(red, lerp(red, palette.text, 0.5f), lerp(red, palette.base, 0.35f))
            .filter { contrastRatio(it, palette.base) >= 3.0 }
            .maxByOrNull { contrastRatio(it, accent) } ?: red

    /** The hues the budget uses for status (Red and Maroon for overspent, Peach for warning, Green for on track). */
    private val StatusAccents = setOf(CatppuccinAccent.Red, CatppuccinAccent.Maroon, CatppuccinAccent.Peach, CatppuccinAccent.Green)

    /**
     * The accent [step] places along the palette's ring from [accent] (-1 or 1), skipping the status hues so that
     * secondary and tertiary containers (chips, selected filters) never read as an overspent or on-track state.
     * Where the accent is itself a status hue, its own neighbors are used.
     */
    fun neighbor(accent: CatppuccinAccent, step: Int): CatppuccinAccent {
        val ring = CatppuccinAccent.entries
        var index = ring.indexOf(accent)
        repeat(ring.size) {
            index = (index + step + ring.size) % ring.size
            val candidate = ring[index]
            if (accent in StatusAccents || candidate !in StatusAccents) return candidate
        }
        return accent
    }

    /** [color], moved towards [towards] in small steps until [text] reads on it at WCAG AA. */
    private fun easeUntilReadable(color: Color, towards: Color, text: Color): Color {
        var amount = 0f
        while (amount < 1f) {
            val candidate = lerp(color, towards, amount)
            if (contrastRatio(text, candidate) >= MIN_TEXT_CONTRAST) return candidate
            amount += 0.05f
        }
        return towards
    }

    /** The first of [candidates] that reads at WCAG AA on every one of [backgrounds], else the last candidate. */
    private fun onAll(backgrounds: List<Color>, candidates: List<Color>): Color =
        candidates.firstOrNull { text -> backgrounds.all { contrastRatio(text, it) >= MIN_TEXT_CONTRAST } } ?: candidates.last()

    /**
     * A container tinted with [accent] over [base]. If [preferredText] can't read on it at [MIN_TEXT_CONTRAST],
     * the tint is eased back towards [base] until it can, so only the container tone moves, never the accent.
     */
    private fun container(base: Color, accent: Color, tint: Float, preferredText: Color): Color {
        var amount = tint
        while (amount > 0f) {
            val candidate = lerp(base, accent, amount)
            if (contrastRatio(preferredText, candidate) >= MIN_CONTAINER_TEXT_CONTRAST) return candidate
            amount -= 0.02f
        }
        return base
    }

    fun build(palette: CatppuccinPalette, counterpart: CatppuccinPalette, accent: CatppuccinAccent, dark: Boolean): ColorScheme {
        val primary = palette.accents.getValue(accent)
        val secondary = palette.accents.getValue(neighbor(accent, step = -1))
        val tertiary = palette.accents.getValue(neighbor(accent, step = 1))
        val error = errorColor(palette, accent)
        val tint = if (dark) 0.30f else 0.22f
        val onAccent = if (dark) palette.crust else palette.base

        val primaryContainer = container(palette.base, primary, tint, palette.text)
        val secondaryContainer = container(palette.base, secondary, tint, palette.text)
        val tertiaryContainer = container(palette.base, tertiary, tint, palette.text)
        val errorContainer = container(palette.base, error, tint, palette.text)

        // Surface containers step from Base's neighbors outwards: Crust and Mantle are darker than Base in every flavor,
        // Surface 0 to 2 lighter in the dark flavors. In Latte the steps run darker, and the last two are blended so
        // that Text keeps WCAG AA on them.
        val lowest: Color
        val low: Color
        val mid: Color
        val high: Color
        val highest: Color
        if (dark) {
            lowest = palette.crust; low = palette.mantle; mid = palette.surface0; high = palette.surface1
            // Surface 2 is too light for Text to keep AA on in some flavors, so it is eased back towards Surface 1.
            highest = easeUntilReadable(palette.surface2, palette.surface1, palette.text)
        } else {
            lowest = palette.base; low = palette.mantle; mid = palette.crust
            high = lerp(palette.crust, palette.surface0, 0.5f)
            highest = easeUntilReadable(palette.surface0, palette.crust, palette.text)
        }
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary, onPrimary = readableOn(primary, listOf(onAccent, palette.crust, palette.base)),
            primaryContainer = primaryContainer, onPrimaryContainer = readableOn(primaryContainer, listOf(palette.text)),
            inversePrimary = counterpart.accents.getValue(accent),
            secondary = secondary, onSecondary = readableOn(secondary, listOf(onAccent, palette.crust, palette.base)),
            secondaryContainer = secondaryContainer, onSecondaryContainer = readableOn(secondaryContainer, listOf(palette.text)),
            tertiary = tertiary, onTertiary = readableOn(tertiary, listOf(onAccent, palette.crust, palette.base)),
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = readableOn(tertiaryContainer, listOf(palette.text)),
            error = error, onError = readableOn(error, listOf(onAccent, palette.crust, palette.base)),
            errorContainer = errorContainer, onErrorContainer = readableOn(errorContainer, listOf(palette.text)),
            background = palette.base, onBackground = palette.text,
            surface = palette.base, onSurface = palette.text,
            surfaceVariant = mid,
            // Subtext where it reads on every card surface, Text where it doesn't (Latte's lighter steps).
            onSurfaceVariant = onAll(listOf(low, mid, high, highest), listOf(palette.subtext1, palette.text)),
            outline = palette.overlay1, outlineVariant = palette.overlay0,
            scrim = Color.Black,
            inverseSurface = palette.text, inverseOnSurface = palette.base,
            surfaceDim = palette.mantle,
            surfaceBright = if (dark) palette.surface1 else palette.base,
            surfaceContainerLowest = lowest, surfaceContainerLow = low, surfaceContainer = mid,
            surfaceContainerHigh = high, surfaceContainerHighest = highest,
        )
    }
}
