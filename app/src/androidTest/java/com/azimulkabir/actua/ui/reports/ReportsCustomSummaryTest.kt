package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportSummary
import com.azimulkabir.actua.model.ReportSummaryKind
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Custom report Summary (actua#644): hidden by default, toggled by the device-local preference. */
@RunWith(AndroidJUnit4::class)
class ReportsCustomSummaryTest {
    @get:Rule val compose = createComposeRule()

    private val widget = ReportWidget(
        id = "saved:r", kind = ReportWidgetKind.CUSTOM_REPORT, name = "Groceries (6 months)",
        valueCents = -60000, timeMode = true, graphType = "BarGraph",
        points = listOf(ReportPoint("2026-08", -20000), ReportPoint("2026-09", -40000)),
        summary = ReportSummary(ReportSummaryKind.SPENDING, -60000, -30000, 2, "Monthly"),
    )
    private val snapshot = ReportSnapshot(emptyList(), emptyList(), 0,
        listOf(ReportDashboardPage("p", "Dashboard", listOf(widget))))

    @Test fun summaryIsHiddenUntilToggledAndReportsThePreferenceChange() {
        var shown by mutableStateOf(false)
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            MaterialTheme {
                ReportsScreen(snapshot = snapshot, hideDecimalPlaces = false, showReportSummary = shown,
                    onShowReportSummaryChange = { changes += it; shown = it })
            }
        }

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Show summary"))
        compose.onNodeWithTag("customReportSummary").assertDoesNotExist()
        compose.onNodeWithText("Show summary").performClick()

        compose.onNodeWithTag("customReportSummary").assertExists()
        compose.onNodeWithText("Total spending").assertExists()
        compose.onNodeWithText("Average spending").assertExists()
        compose.onNodeWithText("Per month").assertExists()

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Hide summary"))
        compose.onNodeWithText("Hide summary").performClick()
        compose.onNodeWithTag("customReportSummary").assertDoesNotExist()
        assertEquals(listOf(true, false), changes)
    }
}
