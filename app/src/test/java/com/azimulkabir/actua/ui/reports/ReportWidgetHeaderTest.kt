package com.azimulkabir.actua.ui.reports

import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.ui.reports.charts.ChartTrend
import com.azimulkabir.actua.ui.reports.charts.ChartTrendDirection
import com.azimulkabir.actua.ui.components.formatMoneyCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportWidgetHeaderTest {

    @Test
    fun netWorthWidgetProvidesCurrentValueHeadline() {
        val widget = ReportWidget(
            id = "nw",
            kind = ReportWidgetKind.NET_WORTH,
            name = "Net Worth",
            valueCents = 150_250_00L,
        )
        val header = calculateWidgetHeader(widget, hideDecimals = false)
        assertEquals(formatMoneyCents(150_250_00L, hideDecimalPlaces = false), header.headlineValue)
        assertNull(header.caption)
        assertNull(header.trend)

        val headerNoDecimals = calculateWidgetHeader(widget, hideDecimals = true)
        assertEquals(formatMoneyCents(150_250_00L, hideDecimalPlaces = true), headerNoDecimals.headlineValue)
    }

    @Test
    fun ageOfMoneyWidgetProvidesDaysAndTrendArrow() {
        // Trend up
        val pointsUp = listOf(
            ReportPoint("2026-01", 30L),
            ReportPoint("2026-02", 45L),
        )
        val widgetUp = ReportWidget(
            id = "aom",
            kind = ReportWidgetKind.AGE_OF_MONEY,
            name = "Age of Money",
            valueCents = 45L,
            points = pointsUp,
        )
        val headerUp = calculateWidgetHeader(widgetUp, hideDecimals = false)
        assertEquals("45 days", headerUp.headlineValue)
        assertNull(headerUp.caption)
        assertEquals(ChartTrend(ChartTrendDirection.UP, isPositiveGood = true), headerUp.trend)

        // Trend down
        val pointsDown = listOf(
            ReportPoint("2026-01", 50L),
            ReportPoint("2026-02", 35L),
        )
        val widgetDown = widgetUp.copy(points = pointsDown)
        val headerDown = calculateWidgetHeader(widgetDown, hideDecimals = false)
        assertEquals(ChartTrend(ChartTrendDirection.DOWN, isPositiveGood = true), headerDown.trend)

        // Trend neutral
        val pointsNeutral = listOf(
            ReportPoint("2026-01", 40L),
            ReportPoint("2026-02", 40L),
        )
        val widgetNeutral = widgetUp.copy(points = pointsNeutral)
        val headerNeutral = calculateWidgetHeader(widgetNeutral, hideDecimals = false)
        assertEquals(ChartTrend(ChartTrendDirection.NEUTRAL, isPositiveGood = true), headerNeutral.trend)

        // Single point -> no trend
        val widgetSingle = widgetUp.copy(points = listOf(ReportPoint("2026-01", 30L)))
        val headerSingle = calculateWidgetHeader(widgetSingle, hideDecimals = false)
        assertNull(headerSingle.trend)

        // No valueCents
        val widgetNull = widgetUp.copy(valueCents = null)
        val headerNull = calculateWidgetHeader(widgetNull, hideDecimals = false)
        assertEquals("No age available", headerNull.headlineValue)
    }

    @Test
    fun crossoverWidgetProvidesYearsHeadlineAndCaption() {
        val widget = ReportWidget(
            id = "cross",
            kind = ReportWidgetKind.CROSSOVER,
            name = "Crossover",
            valueCents = 150L, // 150 months = 12.5 years
        )
        val header = calculateWidgetHeader(widget, hideDecimals = false)
        assertEquals("12.5 years", header.headlineValue)
        assertEquals("Years to retire", header.caption)
        assertNull(header.trend)

        val widgetNotReached = widget.copy(valueCents = null)
        val headerNotReached = calculateWidgetHeader(widgetNotReached, hideDecimals = false)
        assertEquals("Not reached", headerNotReached.headlineValue)
        assertEquals("Years to retire", headerNotReached.caption)
    }

    @Test
    fun balanceForecastWidgetProvidesEndingAndLowCaption() {
        val widget = ReportWidget(
            id = "bf",
            kind = ReportWidgetKind.BALANCE_FORECAST,
            name = "Balance Forecast",
            valueCents = 20_000_00L,
            comparisonCents = 5_000_00L,
        )
        val header = calculateWidgetHeader(widget, hideDecimals = false)
        assertEquals(formatMoneyCents(20_000_00L, hideDecimalPlaces = false), header.headlineValue)
        assertEquals("Low: ${formatMoneyCents(5_000_00L, hideDecimalPlaces = false)}", header.caption)
        assertNull(header.trend)

        // When comparisonCents equals valueCents -> no low caption
        val widgetSame = widget.copy(comparisonCents = 20_000_00L)
        val headerSame = calculateWidgetHeader(widgetSame, hideDecimals = false)
        assertEquals(formatMoneyCents(20_000_00L, hideDecimalPlaces = false), headerSame.headlineValue)
        assertNull(headerSame.caption)

        // When comparisonCents is null -> no low caption
        val widgetNullComp = widget.copy(comparisonCents = null)
        val headerNullComp = calculateWidgetHeader(widgetNullComp, hideDecimals = false)
        assertNull(headerNullComp.caption)
    }

    @Test
    fun monteCarloWidgetProvidesSuccessRateAndSubtitleCaption() {
        val widget = ReportWidget(
            id = "mc",
            kind = ReportWidgetKind.MONTE_CARLO,
            name = "Monte Carlo",
            percentage = 87.54,
            subtitle = "to age 65",
        )
        val header = calculateWidgetHeader(widget, hideDecimals = false)
        assertEquals("87.5%", header.headlineValue)
        assertEquals("to age 65", header.caption)
        assertNull(header.trend)
    }

    @Test
    fun otherWidgetsHaveNoHeaderHeadlineOrCaption() {
        val kinds = listOf(
            ReportWidgetKind.SUMMARY,
            ReportWidgetKind.FORMULA,
            ReportWidgetKind.MARKDOWN,
            ReportWidgetKind.CASH_FLOW,
            ReportWidgetKind.INCOME_EXPENSE,
            ReportWidgetKind.SPENDING,
            ReportWidgetKind.CALENDAR,
            ReportWidgetKind.CUSTOM_REPORT,
            ReportWidgetKind.BUDGET_ANALYSIS,
            ReportWidgetKind.SANKEY,
        )
        for (kind in kinds) {
            val widget = ReportWidget(id = "test", kind = kind, name = "Test Widget", valueCents = 1000L)
            val header = calculateWidgetHeader(widget, hideDecimals = false)
            assertNull("Headline should be null for $kind", header.headlineValue)
            assertNull("Caption should be null for $kind", header.caption)
            assertNull("Trend should be null for $kind", header.trend)
        }
    }
}
