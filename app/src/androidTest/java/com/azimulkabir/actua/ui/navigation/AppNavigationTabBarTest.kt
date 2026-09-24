package com.azimulkabir.actua.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
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
 * Tab bar Slice 2 (#479) regression coverage: the bottom bar renders whatever [TabBarPreferences]
 * currently specifies, and Reports can be configured as a root tab with correct back-navigation.
 * Uses the demo budget so AppNavigation's eager repository reads don't crash during composition
 * (see AppNavigationRestorationTest).
 */
class AppNavigationTabBarTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = BudgetFileManager(context)
    private val activeBudget = ActiveBudgetStore(context)
    private val tabBarPreferences = TabBarPreferences(context)
    private val displayPreferences = DisplayPreferences(context)
    private var previousBudgetId: String? = null
    private var previousStartPage: String? = null

    @Before fun seedDemoBudget() {
        previousBudgetId = activeBudget.budgetId
        previousStartPage = displayPreferences.startPage
        runCatching { if (files.budgetDirectory(DemoBudgetManager.BUDGET_ID).exists()) files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
        DemoBudgetManager.recreate(files)
        activeBudget.budgetId = DemoBudgetManager.BUDGET_ID
    }

    @After fun restorePreviousBudget() {
        activeBudget.budgetId = previousBudgetId
        tabBarPreferences.restoreDefaults()
        previousStartPage?.let { displayPreferences.startPage = it }
        runCatching { files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
    }

    @Test fun defaultLayoutRendersTodaysFixedFiveTabBottomBarUnchanged() {
        tabBarPreferences.restoreDefaults()
        composeRule.setContent { MaterialTheme { AppNavigation() } }

        // NavigationBarItem merges its icon/label/selection-state semantics into one node, so the
        // icon's contentDescription is only visible in the unmerged tree.
        for (label in listOf("Budget", "Accounts", "Add", "Reports", "Manage")) {
            composeRule.onNodeWithContentDescription(label, useUnmergedTree = true).assertExists()
        }
        composeRule.onNodeWithContentDescription("Home", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Transactions", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun reportsCanBeConfiguredAsATabAndBackReturnsToConfiguredStartPage() {
        // The back gesture's exit tab follows the Display settings start page (#568), so pin it
        // to Home here to keep this test's expectation explicit rather than relying on whatever
        // the shared default happens to be.
        displayPreferences.startPage = "Home"
        tabBarPreferences.save(
            TabBarLayout(
                order = listOf(
                    TabItem.HOME, TabItem.BUDGET, TabItem.TRANSACTIONS, TabItem.ACCOUNTS,
                    TabItem.MANAGE, TabItem.REPORTS, TabItem.ADD,
                ),
                hidden = setOf(TabItem.TRANSACTIONS, TabItem.ADD),
            ),
        )
        composeRule.setContent { MaterialTheme { AppNavigation() } }

        // Reports is now a visible tab; Transactions was hidden by the layout above. (See
        // defaultLayoutRendersTodaysFixedFiveTabBottomBarUnchanged for why useUnmergedTree is needed.)
        composeRule.onNodeWithContentDescription("Reports", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Transactions", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Reports", useUnmergedTree = true).performClick()
        // "Customize Home" only exists on Home's root content, so its absence confirms the tab
        // actually switched away from Home; the Reports screen renders its own "Reports" title.
        composeRule.onNodeWithContentDescription("Customize Home", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onAllNodesWithText("Reports").onFirst().assertExists()

        pressBack()
        composeRule.onNodeWithContentDescription("Customize Home", useUnmergedTree = true).assertExists()
    }

    @Test fun defaultLayoutShowsAddTabAndNoFab() {
        // The default tab bar (#570) now includes Add as a visible tab, so the FAB is replaced.
        tabBarPreferences.restoreDefaults()
        composeRule.setContent { MaterialTheme { AppNavigation() } }

        composeRule.onNodeWithText("Transaction", useUnmergedTree = true).assertDoesNotExist() // the FAB's label
        composeRule.onNodeWithContentDescription("Add", useUnmergedTree = true).assertExists()
    }

    @Test fun addTabLayoutHidesFabAndOpensAddTransactionEditor() {
        // Tab bar Slice 3 (#481): configuring "Add" as a tab replaces the FAB entirely.
        tabBarPreferences.save(
            TabBarLayout(
                order = listOf(
                    TabItem.HOME, TabItem.BUDGET, TabItem.ACCOUNTS, TabItem.ADD,
                    TabItem.REPORTS, TabItem.MANAGE, TabItem.TRANSACTIONS,
                ),
                hidden = setOf(TabItem.REPORTS, TabItem.TRANSACTIONS),
            ),
        )
        composeRule.setContent { MaterialTheme { AppNavigation() } }

        composeRule.onNodeWithText("Transaction", useUnmergedTree = true).assertDoesNotExist() // FAB is gone
        composeRule.onNodeWithContentDescription("Add", useUnmergedTree = true).assertExists()

        composeRule.onNodeWithContentDescription("Add", useUnmergedTree = true).performClick()
        // The add-transaction editor's top bar has a "Cancel" close action unique to that screen.
        composeRule.onNodeWithContentDescription("Cancel", useUnmergedTree = true).assertExists()
    }

    @Test fun customizingFromSettingsUpdatesTheBottomBarImmediately() {
        // Tab bar Slice 4 (#483): the Customize Tab Bar screen, reached from Settings, is the only
        // way a real user can reach any of the non-default layouts the earlier slices/tests above
        // construct directly via TabBarPreferences.
        tabBarPreferences.restoreDefaults()
        composeRule.setContent { MaterialTheme { AppNavigation() } }

        composeRule.onNodeWithContentDescription("Manage", useUnmergedTree = true).performClick()
        composeRule.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Display").performScrollTo().performClick()
        composeRule.onNodeWithText("Tab Bar").performScrollTo().performClick()

        composeRule.onNodeWithContentDescription(
            "Show ${TabItem.TRANSACTIONS.label} in the bottom bar",
            useUnmergedTree = true,
        ).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(
            "Show ${TabItem.REPORTS.label} in the bottom bar",
            useUnmergedTree = true,
        ).performScrollTo().performClick()

        // Leaving the (full-screen) Customize Tab Bar destination returns to the Display settings
        // page it was opened from (#570), not all the way back to Manage; no app restart or
        // explicit save step is needed for the bottom bar to reflect the change either way.
        pressBack()

        composeRule.onNodeWithText("Tab Bar").assertExists()
        composeRule.onNodeWithContentDescription("Reports", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Transactions", useUnmergedTree = true).assertDoesNotExist()
    }
}
