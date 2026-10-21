package com.azimulkabir.actua.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAutomationNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val strings get() = composeRule.activity

    @Test
    fun billsCalendarIsASeparateAutomationDestination() {
        var opened = false
        composeRule.setContent {
            SettingsScreen(onBillsCalendarClick = { opened = true })
        }

        composeRule.onNodeWithText(strings.getString(R.string.settings_bills_calendar))
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(5_000) { opened }
        assertTrue(opened)
        composeRule.onNodeWithText(strings.getString(R.string.settings_scheduled_transactions)).assertExists()
    }
    @Test
    fun manageGearOpensGeneralSettingsAndBackReturnsToManage() {
        composeRule.setContent {
            SettingsScreen()
        }

        composeRule.onNodeWithContentDescription(strings.getString(R.string.settings_open_settings)).performClick()

        composeRule.onNodeWithText(strings.getString(R.string.settings_page_transactions_accounts)).assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.settings_bills_calendar)).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText(strings.getString(R.string.settings_bills_calendar)).assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.settings_page_transactions_accounts)).assertDoesNotExist()
    }
}
