package com.azimulkabir.actua.widget

import com.azimulkabir.actua.ui.theme.AccentPalette
import com.azimulkabir.actua.ui.theme.CustomAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetLookTest {
    private val styled = WidgetLook(WidgetStyle.Midnight, WidgetCornerRadius.Large, 80)

    private fun look(perks: Boolean = true, supporter: Boolean = true, override: WidgetLookOverride? = null, tint: Boolean = true) =
        effectiveWidgetLook(perksAvailable = perks, isSupporter = supporter, global = styled, override = override, tintAvailable = tint)

    @Test
    fun onlyASupporterGetsAnythingButTheDefaultLook() {
        assertEquals(styled, look())
        assertEquals(WidgetLook.Default, look(supporter = false))
        assertEquals(WidgetLook.Default, look(perks = false))
        assertEquals(WidgetLook.Default, look(supporter = false, override = WidgetLookOverride(WidgetStyle.Outline, WidgetCornerRadius.Small, 60)))
    }

    @Test
    fun aWidgetsOwnLookOverridesOnlyWhatItSets() {
        assertEquals(styled.copy(style = WidgetStyle.Outline), look(override = WidgetLookOverride(style = WidgetStyle.Outline)))
        assertEquals(styled.copy(radius = WidgetCornerRadius.Small), look(override = WidgetLookOverride(radius = WidgetCornerRadius.Small)))
        assertEquals(styled.copy(opacity = 60), look(override = WidgetLookOverride(opacity = 60)))
        assertEquals(styled, look(override = WidgetLookOverride()))
    }

    @Test
    fun opacityStaysWithinItsLimits() {
        assertEquals(WidgetLook.LOWEST_OPACITY, look(override = WidgetLookOverride(opacity = 5)).opacity)
        assertEquals(WidgetLook.FULL_OPACITY, look(override = WidgetLookOverride(opacity = 150)).opacity)
    }

    @Test
    fun beforeAndroid12TheTintedStylesFallBackAndRadiusAndOpacityStay() {
        assertEquals(WidgetLook.Default, look(tint = false))
        assertEquals(
            WidgetLook(WidgetStyle.SolidDark, WidgetCornerRadius.System, WidgetLook.FULL_OPACITY),
            look(tint = false, override = WidgetLookOverride(style = WidgetStyle.SolidDark)),
        )
    }

    /** Every supporter look in both themes for every palette and some custom colors. */
    private fun everyStyleColors(): List<Pair<String, WidgetStyleColors>> {
        val schemes = AccentPalette.entries.flatMap { palette ->
            listOf(false, true).map { "$palette dark=$it" to palette.colorScheme(it) }
        } + listOf(0xFFFFEB3B, 0xFF101010, 0xFF9E9E9E, 0xFFE0F7FA, 0xFF00BCD4).map { it.toInt() }.flatMap { seed ->
            listOf(false, true).map { "custom ${CustomAccent.formatHex(seed)} dark=$it" to AccentPalette.Custom.colorScheme(it, seed) }
        }
        return WidgetStyle.entries.filter { it != WidgetStyle.Default }.flatMap { style ->
            schemes.map { (name, scheme) ->
                "$style $name" to style.colors(night = name.endsWith("true"), accent = scheme)!!
            }
        }
    }

    @Test
    fun everyStyleIsReadableAtItsLowestOpacityOverAnyWallpaper() {
        for ((name, colors) in everyStyleColors()) {
            val floor = minimumOpacity(colors)
            assertTrue("$name floor $floor", floor in WidgetLook.LOWEST_OPACITY..WidgetLook.FULL_OPACITY)
            assertTrue("$name is unreadable at its floor of $floor%", readableAt(colors, floor))
            assertTrue("$name is unreadable when opaque", readableAt(colors, WidgetLook.FULL_OPACITY))
        }
    }

    @Test
    fun aDarkStyleCannotFadeTo40PercentOverAWhiteWallpaper() {
        val dark = WidgetStyle.SolidDark.colors(night = true, accent = null)!!
        assertTrue(minimumOpacity(dark) > WidgetLook.LOWEST_OPACITY)
    }

    @Test
    fun theTintNeverGoesBelowTheReadableFloor() {
        val colors = WidgetStyle.SolidDark.colors(night = true, accent = null)!!
        val tint = WidgetStyles.backgroundTint(WidgetLook(WidgetStyle.SolidDark, WidgetCornerRadius.System, WidgetLook.LOWEST_OPACITY), colors)
        val alphaPercent = ((tint ushr 24) and 0xFF) * 100 / 255
        assertTrue("alpha $alphaPercent", alphaPercent + 1 >= minimumOpacity(colors))
        assertEquals(colors.background and 0xFFFFFF, tint and 0xFFFFFF)
    }

    @Test
    fun theGradientDrawablesEndOnTheShadeTheColorsAreCheckedAgainst() {
        val shade = "%02X".format(GRADIENT_SHADE)
        for (radius in listOf("system", "small", "medium", "large")) {
            val xml = listOf(File("src/main/res/drawable/widget_gradient_$radius.xml"), File("app/src/main/res/drawable/widget_gradient_$radius.xml"))
                .first { it.exists() }.readText()
            assertTrue("widget_gradient_$radius ends on #FF$shade$shade$shade", "android:endColor=\"#FF$shade$shade$shade\"" in xml)
        }
    }

    @Test
    fun theOutlineBorderTracksAccentIndependentlyOfTheScrim() {
        val look = WidgetLook(WidgetStyle.Outline, WidgetCornerRadius.System, 100)
        for (night in listOf(false, true)) {
            val teal = WidgetStyle.Outline.colors(night, AccentPalette.Teal.colorScheme(night))!!
            val violet = WidgetStyle.Outline.colors(night, AccentPalette.Violet.colorScheme(night))!!
            assertEquals(teal.background, WidgetStyles.backgroundTint(look, teal))
            assertEquals(teal.background, WidgetStyles.backgroundTint(look, violet))
            assertEquals(teal.primary, WidgetStyles.outlineTint(look, teal))
            assertEquals(violet.primary, WidgetStyles.outlineTint(look, violet))
            assertTrue(WidgetStyles.outlineTint(look, teal) != WidgetStyles.outlineTint(look, violet))
        }
    }
}
