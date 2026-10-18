package com.azimulkabir.actua.ui.accounts

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountGroup
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression coverage for #1164: tapping the folder icon in Reorder Accounts opens Manage Account Groups sheet without crashing. */
@RunWith(AndroidJUnit4::class)
class ReorderAccountsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tappingFolderButtonOpensManageAccountGroupsSheetWithoutCrashing() {
        val account = ActualAccount(
            id = "acc-1",
            name = "Checking",
            type = ActualAccountType.CHECKING,
            offBudget = false,
            closed = false,
            sortOrder = 1.0,
            balanceCents = 100_00L,
            groupId = "grp-1",
        )
        val group = ActualAccountGroup(
            id = "grp-1",
            name = "Daily Spending",
            sortOrder = 1.0,
        )

        compose.setContent {
            MaterialTheme {
                ReorderAccountsScreen(
                    accounts = listOf(account),
                    groups = listOf(group),
                    onBack = {},
                    onMoveAccount = { true },
                    onMoveGroup = { true },
                    onCreateGroup = {},
                    onRenameGroup = { _, _ -> },
                    onDeleteGroup = {},
                )
            }
        }

        // Tap the folder icon in the top right header
        compose.onNodeWithContentDescription("Manage account groups")
            .assertIsDisplayed()
            .performClick()

        compose.waitForIdle()

        // Verify the manage account groups sheet is displayed without layout measurement crashing
        compose.onNodeWithText("Manage account groups").assertIsDisplayed()
        compose.onNodeWithContentDescription("New account group").assertIsDisplayed()
        compose.onAllNodesWithText("Daily Spending").assertCountEquals(2)
    }
}
