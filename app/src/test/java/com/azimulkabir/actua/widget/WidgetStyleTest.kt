package com.azimulkabir.actua.widget

import com.azimulkabir.actua.ui.theme.AccentPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class WidgetStyleTest {

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val v = ((argb shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    /** [top] drawn over the opaque [bottom], as the launcher shows a translucent widget over a wallpaper. */
    private fun over(top: Int, bottom: Int): Int {
        val alpha = ((top shr 24) and 0xFF) / 255f
        return blendArgb(top or (0xFF shl 24), bottom, alpha)
    }

    private fun assertReadable(name: String, colors: WidgetStyleColors, backdrop: Int) {
        val background = over(colors.background, backdrop)
        val button = over(colors.button, background)
        val tile = over(colors.tile, background)
        val pairs = mapOf(
            "title/name text" to contrast(colors.onBackground, background),
            "secondary text" to contrast(colors.secondary, background),
            "value text" to contrast(colors.primary, background),
            "button text" to contrast(colors.buttonText, button),
            "value text on a tile" to contrast(colors.primary, tile),
            "label text on a tile" to contrast(colors.secondary, tile),
        )
        pairs.forEach { (what, ratio) ->
            assertTrue("$name $what is $ratio:1", ratio >= 4.5)
        }
    }

    @Test
    fun defaultChangesNothing() {
        assertNull(WidgetStyle.Default.colors(night = false, accent = null))
    }

    @Test
    fun solidStylesAreReadable() {
        for (style in listOf(WidgetStyle.SolidDark, WidgetStyle.SolidLight)) {
            for (night in listOf(false, true)) {
                assertReadable(style.name, style.colors(night, null)!!, backdrop = 0xFFFFFFFF.toInt())
            }
        }
    }

    @Test
    fun translucentIsReadableOverTheLightestAndDarkestWallpaperInLightAndDark() {
        for (night in listOf(false, true)) {
            val colors = WidgetStyle.Translucent.colors(night, null)!!
            val theme = if (night) "dark" else "light"
            assertReadable("Translucent $theme over white", colors, backdrop = 0xFFFFFFFF.toInt())
            assertReadable("Translucent $theme over black", colors, backdrop = 0xFF000000.toInt())
        }
    }

    @Test
    fun translucentFollowsTheDeviceTheme() {
        val light = WidgetStyle.Translucent.colors(night = false, accent = null)!!
        val dark = WidgetStyle.Translucent.colors(night = true, accent = null)!!
        assertTrue("light scrim is lighter than the dark one", luminance(light.background) > luminance(dark.background))
        assertTrue("light text is darker than the dark theme's", luminance(light.onBackground) < luminance(dark.onBackground))
    }

    @Test
    fun accentTintedIsReadableForEveryAccentInLightAndDark() {
        for (accent in AccentPalette.entries) {
            for (dark in listOf(false, true)) {
                val colors = WidgetStyle.AccentTinted.colors(dark, accent.colorScheme(dark))!!
                assertReadable("AccentTinted $accent ${if (dark) "dark" else "light"}", colors, backdrop = 0xFFFFFFFF.toInt())
            }
        }
    }

    @Test
    fun accentTintedWithoutAnAccentChangesNothing() {
        assertNull(WidgetStyle.AccentTinted.colors(night = false, accent = null))
        assertNotNull(WidgetStyle.AccentTinted.colors(night = false, accent = AccentPalette.Teal.colorScheme(false)))
    }

    @Test
    fun onlyAccentTintedFollowsTheAccent() {
        assertEquals(listOf(WidgetStyle.AccentTinted), WidgetStyle.entries.filter { it.followsAccent })
    }

    @Test
    fun anUnknownStoredNameFallsBackToDefault() {
        assertEquals(WidgetStyle.Default, WidgetStyle.fromName(null))
        assertEquals(WidgetStyle.Default, WidgetStyle.fromName("Holographic"))
        assertEquals(WidgetStyle.SolidDark, WidgetStyle.fromName("SolidDark"))
    }

    @Test
    fun blendingMovesBetweenTheTwoColors() {
        assertEquals(0xFF000000.toInt(), blendArgb(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), blendArgb(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 1f))
    }

    @Test
    fun progressStatusFollowsHowSpentACategoryIs() {
        assertEquals(ProgressStatus.Normal, ProgressStatus.of(percentSpent = 0, overspent = false))
        assertEquals(ProgressStatus.Normal, ProgressStatus.of(percentSpent = 84, overspent = false))
        assertEquals(ProgressStatus.Warning, ProgressStatus.of(percentSpent = 85, overspent = false))
        assertEquals(ProgressStatus.Warning, ProgressStatus.of(percentSpent = 100, overspent = false))
        assertEquals(ProgressStatus.Overspent, ProgressStatus.of(percentSpent = 100, overspent = true))
        // A negative balance wins even when the bar isn't full (a rolled-over deficit).
        assertEquals(ProgressStatus.Overspent, ProgressStatus.of(percentSpent = 10, overspent = true))
    }
}
