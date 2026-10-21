package com.azimulkabir.actua.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for #570: the back gesture from the full-screen "Customize Home" and
 * "Customize Tab Bar" destinations must return to the Settings page they were opened from
 * (General, Display), not reset all the way to the Manage root the way other Settings rows do.
 * Uses the demo budget so AppNavigation's eager repository reads don't crash during composition
 * (see AppNavigationRestorationTest).
 */
class SettingsBackNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = BudgetFileManager(context)
    private val activeBudget = ActiveBudgetStore(context)
    private var previousBudgetId: String? = null
    private val strings get() = composeRule.activity

    @Before fun seedDemoBudget() {
        previousBudgetId = activeBudget.budgetId
        runCatching { if (files.budgetDirectory(DemoBudgetManager.BUDGET_ID).exists()) files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
        DemoBudgetManager.recreate(files)
        activeBudget.budgetId = DemoBudgetManager.BUDGET_ID
    }

    @After fun restorePreviousBudget() {
        activeBudget.budgetId = previousBudgetId
        runCatching { files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
    }

    @Test fun backFromCustomizeHomeReturnsToSettingsNotManage() {
        composeRule.setContent { MaterialTheme { AppNavigation() } }

        composeRule.onNodeWithContentDescription("Manage", useUnmergedTree = true).performClick()
        composeRule.onNodeWithContentDescription(strings.getString(R.string.settings_open_settings)).performClick()
        composeRule.onNodeWithText(strings.getString(R.string.settings_home)).performScrollTo().performClick()

        // Customize Home's own header title confirms we actually reached it. (Its "Customize Home"
        // content description belongs to a different icon, on the Home tab screen itself.)
        composeRule.onNodeWithText("Customize Home").assertExists()

        pressBack()

        // Landing back on the General settings page (title "Settings") means the row that opened
        // Customize Home, not the Manage root, is what the back gesture returned to.
        composeRule.onNodeWithText(strings.getString(R.string.settings_home)).assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.settings_page_display)).assertExists()
    }

    @Test fun backFromCustomizeTabBarReturnsToDisplaySettingsNotManage() {
        composeRule.setContent { MaterialTheme { AppNavigation() } }

        composeRule.onNodeWithContentDescription("Manage", useUnmergedTree = true).performClick()
        composeRule.onNodeWithContentDescription(strings.getString(R.string.settings_open_settings)).performClick()
        composeRule.onNodeWithText(strings.getString(R.string.settings_page_display)).performScrollTo().performClick()
        composeRule.onNodeWithText(strings.getString(R.string.settings_tab_bar)).performScrollTo().performClick()

        composeRule.onNodeWithContentDescription("Restore default tab bar").assertExists()

        pressBack()

        // Landing back on the Display settings page (still showing the "Tab Bar" row) means back
        // returned to Display, not all the way to the Manage root.
        composeRule.onNodeWithText(strings.getString(R.string.settings_tab_bar)).assertExists()
        composeRule.onNodeWithText(strings.getString(R.string.settings_number_format)).assertExists()
    }
}
