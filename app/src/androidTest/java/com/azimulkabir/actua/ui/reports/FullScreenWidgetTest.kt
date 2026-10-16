package com.azimulkabir.actua.ui.reports

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportRange
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.components.formatMoneyCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Slice 11 of #1106 (#1117): full-screen widget view with a view-only range and a data table. */
@RunWith(AndroidJUnit4::class)
class FullScreenWidgetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val may = DrillDownQuery(endDate = 20260531)
    private val april = DrillDownQuery(endDate = 20260430)

    private val saved = ReportWidget(
        id = "net-worth", kind = ReportWidgetKind.NET_WORTH, name = "Net Worth", valueCents = 2_000_00,
        points = listOf(
            ReportPoint("2026-04-30", 1_000_00, drillDownQuery = april),
            ReportPoint("2026-05-31", 2_000_00, drillDownQuery = may),
        ),
    )
    // The wider range the viewer asks for: one extra, earlier month.
    private val twelveMonths = saved.copy(
        points = listOf(ReportPoint("2026-03-31", 500_00, drillDownQuery = DrillDownQuery(endDate = 20260331))) + saved.points,
    )

    private var requestedRange: Pair<String, ReportRange>? = null
    private var requestedQuery: DrillDownQuery? = null

    private fun setContent() = compose.setContent {
        MaterialTheme {
            ReportsScreen(
                snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("o", "Overview", listOf(saved)))),
                hideDecimalPlaces = false,
                loadWidgetForRange = { id, range ->
                    requestedRange = id to range
                    if (range == ReportRange.TWELVE_MONTHS) twelveMonths else saved
                },
                loadDrillDownTransactions = { query ->
                    requestedQuery = query
                    listOf(Transaction("t", "2026-05-12", "Landlord Ltd", "Rent", "Checking", -500, cleared = true))
                },
            )
        }
    }

    @Test
    fun expandChangeRangeOpenTableRowThenBack() {
        setContent()

        compose.onNodeWithContentDescription("Expand chart").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("fullScreenWidget").assertIsDisplayed()

        // A range recomputes the widget for this view only.
        compose.onNodeWithText("12 months").performClick()
        compose.waitForIdle()
        assertEquals("net-worth" to ReportRange.TWELVE_MONTHS, requestedRange)

        compose.onNodeWithText("Data table").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("2026-03-31").assertIsDisplayed()
        compose.onNodeWithText(formatMoneyCents(500_00, false)).assertIsDisplayed()

        compose.onNodeWithText("2026-05-31").performClick()
        compose.waitForIdle()
        assertEquals(may, requestedQuery)
        compose.onNodeWithText("Landlord Ltd").assertIsDisplayed()

        // Back closes the transactions sheet first, then the full-screen view, and the dashboard is still there.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("fullScreenWidget").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("fullScreenWidget").assertDoesNotExist()
        compose.onNodeWithText("Net Worth").assertIsDisplayed()
    }

    @Test
    fun theChartTabStillOffersTheCardsInteractionsAndTheBackButtonCloses() {
        setContent()
        compose.onNodeWithContentDescription("Expand chart").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("2026-05:", substring = true).performClick()
        compose.onNodeWithText("Tap to view transactions").performClick()
        compose.waitForIdle()
        assertEquals(may, requestedQuery)

        // System Back closes the sheet, then the on-screen Back button closes the full-screen view.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("fullScreenWidget").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to dashboard").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("fullScreenWidget").assertDoesNotExist()
    }

    @Test
    fun rangeIsViewOnlySoTheSavedWidgetIsUnchangedAfterClosing() {
        setContent()
        compose.onNodeWithContentDescription("Expand chart").performClick()
        compose.onNodeWithText("3 months").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back to dashboard").performClick()
        compose.waitForIdle()
        // Reopening starts from the saved widget again ("As saved"), not the last viewer range.
        compose.onNodeWithContentDescription("Expand chart").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Data table").performClick()
        compose.onNodeWithText("2026-03-31").assertDoesNotExist()
        assertNull(requestedQuery)
    }
}
