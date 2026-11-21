package com.azimulkabir.actua.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomAccentTest {

    /** Synthetic seeds: very light, very dark, low saturation, pure and mid colors. */
    private val seeds = listOf(
        0xFFFFFFFF, 0xFF000000, 0xFFFFFF00, 0xFFFFF59D, 0xFF0A0A1F, 0xFF101010,
        0xFF808080, 0xFFC8C8CC, 0xFF7A7A80, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
        0xFF00FFFF, 0xFFFF00FF, 0xFFC2185B, 0xFF2E7D6B, 0xFF6750A4, 0xFFFFC107,
        0xFFE0F7FA, 0xFF1B2A3A, 0xFFFFE0B2, 0xFF4E342E, 0xFF7C7C6A,
    ).map { it.toInt() } + (0 until 36).map { CustomAccent.fromHsv(it * 10f, 0.8f, 0.9f) } +
        (0 until 12).flatMap { h -> listOf(0.05f, 0.5f, 1f).flatMap { s -> listOf(0.1f, 0.5f, 1f).map { v -> CustomAccent.fromHsv(h * 30f, s, v) } } }

    private fun contrast(a: Color, b: Color) = CustomAccent.contrast(a, b)

    private fun textPairs(c: AccentColors) = mapOf(
        "primary" to (c.onPrimary to c.primary),
        "primaryContainer" to (c.onPrimaryContainer to c.primaryContainer),
        "secondary" to (c.onSecondary to c.secondary),
        "secondaryContainer" to (c.onSecondaryContainer to c.secondaryContainer),
        "tertiary" to (c.onTertiary to c.tertiary),
        "tertiaryContainer" to (c.onTertiaryContainer to c.tertiaryContainer),
    )

    @Test
    fun everyDerivedTextPairMeetsWcagAaInBothThemes() {
        for (seed in seeds) {
            val derived = CustomAccent.derive(seed)
            for (dark in listOf(false, true)) {
                textPairs(derived.colors(dark)).forEach { (role, pair) ->
                    val ratio = contrast(pair.first, pair.second)
                    assertTrue("${CustomAccent.formatHex(seed)} ${if (dark) "dark" else "light"} $role is $ratio:1", ratio >= 4.5)
                }
            }
        }
    }

    @Test
    fun surfacesStayReadableAndPrimaryReadsAsAnIconOnThePage() {
        for (seed in seeds) {
            val derived = CustomAccent.derive(seed)
            for (dark in listOf(false, true)) {
                val base = if (dark) ActuaDarkColorScheme else ActuaLightColorScheme
                val c = derived.colors(dark)
                val name = "${CustomAccent.formatHex(seed)} ${if (dark) "dark" else "light"}"
                for (surface in listOf(c.surfaceVariant, c.surfaceContainerLow, c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest)) {
                    assertTrue("$name onSurface", contrast(base.onSurface, surface) >= 4.5)
                    assertTrue("$name onSurfaceVariant", contrast(c.onSurfaceVariant, surface) >= 4.5)
                }
                assertTrue("$name outline", contrast(c.outline, c.surfaceVariant) >= 3.0)
                assertTrue("$name primary on page", contrast(c.primary, base.background) >= 3.0)
            }
        }
    }

    @Test
    fun containersStayDarkInTheDarkThemeAndLightInTheLightTheme() {
        for (seed in seeds) {
            val derived = CustomAccent.derive(seed)
            for (c in listOf(derived.dark.primaryContainer, derived.dark.secondaryContainer, derived.dark.tertiaryContainer)) {
                assertTrue("${CustomAccent.formatHex(seed)} dark container", contrast(Color.White, c) >= 7.0)
            }
            for (c in listOf(derived.light.primaryContainer, derived.light.secondaryContainer, derived.light.tertiaryContainer)) {
                assertTrue("${CustomAccent.formatHex(seed)} light container", contrast(Color.Black, c) >= 7.0)
            }
        }
    }

    @Test
    fun aMidColorIsKeptAsThePickedPrimaryInTheLightTheme() {
        val derived = CustomAccent.derive(0xFF2E7D6B.toInt())
        assertFalse(derived.adjusted)
        assertEquals(Color(0xFF2E7D6B), derived.light.primary)
    }

    @Test
    fun aVeryLightColorIsDarkenedAndFlaggedAsAdjusted() {
        val derived = CustomAccent.derive(0xFFFFFF00.toInt())
        assertTrue(derived.adjusted)
        assertTrue(contrast(Color.White, derived.light.primary) >= 4.5)
    }

    @Test
    fun transparencyInThePickedColorIsIgnored() {
        val a = CustomAccent.derive(0x802E7D6B.toInt())
        val b = CustomAccent.derive(0xFF2E7D6B.toInt())
        assertEquals(b.seed, a.seed)
        assertEquals(b.light.primary, a.light.primary)
    }

    @Test
    fun hexParsingAcceptsSixAndThreeDigitsWithOrWithoutHash() {
        assertEquals(0xFF2E7D6B.toInt(), CustomAccent.parseHex("#2E7D6B"))
        assertEquals(0xFF2E7D6B.toInt(), CustomAccent.parseHex(" 2e7d6b "))
        assertEquals(0xFFAABBCC.toInt(), CustomAccent.parseHex("#abc"))
        assertNull(CustomAccent.parseHex(""))
        assertNull(CustomAccent.parseHex("#12345"))
        assertNull(CustomAccent.parseHex("#GGGGGG"))
        assertEquals("#2E7D6B", CustomAccent.formatHex(0xFF2E7D6B.toInt()))
    }

    @Test
    fun hsvRoundTripsWithinOneStep() {
        for (seed in seeds) {
            val hsv = CustomAccent.toHsv(seed)
            val back = CustomAccent.fromHsv(hsv[0], hsv[1], hsv[2])
            for (shift in listOf(16, 8, 0)) {
                val d = ((seed shr shift) and 0xFF) - ((back shr shift) and 0xFF)
                assertTrue("${CustomAccent.formatHex(seed)} -> ${CustomAccent.formatHex(back)}", kotlin.math.abs(d) <= 1)
            }
        }
    }
}
