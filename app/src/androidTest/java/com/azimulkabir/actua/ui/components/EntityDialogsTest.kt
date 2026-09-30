package com.azimulkabir.actua.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntityDialogsTest {
    @get:Rule val compose = createComposeRule()

    private data class NewAccount(val name: String, val offBudget: Boolean, val balance: String, val type: String)

    @Test fun newAccountSavesTheChosenTypeAndOffBudget() {
        var saved: NewAccount? = null
        compose.setContent {
            MaterialTheme {
                NewAccountDialog(onDismiss = {}, onSave = { name, offBudget, balance, type ->
                    saved = NewAccount(name, offBudget, balance, type)
                })
            }
        }

        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Cash")
        compose.onNodeWithText("Type").performClick()
        compose.onNodeWithText("Savings").performClick()
        compose.onNodeWithText("Off budget").performClick()
        compose.onNodeWithText("Add").performClick()

        assertEquals(NewAccount("Cash", true, "", "Savings"), saved)
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
