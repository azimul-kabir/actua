package com.azimulkabir.actua.widget

import com.azimulkabir.actua.ui.theme.AccentPalette
import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingChartTest {
    private fun bars(cents: List<Long>) = SpendingChart.bars(cents, width = 300f, height = 100f, minBar = 4f, maxBarWidth = 40f)

    @Test
    fun accentTintedChartsAreReadableForEveryAccentLightAndDark() {
        for (accent in AccentPalette.entries) {
            for (night in listOf(false, true)) {
                assertChartReadable(
                    "accent tinted $accent night=$night",
                    WidgetStyle.AccentTinted.colors(night, accent.colorScheme(night))!!,
                )
            }
        }
    }

    @Test
    fun theTallestMonthFillsTheHeightAndOthersAreInProportion() {
        val bars = bars(listOf(1_000, 500, 250))
        assertEquals(listOf(100f, 50f, 25f), bars.map { it.height })
        assertTrue("bars rest on the baseline", bars.all { it.bottom == 100f })
    }

    @Test
    fun aMonthWithNothingSpentStillShowsAStub() {
        val bars = bars(listOf(0, 800, -300))
        assertEquals(listOf(4f, 100f, 4f), bars.map { it.height })
    }

    @Test
    fun barsSitInEqualSlotsAndAreCapped() {
        val bars = bars(listOf(1, 1, 1))
        // Three slots of 100px; a slot's bar would be 62px wide, but is capped at 40px.
        assertEquals(listOf(50f, 150f, 250f), bars.map { it.centerX })
        assertTrue(bars.all { it.right - it.left == 40f })
    }

    @Test
    fun nothingToDrawForNoMonths() {
        assertTrue(bars(emptyList()).isEmpty())
        assertEquals(4f, bars(listOf(0, 0)).first().height)
    }

    // ---- colors

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

    private fun assertChartReadable(name: String, colors: WidgetStyleColors) {
        // Over an opaque backdrop: a translucent scrim is drawn over a wallpaper, which the strongest
        // contrast case (the scrim's own color) stands in for.
        val backdrop = colors.background or (0xFF shl 24)
        val chart = chartColorsFor(colors)
        assertTrue("$name: this month's bar", contrast(chart.highlight, backdrop) >= 3.0)
        assertTrue("$name: other months' bars are a clear shape (3:1)", contrast(chart.bar, backdrop) >= 3.0)
        assertTrue("$name: month labels are readable (4.5:1)", contrast(chart.label, backdrop) >= 4.5)
        assertTrue("$name: this month's spending bar", contrast(chart.second, backdrop) >= 3.0)
        assertTrue("$name: other months' spending bars", contrast(chart.secondPast, backdrop) >= 3.0)
        assertTrue("$name: income and spending bars tell apart", contrast(chart.highlight, chart.second) >= 1.3)
    }

    @Test
    fun theChartIsReadableInEverySupporterStyleLightAndDark() {
        assertChartReadable("translucent light", WidgetStyle.Translucent.colors(night = false, accent = null)!!)
        assertChartReadable("translucent dark", WidgetStyle.Translucent.colors(night = true, accent = null)!!)
        assertChartReadable("solid dark", WidgetStyle.SolidDark.colors(night = true, accent = null)!!)
        assertChartReadable("solid light", WidgetStyle.SolidLight.colors(night = false, accent = null)!!)
    }

    @Test
    fun theSpendingBarIsASoftToneNotTheStrongestText() {
        // On a light backdrop the body text is near-black (about 15:1); the spending bar is a clear shape that stays well below it.
        for ((name, colors) in listOf(
            "solid light" to WidgetStyle.SolidLight.colors(night = false, accent = null)!!,
            "translucent light" to WidgetStyle.Translucent.colors(night = false, accent = null)!!,
        )) {
            val backdrop = colors.background or (0xFF shl 24)
            val chart = chartColorsFor(colors)
            assertTrue("$name: spending bar is softer than the body text", contrast(chart.second, backdrop) < contrast(colors.onBackground, backdrop) / 2)
            assertTrue("$name: spending bar is softer than the label", contrast(chart.second, backdrop) <= contrast(colors.secondary, backdrop))
        }
    }

    @Test
    fun theChartIsReadableInTheDefaultStyleLightAndDark() {
        fun parse(path: String) = Regex("""<color name="(\w+)">#([0-9A-Fa-f]{6})</color>""")
            .findAll(File("src/main/res/$path/widget_colors.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2].toInt(16) }
        val light = parse("values")
        val dark = light + parse("values-night")
        for ((name, c) in listOf("light" to light, "dark" to dark)) {
            assertChartReadable(
                "default $name",
                WidgetStyleColors(
                    background = c.getValue("widget_background"), onBackground = c.getValue("widget_on_background"),
                    secondary = c.getValue("widget_secondary_text"), primary = c.getValue("widget_primary"),
                    button = c.getValue("widget_button"), buttonText = c.getValue("widget_primary"),
                    progressTrack = c.getValue("widget_button"), tile = c.getValue("widget_button"),
                    warning = c.getValue("widget_status_warning"), danger = c.getValue("widget_status_danger"),
                ),
            )
        }
    }
}
