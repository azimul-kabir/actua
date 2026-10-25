package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.ReportPoint
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #1305: each month label is centred under its bar group, not on its slot's left edge. */
@RunWith(AndroidJUnit4::class)
class GroupedBarChartLabelsTest {
    @get:Rule val compose = createComposeRule()

    private fun labelCenter(month: String) =
        compose.onNodeWithText(month).fetchSemanticsNode().boundsInRoot.center.x

    /** The bar slot's centre: the income bar's slot ends where the expense bar's slot starts. */
    private fun slotCenter(month: String) =
        compose.onNode(hasContentDescription("$month Income", substring = true)).fetchSemanticsNode().boundsInRoot.right

    private fun assertLabelsCentredUnderTheirGroups(months: List<String>) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    GroupedBarChart(
                        points = months.mapIndexed { i, m -> ReportPoint(m, (i + 1) * 100_000L, (i + 2) * 50_000L) },
                        hideDecimals = true,
                        onDrillDown = {},
                    )
                }
            }
        }
        months.forEach { month ->
            assertEquals("label $month", slotCenter(month), labelCenter(month), 2f)
        }
    }

    @Test fun threeMonthsAreCentredUnderTheirBars() =
        assertLabelsCentredUnderTheirGroups(listOf("2026-08", "2026-09", "2026-10"))

    @Test fun twoMonthsAreCentredUnderTheirBars() =
        assertLabelsCentredUnderTheirGroups(listOf("2026-09", "2026-10"))
}
