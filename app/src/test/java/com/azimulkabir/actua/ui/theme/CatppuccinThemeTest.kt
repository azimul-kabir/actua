package com.azimulkabir.actua.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatppuccinThemeTest {
    private val everyTheme = CatppuccinFlavor.dark.flatMap { flavor ->
        CatppuccinAccent.entries.map { CatppuccinTheme(flavor, it) }
    }

    private fun textPairs(scheme: ColorScheme) = mapOf(
        "primary" to (scheme.onPrimary to scheme.primary),
        "primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
        "secondary" to (scheme.onSecondary to scheme.secondary),
        "secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
        "tertiary" to (scheme.onTertiary to scheme.tertiary),
        "tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
        "error" to (scheme.onError to scheme.error),
        "errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
        "background" to (scheme.onBackground to scheme.background),
        "surface" to (scheme.onSurface to scheme.surface),
        "inverse" to (scheme.inverseOnSurface to scheme.inverseSurface),
    )

    private fun cardSurfaces(scheme: ColorScheme) = mapOf(
        "surfaceVariant" to scheme.surfaceVariant,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
    )

    @Test fun everyTextOnColorPairMeetsWcagAaForEveryFlavorAndAccent() {
        for (theme in everyTheme) for (dark in listOf(false, true)) {
            val scheme = theme.colorScheme(dark)
            textPairs(scheme).forEach { (role, pair) ->
                val ratio = contrastRatio(pair.first, pair.second)
                assertTrue("${theme.darkFlavor} ${theme.accent} ${if (dark) "dark" else "light"} $role is $ratio:1", ratio >= 4.5)
            }
        }
    }

    @Test fun textStaysReadableOnEveryCardSurface() {
        for (theme in everyTheme) for (dark in listOf(false, true)) {
            val scheme = theme.colorScheme(dark)
            cardSurfaces(scheme).forEach { (name, surface) ->
                val label = "${theme.darkFlavor} ${if (dark) "dark" else "light"} $name"
                assertTrue("$label onSurface", contrastRatio(scheme.onSurface, surface) >= 4.5)
                assertTrue("$label onSurfaceVariant", contrastRatio(scheme.onSurfaceVariant, surface) >= 4.5)
            }
        }
    }

    @Test fun darkFlavorsShowTheAccentAtLeastThreeToOneOnThePageBackground() {
        // Latte's pastels are lighter than that on its own base for some accents, by Catppuccin's design, and the
        // named accent color itself is never altered, so only the dark flavors are held to it.
        for (theme in everyTheme) {
            val scheme = theme.colorScheme(darkTheme = true)
            val ratio = contrastRatio(scheme.primary, scheme.background)
            assertTrue("${theme.darkFlavor} ${theme.accent} is $ratio:1", ratio >= 3.0)
        }
    }

    @Test fun lightModeIsLatteAndDarkModeIsTheChosenFlavor() {
        val theme = CatppuccinTheme(CatppuccinFlavor.Macchiato, CatppuccinAccent.Teal)
        assertEquals(CatppuccinFlavor.Latte, theme.flavor(false))
        assertEquals(CatppuccinFlavor.Macchiato, theme.flavor(true))
        assertEquals(Color(0xFFEFF1F5), theme.colorScheme(false).background) // Latte Base
        assertEquals(Color(0xFF24273A), theme.colorScheme(true).background) // Macchiato Base
    }

    @Test fun theChosenAccentIsThePrimaryColorUnchanged() {
        for (accent in CatppuccinAccent.entries) {
            val theme = CatppuccinTheme(CatppuccinFlavor.Mocha, accent)
            assertEquals(CatppuccinMochaPalette.accents.getValue(accent), theme.colorScheme(true).primary)
            assertEquals(CatppuccinLattePalette.accents.getValue(accent), theme.colorScheme(false).primary)
        }
    }

    @Test fun defaultsAreMochaAndMauve() {
        val theme = CatppuccinTheme()
        assertEquals(CatppuccinFlavor.Mocha, theme.darkFlavor)
        assertEquals(CatppuccinAccent.Mauve, theme.accent)
        assertEquals(14, CatppuccinAccent.entries.size)
    }

    @Test fun surfaceContainersStepInTheDirectionOfElevation() {
        for (flavor in CatppuccinFlavor.dark) {
            val dark = CatppuccinTheme(flavor).colorScheme(true)
            val steps = listOf(dark.surfaceContainerLowest, dark.surfaceContainerLow, dark.surfaceContainer, dark.surfaceContainerHigh, dark.surfaceContainerHighest)
            assertTrue("$flavor dark steps get lighter", steps.zipWithNext().all { (a, b) -> luminanceOf(a) < luminanceOf(b) })
        }
        val light = CatppuccinTheme().colorScheme(false)
        val steps = listOf(light.surfaceContainerLowest, light.surfaceContainerLow, light.surfaceContainer, light.surfaceContainerHigh, light.surfaceContainerHighest)
        assertTrue("Latte steps get darker", steps.zipWithNext().all { (a, b) -> luminanceOf(a) > luminanceOf(b) })
    }

    @Test fun redAndMaroonAccentsKeepErrorsDistinctFromOrdinaryControls() {
        for (accent in listOf(CatppuccinAccent.Red, CatppuccinAccent.Maroon)) for (dark in listOf(false, true)) {
            val scheme = CatppuccinTheme(CatppuccinFlavor.Mocha, accent).colorScheme(dark)
            assertNotEquals("$accent error must differ from primary", scheme.primary, scheme.error)
            // A different lightness, not only a different hue.
            assertTrue("$accent ${if (dark) "dark" else "light"} error vs primary", contrastRatio(scheme.error, scheme.primary) >= 1.15)
            assertNotEquals(scheme.primaryContainer, scheme.errorContainer)
        }
    }

    @Test fun statusColorsAreGreenPeachAndRedOfTheFlavor() {
        val dark = CatppuccinTheme(CatppuccinFlavor.Mocha).statusColors(true)
        assertEquals(CatppuccinMochaPalette.accents.getValue(CatppuccinAccent.Green), dark.success)
        assertEquals(CatppuccinMochaPalette.accents.getValue(CatppuccinAccent.Peach), dark.warning)
        assertEquals(CatppuccinMochaPalette.accents.getValue(CatppuccinAccent.Red), dark.danger)
        val light = CatppuccinTheme(CatppuccinFlavor.Mocha).statusColors(false)
        assertEquals(CatppuccinLattePalette.accents.getValue(CatppuccinAccent.Red), light.danger)
    }

    @Test fun withARedAccentOverspentIsTheSameToneAsErrorsAndDiffersFromThePrimary() {
        for (accent in listOf(CatppuccinAccent.Red, CatppuccinAccent.Maroon)) for (dark in listOf(false, true)) {
            val theme = CatppuccinTheme(CatppuccinFlavor.Mocha, accent)
            val scheme = theme.colorScheme(dark)
            val status = theme.statusColors(dark)
            assertEquals(scheme.error, status.danger)
            assertTrue("$accent ${if (dark) "dark" else "light"}", contrastRatio(status.danger, scheme.primary) >= 1.15)
        }
    }

    @Test fun statusColorsAreToldApartByLightnessAsWellAsHue() {
        // For red-green color blindness the three need different lightness too, in every flavor.
        for (flavor in CatppuccinFlavor.entries) {
            val status = CatppuccinTheme(if (flavor.isDark) flavor else CatppuccinFlavor.Mocha).statusColors(flavor.isDark)
            assertTrue("$flavor success vs danger", contrastRatio(status.success, status.danger) >= 1.3)
        }
    }

    @Test fun anUnknownStoredDarkFlavorFallsBackToMocha() {
        assertEquals(CatppuccinFlavor.Mocha, CatppuccinFlavor.darkFromName(null))
        assertEquals(CatppuccinFlavor.Mocha, CatppuccinFlavor.darkFromName("Latte")) // Latte isn't a dark flavor
        assertEquals(CatppuccinFlavor.Frappe, CatppuccinFlavor.darkFromName("Frappe"))
    }

    @Test fun secondaryAndTertiaryNeverTakeAStatusHueUnlessTheAccentIsOne() {
        val status = setOf(CatppuccinAccent.Red, CatppuccinAccent.Maroon, CatppuccinAccent.Peach, CatppuccinAccent.Green)
        for (accent in CatppuccinAccent.entries - status) {
            for (step in listOf(-1, 1)) {
                val neighbor = CatppuccinScheme.neighbor(accent, step)
                assertTrue("$accent step $step gave $neighbor", neighbor !in status && neighbor != accent)
            }
        }
        assertEquals(CatppuccinAccent.Pink, CatppuccinScheme.neighbor(CatppuccinAccent.Mauve, -1))
        assertEquals(CatppuccinAccent.Yellow, CatppuccinScheme.neighbor(CatppuccinAccent.Mauve, 1))
        // A status accent keeps its plain ring neighbors.
        assertEquals(CatppuccinAccent.Maroon, CatppuccinScheme.neighbor(CatppuccinAccent.Red, 1))
    }

    private fun luminanceOf(color: Color): Float = color.luminance()
}
