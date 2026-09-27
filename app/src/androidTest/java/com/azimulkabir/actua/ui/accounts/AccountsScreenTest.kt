package com.azimulkabir.actua.ui.accounts

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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

    /** Regression coverage for issue #597: a reorder entry point sits beside the add-account button. */
    @Test fun reorderIconInvokesOnReorderAccounts() {
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

        compose.onNodeWithContentDescription("Reorder accounts").performClick()
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
}
