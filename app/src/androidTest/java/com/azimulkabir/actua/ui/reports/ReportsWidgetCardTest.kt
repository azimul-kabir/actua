package com.azimulkabir.actua.ui.reports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.ui.reports.charts.ChartCard
import com.azimulkabir.actua.ui.reports.charts.ChartTrend
import com.azimulkabir.actua.ui.reports.charts.ChartTrendDirection
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for slice 4 of #1106 (#1110): moving widget titles and headline values
 * into ChartCard, eliminating outer ActuaGroupLabel, and positioning headline values, trend
 * badges, and captions in the card header.
 */
@RunWith(AndroidJUnit4::class)
class ReportsWidgetCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun chartCardRendersTitleWithHeadingSemanticsAndHeadlineWithCaption() {
        var expanded = false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    ChartCard(
                        title = "Investment Growth",
                        headlineValue = "$42,500.00",
                        caption = "Past 12 months",
                        trend = ChartTrend(ChartTrendDirection.UP, isPositiveGood = true),
                        onExpand = { expanded = true },
                    ) {
                        Text("Chart Content Area")
                    }
                }
            }
        }

        // Title has heading semantics
        compose.onNode(hasText("Investment Growth") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()

        // Headline, caption, and trend content description exist
        compose.onNodeWithText("$42,500.00").assertIsDisplayed()
        compose.onNodeWithText("Past 12 months").assertIsDisplayed()
        compose.onNodeWithContentDescription("Trending up").assertIsDisplayed()
        compose.onNodeWithText("Chart Content Area").assertIsDisplayed()

        // Expand button triggers callback
        compose.onNodeWithContentDescription("Expand chart").performClick()
        assertTrue("onExpand should have been triggered", expanded)
    }

    @Test
    fun netWorthWidgetShowsHeadlineInChartCard() {
        val widget = ReportWidget(
            id = "nw",
            kind = ReportWidgetKind.NET_WORTH,
            name = "Net Worth",
            valueCents = 120_000_00L,
            points = listOf(ReportPoint("2026-01", 100_000_00L), ReportPoint("2026-02", 120_000_00L)),
        )
        val snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("p", "Main", listOf(widget))))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(snapshot = snapshot, hideDecimalPlaces = false)
            }
        }

        // Title is a heading
        compose.onNode(hasText("Net Worth") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()

        // Headline value on card
        compose.onNodeWithText("$120,000.00").assertIsDisplayed()
    }

    @Test
    fun ageOfMoneyWidgetShowsDaysHeadlineAndTrendArrow() {
        val widget = ReportWidget(
            id = "aom",
            kind = ReportWidgetKind.AGE_OF_MONEY,
            name = "Age of Money",
            valueCents = 45L,
            points = listOf(ReportPoint("2026-01", 30L), ReportPoint("2026-02", 45L)),
        )
        val snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("p", "Main", listOf(widget))))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(snapshot = snapshot, hideDecimalPlaces = false)
            }
        }

        compose.onNode(hasText("Age of Money") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()
        compose.onNodeWithText("45 days").assertIsDisplayed()
        compose.onNodeWithContentDescription("Trending up").assertIsDisplayed()
    }

    @Test
    fun crossoverWidgetShowsYearsHeadlineAndCaption() {
        val widget = ReportWidget(
            id = "crossover",
            kind = ReportWidgetKind.CROSSOVER,
            name = "Crossover",
            valueCents = 180L, // 15 years
            comparisonCents = 3_000_00L,
        )
        val snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("p", "Main", listOf(widget))))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(snapshot = snapshot, hideDecimalPlaces = false)
            }
        }

        compose.onNode(hasText("Crossover") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()
        compose.onNodeWithText("15.0 years").assertIsDisplayed()
        compose.onNodeWithText("Years to retire").assertIsDisplayed()
    }

    @Test
    fun balanceForecastWidgetShowsEndingBalanceAndLowCaption() {
        val widget = ReportWidget(
            id = "bf",
            kind = ReportWidgetKind.BALANCE_FORECAST,
            name = "Balance Forecast",
            valueCents = 15_000_00L,
            comparisonCents = 4_500_00L,
            points = listOf(ReportPoint("2026-01", 10_000_00L), ReportPoint("2026-02", 15_000_00L)),
        )
        val snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("p", "Main", listOf(widget))))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(snapshot = snapshot, hideDecimalPlaces = false)
            }
        }

        compose.onNode(hasText("Balance Forecast") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()
        compose.onNodeWithText("$15,000.00").assertIsDisplayed()
        compose.onNodeWithText("Low: $4,500.00").assertIsDisplayed()
    }

    @Test
    fun monteCarloWidgetShowsSuccessRateAndSubtitleCaption() {
        val widget = ReportWidget(
            id = "mc",
            kind = ReportWidgetKind.MONTE_CARLO,
            name = "Monte Carlo",
            percentage = 92.4,
            subtitle = "to age 65",
        )
        val snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("p", "Main", listOf(widget))))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(snapshot = snapshot, hideDecimalPlaces = false)
            }
        }

        compose.onNode(hasText("Monte Carlo") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()
        compose.onNodeWithText("92.4%").assertIsDisplayed()
        compose.onNodeWithText("to age 65").assertIsDisplayed()
    }
}
