package com.azimulkabir.actua.ui.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetOverview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the acceptance criteria from #260: the Ready/To Budget summary must be a direct
 * entry point into "Move to a category" / "Hold for next month", with no overflow menu and
 * no popup nested inside another modal.
 *
 * Bisection in progress: the no-click smoke test passed on CI, so basic composition is fine.
 * Restoring the click-to-open-sheet path next to see if that's where it fails.
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
}
