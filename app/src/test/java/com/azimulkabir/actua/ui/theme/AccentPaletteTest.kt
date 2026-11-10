package com.azimulkabir.actua.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AccentPaletteTest {

    /** WCAG 2.x relative luminance. */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun textPairs(scheme: ColorScheme) = mapOf(
        "primary" to (scheme.onPrimary to scheme.primary),
        "primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
        "secondary" to (scheme.onSecondary to scheme.secondary),
        "secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
        "tertiary" to (scheme.onTertiary to scheme.tertiary),
        "tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
    )

    @Test
    fun everyTextOnColorPairMeetsWcagAaInLightAndDark() {
        for (accent in AccentPalette.entries) {
            for (dark in listOf(false, true)) {
                textPairs(accent.colorScheme(dark)).forEach { (role, pair) ->
                    val ratio = contrast(pair.first, pair.second)
                    assertTrue("$accent ${if (dark) "dark" else "light"} $role is $ratio:1", ratio >= 4.5)
                }
            }
        }
    }

    @Test
    fun violetIsActuasOwnScheme() {
        assertSame(ActuaLightColorScheme, AccentPalette.Violet.colorScheme(darkTheme = false))
        assertSame(ActuaDarkColorScheme, AccentPalette.Violet.colorScheme(darkTheme = true))
    }

    @Test
    fun cardSurfacesFollowTheAccentAndStayReadable() {
        for (dark in listOf(false, true)) {
            val base = if (dark) ActuaDarkColorScheme else ActuaLightColorScheme
            for (accent in AccentPalette.entries - AccentPalette.Violet) {
                val scheme = accent.colorScheme(dark)
                assertTrue(scheme.surfaceContainer != base.surfaceContainer)
                assertTrue(scheme.surfaceContainerHigh != base.surfaceContainerHigh)
                for (surface in listOf(
                    scheme.surfaceVariant, scheme.surfaceContainerLow, scheme.surfaceContainer,
                    scheme.surfaceContainerHigh, scheme.surfaceContainerHighest,
                )) {
                    assertTrue(contrast(scheme.onSurface, surface) >= 4.5)
                    assertTrue(contrast(scheme.onSurfaceVariant, surface) >= 4.5)
                }
            }
        }
    }

    @Test
    fun anAccentKeepsThePageBackgroundAndErrorColors() {
        for (dark in listOf(false, true)) {
            val base = if (dark) ActuaDarkColorScheme else ActuaLightColorScheme
            for (accent in AccentPalette.entries - AccentPalette.Violet) {
                val scheme = accent.colorScheme(dark)
                assertTrue(scheme.primary != base.primary)
                assertEquals(base.background, scheme.background)
                assertEquals(base.surface, scheme.surface)
                assertEquals(base.error, scheme.error)
            }
        }
    }

    @Test
    fun anUnknownStoredNameFallsBackToViolet() {
        assertEquals(AccentPalette.Violet, AccentPalette.fromName(null))
        assertEquals(AccentPalette.Violet, AccentPalette.fromName("Chartreuse"))
        assertEquals(AccentPalette.Teal, AccentPalette.fromName("Teal"))
    }
}
