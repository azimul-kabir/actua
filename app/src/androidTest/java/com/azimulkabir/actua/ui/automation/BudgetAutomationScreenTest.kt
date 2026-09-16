package com.azimulkabir.actua.ui.automation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers issue #265: Budget Automation as a dedicated full-page editor matching upstream
 * Actual Budget's structure - a separate "Automations" list (7 contribution types) and
 * "Options" section (Balance cap / Long-term goal, [BudgetTarget.Type.isOption]).
 */
@RunWith(AndroidJUnit4::class)
class BudgetAutomationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val group = BudgetGroup(name = "Bills", categories = emptyList())

    @Test
    fun listsExistingAutomationsWithTypeAndPriority() {
        val category = BudgetCategory(
            name = "Rent",
            assigned = 0,
            spent = 0,
            target = BudgetTarget(BudgetTarget.Type.FIXED, amountCents = 5_000L, priority = 2),
        )
        compose.setContent {
            MaterialTheme {
                BudgetAutomationScreen(group = group, category = category, month = "2026-09", hideDecimalPlaces = false, onBack = {}, onSave = {})
            }
        }

        compose.onNodeWithText("Budget Automation").assertExists()
        compose.onNodeWithText("Fixed amount").assertExists()
        compose.onNodeWithText("P2").assertExists()
    }

    @Test
    fun addingAnAutomationDefaultsToFixedAmountAndSaves() {
        val category = BudgetCategory(name = "Rent", assigned = 0, spent = 0)
        var saved: List<BudgetTarget>? = null
        compose.setContent {
            MaterialTheme {
                BudgetAutomationScreen(
                    group = group, category = category, month = "2026-09", hideDecimalPlaces = false,
                    onBack = {}, onSave = { saved = it },
                )
            }
        }

        compose.onNodeWithText("+ Add an automation").performClick()
        // Page title reflects the selected type; it also appears once more as the selected type card.
        compose.onAllNodesWithText("Fixed amount").assertCountEquals(2)
        compose.onNode(hasSetTextAction() and hasText("Amount", substring = true)).performTextInput("25")
        compose.onNodeWithText("Add automation").performScrollTo().performClick()

        compose.onNodeWithText("Save automations").performScrollTo().performClick()

        assertEquals(1, saved?.size)
        assertEquals(BudgetTarget.Type.FIXED, saved?.single()?.type)
        assertEquals(2_500L, saved?.single()?.amountCents)
    }

    @Test
    fun refillRequiresABalanceCapOptionBeforeItCanBeAdded() {
        val category = BudgetCategory(name = "Buffer", assigned = 0, spent = 0)
        var saved: List<BudgetTarget>? = null
        compose.setContent {
            MaterialTheme {
                BudgetAutomationScreen(
                    group = group, category = category, month = "2026-09", hideDecimalPlaces = false,
                    onBack = {}, onSave = { saved = it },
                )
            }
        }

        // Add the balance cap Option first.
        compose.onNodeWithText("+ Add balance cap").performClick()
        compose.onNode(hasSetTextAction() and hasText("Amount", substring = true)).performTextInput("50")
        compose.onNodeWithText("Add balance cap").performScrollTo().performClick()

        // Now Refill to cap is available with no warning and no fields of its own.
        compose.onNodeWithText("+ Add an automation").performClick()
        compose.onNodeWithText("Refill to cap").performClick()
        compose.onNodeWithText("Add a balance cap automation to set the refill target.").assertDoesNotExist()
        compose.onNodeWithText("Add automation").performScrollTo().performClick()

        compose.onNodeWithText("Save automations").performScrollTo().performClick()

        assertEquals(2, saved?.size)
        assertTrue(saved?.any { it.type == BudgetTarget.Type.LIMIT && it.amountCents == 5_000L } == true)
        assertTrue(saved?.any { it.type == BudgetTarget.Type.REFILL } == true)
    }

    @Test
    fun editingAnExistingAutomationPreloadsItsValues() {
        val category = BudgetCategory(
            name = "Groceries", assigned = 0, spent = 0,
            target = BudgetTarget(BudgetTarget.Type.PERCENTAGE, percentage = 15, priority = 3),
        )
        compose.setContent {
            MaterialTheme {
                BudgetAutomationScreen(group = group, category = category, month = "2026-09", hideDecimalPlaces = false, onBack = {}, onSave = {})
            }
        }

        compose.onNodeWithText("% of income").performClick()

        compose.onAllNodesWithText("% of income").assertCountEquals(2)
        compose.onNodeWithText("15").assertExists()
        compose.onNodeWithText("3").assertExists()
    }

    @Test
    fun switchingTypeMidEditSwapsConfigurationFields() {
        val category = BudgetCategory(name = "Groceries", assigned = 0, spent = 0)
        compose.setContent {
            MaterialTheme {
                BudgetAutomationScreen(group = group, category = category, month = "2026-09", hideDecimalPlaces = false, onBack = {}, onSave = {})
            }
        }

        compose.onNodeWithText("+ Add an automation").performClick()
        compose.onNodeWithText("Amount").assertExists()

        compose.onNodeWithText("From history").performClick()
        compose.onNodeWithText("Months back").assertExists()
        compose.onAllNodesWithText("Amount").assertCountEquals(0)
    }

    @Test
    fun deletingAnAutomationRequiresConfirmation() {
        val category = BudgetCategory(
            name = "Rent", assigned = 0, spent = 0,
            target = BudgetTarget(BudgetTarget.Type.FIXED, amountCents = 5_000L),
        )
        var saved: List<BudgetTarget>? = null
        compose.setContent {
            MaterialTheme {
                BudgetAutomationScreen(
                    group = group, category = category, month = "2026-09", hideDecimalPlaces = false,
                    onBack = {}, onSave = { saved = it },
                )
            }
        }

        compose.onNodeWithText("Fixed amount").performClick()
        compose.onNodeWithContentDescription("Remove automation").performClick()
        compose.onNodeWithText("Remove this automation?").assertExists()
        compose.onNodeWithText("Remove").performClick()

        compose.onNodeWithText("No automations yet").assertExists()
        compose.onNodeWithText("Save (remove automations)").performScrollTo().performClick()
        assertTrue(saved?.isEmpty() == true)
    }

    @Test
    fun unsupportedAutomationShowsReadOnlyNotice() {
        val category = BudgetCategory(
            name = "Advanced", assigned = 0, spent = 0,
            hasUnsupportedTarget = true, unsupportedAutomationTypes = listOf("condition"),
        )
        compose.setContent {
            MaterialTheme {
                BudgetAutomationScreen(group = group, category = category, month = "2026-09", hideDecimalPlaces = false, onBack = {}, onSave = {})
            }
        }

        compose.onNodeWithText("Automations are read-only").assertExists()
    }
}
