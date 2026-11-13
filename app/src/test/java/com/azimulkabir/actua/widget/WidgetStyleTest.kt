package com.azimulkabir.actua.widget

import androidx.compose.ui.graphics.toArgb
import com.azimulkabir.actua.ui.theme.AccentPalette
import com.azimulkabir.actua.ui.theme.CatppuccinAccent
import com.azimulkabir.actua.ui.theme.CatppuccinFlavor
import com.azimulkabir.actua.ui.theme.CatppuccinTheme
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

    /** The amber and red progress states must be clear shapes (3:1) on the background they sit on. */
    private fun assertStatusTonesAreClear(name: String, colors: WidgetStyleColors, backdrop: Int) {
        val background = over(colors.background, backdrop)
        for ((what, tone) in mapOf("warning" to colors.warning, "danger" to colors.danger)) {
            val ratio = contrast(tone, background)
            assertTrue("$name $what is $ratio:1", ratio >= 3.0)
        }
        assertTrue("$name warning and danger differ", colors.warning != colors.danger)
        assertTrue("$name warning differs from the usual bar", colors.warning != colors.accent)
        assertTrue("$name danger differs from the usual bar", colors.danger != colors.accent)
    }

    @Test
    fun progressStatesAreClearInEveryFixedStyleLightAndDark() {
        for (style in listOf(WidgetStyle.SolidDark, WidgetStyle.SolidLight)) {
            for (night in listOf(false, true)) {
                assertStatusTonesAreClear(style.name, style.colors(night, null)!!, backdrop = 0xFFFFFFFF.toInt())
            }
        }
        for (night in listOf(false, true)) {
            val colors = WidgetStyle.Translucent.colors(night, null)!!
            assertStatusTonesAreClear("Translucent night=$night over white", colors, backdrop = 0xFFFFFFFF.toInt())
            assertStatusTonesAreClear("Translucent night=$night over black", colors, backdrop = 0xFF000000.toInt())
        }
    }

    @Test
    fun progressStatesAreClearInAccentTintedForEveryAccentLightAndDark() {
        for (accent in AccentPalette.entries) {
            for (night in listOf(false, true)) {
                val colors = WidgetStyle.AccentTinted.colors(night, accent.colorScheme(night))!!
                assertStatusTonesAreClear("AccentTinted $accent night=$night", colors, backdrop = 0xFFFFFFFF.toInt())
            }
        }
    }

    @Test
    fun accentTintedIsReadableAndItsProgressStatesAreClearForEveryCatppuccinFlavorAndAccent() {
        for (flavor in CatppuccinFlavor.dark) for (accent in CatppuccinAccent.entries) {
            val theme = CatppuccinTheme(flavor, accent)
            for (night in listOf(false, true)) {
                val scheme = theme.colorScheme(night)
                val colors = WidgetStyle.AccentTinted.colors(night, scheme, theme.statusColors(night))!!
                val name = "AccentTinted $flavor $accent night=$night"
                assertReadable(name, colors, backdrop = 0xFFFFFFFF.toInt())
                assertStatusTonesAreClear(name, colors, backdrop = 0xFFFFFFFF.toInt())
                val background = colors.background or (0xFF shl 24)
                assertTrue("$name progress bar is ${contrast(colors.accent, background)}:1", contrast(colors.accent, background) >= 3.0)
                assertTrue("$name progress bar apart from warning", contrast(colors.accent, colors.warning) >= 1.15)
                assertTrue("$name progress bar apart from overspent", contrast(colors.accent, colors.danger) >= 1.15)
            }
        }
    }

    @Test
    fun accentTintedProgressBarsUseCatppuccinPeachAndRedWhereTheyAlreadyReadAndStayCloseToThemElsewhere() {
        val theme = CatppuccinTheme(CatppuccinFlavor.Mocha, CatppuccinAccent.Mauve)
        val scheme = theme.colorScheme(darkTheme = true)
        val status = theme.statusColors(darkTheme = true)
        val colors = WidgetStyle.AccentTinted.colors(night = true, accent = scheme, status = status)!!
        // Mocha's Peach and Red read at 3:1 on the dark container, so they are used as they are.
        assertEquals(status.warning.toArgb(), colors.warning)
        assertEquals(status.danger.toArgb(), colors.danger)
        // Without a Catppuccin theme the generic amber and red are unchanged.
        val plain = WidgetStyle.AccentTinted.colors(night = true, accent = AccentPalette.Teal.colorScheme(true))!!
        assertTrue(plain.warning != colors.warning)
    }

    @Test
    fun accentTintedTakesItsBackgroundTextAndButtonFromTheCatppuccinScheme() {
        val scheme = CatppuccinTheme(CatppuccinFlavor.Mocha, CatppuccinAccent.Green).colorScheme(darkTheme = true)
        val colors = WidgetStyle.AccentTinted.colors(night = true, accent = scheme)!!
        assertEquals(scheme.primaryContainer.toArgb(), colors.background)
        assertEquals(scheme.onPrimaryContainer.toArgb(), colors.onBackground)
        assertEquals(scheme.primary.toArgb(), colors.button)
        assertEquals(scheme.onPrimary.toArgb(), colors.buttonText)
    }

    @Test
    fun statusToneKeepsAPassingToneAndPicksTheStrongerOfTheTwo() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val deep = 0xFF8A5400.toInt()
        val pale = 0xFFFFB74D.toInt()
        assertEquals("deep tone on a light background", deep, statusTone(deep, pale, white))
        assertEquals("pale tone on a dark background", pale, statusTone(deep, pale, black))
    }

    @Test
    fun statusToneMovesTowardWhiteOrBlackWhenNeitherToneIsClearEnough() {
        val midTone = 0xFF8A5400.toInt()
        val tone = statusTone(midTone, midTone, background = 0xFF8A5C10.toInt())
        assertTrue("the adjusted tone is ${contrast(tone, 0xFF8A5C10.toInt())}:1", contrast(tone, 0xFF8A5C10.toInt()) >= 3.0)
    }

    @Test
    fun progressFillFollowsTheStatus() {
        val colors = WidgetStyle.SolidLight.colors(night = false, accent = null)!!
        assertEquals(colors.primary, colors.progressFill(ProgressStatus.Normal))
        assertEquals(colors.warning, colors.progressFill(ProgressStatus.Warning))
        assertEquals(colors.danger, colors.progressFill(ProgressStatus.Overspent))
    }

    @Test
    fun aStyleIsOnlyDrawnForASupporterOnThePlayBuild() {
        val stored = WidgetStyle.SolidDark
        assertEquals("a supporter", stored, effectiveWidgetStyle(perksAvailable = true, isSupporter = true, stored = stored, accentTintedAvailable = true))
        assertEquals(
            "not a supporter, or support lapsed before the reset ran",
            WidgetStyle.Default, effectiveWidgetStyle(perksAvailable = true, isSupporter = false, stored = stored, accentTintedAvailable = true),
        )
        assertEquals(
            "the F-Droid build has no extras, whatever is stored",
            WidgetStyle.Default, effectiveWidgetStyle(perksAvailable = false, isSupporter = true, stored = stored, accentTintedAvailable = true),
        )
        assertEquals(
            WidgetStyle.Default, effectiveWidgetStyle(perksAvailable = false, isSupporter = false, stored = stored, accentTintedAvailable = true),
        )
    }

    @Test
    fun accentTintedIsNotDrawnWhereItCannotBeTinted() {
        assertEquals(
            WidgetStyle.Default,
            effectiveWidgetStyle(perksAvailable = true, isSupporter = true, stored = WidgetStyle.AccentTinted, accentTintedAvailable = false),
        )
        assertEquals(
            WidgetStyle.AccentTinted,
            effectiveWidgetStyle(perksAvailable = true, isSupporter = true, stored = WidgetStyle.AccentTinted, accentTintedAvailable = true),
        )
        assertEquals(
            WidgetStyle.Default,
            effectiveWidgetStyle(perksAvailable = true, isSupporter = true, stored = WidgetStyle.Default, accentTintedAvailable = false),
        )
    }

    @Test
    fun accentTintedSecondaryTextIsVisiblySofterThanTheMainText() {
        for (accent in AccentPalette.entries) {
            for (night in listOf(false, true)) {
                val name = "AccentTinted $accent night=$night"
                val colors = WidgetStyle.AccentTinted.colors(night, accent.colorScheme(night))!!
                val main = contrast(colors.onBackground, colors.background)
                val secondary = contrast(colors.secondary, colors.background)
                assertTrue("$name: secondary text is $secondary:1 against $main:1 for the main text", secondary <= main * 0.92)
                assertTrue("$name: secondary text stays readable ($secondary:1)", secondary >= 4.5)
            }
        }
    }

    @Test
    fun secondaryToneKeepsItsSoftStrengthWhenThereIsPlentyOfContrast() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val tile = blendArgb(black, white, 0.08f)
        val tone = secondaryTone(onBackground = black, background = white, tile = tile)
        assertEquals(blendArgb(black, white, 0.78f), tone)
    }

    @Test
    fun secondaryToneFirmsUpOnlyAsFarAsReadabilityNeeds() {
        // A text color that is barely readable to begin with: the soft tone would fall below 4.5:1.
        val background = 0xFFFFFFFF.toInt()
        val onBackground = 0xFF6A6A6A.toInt()
        val tile = blendArgb(onBackground, background, 0.08f)
        val tone = secondaryTone(onBackground, background, tile)
        assertTrue(contrast(tone, background) >= 4.5)
        assertTrue(contrast(tone, tile) >= 4.5)
        assertTrue("it moved toward the text color, not all the way", tone != onBackground || contrast(onBackground, tile) < 4.5)
    }

    @Test
    fun secondaryToneIsTheMainColorWhenTheContainerLeavesNoRoom() {
        val background = 0xFF808080.toInt()
        val onBackground = 0xFF909090.toInt()
        assertEquals(onBackground, secondaryTone(onBackground, background, blendArgb(onBackground, background, 0.08f)))
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
        assertEquals(ProgressStatus.Warning, ProgressStatus.of(percentSpent = 99, overspent = false))
        // Spent exactly to the budget is on plan, not a warning.
        assertEquals(ProgressStatus.Normal, ProgressStatus.of(percentSpent = 100, overspent = false))
        assertEquals(ProgressStatus.Overspent, ProgressStatus.of(percentSpent = 100, overspent = true))
        // A negative balance wins even when the bar isn't full (a rolled-over deficit).
        assertEquals(ProgressStatus.Overspent, ProgressStatus.of(percentSpent = 10, overspent = true))
    }
}
