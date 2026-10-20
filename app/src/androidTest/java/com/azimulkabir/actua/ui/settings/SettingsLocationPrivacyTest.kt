package com.azimulkabir.actua.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsLocationPrivacyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val strings get() = composeRule.activity

    @Test
    fun privacyPageShowsOptionalLocationControls() {
        composeRule.setContent {
            SettingsScreen()
        }

        composeRule.onNodeWithContentDescription(strings.getString(R.string.settings_open_settings)).performClick()
        composeRule.onNodeWithText(strings.getString(R.string.settings_page_privacy)).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(strings.getString(R.string.settings_section_location_payees)).assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.settings_record_payee_locations)).assertExists()
    }
}
