package com.azimulkabir.actua.ui.budget

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.R
import com.azimulkabir.actua.ui.settings.BudgetSelectionItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #1313: the Budget menu's Switch budget row lists local budgets and uses the app's budget switch callback. */
@RunWith(AndroidJUnit4::class)
class SwitchBudgetRowTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun label(id: Int) = context.getString(id)

    private val switched = mutableListOf<String>()
    private val twoBudgets = listOf(BudgetSelectionItem("a", "First budget"), BudgetSelectionItem("b", "Second budget"))

    private fun setBudget(options: List<BudgetSelectionItem>) {
        compose.setContent {
            MaterialTheme {
                BudgetScreen(
                    budgetOptions = options,
                    activeBudgetId = "a",
                    onBudgetChange = { switched += it },
                )
            }
        }
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription(label(R.string.budget_display_options)).performClick()
    }

    @Test fun switchBudgetShowsOnlyWithMoreThanOneBudget() {
        setBudget(listOf(BudgetSelectionItem("a", "First budget")))
        openMenu()
        compose.onNodeWithText(label(R.string.budget_switch_budget)).assertDoesNotExist()
    }

    @Test fun choosingAnotherBudgetSwitchesAndTheActiveOneDoesNothing() {
        setBudget(twoBudgets)
        openMenu()
        compose.onNodeWithText(label(R.string.budget_switch_budget)).performClick()
        compose.onNodeWithText("Second budget").performClick()
        assertEquals(listOf("b"), switched)

        openMenu()
        compose.onNodeWithText(label(R.string.budget_switch_budget)).performClick()
        compose.onNodeWithText("First budget").performClick()
        assertEquals(listOf("b"), switched)
    }

    @Test fun theMenuNoLongerHasABudgetTypeRow() {
        setBudget(twoBudgets)
        openMenu()
        compose.onNodeWithText(context.getString(R.string.settings_budget_type) + ": ", substring = true).assertDoesNotExist()
    }

    @Test fun switchBudgetRowDisplaysActiveBudgetName() {
        setBudget(twoBudgets)
        openMenu()
        compose.onNodeWithText("First budget").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.budget_switch_budget)).assertIsDisplayed()
    }
}
