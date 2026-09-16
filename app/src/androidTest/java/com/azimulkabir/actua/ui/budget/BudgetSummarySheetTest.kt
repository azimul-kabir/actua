package com.azimulkabir.actua.ui.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetOverview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the acceptance criteria from #260: the Ready/To Budget summary must be a direct
 * entry point into "Move to a category" / "Hold for next month", with no overflow menu and
 * no popup nested inside another modal.
 */
@RunWith(AndroidJUnit4::class)
class BudgetSummarySheetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun overview(toBudgetCents: Long, bufferedCents: Long = 0L) = BudgetOverview(
        toBudgetCents = toBudgetCents,
        budgetedCents = 0L,
        spentCents = 0L,
        availableCents = 0L,
        bufferedCents = bufferedCents,
    )

    @Test
    fun readyToBudgetOpensBothActionsDirectlyWithoutAnOverflowMenu() {
        compose.setContent {
            MaterialTheme { BudgetScreen(overview = overview(toBudgetCents = 1_200L)) }
        }

        // A single tap on the summary itself is the only step needed to reach both actions.
        compose.onNodeWithText("Ready to Budget").performClick()

        compose.onNodeWithText("Budget Summary").assertExists()
        compose.onNodeWithText("Move to Category").assertExists()
        compose.onNodeWithText("Hold for Next Month").assertExists()
    }

    @Test
    fun actionsExpandInlineInsteadOfStackingAnotherModal() {
        compose.setContent {
            MaterialTheme { BudgetScreen(overview = overview(toBudgetCents = 1_200L)) }
        }

        compose.onNodeWithText("Ready to Budget").performClick()
        compose.onNodeWithText("Hold for Next Month").performClick()
        compose.onNodeWithText("Set aside part or all of To Budget instead of budgeting it now").assertExists()

        // Switching to the other action swaps the inline content in the same sheet, it does not
        // open a second sheet/popup on top of the first.
        compose.onNodeWithText("Move to Category").performClick()
        compose.onNodeWithText("Choose a category to fund from To Budget").assertExists()
        compose.onNodeWithText("Set aside part or all of To Budget instead of budgeting it now").assertDoesNotExist()
        compose.onNodeWithText("Budget Summary").assertExists()
    }

    @Test
    fun holdingAnAmountUpdatesReadyToBudgetAndClosesTheSheet() {
        var heldAmount: Long? = null
        compose.setContent {
            MaterialTheme {
                BudgetScreen(
                    overview = overview(toBudgetCents = 1_200L),
                    onHoldForNextMonth = { heldAmount = it },
                )
            }
        }

        compose.onNodeWithText("Ready to Budget").performClick()
        compose.onNodeWithText("Hold for Next Month").performClick()
        compose.onNodeWithText("5").performClick()
        compose.onNodeWithText("✓").performClick()

        assertTrue(heldAmount != null && heldAmount!! > 0L)
        compose.onNodeWithText("Budget Summary").assertDoesNotExist()
    }

    @Test
    fun resetHoldIsADirectTileWhenAnAmountIsAlreadyHeld() {
        var resetCalled = false
        compose.setContent {
            MaterialTheme {
                BudgetScreen(
                    overview = overview(toBudgetCents = 1_200L, bufferedCents = 500L),
                    onResetNextMonthBuffer = { resetCalled = true },
                )
            }
        }

        compose.onNode(hasText("held for next month", substring = true)).assertExists()

        compose.onNodeWithText("Ready to Budget").performClick()
        compose.onNodeWithText("Reset Hold").performClick()

        assertTrue(resetCalled)
    }
}
