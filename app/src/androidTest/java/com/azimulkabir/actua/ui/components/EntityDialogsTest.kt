package com.azimulkabir.actua.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.budget.model.ActualAccountGroup
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntityDialogsTest {
    @get:Rule val compose = createComposeRule()

    private data class NewAccount(
        val name: String,
        val offBudget: Boolean,
        val balance: String,
        val type: String,
        val groupId: String? = null,
    )

    @Test fun newAccountSavesTheChosenTypeAndOffBudget() {
        var saved: NewAccount? = null
        compose.setContent {
            MaterialTheme {
                NewAccountDialog(onDismiss = {}, onSave = { name, offBudget, balance, type, groupId ->
                    saved = NewAccount(name, offBudget, balance, type, groupId)
                })
            }
        }

        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Cash")
        compose.onNodeWithText("Type").performClick()
        compose.onNodeWithText("Savings").performClick()
        compose.onNodeWithText("Off budget").performClick()
        compose.onNodeWithText("Add").performClick()

        assertEquals(NewAccount("Cash", true, "", "Savings", null), saved)
    }

    @Test fun newAccountDisplaysGroupNamesAndSavesGroupId() {
        var saved: NewAccount? = null
        val groups = listOf(
            ActualAccountGroup("grp-1722804019000", "Household", 1.0),
            ActualAccountGroup("grp-9999999999999", "Investments", 2.0),
        )
        compose.setContent {
            MaterialTheme {
                NewAccountDialog(
                    accountGroups = groups,
                    onDismiss = {},
                    onSave = { name, offBudget, balance, type, groupId ->
                        saved = NewAccount(name, offBudget, balance, type, groupId)
                    },
                )
            }
        }

        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Checking")
        compose.onNodeWithText("Group").performClick()
        // Menu item should show the group name, NOT the long group ID
        compose.onNodeWithText("Household").assertExists().performClick()
        compose.onNodeWithText("Add").performClick()

        assertEquals(NewAccount("Checking", false, "", "Checking", "grp-1722804019000"), saved)
    }

    @Test fun moveCategoryCannotPickItsCurrentGroup() {
        var moved: String? = null
        compose.setContent {
            MaterialTheme {
                MoveCategoryDialog(
                    categoryName = "Groceries",
                    groups = listOf("Bills", "Everyday"),
                    currentGroup = "Everyday",
                    onDismiss = {},
                    onMove = { moved = it },
                )
            }
        }

        compose.onNodeWithText("Everyday").performClick()
        compose.onNodeWithText("Move").performClick()

        assertEquals("Bills", moved)
    }
}
