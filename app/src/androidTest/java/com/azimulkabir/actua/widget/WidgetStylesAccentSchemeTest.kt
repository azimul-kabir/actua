package com.azimulkabir.actua.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.ui.theme.AccentPalette
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetStylesAccentSchemeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = DisplayPreferences(context)
    private var savedAccent = preferences.accent
    private var savedDynamic = preferences.useDynamicColor

    @Before fun useAPalette() {
        savedAccent = preferences.accent
        savedDynamic = preferences.useDynamicColor
        preferences.useDynamicColor = false
    }

    @After fun restore() {
        preferences.accent = savedAccent
        preferences.useDynamicColor = savedDynamic
    }

    @Test fun theSchemeIsTheChosenPaletteForTheRequestedDeviceTheme() {
        AccentPalette.entries.forEach { palette ->
            preferences.accent = palette
            listOf(false, true).forEach { night ->
                val scheme = WidgetStyles.accentScheme(context, preferences, night)
                val expected = palette.colorScheme(night)
                assertEquals("$palette night=$night container", expected.primaryContainer, scheme.primaryContainer)
                assertEquals("$palette night=$night on container", expected.onPrimaryContainer, scheme.onPrimaryContainer)
                assertEquals("$palette night=$night button", expected.primary, scheme.primary)
            }
        }
    }

    @Test fun lightAndDarkDeviceThemesGiveDifferentContainers() {
        preferences.accent = AccentPalette.Teal
        val light = WidgetStyles.accentScheme(context, preferences, night = false)
        val dark = WidgetStyles.accentScheme(context, preferences, night = true)
        assertNotEquals(light.primaryContainer, dark.primaryContainer)
    }
}
