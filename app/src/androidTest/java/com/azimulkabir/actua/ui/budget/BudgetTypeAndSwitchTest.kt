package com.azimulkabir.actua.ui.budget

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
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

/** #1313: Budget type and Switch budget rows in the Budget menu, with a confirmation before the type changes. */
@RunWith(AndroidJUnit4::class)
class BudgetTypeAndSwitchTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun label(id: Int) = context.getString(id)

    private var type by mutableStateOf("envelope")
    private val typeChanges = mutableListOf<String>()
    private val switched = mutableListOf<String>()
    private val twoBudgets = listOf(BudgetSelectionItem("a", "First budget"), BudgetSelectionItem("b", "Second budget"))

    private fun setBudget(options: List<BudgetSelectionItem> = emptyList()) {
        compose.setContent {
            MaterialTheme {
                BudgetScreen(
                    budgetType = type,
                    onBudgetTypeChange = {
                        typeChanges += it
                        type = it
                    },
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

    private val envelopeRow get() = context.getString(R.string.budget_type_row, label(R.string.settings_budget_type_envelope))
    private val trackingRow get() = context.getString(R.string.budget_type_row, label(R.string.settings_budget_type_tracking))

    @Test fun theMenuShowsTheCurrentBudgetType() {
        setBudget()
        openMenu()
        compose.onNodeWithText(envelopeRow).assertExists()
    }

    @Test fun cancellingTheConfirmationLeavesTheTypeUnchanged() {
        setBudget()
        openMenu()
        compose.onNodeWithText(envelopeRow).performClick()
        compose.onNodeWithText(label(R.string.budget_type_confirm_title_tracking)).assertExists()
        compose.onNodeWithText(label(R.string.common_cancel)).performClick()

        assertEquals(emptyList<String>(), typeChanges)
        compose.onNodeWithText(label(R.string.budget_type_confirm_title_tracking)).assertDoesNotExist()
        openMenu()
        compose.onNodeWithText(envelopeRow).assertExists()
    }

    @Test fun confirmingChangesTheTypeOnce() {
        setBudget()
        openMenu()
        compose.onNodeWithText(envelopeRow).performClick()
        compose.onNodeWithText(label(R.string.budget_type_confirm_action)).performClick()

        assertEquals(listOf("tracking"), typeChanges)
        openMenu()
        compose.onNodeWithText(trackingRow).assertExists()
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
}
