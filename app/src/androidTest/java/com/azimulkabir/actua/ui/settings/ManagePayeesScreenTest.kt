package com.azimulkabir.actua.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.budget.model.ActualManagedPayee
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagePayeesScreenTest {
    @get:Rule val compose = createComposeRule()

    private val payees = listOf(
        ActualManagedPayee("cafe", "Cafe", null, favorite = true, learnCategories = true),
        ActualManagedPayee("grocer", "Grocer", null, favorite = false, learnCategories = false),
        ActualManagedPayee("t-savings", "Savings", "savings", favorite = false, learnCategories = true),
    )

    private fun show(
        onRename: (ActualManagedPayee, String) -> Unit = { _, _ -> },
        onDelete: (List<ActualManagedPayee>) -> Unit = {},
        onMerge: (ActualManagedPayee, List<ActualManagedPayee>) -> Unit = { _, _ -> },
        onSetLearnEnabled: (Boolean) -> Unit = {},
    ) = compose.setContent {
        MaterialTheme {
            ManagePayeesScreen(
                payees = payees, learnCategoriesEnabled = true, onBack = {},
                onSetLearnCategoriesEnabled = onSetLearnEnabled, onRename = onRename,
                onSetFavorite = { _, _ -> }, onSetLearnCategories = { _, _ -> },
                onDelete = onDelete, onMerge = onMerge,
            )
        }
    }

    @Test fun transferPayeesAreListedWithoutActions() {
        show()
        compose.onNodeWithText("Savings").assertExists()
        compose.onNodeWithText("Category learning off").assertExists()
        compose.onAllNodesWithContentDescription("Actions for Savings").assertCountEquals(0)
    }

    @Test fun renameUsesTheTrimmedName() {
        var renamed: Pair<String, String>? = null
        show(onRename = { payee, name -> renamed = payee.id to name })

        compose.onNodeWithContentDescription("Actions for Grocer").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNode(hasSetTextAction() and hasText("Grocer")).performTextReplacement("  Green Grocer ")
        compose.onNodeWithText("Save").performClick()

        assertEquals("grocer" to "Green Grocer", renamed)
    }

    @Test fun longPressSelectsPayeesToMergeIntoTheFirstSelectedByDefault() {
        var merge: Pair<String, List<String>>? = null
        show(onMerge = { target, merged -> merge = target.id to merged.map { it.id } })

        compose.onNodeWithText("Cafe").performTouchInput { longClick() }
        compose.onNodeWithText("Grocer").performClick()
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithText("Merge").performClick()
        compose.onNodeWithText("Merge 2 payees").assertExists()
        compose.onNodeWithText("Merge payees").performClick()

        assertEquals("cafe" to listOf("grocer"), merge)
    }

    @Test fun deleteConfirmsFirst() {
        var deleted: List<String>? = null
        show(onDelete = { targets -> deleted = targets.map { it.id } })

        compose.onNodeWithContentDescription("Actions for Cafe").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete Cafe?").assertExists()
        assertEquals(null, deleted)
        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf("cafe"), deleted)
    }

    @Test fun categoryLearningSwitchReportsTheNewValue() {
        var enabled: Boolean? = null
        show(onSetLearnEnabled = { enabled = it })

        compose.onNodeWithContentDescription("Category learning").performClick()

        assertEquals(false, enabled)
    }
}
