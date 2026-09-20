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

/**
 * Regression coverage for the Home migration's Manage-screen integration points: Manage keeps a
 * clear Reports entry, and the start-page setting recognizes Home as a destination.
 */
@RunWith(AndroidJUnit4::class)
class SettingsHomeIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun manageScreenReportsRowRoutesToTheSharedReportsDestination() {
        var reportsClicked = false
        composeRule.setContent {
            SettingsScreen(onReportsClick = { reportsClicked = true })
        }

        composeRule.onNodeWithText("Reports").performScrollTo().performClick()

        assertTrue(reportsClicked)
    }

    @Test
    fun startPageOffersHomeAsAnOption() {
        var selectedStartPage: String? = null
        composeRule.setContent {
            SettingsScreen(startPage = "Budget", onStartPageChange = { selectedStartPage = it })
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Display").performScrollTo().performClick()
        composeRule.onNodeWithText("Budget").performScrollTo().performClick()
        composeRule.onNodeWithText("Home").performClick()

        assertTrue(selectedStartPage == "Home")
    }
}
