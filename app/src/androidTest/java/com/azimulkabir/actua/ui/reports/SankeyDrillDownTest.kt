package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.components.formatMoneyCents
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Slice 9 of #1106 (#1115): Sankey nodes and ribbons open their category's transactions. */
@RunWith(AndroidJUnit4::class)
class SankeyDrillDownTest {
    @get:Rule val compose = createComposeRule()

    private var requested: List<String>? = null

    private val widget = ReportWidget(
        id = "sankey", kind = ReportWidgetKind.SANKEY, name = "Sankey",
        valueCents = 500_00, comparisonCents = 300_00,
        categories = listOf(
            ReportCategory("Rent", 200_00, listOf("rent-1", "rent-2")),
            ReportCategory("Groceries", 100_00, listOf("groc-1")),
        ),
    )

    private fun setContent() = compose.setContent {
        MaterialTheme {
            ReportsScreen(
                snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("o", "Overview", listOf(widget)))),
                hideDecimalPlaces = false,
                loadTransactions = { ids ->
                    requested = ids
                    if (ids.size == 2) listOf(
                        Transaction("rent-1", "2026-05-01", "Landlord", "Rent", "Checking", -150, cleared = true),
                        Transaction("rent-2", "2026-05-02", "Parking", "Rent", "Checking", -50, cleared = true),
                    ) else listOf(Transaction("groc-1", "2026-05-03", "Fresh Market", "Groceries", "Checking", -100, cleared = true))
                },
            )
        }
    }

    // Income is 500 of the column, so Rent (200) fills the top 40% of the bar column, Groceries the next 20%.
    private fun tapRightBar(fractionDown: Float) = compose.onNodeWithContentDescription("Sankey diagram", substring = true)
        .performTouchInput { click(Offset(width - 10.dp.toPx(), height * fractionDown)) }

    @Test
    fun tappingANodeOpensItsTransactionsSummingToTheNodesValue() {
        setContent()
        tapRightBar(0.2f)
        compose.waitForIdle()
        assertEquals(listOf("rent-1", "rent-2"), requested)
        compose.onNodeWithText("Landlord").assertIsDisplayed()
        compose.onNodeWithText("Parking").assertIsDisplayed()
        // -150 + -50 = the node's 200.00
        compose.onNodeWithText(formatMoneyCents(-200_00, false), substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingASecondNodeOpensThatNodeNotItsNeighbour() {
        setContent()
        tapRightBar(0.5f)
        compose.waitForIdle()
        assertEquals(listOf("groc-1"), requested)
        compose.onNodeWithText("Fresh Market").assertIsDisplayed()
    }

    @Test
    fun tappingARibbonOpensItsNodesTransactions() {
        setContent()
        // Right-hand ribbons run from the node column inwards to the income bar; aim at Rent's ribbon
        // in the space left of the node column.
        compose.onNodeWithContentDescription("Sankey diagram", substring = true)
            .performTouchInput { click(Offset(width - 110.dp.toPx(), height * 0.1f)) }
        compose.waitForIdle()
        assertEquals(listOf("rent-1", "rent-2"), requested)
    }

    @Test
    fun longPressFocusesANodeWithoutOpeningTransactions() {
        setContent()
        compose.onNodeWithContentDescription("Sankey diagram", substring = true)
            .performTouchInput { longClick(Offset(width - 10.dp.toPx(), height * 0.2f)) }
        compose.waitForIdle()
        assertEquals(null, requested)
        compose.onNodeWithText("Rent · 40.0%").assertIsDisplayed()
    }
}
