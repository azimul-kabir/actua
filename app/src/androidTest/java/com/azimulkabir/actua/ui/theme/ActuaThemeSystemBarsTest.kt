package com.azimulkabir.actua.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActuaThemeSystemBarsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBarIconsFollowAppearanceLiveWithoutRecreation() {
        assertBarsFollowAppearance(dynamicColor = false)
    }

    @Test
    fun systemBarIconsFollowAppearanceWithDynamicColor() {
        assertBarsFollowAppearance(dynamicColor = true)
    }

    private fun assertBarsFollowAppearance(dynamicColor: Boolean) {
        var appearance by mutableStateOf("Dark")
        composeRule.setContent {
            ActuaTheme(appearance = appearance, dynamicColor = dynamicColor) {}
        }

        // Explicit Dark and Light both run regardless of the device's own night mode, so one of
        // them always disagrees with the system and would fail if the bars followed the system.
        composeRule.waitForIdle()
        assertLightBars(expected = false)

        appearance = "Light"
        composeRule.waitForIdle()
        assertLightBars(expected = true)

        appearance = "Dark"
        composeRule.waitForIdle()
        assertLightBars(expected = false)
    }

    private fun assertLightBars(expected: Boolean) {
        composeRule.runOnUiThread {
            val window = composeRule.activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            assertEquals(expected, controller.isAppearanceLightStatusBars)
            assertEquals(expected, controller.isAppearanceLightNavigationBars)
        }
    }
}
