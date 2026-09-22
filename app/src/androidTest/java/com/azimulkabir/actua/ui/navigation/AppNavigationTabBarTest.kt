package com.azimulkabir.actua.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import com.azimulkabir.actua.data.navigation.TabBarLayout
import com.azimulkabir.actua.data.navigation.TabItem
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
    private var previousBudgetId: String? = null

    @Before fun seedDemoBudget() {
        previousBudgetId = activeBudget.budgetId
        runCatching { if (files.budgetDirectory(DemoBudgetManager.BUDGET_ID).exists()) files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
        DemoBudgetManager.recreate(files)
        activeBudget.budgetId = DemoBudgetManager.BUDGET_ID
    }

    @After fun restorePreviousBudget() {
        activeBudget.budgetId = previousBudgetId
        tabBarPreferences.restoreDefaults()
        runCatching { files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
    }

    @Test fun defaultLayoutRendersTodaysFixedFiveTabBottomBarUnchanged() {
        tabBarPreferences.restoreDefaults()
        composeRule.setContent { MaterialTheme { AppNavigation() } }

        // NavigationBarItem merges its icon/label/selection-state semantics into one node, so the
        // icon's contentDescription is only visible in the unmerged tree.
        for (label in listOf("Home", "Budget", "Transactions", "Accounts", "Manage")) {
            composeRule.onNodeWithContentDescription(label, useUnmergedTree = true).assertExists()
        }
    }

    @Test fun reportsCanBeConfiguredAsATabAndBackReturnsToHome() {
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

    @Test fun defaultLayoutShowsFabAndNoAddTab() {
        // Tab bar Slice 3 (#481): the FAB-based layout is the default, unchanged by this slice.
        tabBarPreferences.restoreDefaults()
        composeRule.setContent { MaterialTheme { AppNavigation() } }

        composeRule.onNodeWithText("Transaction").assertExists() // the FAB's label
        composeRule.onNodeWithContentDescription("+ Add", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun addTabLayoutHidesFabAndOpensAddTransactionEditor() {
        // Tab bar Slice 3 (#481): configuring "+ Add" as a tab replaces the FAB entirely.
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

        composeRule.onNodeWithText("Transaction").assertDoesNotExist() // FAB is gone
        composeRule.onNodeWithContentDescription("+ Add", useUnmergedTree = true).assertExists()

        composeRule.onNodeWithContentDescription("+ Add", useUnmergedTree = true).performClick()
        // The add-transaction editor's top bar has a "Cancel" close action unique to that screen.
        composeRule.onNodeWithContentDescription("Cancel", useUnmergedTree = true).assertExists()
    }
}
