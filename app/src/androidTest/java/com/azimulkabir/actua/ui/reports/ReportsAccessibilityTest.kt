package com.azimulkabir.actua.ui.reports

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.ui.reports.charts.rememberChartReveal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Slice 12 of #1106 (#1118): TalkBack reaches every chart interaction, and animations obey the system setting. */
@RunWith(AndroidJUnit4::class)
class ReportsAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    private val widget = ReportWidget(
        id = "net-worth", kind = ReportWidgetKind.NET_WORTH, name = "Net Worth", valueCents = 3_000_00,
        points = listOf(
            ReportPoint("2026-04-30", 1_000_00, drillDownQuery = DrillDownQuery(endDate = 20260430)),
            ReportPoint("2026-05-31", 2_000_00, drillDownQuery = DrillDownQuery(endDate = 20260531)),
            ReportPoint("2026-06-30", 3_000_00, drillDownQuery = DrillDownQuery(endDate = 20260630)),
        ),
    )

    private fun setContent() = compose.setContent {
        MaterialTheme {
            ReportsScreen(
                snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("o", "Overview", listOf(widget)))),
                hideDecimalPlaces = false,
            )
        }
    }

    private fun performCustomAction(label: String) {
        val node = compose.onNodeWithContentDescription("Trend chart", substring = true).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
    }

    @Test
    fun chartHasASummaryAndNextAndPreviousPeriodActions() {
        setContent()
        val description = compose.onNodeWithContentDescription("Trend chart", substring = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue(description, description.contains("3 periods, from 2026-04-30"))
        assertTrue(description, description.contains("highest"))

        performCustomAction("Next period")
        compose.onNodeWithText("2026-04").assertIsDisplayed()
        performCustomAction("Next period")
        compose.onNodeWithText("2026-05").assertIsDisplayed()
        performCustomAction("Previous period")
        compose.onNodeWithText("2026-04").assertIsDisplayed()
    }

    @Test
    fun theSelectedPeriodOffersASeeTransactionsAction() {
        setContent()
        performCustomAction("Next period")
        compose.onNodeWithText("Tap to view transactions").assertIsDisplayed()
        // Every point can also be reached directly with its own "View transactions" action.
        val node = compose.onNodeWithContentDescription("2026-05:", substring = true).fetchSemanticsNode()
        assertEquals("View transactions", node.config[SemanticsActions.OnClick].label)
    }

    private fun setAnimatorScale(value: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put global animator_duration_scale $value").close()
        Thread.sleep(300)
    }

    private fun firstRevealValue(): Float {
        var first = -1f
        compose.setContent {
            val reveal = rememberChartReveal("key")
            if (first < 0f) first = reveal.value
        }
        compose.waitForIdle()
        return first
    }

    @Test
    fun noEntranceAnimationWhenTheSystemTurnsAnimationsOff() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val original = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            setAnimatorScale("0")
            assertEquals(1f, firstRevealValue(), 0f)
        } finally {
            setAnimatorScale(original.toString())
        }
    }
}
