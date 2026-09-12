package com.azimulkabir.actua.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.fetchSemanticsNodes
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsLocationPrivacyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun privacyPageShowsOptionalLocationControls() {
        composeRule.setContent {
            SettingsScreen()
        }

        composeRule.onNodeWithText("Privacy").performClick()
        composeRule.waitForIdle()

        waitForText("Location-aware payees")
        waitForText("Record payee locations")
        waitForText("Location permission: not granted")
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
