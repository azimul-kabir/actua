package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartToolkitTest {

    // ==========================================
    // 1. Nice Gridlines
    // ==========================================

    @Test
    fun niceGridlinesZeroOnlyData() {
        val gridlines = calculateNiceGridlines(0L, 0L)
        assertEquals(listOf(0L), gridlines)
    }

    @Test
    fun niceGridlinesSingleDataPoint() {
        val positive = calculateNiceGridlines(5000L, 5000L)
        assertEquals(listOf(5000L), positive)

        val negative = calculateNiceGridlines(-5000L, -5000L)
        assertEquals(listOf(-5000L), negative)
    }

    @Test
    fun niceGridlinesPositiveRange() {
        val gridlines = calculateNiceGridlines(0L, 1000L)
        assertTrue("Gridlines count should be in 3..5, but was ${gridlines.size}", gridlines.size in 3..5)
        assertTrue(gridlines.first() <= 0L)
        assertTrue(gridlines.last() >= 1000L)
        // Verify monotonically strictly increasing
        for (i in 0 until gridlines.lastIndex) {
            assertTrue(gridlines[i] < gridlines[i + 1])
        }
    }

    @Test
    fun niceGridlinesNegativeRange() {
        val gridlines = calculateNiceGridlines(-5000L, -1000L)
        assertTrue("Gridlines count should be in 3..5, but was ${gridlines.size}", gridlines.size in 3..5)
        assertTrue(gridlines.first() <= -5000L)
        assertTrue(gridlines.last() >= -1000L)
        for (i in 0 until gridlines.lastIndex) {
            assertTrue(gridlines[i] < gridlines[i + 1])
        }
    }

    @Test
    fun niceGridlinesSpanningZero() {
        val gridlines = calculateNiceGridlines(-200L, 300L)
        assertTrue("Gridlines count should be in 3..5, but was ${gridlines.size}", gridlines.size in 3..5)
        assertTrue(gridlines.first() <= -200L)
        assertTrue(gridlines.last() >= 300L)
        assertTrue("Gridlines spanning zero should include zero", gridlines.contains(0L))
    }

    @Test
    fun niceGridlinesVeryLargeAmounts() {
        val min = 100_000_000_000L // 10^11
        val max = 500_000_000_000L // 5 * 10^11
        val gridlines = calculateNiceGridlines(min, max)
        assertTrue("Gridlines count should be in 3..5, but was ${gridlines.size}", gridlines.size in 3..5)
        assertTrue(gridlines.first() <= min)
        assertTrue(gridlines.last() >= max)
    }

    @Test
    fun niceGridlinesReversedBounds() {
        val regular = calculateNiceGridlines(100L, 500L)
        val reversed = calculateNiceGridlines(500L, 100L)
        assertEquals(regular, reversed)
    }

    // ==========================================
    // 2. Compact Money Formatter
    // ==========================================

    @Test
    fun formatCompactMoneyZero() {
        val formatted = formatCompactMoney(
            cents = 0L,
            currencySymbol = "$",
            symbolAfterAmount = false,
            numberFormat = "1,234.56",
            decimalPlaces = 2,
        )
        assertEquals("$0", formatted)
    }

    @Test
    fun formatCompactMoneyPositiveAndNegative() {
        val positive = formatCompactMoney(
            cents = 50000L, // $500.00
            currencySymbol = "$",
            symbolAfterAmount = false,
            numberFormat = "1,234.56",
            decimalPlaces = 2,
        )
        assertEquals("$500", positive)

        val negative = formatCompactMoney(
            cents = -50000L, // -$500.00
            currencySymbol = "$",
            symbolAfterAmount = false,
            numberFormat = "1,234.56",
            decimalPlaces = 2,
        )
        assertEquals("−$500", negative)
    }

    @Test
    fun formatCompactMoneyRoundingToZeroShowsNoMinus() {
        val formatted = formatCompactMoney(
            cents = -40L, // -$0.40, rounds to $0
            currencySymbol = "$",
            symbolAfterAmount = false,
            numberFormat = "1,234.56",
            decimalPlaces = 2,
        )
        assertEquals("$0", formatted)
    }

    @Test
    fun formatCompactMoneyDigitGrouping() {
        // Indian grouping 1,23,456.78
        val indian = formatCompactMoney(
            cents = 10_000_000L, // 100,000.00
            currencySymbol = "₹",
            symbolAfterAmount = false,
            numberFormat = "1,23,456.78",
            decimalPlaces = 2,
        )
        assertEquals("₹1,00,000", indian)

        // German dot grouping 1.234,56
        val german = formatCompactMoney(
            cents = 100_000L, // 1,000.00
            currencySymbol = "€",
            symbolAfterAmount = false,
            numberFormat = "1.234,56",
            decimalPlaces = 2,
        )
        assertEquals("€1.000", german)
    }

    @Test
    fun formatCompactMoneySymbolPositionAndSpacing() {
        val afterWithSpace = formatCompactMoney(
            cents = 150000L, // 1,500.00
            currencySymbol = "€",
            symbolAfterAmount = true,
            spaceBetweenAmountAndSymbol = true,
            numberFormat = "1 234,56",
            decimalPlaces = 2,
        )
        assertEquals("1 500\u202F€", afterWithSpace)

        val afterWithoutSpace = formatCompactMoney(
            cents = 150000L,
            currencySymbol = "€",
            symbolAfterAmount = true,
            spaceBetweenAmountAndSymbol = false,
            numberFormat = "1,234.56",
            decimalPlaces = 2,
        )
        assertEquals("1,500€", afterWithoutSpace)
    }

    @Test
    fun formatCompactMoneyVeryLargeAmount() {
        val largeCents = 100_000_000_000_00L // 10^11 dollars in cents
        val formatted = formatCompactMoney(
            cents = largeCents,
            currencySymbol = "$",
            symbolAfterAmount = false,
            numberFormat = "1,234.56",
            decimalPlaces = 2,
        )
        assertEquals("$100,000,000,000", formatted)
    }

    @Test
    fun formatCompactMoneyZeroDecimalPlaces() {
        val formatted = formatCompactMoney(
            cents = 500L, // JPY: 500 is already whole units
            currencySymbol = "¥",
            symbolAfterAmount = false,
            numberFormat = "1,234.56",
            decimalPlaces = 0,
        )
        assertEquals("¥500", formatted)
    }

    // ==========================================
    // 3. Month Label Thinning
    // ==========================================

    @Test
    fun thinMonthLabelsEdgeCases() {
        assertEquals(emptyList<Int>(), thinMonthLabels(emptyList(), 500f, 40f))
        assertEquals(listOf(0), thinMonthLabels(listOf("Jan"), 500f, 40f))
        assertEquals(emptyList<Int>(), thinMonthLabels(listOf("Jan", "Feb"), 0f, 40f))
        assertEquals(emptyList<Int>(), thinMonthLabels(listOf("Jan", "Feb"), -100f, 40f))
    }

    @Test
    fun thinMonthLabelsWideWidthFitsAll() {
        val months = (1..12).map { "Month $it" }
        val result = thinMonthLabels(months, availableWidthPx = 1000f, labelWidthPx = 40f, minSpacingPx = 8f)
        assertEquals((0..11).toList(), result)
    }

    @Test
    fun thinMonthLabelsThinsWhenTight() {
        val months = (1..12).map { "M$it" }
        val result = thinMonthLabels(months, availableWidthPx = 200f, labelWidthPx = 40f, minSpacingPx = 8f)
        assertTrue("Thinned count should be less than 12", result.size < 12)
        assertTrue("At least one label should be shown", result.isNotEmpty())
        assertEquals(0, result.first())
        // Step should be constant
        if (result.size > 1) {
            val step = result[1] - result[0]
            for (i in 0 until result.lastIndex) {
                assertEquals(step, result[i + 1] - result[i])
            }
        }
    }

    @Test
    fun thinMonthLabelsExtremelyNarrowWidth() {
        val months = (1..12).map { "Month $it" }
        val result = thinMonthLabels(months, availableWidthPx = 45f, labelWidthPx = 40f, minSpacingPx = 8f)
        assertEquals(listOf(0), result)
    }

    // ==========================================
    // 4. Fritsch–Carlson Monotone Cubic Smoothing
    // ==========================================

    @Test
    fun monotoneCubicSplineDegenerateCases() {
        assertEquals(emptyList<CubicBezierSegment>(), computeMonotoneCubicSpline(emptyList()))
        assertEquals(emptyList<CubicBezierSegment>(), computeMonotoneCubicSpline(listOf(Offset(0f, 0f))))

        // Two points -> straight segment
        val twoPoints = computeMonotoneCubicSpline(listOf(Offset(0f, 0f), Offset(30f, 30f)))
        assertEquals(1, twoPoints.size)
        assertEquals(Offset(0f, 0f), twoPoints[0].start)
        assertEquals(Offset(30f, 30f), twoPoints[0].end)
    }

    @Test
    fun monotoneCubicSplineZeroOnlyData() {
        val points = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(20f, 0f), Offset(30f, 0f))
        val segments = computeMonotoneCubicSpline(points)
        assertEquals(3, segments.size)
        for (seg in segments) {
            assertEquals(0f, seg.start.y, 0.001f)
            assertEquals(0f, seg.control1.y, 0.001f)
            assertEquals(0f, seg.control2.y, 0.001f)
            assertEquals(0f, seg.end.y, 0.001f)
        }
    }

    @Test
    fun monotoneCubicSplineNeverOvershootsOrUndershoots() {
        // Step pattern: 0 -> 100 -> 100 -> 0
        val points = listOf(
            Offset(0f, 0f),
            Offset(50f, 100f),
            Offset(100f, 100f),
            Offset(150f, 0f),
        )
        val segments = computeMonotoneCubicSpline(points)
        assertEquals(3, segments.size)

        // Sample each segment at 100 points and ensure y stays strictly in [0, 100]
        for (seg in segments) {
            val minY = minOf(seg.start.y, seg.end.y)
            val maxY = maxOf(seg.start.y, seg.end.y)
            for (step in 0..100) {
                val t = step / 100f
                val pt = evaluateCubicBezier(seg, t)
                assertTrue("Point $pt y (${pt.y}) must be >= minY ($minY)", pt.y >= minY - 0.001f)
                assertTrue("Point $pt y (${pt.y}) must be <= maxY ($maxY)", pt.y <= maxY + 0.001f)
            }
        }
    }

    @Test
    fun monotoneCubicSplinePreservesMonotonicity() {
        // Monotonically increasing curve
        val points = listOf(
            Offset(0f, 10f),
            Offset(20f, 30f),
            Offset(40f, 90f),
            Offset(60f, 120f),
        )
        val segments = computeMonotoneCubicSpline(points)
        for (seg in segments) {
            var prevY = seg.start.y
            for (step in 1..50) {
                val t = step / 50f
                val pt = evaluateCubicBezier(seg, t)
                assertTrue("Monotone increasing failed: prev $prevY, curr ${pt.y}", pt.y >= prevY - 0.001f)
                prevY = pt.y
            }
        }
    }

    @Test
    fun monotoneCubicSplineVeryLargeAmounts() {
        val min = 100_000_000_000f
        val max = 500_000_000_000f
        val points = listOf(
            Offset(0f, min),
            Offset(100f, min * 2),
            Offset(200f, max),
        )
        val segments = computeMonotoneCubicSpline(points)
        assertEquals(2, segments.size)
        for (seg in segments) {
            assertTrue(!seg.control1.y.isNaN() && !seg.control1.y.isInfinite())
            assertTrue(!seg.control2.y.isNaN() && !seg.control2.y.isInfinite())
        }
    }

    // ==========================================
    // 5. Hit Testing
    // ==========================================

    @Test
    fun hitTestNodeWithinTouchRadius() {
        val nodes = listOf(Offset(50f, 50f), Offset(150f, 150f))

        // Direct hit
        assertEquals(0, hitTestNode(Offset(50f, 50f), nodes, minTouchRadiusPx = 24f))

        // Hit within 24px radius
        assertEquals(0, hitTestNode(Offset(60f, 60f), nodes, minTouchRadiusPx = 24f))

        // Miss (outside radius)
        assertNull(hitTestNode(Offset(90f, 90f), nodes, minTouchRadiusPx = 24f))

        // Empty list
        assertNull(hitTestNode(Offset(50f, 50f), emptyList(), minTouchRadiusPx = 24f))
    }

    @Test
    fun hitTestBarWith48dpMinimumArea() {
        val bar = BarBounds(index = 0, left = 95f, top = 50f, right = 105f, bottom = 100f) // width = 10, height = 50
        val bars = listOf(bar)

        // Inside bar
        assertEquals(0, hitTestBar(Offset(100f, 75f), bars, minTouchTargetPx = 48f))

        // Outside bar bounds, but within expanded 48px touch width
        assertEquals(0, hitTestBar(Offset(80f, 75f), bars, minTouchTargetPx = 48f))

        // Far outside 48px touch target
        assertNull(hitTestBar(Offset(50f, 75f), bars, minTouchTargetPx = 48f))

        assertNull(hitTestBar(Offset(100f, 75f), emptyList(), minTouchTargetPx = 48f))
    }

    @Test
    fun hitTestSegmentStacked() {
        val seg0 = SegmentBounds(groupIndex = 0, segmentIndex = 0, left = 10f, top = 50f, right = 30f, bottom = 100f)
        val seg1 = SegmentBounds(groupIndex = 0, segmentIndex = 1, left = 10f, top = 0f, right = 30f, bottom = 50f)
        val segments = listOf(seg0, seg1)

        assertEquals(0 to 0, hitTestSegment(Offset(20f, 75f), segments))
        assertEquals(0 to 1, hitTestSegment(Offset(20f, 25f), segments))
        assertNull(hitTestSegment(Offset(100f, 100f), segments))
        assertNull(hitTestSegment(Offset(20f, 25f), emptyList()))
    }

    @Test
    fun hitTestDonutSliceAnglesAndRadius() {
        val center = Offset(100f, 100f)
        val slices = listOf(
            DonutSlice(index = 0, startAngleDeg = 0f, sweepAngleDeg = 90f),
            DonutSlice(index = 1, startAngleDeg = 90f, sweepAngleDeg = 270f),
        )

        // Hit in quadrant 1 (between 0 and 90 degrees) at radius 50
        val hitQ1 = hitTestDonutSlice(
            tap = Offset(140f, 140f),
            center = center,
            innerRadiusPx = 30f,
            outerRadiusPx = 70f,
            slices = slices,
        )
        assertEquals(0, hitQ1)

        // Outside outer radius + 24px
        val hitFar = hitTestDonutSlice(
            tap = Offset(250f, 250f),
            center = center,
            innerRadiusPx = 30f,
            outerRadiusPx = 70f,
            slices = slices,
        )
        assertNull(hitFar)
    }

    @Test
    fun hitTestDayCell() {
        val day = DayCellBounds(dayIndex = 15, left = 40f, top = 40f, right = 60f, bottom = 60f)
        assertEquals(15, hitTestDay(Offset(50f, 50f), listOf(day)))
        assertEquals(15, hitTestDay(Offset(35f, 50f), listOf(day), minTouchTargetPx = 48f))
        assertNull(hitTestDay(Offset(10f, 10f), listOf(day)))
        assertNull(hitTestDay(Offset(50f, 50f), emptyList()))
    }

    @Test
    fun hitTestRibbonFlow() {
        val ribbon = RibbonBounds(
            index = 2,
            left = 10f,
            right = 100f,
            topAtX = { x -> 20f + x * 0.1f },
            bottomAtX = { x -> 40f + x * 0.1f },
        )
        assertEquals(2, hitTestRibbon(Offset(50f, 30f), listOf(ribbon)))
        assertNull(hitTestRibbon(Offset(50f, 150f), listOf(ribbon)))
        assertNull(hitTestRibbon(Offset(50f, 30f), emptyList()))
    }

    // ==========================================
    // 6. Tooltip Placement
    // ==========================================

    @Test
    fun tooltipPlacementStaysOnRightWhenSpaceAvailable() {
        val pos = computeTooltipPosition(
            anchorX = 50f,
            anchorY = 100f,
            tooltipWidth = 80f,
            tooltipHeight = 40f,
            containerWidth = 400f,
            containerHeight = 300f,
            spacing = 8f,
            margin = 8f,
        )
        assertEquals(58f, pos.x, 0.001f) // 50 + 8
        assertEquals(80f, pos.y, 0.001f) // 100 - 40/2
    }

    @Test
    fun tooltipPlacementFlipsToLeftWhenNearRightEdge() {
        val pos = computeTooltipPosition(
            anchorX = 360f,
            anchorY = 100f,
            tooltipWidth = 80f,
            tooltipHeight = 40f,
            containerWidth = 400f,
            containerHeight = 300f,
            spacing = 8f,
            margin = 8f,
        )
        // 360 + 8 + 80 + 8 = 456 > 400, so flips to left
        assertEquals(272f, pos.x, 0.001f) // 360 - 8 - 80
        assertEquals(80f, pos.y, 0.001f)
    }

    @Test
    fun tooltipPlacementClampsVerticalEdges() {
        // Anchor near top
        val topPos = computeTooltipPosition(
            anchorX = 100f,
            anchorY = 5f,
            tooltipWidth = 80f,
            tooltipHeight = 40f,
            containerWidth = 400f,
            containerHeight = 300f,
            spacing = 8f,
            margin = 8f,
        )
        assertEquals(8f, topPos.y, 0.001f)

        // Anchor near bottom
        val bottomPos = computeTooltipPosition(
            anchorX = 100f,
            anchorY = 295f,
            tooltipWidth = 80f,
            tooltipHeight = 40f,
            containerWidth = 400f,
            containerHeight = 300f,
            spacing = 8f,
            margin = 8f,
        )
        assertEquals(252f, bottomPos.y, 0.001f) // 300 - 40 - 8
    }

    // ==========================================
    // 7. Palette & Contrast
    // ==========================================

    @Test
    fun chartPaletteHas12CategoricalColors() {
        assertEquals(12, ChartPalette.LightCategorical.size)
        assertEquals(12, ChartPalette.DarkCategorical.size)
    }

    @Test
    fun chartPaletteLightModeContrastRatio() {
        val white = Color.White
        val lightColors = ChartPalette.LightCategorical + listOf(
            ChartPalette.LightSemantic.income,
            ChartPalette.LightSemantic.expense,
            ChartPalette.LightSemantic.overspending,
            ChartPalette.LightSemantic.balance,
            ChartPalette.LightSemantic.forecast,
        )
        for (color in lightColors) {
            val ratio = calculateContrastRatio(color, white)
            assertTrue("Color $color should have contrast ratio >= 3.0 on white, was $ratio", ratio >= 3.0f)
        }
    }

    @Test
    fun chartPaletteDarkModeContrastRatio() {
        val darkSurface = Color(0xFF141318)
        val darkColors = ChartPalette.DarkCategorical + listOf(
            ChartPalette.DarkSemantic.income,
            ChartPalette.DarkSemantic.expense,
            ChartPalette.DarkSemantic.overspending,
            ChartPalette.DarkSemantic.balance,
            ChartPalette.DarkSemantic.forecast,
        )
        for (color in darkColors) {
            val ratio = calculateContrastRatio(color, darkSurface)
            assertTrue("Color $color should have contrast ratio >= 3.0 on dark surface, was $ratio", ratio >= 3.0f)
        }
    }

    @Test
    fun chartPaletteCategoricalColorModulo() {
        val c0 = ChartPalette.categoricalColor(0)
        val c12 = ChartPalette.categoricalColor(12)
        assertEquals(c0, c12)

        val cNeg = ChartPalette.categoricalColor(-1)
        val c11 = ChartPalette.categoricalColor(11)
        assertEquals(c11, cNeg)
    }
}
