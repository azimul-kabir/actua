package com.azimulkabir.actua.ui.accounts

import com.azimulkabir.actua.data.home.MonthStartRule
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.Account
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() {
        context.getSharedPreferences("budget_ui_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("active_budget", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun favoritesSectionAppearsAndUpdatesWhenFavoriteIdsChange() {
        val checking = Account("Checking", 10_000, "checking", id = "checking")
        val savings = Account("Savings", 20_000, "savings", id = "savings")
        var favoriteIds by mutableStateOf(emptySet<String>())

        compose.setContent {
            MaterialTheme {
                AccountsScreen(
                    accounts = listOf(checking, savings),
                    favoriteAccountIds = favoriteIds,
                    showMonthlySummary = false,
                )
            }
        }

        compose.onNodeWithText("Favorites").assertDoesNotExist()

        compose.runOnIdle { favoriteIds = setOf(checking.id) }

        compose.onNodeWithText("Favorites").assertExists()
        // Favorite accounts remain in their normal group as well.
        compose.onAllNodesWithText("Checking").assertCountEquals(2)
        compose.onAllNodesWithText("Savings").assertCountEquals(1)
    }

    /** Regression coverage for issue #597: Reorder accounts is reachable from the account options menu. */
    @Test fun reorderMenuItemInvokesOnReorderAccounts() {
        var reorderClicked = false

        compose.setContent {
            MaterialTheme {
                AccountsScreen(
                    accounts = emptyList(),
                    showMonthlySummary = false,
                    onReorderAccounts = { reorderClicked = true },
                )
            }
        }

        compose.onNodeWithContentDescription("Account display options").performClick()
        compose.onNodeWithText("Reorder accounts").performClick()
        assert(reorderClicked)
    }

    /** Regression coverage for issue #619: collapsing an account section survives leaving and returning to Accounts. */
    @Test fun collapsedSectionSurvivesLeavingAndReturningToAccountsScreen() {
        val checking = Account("Checking", 10_000, "checking", id = "checking")
        var showScreen by mutableStateOf(true)

        compose.setContent {
            MaterialTheme {
                if (showScreen) {
                    AccountsScreen(accounts = listOf(checking), showMonthlySummary = false)
                }
            }
        }

        compose.onNodeWithContentDescription("Collapse On budget").performClick()
        compose.onNodeWithContentDescription("Expand On budget").assertExists()

        // Simulate leaving the Accounts tab (screen torn down) and coming back to it.
        compose.runOnIdle { showScreen = false }
        compose.runOnIdle { showScreen = true }

        compose.onNodeWithContentDescription("Expand On budget").assertExists()
    }

    @Test fun monthlySummaryDisplaysCustomPeriodWhenStartDayIsNotOne() {
        val checking = Account("Checking", 10_000, "checking", id = "checking")

        compose.setContent {
            MaterialTheme {
                AccountsScreen(
                    accounts = listOf(checking),
                    showMonthlySummary = true,
                    monthStart = MonthStartRule(25),
                )
            }
        }

        compose.onNodeWithTag("accountSummaryPeriod").assertExists()
    }

    /** Regression coverage for #1190: In-place edit mode for account reordering, Done button, and Back button. */
    @Test fun reorderMenuItemEntersInPlaceEditModeAndExitsWithDoneOrBack() {
        val checking = Account("Checking", 10_000, "checking", id = "checking")

        compose.setContent {
            MaterialTheme {
                AccountsScreen(
                    accounts = listOf(checking),
                    showMonthlySummary = false,
                )
            }
        }

        compose.onNodeWithText("Accounts").assertExists()
        compose.onNodeWithText("Done").assertDoesNotExist()

        // Enter edit mode from menu
        compose.onNodeWithContentDescription("Account display options").performClick()
        compose.onNodeWithText("Reorder accounts").performClick()

        // Verify edit mode top bar and drag hint
        compose.onNodeWithText("Reorder accounts").assertExists()
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithText("Drag a handle to reorder, or use the up/down arrows.").assertExists()

        // Tap Done to exit edit mode
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Accounts").assertExists()
        compose.onNodeWithText("Done").assertDoesNotExist()

        // Enter edit mode again and exit with Back button
        compose.onNodeWithContentDescription("Account display options").performClick()
        compose.onNodeWithText("Reorder accounts").performClick()
        compose.onNodeWithText("Done").assertExists()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Accounts").assertExists()
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    /** Regression coverage for #1190: In-place edit mode exposes Manage account groups button in top bar. */
    @Test fun editModeExposesManageAccountGroupsButtonAndOpensSheet() {
        val checking = Account("Checking", 10_000, "checking", id = "checking", groupId = "grp-1")
        val group = com.azimulkabir.actua.data.budget.model.ActualAccountGroup("grp-1", "Daily Spending", 1.0)

        compose.setContent {
            MaterialTheme {
                AccountsScreen(
                    accounts = listOf(checking),
                    accountGroups = listOf(group),
                    onCreateGroup = {},
                    onRenameGroup = { _, _ -> },
                    onDeleteGroup = {},
                    onMoveGroup = { true },
                    initialEditMode = true,
                    showMonthlySummary = false,
                )
            }
        }

        compose.onNodeWithContentDescription("Manage account groups").assertExists().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Manage account groups").assertExists()
        compose.onNodeWithContentDescription("New account group").assertExists()
    }

    /** Regression coverage for #1192: Favorites section renders as flat account list without group headers. */
    @Test fun favoritesSectionRendersAsFlatListWithoutGroupHeaders() {
        val acc1 = Account("Checking", 10_000, "checking", id = "checking", groupId = "grp-1", groupName = "Daily Spending")
        val acc2 = Account("Savings", 20_000, "savings", id = "savings", groupId = "grp-2", groupName = "Long Term")
        val group1 = com.azimulkabir.actua.data.budget.model.ActualAccountGroup("grp-1", "Daily Spending", 1.0)
        val group2 = com.azimulkabir.actua.data.budget.model.ActualAccountGroup("grp-2", "Long Term", 2.0)

        compose.setContent {
            MaterialTheme {
                AccountsScreen(
                    accounts = listOf(acc1, acc2),
                    accountGroups = listOf(group1, group2),
                    favoriteAccountIds = setOf("checking", "savings"),
                    showMonthlySummary = false,
                )
            }
        }

        compose.onNodeWithText("Favorites").assertExists()
        // Daily Spending and Long Term group headers only exist in On budget section, NOT in Favorites
        compose.onAllNodesWithText("Daily Spending").assertCountEquals(1)
        compose.onAllNodesWithText("Long Term").assertCountEquals(1)
    }

    /** Regression coverage for #1192: Account groups can be collapsed and remember state in preferences. */
    @Test fun accountGroupCanBeCollapsedAndRemembersStateInPreferences() {
        val acc1 = Account("Checking", 10_000, "checking", id = "checking", groupId = "grp-1", groupName = "Daily Spending")
        val acc2 = Account("Cash", 5_000, "cash", id = "cash", groupId = "grp-1", groupName = "Daily Spending")
        val group1 = com.azimulkabir.actua.data.budget.model.ActualAccountGroup("grp-1", "Daily Spending", 1.0)

        compose.setContent {
            MaterialTheme {
                AccountsScreen(
                    accounts = listOf(acc1, acc2),
                    accountGroups = listOf(group1),
                    showMonthlySummary = false,
                )
            }
        }

        // Initially both accounts in group are visible
        compose.onNodeWithText("Checking").assertExists()
        compose.onNodeWithText("Cash").assertExists()
        compose.onNodeWithContentDescription("Collapse Daily Spending").assertExists()

        // Tap the group header to collapse it
        compose.onNodeWithText("Daily Spending").performClick()
        compose.waitForIdle()

        // Accounts in that group are now collapsed/hidden
        compose.onNodeWithText("Checking").assertDoesNotExist()
        compose.onNodeWithText("Cash").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expand Daily Spending").assertExists()

        // Verify state is stored in SharedPreferences
        val prefs = context.getSharedPreferences("budget_ui_preferences", Context.MODE_PRIVATE)
        val activeBudgetId = com.azimulkabir.actua.data.budget.ActiveBudgetStore(context).budgetId ?: "no-budget"
        val saved = prefs.getStringSet("collapsed_account_groups_$activeBudgetId", emptySet())
        org.junit.Assert.assertTrue(saved?.contains("On budget-grp-1") == true)
    }
}
