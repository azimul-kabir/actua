package com.azimulkabir.actua.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import com.azimulkabir.actua.data.navigation.TabBarLayout
import com.azimulkabir.actua.data.navigation.TabItem
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.TabBarPreferences
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Home Slice 5 (#346) regression coverage: root destination state must survive process/config
 * recreation rather than resetting to the app's default tab. Uses the demo budget so
 * AppNavigation's eager repository reads (see DemoBudgetTest) don't crash during composition.
 */
class AppNavigationRestorationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = BudgetFileManager(context)
    private val activeBudget = ActiveBudgetStore(context)
    private val displayPreferences = DisplayPreferences(context)
    private val tabBarPreferences = TabBarPreferences(context)
    private var previousBudgetId: String? = null
    private var previousStartPage: String = "Budget"

    @Before fun seedDemoBudget() {
        previousBudgetId = activeBudget.budgetId
        previousStartPage = displayPreferences.startPage
        runCatching { if (files.budgetDirectory(DemoBudgetManager.BUDGET_ID).exists()) files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
        DemoBudgetManager.recreate(files)
        activeBudget.budgetId = DemoBudgetManager.BUDGET_ID
        displayPreferences.startPage = "Home"
        // Home is hidden from the default tab bar, so make it visible here - this test is about
        // destination state surviving recreation, not about the tab bar's own default contents.
        tabBarPreferences.save(
            TabBarLayout(
                order = listOf(
                    TabItem.HOME, TabItem.BUDGET, TabItem.ACCOUNTS, TabItem.TRANSACTIONS,
                    TabItem.ADD, TabItem.REPORTS, TabItem.MANAGE,
                ),
                hidden = setOf(TabItem.TRANSACTIONS, TabItem.ADD),
            ),
        )
    }

    @After fun restorePreviousBudget() {
        displayPreferences.startPage = previousStartPage
        activeBudget.budgetId = previousBudgetId
        tabBarPreferences.restoreDefaults()
        runCatching { files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
    }

    @Test fun rootDestinationSurvivesRecreationLandingBackOnHome() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                AppNavigation()
            }
        }

        composeRule.onNodeWithContentDescription("Customize Home").assertExists()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithContentDescription("Customize Home").assertExists()
    }
}
