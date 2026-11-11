package com.azimulkabir.actua.widget

import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrast of the Default widget style, which is drawn from the color resources rather than from
 * [WidgetStyleColors] (the supporter styles are covered by [WidgetStyleTest]).
 */
class WidgetContrastTest {
    private fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(rgb: Int) =
        0.2126 * channel(rgb shr 16 and 0xFF) + 0.7152 * channel(rgb shr 8 and 0xFF) + 0.0722 * channel(rgb and 0xFF)

    private fun contrast(a: Double, b: Double): Double {
        val (hi, lo) = if (a >= b) a to b else b to a
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun colors(path: String): Map<String, Int> {
        val xml = File("src/main/res/$path/widget_colors.xml").readText()
        return Regex("""<color name="(\w+)">#([0-9A-Fa-f]{6})</color>""").findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2].toInt(16) }
    }

    private fun assertTextPairs(label: String, c: Map<String, Int>) {
        val text = mapOf(
            "on_background" to "widget_on_background", "secondary" to "widget_secondary_text", "primary" to "widget_primary",
        )
        for ((name, key) in text) {
            for (surface in listOf("widget_background", "widget_button")) {
                val ratio = contrast(luminance(c.getValue(key)), luminance(c.getValue(surface)))
                assertTrue("$label: $name on $surface is $ratio, below 4.5", ratio >= 4.5)
            }
        }
        // A progress bar is a graphic: 3:1 against the surface it sits on.
        for (status in listOf("widget_status_warning", "widget_status_danger")) {
            val ratio = contrast(luminance(c.getValue(status)), luminance(c.getValue("widget_background")))
            assertTrue("$label: $status is $ratio on the background, below 3", ratio >= 3.0)
        }
    }

    @Test
    fun lightFallbackColorsAreReadable() = assertTextPairs("light", colors("values"))

    @Test
    fun darkFallbackColorsAreReadable() {
        // Night resources override every color the light set defines.
        assertTextPairs("dark", colors("values") + colors("values-night"))
    }

    /** CIE L* tone to relative luminance, which is what a Material You palette entry is built to. */
    private fun toneLuminance(tone: Int): Double {
        val t = tone.toDouble()
        return if (t > 8) ((t + 16) / 116).pow(3) else t / 903.3
    }

    private fun assertTones(label: String, text: Int, surface: Int) {
        val ratio = contrast(toneLuminance(text), toneLuminance(surface))
        assertTrue("$label: tone $text on tone $surface is $ratio, below 4.5", ratio >= 4.5)
    }

    /**
     * On Android 12+ the Default style uses the device's Material You colors (values-v31 and
     * values-night-v31). Their shades are fixed tones of the wallpaper's hues (system_*_100 is tone 90,
     * _600 tone 40, and so on), so the contrast holds for every wallpaper, not only for two palettes.
     */
    @Test
    fun materialYouTonesAreReadableForAnyWallpaper() {
        // Light: neutral1_10 (99) surface, accent1_100 (90) tile; neutral1_900 (10), neutral2_700 (30), accent1_600 (40) text.
        for (surface in listOf(99, 90)) {
            assertTones("light", 10, surface)
            assertTones("light", 30, surface)
            assertTones("light", 40, surface)
        }
        // Dark: neutral1_800 (20) surface, accent1_700 (30) tile; neutral1_100 (90), neutral2_200 and accent1_200 (80) text.
        for (surface in listOf(20, 30)) {
            assertTones("dark", 90, surface)
            assertTones("dark", 80, surface)
        }
    }
}
