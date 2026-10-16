package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Slice 7 of #1106 (#1113): donut slices and legend rows open their category's transactions. */
@RunWith(AndroidJUnit4::class)
class DonutChartDrillDownTest {
    @get:Rule val compose = createComposeRule()

    private val groceries = DrillDownQuery(categoryIds = setOf("groceries"), categoryName = "Groceries")
    private val rent = DrillDownQuery(categoryIds = setOf("rent"), categoryName = "Rent")

    private fun widget() = ReportWidget(
        id = "donut", kind = ReportWidgetKind.CUSTOM_REPORT, name = "Spending by category",
        graphType = "DonutGraph",
        categories = listOf(
            ReportCategory("Groceries", 300_00L, drillDownQuery = groceries),
            ReportCategory("Rent", 100_00L, drillDownQuery = rent),
        ),
    )

    private fun setContent(onQuery: (DrillDownQuery) -> Unit) = compose.setContent {
        MaterialTheme {
            ReportsScreen(
                snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("o", "Overview", listOf(widget())))),
                hideDecimalPlaces = false,
                loadDrillDownTransactions = { query ->
                    onQuery(query)
                    listOf(Transaction("t", "2026-05-12", "Fresh Market", "Groceries", "Checking", -300, cleared = true))
                },
            )
        }
    }

    @Test
    fun tappingASliceOpensThatCategoriesTransactions() {
        var requested: DrillDownQuery? = null
        setContent { requested = it }

        // Groceries is 75% of the ring and starts at 12 o'clock: its mid-angle is 135 degrees
        // clockwise from the top, i.e. lower right. The ring centreline sits at ~0.395 of the width.
        compose.onNodeWithContentDescription("Donut chart", substring = true).performTouchInput {
            val radius = width * 0.395f
            val angle = Math.toRadians(-90.0 + 135.0)
            click(Offset(center.x + radius * Math.cos(angle).toFloat(), center.y + radius * Math.sin(angle).toFloat()))
        }
        compose.waitForIdle()

        assertEquals(groceries, requested)
        compose.onNodeWithText("Fresh Market").assertIsDisplayed()
    }

    @Test
    fun tappingATinyOrSmallSliceOpensItNotItsNeighbour() {
        var requested: DrillDownQuery? = null
        setContent { requested = it }

        // Rent covers the last 25% (270 to 360 degrees clockwise from the top); aim at its middle.
        compose.onNodeWithContentDescription("Donut chart", substring = true).performTouchInput {
            val radius = width * 0.395f
            val angle = Math.toRadians(-90.0 + 315.0)
            click(Offset(center.x + radius * Math.cos(angle).toFloat(), center.y + radius * Math.sin(angle).toFloat()))
        }
        compose.waitForIdle()
        assertEquals(rent, requested)
    }

    @Test
    fun tappingALegendRowOpensItsTransactions() {
        var requested: DrillDownQuery? = null
        setContent { requested = it }

        compose.onNodeWithText("Rent").performClick()
        compose.waitForIdle()
        assertEquals(rent, requested)
    }

    @Test
    fun selectingASliceShowsItsAmountAndShareInTheCentre() {
        setContent { }
        compose.onNodeWithText("Total").assertIsDisplayed()
        // Before selecting, 75.0% shows once, in the legend.
        val before = compose.onAllNodesWithText("75.0%").fetchSemanticsNodes().size
        compose.onNodeWithText("Groceries").performClick()
        compose.waitForIdle()
        // Selecting adds the share to the centre.
        assertTrue(compose.onAllNodesWithText("75.0%").fetchSemanticsNodes().size > before)
    }
}
