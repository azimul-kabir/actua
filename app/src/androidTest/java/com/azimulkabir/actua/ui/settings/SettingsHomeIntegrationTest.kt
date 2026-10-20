package com.azimulkabir.actua.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
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

/**
 * Regression coverage for the Home migration's Manage-screen integration points: Manage keeps a
 * clear Reports entry, and the start-page setting recognizes Home as a destination.
 */
@RunWith(AndroidJUnit4::class)
class SettingsHomeIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val strings get() = composeRule.activity

    @Test
    fun manageScreenReportsRowRoutesToTheSharedReportsDestination() {
        var reportsClicked = false
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(onReportsClick = { reportsClicked = true })
            }
        }

        composeRule.onNodeWithText(strings.getString(R.string.settings_reports)).performScrollTo().performClick()

        assertTrue(reportsClicked)
    }

    @Test
    fun manageScreenHomeRowRoutesToHomeWhenHomeIsNotATab() {
        var homeClicked = false
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(showHomeShortcut = true, onHomeClick = { homeClicked = true })
            }
        }

        composeRule.onNodeWithText(strings.getString(R.string.settings_manage_home)).performScrollTo().performClick()

        assertTrue(homeClicked)
    }

    @Test
    fun insightsHidesShortcutsForTabsAlreadyInTheBottomBar() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(showHomeShortcut = false, showReportsShortcut = true)
            }
        }
        composeRule.onNodeWithText(strings.getString(R.string.settings_reports)).assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.settings_manage_home)).assertDoesNotExist()
    }

    @Test
    fun insightsDisappearsWhenHomeAndReportsAreBothTabs() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(showHomeShortcut = false, showReportsShortcut = false)
            }
        }
        composeRule.onNodeWithText(strings.getString(R.string.settings_section_insights), ignoreCase = true).assertDoesNotExist()
        composeRule.onNodeWithText(strings.getString(R.string.settings_reports)).assertDoesNotExist()
    }

    @Test
    fun startPageOffersHomeAsAnOption() {
        var selectedStartPage: String? = null
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(startPage = "Budget", onStartPageChange = { selectedStartPage = it })
            }
        }

        composeRule.onNodeWithContentDescription(strings.getString(R.string.settings_open_settings)).performClick()
        composeRule.onNodeWithText(strings.getString(R.string.settings_page_display)).performScrollTo().performClick()
        // The picker label resolves from MainDestination's labelRes (navigation_* strings).
        composeRule.onNodeWithText(strings.getString(R.string.navigation_budget)).performScrollTo().performClick()
        composeRule.onNodeWithText(strings.getString(R.string.navigation_home)).performClick()

        // The stored start-page preference keeps the English MainDestination token, not the label.
        assertTrue(selectedStartPage == "Home")
    }
}
