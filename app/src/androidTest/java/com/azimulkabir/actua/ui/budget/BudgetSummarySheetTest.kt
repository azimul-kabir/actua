package com.azimulkabir.actua.ui.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the acceptance criteria from #260 and #266: the Ready/To Budget summary must be a
 * direct entry point into "Move to a category" / "Hold for next month", with no overflow menu
 * and no popup nested inside another modal, and it must open straight into the expanded
 * Move to Category editor without requiring a second tap.
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

    private val oneGroup = listOf(
        BudgetGroup(
            name = "Bills",
            categories = listOf(BudgetCategory(name = "Rent", assigned = 0, spent = 0)),
        ),
    )

    @Test
    fun smokeBudgetScreenComposesAndShowsReadyToBudget() {
        compose.setContent {
            MaterialTheme { BudgetScreen(groups = emptyList(), overview = overview(toBudgetCents = 1_200L)) }
        }

        compose.onNodeWithText("Ready to Budget").assertExists()
    }

    @Test
    fun readyToBudgetOpensBothActionsDirectlyWithoutAnOverflowMenu() {
        compose.setContent {
            MaterialTheme { BudgetScreen(groups = emptyList(), overview = overview(toBudgetCents = 1_200L)) }
        }

        // A single tap on the summary itself is the only step needed to reach both actions.
        compose.onNodeWithText("Ready to Budget").performClick()

        compose.onNodeWithText("Budget Summary").assertExists()
        compose.onNodeWithText("Move to Category").assertExists()
        compose.onNodeWithText("Hold for Next Month").assertExists()
    }

    @Test
    fun readyToBudgetOpensDirectlyIntoMoveToCategoryWithoutASecondTap() {
        compose.setContent {
            MaterialTheme { BudgetScreen(groups = oneGroup, overview = overview(toBudgetCents = 1_200L)) }
        }

        // A single tap on Ready to Budget should land straight in the expanded Move to
        // Category editor: category selector, amount, and calculator all visible already,
        // with no need to tap "Move to Category" a second time.
        compose.onNodeWithText("Ready to Budget").performClick()

        compose.onNodeWithText("Bills · Rent").assertExists()
        compose.onNodeWithText("Choose a category to fund from To Budget").assertExists()
        compose.onNodeWithText("Amount").assertExists()
    }
}
