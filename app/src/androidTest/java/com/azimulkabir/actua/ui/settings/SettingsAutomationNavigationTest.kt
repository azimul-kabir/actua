package com.azimulkabir.actua.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAutomationNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun billsCalendarIsASeparateAutomationDestination() {
        var opened = false
        composeRule.setContent {
            SettingsScreen(onBillsCalendarClick = { opened = true })
        }

        composeRule.onNodeWithText("Bills & Calendar")
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(5_000) { opened }
        assertTrue(opened)
        composeRule.onNodeWithText("Scheduled Transactions").assertExists()
    }
    @Test
    fun manageGearOpensGeneralSettingsAndBackReturnsToManage() {
        composeRule.setContent {
            SettingsScreen()
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNodeWithText("Transactions & Accounts").assertExists()
        composeRule.onNodeWithText("Bills & Calendar").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Bills & Calendar").assertExists()
        composeRule.onNodeWithText("Transactions & Accounts").assertDoesNotExist()
    }
}
