package com.azimulkabir.actua.ui.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.BudgetDisplayOptions
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #1142: in the Budget table view's overview card, the To budget chip (a button, so Material gives it
 * a 48dp minimum height) pushed its label above Budgeted, Spent and Balance.
 */
@RunWith(AndroidJUnit4::class)
class BudgetOverviewAlignmentTest {
    @get:Rule val compose = createComposeRule()

    private fun overviewLabelTop(label: String): Float =
        compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().minOf { it.boundsInRoot.top }

    /** Column labels come from resources, so assertions follow the app language. */
    private fun label(resId: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    @Test fun overviewLabelsShareOneLineInTheColumnsLayout() {
        compose.setContent {
            MaterialTheme {
                // requiredWidth: a small device's screen must not clamp this below the columns layout.
                Box(Modifier.requiredWidth(600.dp)) {
                    BudgetScreen(
                        groups = listOf(
                            BudgetGroup(name = "Bills", categories = listOf(BudgetCategory(name = "Rent", assigned = 1_100, spent = 1_100))),
                        ),
                        overview = BudgetOverview(
                            toBudgetCents = 0L, budgetedCents = 290_000L, spentCents = -165_456L, availableCents = 1_366_500L,
                        ),
                        budgetView = "Table",
                        displayOptions = BudgetDisplayOptions(showSpent = true, showGroupTotals = true),
                    )
                }
            }
        }
        compose.waitForIdle()
        val tops = listOf(
            label(R.string.budget_to_budget),
            label(R.string.budget_budgeted),
            label(R.string.budget_spent),
            label(R.string.budget_balance),
        ).map(::overviewLabelTop)
        tops.forEach { assertEquals("labels are on different lines: $tops", tops.first(), it, 1f) }
    }
}
