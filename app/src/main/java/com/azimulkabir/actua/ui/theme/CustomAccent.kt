package com.azimulkabir.actua.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A full accent palette for light and dark themes derived from one color a supporter picked.
 * [adjusted] is true when the picked color could not carry white text at WCAG AA in the light theme
 * and was darkened, so the screen can say so.
 */
class DerivedAccent(
    val seed: Int,
    val light: AccentColors,
    val dark: AccentColors,
    val adjusted: Boolean,
) {
    fun colors(darkTheme: Boolean): AccentColors = if (darkTheme) dark else light
}

/**
 * Derives the palette behind the Custom accent (a Supporter extra). Every on-color is at least 4.5:1
 * against its color, container and surface, and the primary reaches at least 3:1 against the page
 * background for icons and large text, in both themes. The derivation is pure so it can be unit tested
 * across a spread of colors.
 */
object CustomAccent {
    /** The color the picker starts from before a supporter has chosen one (opaque teal-green). */
    const val DEFAULT_SEED: Int = 0xFF2E7D6B.toInt()

    private const val TEXT = 4.5

    /**
     * How far a container keeps from the far extreme: a dark theme's container at least this far from white, a light
     * theme's from black. A bright hue such as yellow is still a mid tone at a low HSL lightness, and text then only just
     * reads on the container itself, not on surfaces drawn over it, such as a widget's tiles.
     */
    private const val CONTAINER = 7.0
    private const val ICON = 3.0

    /** Keeps only the RGB of [argb] and makes it opaque. */
    fun opaque(argb: Int): Int = argb or 0xFF000000.toInt()

