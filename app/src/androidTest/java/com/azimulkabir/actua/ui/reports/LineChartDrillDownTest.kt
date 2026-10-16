package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Slice 6 of #1106 (#1112): line charts offer View transactions for the selected month. */
@RunWith(AndroidJUnit4::class)
class LineChartDrillDownTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun netWorthMonthOffersItsTransactions() {
        val april = DrillDownQuery(endDate = 20260430)
        val may = DrillDownQuery(endDate = 20260531)
        val widget = ReportWidget(
            id = "net-worth", kind = ReportWidgetKind.NET_WORTH, name = "Net Worth",
            valueCents = 2_000_00L,
            points = listOf(
                ReportPoint("2026-04-30", 1_000_00L, drillDownQuery = april),
                ReportPoint("2026-05-31", 2_000_00L, drillDownQuery = may),
            ),
        )
        var requested: DrillDownQuery? = null

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("o", "Overview", listOf(widget)))),
                    hideDecimalPlaces = false,
                    loadDrillDownTransactions = { query ->
                        requested = query
                        listOf(Transaction("tx-may", "2026-05-12", "Landlord Ltd", "Rent", "Checking", -500, cleared = true))
                    },
                )
            }
        }

        compose.onNodeWithContentDescription("2026-05-31:", substring = true).performClick()
        compose.onNodeWithText("Tap to view transactions").assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertEquals(may, requested)
        compose.onNodeWithText("Landlord Ltd").assertIsDisplayed()
    }

    @Test
    fun ageOfMoneyTooltipShowsAgeWithoutDrillDown() {
        val widget = ReportWidget(
            id = "age", kind = ReportWidgetKind.AGE_OF_MONEY, name = "Age of Money",
            valueCents = 34,
            points = listOf(ReportPoint("2026-04", 30), ReportPoint("2026-05", 34)),
        )
        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("o", "Overview", listOf(widget)))),
                    hideDecimalPlaces = false,
                )
            }
        }
        compose.onNodeWithContentDescription("2026-05:", substring = true).performClick()
        compose.onNodeWithText("34 days").assertIsDisplayed()
        compose.onNodeWithText("Tap to view transactions").assertDoesNotExist()
    }
}
