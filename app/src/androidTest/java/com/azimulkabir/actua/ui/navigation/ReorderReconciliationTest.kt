package com.azimulkabir.actua.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountGroup
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.ui.accounts.ReorderAccountsScreen
import com.azimulkabir.actua.ui.categories.ManageCategoriesScreen
import com.azimulkabir.actua.ui.categories.ReorderGroupsScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReorderReconciliationTest {
    @get:Rule val compose = createComposeRule()
    private fun account(id: String, group: String? = null) = ActualAccount(
        id, id, ActualAccountType.CHECKING, false, false, 1.0, 0L, groupId = group,
    )
    private fun categoryGroup(id: String, categories: List<ActualCategory> = emptyList()) =
        ActualCategoryGroup(id, id, false, false, 1.0, categories)

    private fun assertBefore(first: String, second: String) {
        compose.waitForIdle()
        val a = compose.onNodeWithText(first).fetchSemanticsNode().boundsInRoot.top
        val b = compose.onNodeWithText(second).fetchSemanticsNode().boundsInRoot.top
        assertTrue("$first must precede $second", a < b)
    }

    @Test fun rejectedAccountMoveRevertsEvenWhenTheSourceIdsNeverChanged() {
        val saving = mutableStateOf(false)
        val revision = mutableLongStateOf(0)
        val accounts = listOf(account("First"), account("Second"))
        compose.setContent {
            MaterialTheme {
                ReorderAccountsScreen(
                    accounts = accounts, onBack = {}, savingOrder = saving.value, reorderRevision = revision.longValue,
                    onMoveAccount = { saving.value = true; true },
                )
            }
        }
        compose.onNodeWithContentDescription("Move Second account up").performClick()
        assertBefore("Second", "First")
        compose.runOnIdle { saving.value = false; revision.longValue++ }
        assertBefore("First", "Second")
    }

    @Test fun accountGroupMovesAreVisibleWhilePendingAndThenReconcile() {
        val saving = mutableStateOf(false)
        val revision = mutableLongStateOf(0)
        val groups = listOf(ActualAccountGroup("one", "First group", 1.0), ActualAccountGroup("two", "Second group", 2.0))
        compose.setContent {
            MaterialTheme {
                ReorderAccountsScreen(
                    accounts = listOf(account("First", "one"), account("Second", "two")), groups = groups,
                    onBack = {}, onMoveAccount = { true }, onMoveGroup = { saving.value = true; true },
                    savingOrder = saving.value, reorderRevision = revision.longValue,
                )
            }
        }
        compose.onNodeWithContentDescription("Move Second group group up").performClick()
        assertBefore("Second group", "First group")
        compose.runOnIdle { saving.value = false; revision.longValue++ }
        assertBefore("First group", "Second group")
    }

    @Test fun rejectedCategoryGroupMoveReconcilesFromTheConfirmedOrder() {
        val saving = mutableStateOf(false)
        val revision = mutableLongStateOf(0)
        compose.setContent {
            MaterialTheme {
                ReorderGroupsScreen(
                    groups = listOf(categoryGroup("First"), categoryGroup("Second")), onBack = {},
                    onMoveGroup = { saving.value = true; true }, savingOrder = saving.value, reorderRevision = revision.longValue,
                )
            }
        }
        compose.onNodeWithContentDescription("Move Second group up").performClick()
        assertBefore("Second", "First")
        compose.runOnIdle { saving.value = false; revision.longValue++ }
        assertBefore("First", "Second")
    }

    @Test fun rejectedCategoryMoveReconcilesAfterTheMenuCloses() {
        val saving = mutableStateOf(false)
        val revision = mutableLongStateOf(0)
        val categories = listOf(ActualCategory("First", "First", "group", false, false, 1.0), ActualCategory("Second", "Second", "group", false, false, 2.0))
        compose.setContent {
            MaterialTheme {
                ManageCategoriesScreen(
                    groups = listOf(categoryGroup("group", categories)), onBack = {}, onReorderGroupsClick = {},
                    onCreateGroup = {}, onRenameGroup = { _, _ -> }, onSetGroupHidden = { _, _ -> },
                    onCreateCategory = { _, _ -> }, onRenameCategory = { _, _, _ -> }, onSetCategoryHidden = { _, _, _ -> },
                    onDeleteCategory = { _, _, _ -> }, loadCategoryDeletePlan = { _, _ -> null },
                    onMoveCategory = { saving.value = true; true }, savingOrder = saving.value, reorderRevision = revision.longValue,
                )
            }
        }
        compose.onNodeWithContentDescription("Second category options").performClick()
        compose.onNodeWithText("Move up").performClick()
        assertBefore("Second", "First")
        compose.runOnIdle { saving.value = false; revision.longValue++ }
        assertBefore("First", "Second")
    }
}
