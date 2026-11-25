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
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.model.SankeyDiagram
import com.azimulkabir.actua.model.SankeyDiagramLink
import com.azimulkabir.actua.model.SankeyDiagramNode
import com.azimulkabir.actua.model.SankeyLayer
import com.azimulkabir.actua.model.SankeyNodeRole
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

    private var requested: DrillDownQuery? = null

    private fun node(name: String, layer: SankeyLayer, column: Int, cents: Long, query: DrillDownQuery?) = SankeyDiagramNode(
        key = name, name = name, layer = layer, role = SankeyNodeRole.NORMAL, column = column, valueCents = cents,
        share = cents / 300_00.0, drillDownQuery = query,
    )

    // An account feeding Rent (200) and Groceries (100): the right-hand column is two thirds Rent, one third Groceries.
    private val diagram = SankeyDiagram(
        nodes = listOf(
            node("Checking", SankeyLayer.ACCOUNT, 0, 300_00, DrillDownQuery(accountIds = setOf("chk"))),
            node("Rent", SankeyLayer.CATEGORY, 1, 200_00, DrillDownQuery(categoryIds = setOf("rent"), categoryName = "Rent")),
            node("Groceries", SankeyLayer.CATEGORY, 1, 100_00, DrillDownQuery(categoryIds = setOf("groc"), categoryName = "Groceries")),
        ),
        links = listOf(SankeyDiagramLink(0, 1, 200_00, true), SankeyDiagramLink(0, 2, 100_00, true)),
        columnLayers = listOf(SankeyLayer.ACCOUNT, SankeyLayer.CATEGORY),
    )

    private val widget = ReportWidget(
        id = "sankey", kind = ReportWidgetKind.SANKEY, name = "Sankey",
        valueCents = 300_00, comparisonCents = 300_00, sankey = diagram,
    )

    private fun setContent() = compose.setContent {
        MaterialTheme {
            ReportsScreen(
                snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("o", "Overview", listOf(widget)))),
                hideDecimalPlaces = false,
                loadDrillDownTransactions = { query ->
                    requested = query
                    if (query.categoryIds == setOf("rent")) listOf(
                        Transaction("rent-1", "2026-05-01", "Landlord", "Rent", "Checking", -150, cleared = true),
                        Transaction("rent-2", "2026-05-02", "Parking", "Rent", "Checking", -50, cleared = true),
                    ) else listOf(Transaction("groc-1", "2026-05-03", "Fresh Market", "Groceries", "Checking", -100, cleared = true))
                },
            )
        }
    }

    // Rent (200 of 300) fills the top two thirds of the last column and Groceries the bottom third.
    private fun tapRightBar(fractionDown: Float) = compose.onNodeWithContentDescription("Sankey diagram", substring = true)
        .performTouchInput { click(Offset(width - 10.dp.toPx(), height * fractionDown)) }

    @Test
    fun tappingANodeOpensItsTransactionsSummingToTheNodesValue() {
        setContent()
        tapRightBar(0.2f)
        compose.waitForIdle()
        assertEquals(setOf("rent"), requested?.categoryIds)
        compose.onNodeWithText("Landlord").assertIsDisplayed()
        compose.onNodeWithText("Parking").assertIsDisplayed()
        // -150 + -50 = the node's 200.00
        compose.onNodeWithText(formatMoneyCents(-200_00, false), substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingASecondNodeOpensThatNodeNotItsNeighbour() {
        setContent()
        tapRightBar(0.85f)
        compose.waitForIdle()
        assertEquals(setOf("groc"), requested?.categoryIds)
        compose.onNodeWithText("Fresh Market").assertIsDisplayed()
    }

    @Test
    fun tappingARibbonOpensItsNodesTransactions() {
        setContent()
        // The ribbon runs from the account bar to the Rent bar; aim at the space between the two columns.
        compose.onNodeWithContentDescription("Sankey diagram", substring = true)
            .performTouchInput { click(Offset(width / 2f, height * 0.1f)) }
        compose.waitForIdle()
        assertEquals(setOf("rent"), requested?.categoryIds)
    }

    @Test
    fun longPressFocusesANodeWithoutOpeningTransactions() {
        setContent()
        compose.onNodeWithContentDescription("Sankey diagram", substring = true)
            .performTouchInput { longClick(Offset(width - 10.dp.toPx(), height * 0.2f)) }
        compose.waitForIdle()
        assertEquals(null, requested)
        compose.onNodeWithText("Categories · Rent · 66.7%").assertIsDisplayed()
    }
}
