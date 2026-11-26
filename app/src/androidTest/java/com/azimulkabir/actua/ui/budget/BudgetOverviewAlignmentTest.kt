package com.azimulkabir.actua.ui.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.BudgetDisplayOptions
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.ui.components.formatMoneyCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    @Test fun stackedOverviewUsesOneTappableSummaryLine() {
        val overview = BudgetOverview(23_000L, 45_000L, -30_000L, 15_000L)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme {
                    Box(Modifier.requiredWidth(320.dp)) {
                        BudgetScreen(
                            groups = emptyList(),
                            overview = overview,
                            budgetView = "Table",
                            displayOptions = BudgetDisplayOptions(flatTable = true, showSpent = true),
                        )
                    }
                }
            }
        }

        val lead = label(R.string.budget_to_budget)
        val amount = formatMoneyCents(23_000L, false)
        val summary = compose.onNode(hasText(lead) and hasText(amount) and hasClickAction())
        val labelBounds = compose.onNodeWithText(lead, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val amountBounds = compose.onNodeWithText(amount, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("The amount should sit beside its label", labelBounds.right < amountBounds.left)
        assertTrue("The label and amount should overlap vertically", labelBounds.bottom > amountBounds.top)
        val target = summary.getUnclippedBoundsInRoot()
        assertTrue("The summary should keep a 48 dp touch target", target.bottom - target.top >= 48.dp)

        summary.performClick()
        compose.onNodeWithText(label(R.string.budget_summary)).assertIsDisplayed()
    }
}