    /** "#RRGGBB" for [argb]. */
    fun formatHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** An opaque ARGB int from "RRGGBB", "#RRGGBB", "RGB" or "#RGB", or null when [text] is not a color. */
    fun parseHex(text: String): Int? {
        var value = text.trim().removePrefix("#")
        if (value.length == 3) value = value.map { "$it$it" }.joinToString("")
        if (value.length != 6 || !value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return opaque(value.toInt(16))
    }

    /** Hue (0..360), saturation and value (0..1) of [argb]. */
    fun toHsv(argb: Int): FloatArray {
        val c = Color(opaque(argb))
        val r = c.red; val g = c.green; val b = c.blue
        val maxC = max(r, max(g, b)); val minC = min(r, min(g, b))
        val delta = maxC - minC
        val hue = when {
            delta == 0f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        return floatArrayOf((hue + 360f) % 360f, if (maxC == 0f) 0f else delta / maxC, maxC)
    }

    /** The opaque ARGB color for hue (0..360), saturation and value (0..1). */
    fun fromHsv(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f); val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return opaque(
            (((r + m) * 255f + 0.5f).toInt() shl 16) or (((g + m) * 255f + 0.5f).toInt() shl 8) or ((b + m) * 255f + 0.5f).toInt(),
        )
    }

    /** WCAG 2.x contrast ratio between two colors. */
    fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private class Hsl(val h: Double, val s: Double, val l: Double)

    private fun toHsl(c: Color): Hsl {
        val r = c.red.toDouble(); val g = c.green.toDouble(); val b = c.blue.toDouble()
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        val d = mx - mn
        if (d == 0.0) return Hsl(0.0, 0.0, l)
        val s = d / (1 - abs(2 * l - 1))
        val h = when (mx) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        return Hsl((h + 360) % 360, s.coerceIn(0.0, 1.0), l)
    }

    private fun hsl(h: Double, s: Double, l: Double): Color {
        val hh = ((h % 360) + 360) % 360
        val ss = s.coerceIn(0.0, 1.0); val ll = l.coerceIn(0.0, 1.0)
        val c = (1 - abs(2 * ll - 1)) * ss
        val x = c * (1 - abs((hh / 60) % 2 - 1))
        val m = ll - c / 2
        val (r, g, b) = when ((hh / 60).toInt()) {
            0 -> Triple(c, x, 0.0)
            1 -> Triple(x, c, 0.0)
            2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c)
            4 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        return Color((r + m).toFloat(), (g + m).toFloat(), (b + m).toFloat())
    }

    /** Moves [fg]'s lightness away from [bg] until it reaches [min] contrast; black or white as a last resort. */
    private fun readable(fg: Color, bg: Color, min: Double): Color {
        if (contrast(fg, bg) >= min) return fg
        val h = toHsl(fg)
        val towardsLight = luminance(bg) < 0.18
        var l = h.l
        while (l in 0.0..1.0) {
            l += if (towardsLight) 0.01 else -0.01
            val candidate = hsl(h.h, h.s, l)
            if (contrast(candidate, bg) >= min) return candidate
        }
        return if (towardsLight) Color.White else Color.Black
    }

    /** [fg] against every one of [bgs]. */
    private fun readableOnAll(fg: Color, bgs: List<Color>, min: Double): Color {
        var out = fg
        repeat(3) { out = bgs.fold(out) { acc, bg -> readable(acc, bg, min) } }
        return out
    }

    private class Role(val color: Color, val on: Color, val container: Color, val onContainer: Color)

    private fun role(hue: Double, sat: Double, dark: Boolean, primary: Color? = null): Role {
        val containerSat = min(sat, 0.9)
        return if (dark) {
            val color = hsl(hue, sat, 0.78)
            val container = readable(hsl(hue, containerSat, 0.28), Color.White, CONTAINER)
            Role(color, readable(hsl(hue, sat, 0.12), color, TEXT), container, readable(hsl(hue, sat, 0.92), container, TEXT))
        } else {
            val color = primary ?: readable(hsl(hue, sat, 0.38), Color.White, TEXT)
            val container = readable(hsl(hue, containerSat, 0.90), Color.Black, CONTAINER)
            Role(color, Color.White, container, readable(hsl(hue, sat, 0.15), container, TEXT))
        }
    }

    private fun build(hue: Double, sat: Double, dark: Boolean, primary: Color?): AccentColors {
        val p = role(hue, sat, dark, primary)
        val s = role(hue, sat * 0.35, dark)
        val t = role(hue + 60, sat * 0.6, dark)
        val tint = min(sat, 0.4)
        val (low, mid, high, highest, variant) = if (dark) {
            listOf(0.11, 0.14, 0.18, 0.22, 0.24).map { hsl(hue, tint, it) }
        } else {
            listOf(0.97, 0.95, 0.93, 0.91, 0.93).map { hsl(hue, tint, it) }
        }
        val surfaces = listOf(low, mid, high, highest, variant)
        val onVariant = readableOnAll(hsl(hue, sat * 0.25, if (dark) 0.85 else 0.30), surfaces, TEXT)
        val outline = readable(hsl(hue, sat * 0.25, if (dark) 0.62 else 0.46), variant, ICON)
        val outlineVariant = if (dark) variant else hsl(hue, tint, 0.80)
        return AccentColors(
            p.color, p.on, p.container, p.onContainer,
            s.color, s.on, s.container, s.onContainer,
            t.color, t.on, t.container, t.onContainer,
            variant, onVariant, outline, outlineVariant,
            low, mid, high, highest,
        )
    }

    /** The palette for [argb] (alpha ignored). */
    fun derive(argb: Int): DerivedAccent {
        val seedColor = Color(opaque(argb))
        val h = toHsl(seedColor)
        // The light primary is the pick itself unless white text cannot read on it.
        val lightPrimary = readable(seedColor, Color.White, TEXT)
        val adjusted = lightPrimary != seedColor
        val lightHsl = toHsl(lightPrimary)
        return DerivedAccent(
            seed = opaque(argb),
            light = build(lightHsl.h, lightHsl.s, dark = false, primary = lightPrimary),
            dark = build(h.h, h.s, dark = true, primary = null),
            adjusted = adjusted,
        )
    }
}
