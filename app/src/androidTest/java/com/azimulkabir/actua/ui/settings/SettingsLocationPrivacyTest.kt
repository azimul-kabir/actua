package com.azimulkabir.actua.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        composeRule.onNodeWithText("Location-aware payees").assertIsDisplayed()
        composeRule.onNodeWithText("Record payee locations").assertIsDisplayed()
        composeRule.onNodeWithText("Location permission: not granted").assertIsDisplayed()
    }
}
