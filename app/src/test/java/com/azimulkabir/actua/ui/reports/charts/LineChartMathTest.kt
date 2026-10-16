package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineChartMathTest {
    @Test
    fun smoothingStaysBetweenNeighbouringValues() {
        val values = listOf(100f, 400f, 380f, 380f, 50f, 60f, 900f)
        val points = values.mapIndexed { i, v -> Offset(i * 10f, v) }
        computeMonotoneCubicSpline(points).forEachIndexed { k, seg ->
            val low = minOf(values[k], values[k + 1])
            val high = maxOf(values[k], values[k + 1])
            for (step in 0..20) {
                val y = evaluateCubicBezier(seg, step / 20f).y
                assertTrue("segment $k overshoots at $step: $y", y >= low - 0.01f && y <= high + 0.01f)
            }
        }
    }

    @Test
    fun forecastBoundaryIsTheFirstProjectedPoint() {
        assertEquals(2, forecastBoundaryIndex(listOf(false, false, true, true)))
        assertEquals(0, forecastBoundaryIndex(listOf(true, true)))
        assertNull(forecastBoundaryIndex(listOf(false, false)))
        assertNull(forecastBoundaryIndex(emptyList()))
    }

    @Test
    fun crossoverIsInterpolatedBetweenPoints() {
        // Gap closes from 100 to -100 between index 1 and 2: they meet halfway.
        assertEquals(1.5f, findCrossoverPosition(listOf(0, 50, 250), listOf(200, 150, 150))!!, 0.001f)
        assertEquals(0f, findCrossoverPosition(listOf(300, 10), listOf(200, 200)))
        assertNull(findCrossoverPosition(listOf(1, 2, 3), listOf(10, 10, 10)))
        assertNull(findCrossoverPosition(emptyList(), emptyList()))
    }

    @Test
    fun nearestPointIndexClampsToPlot() {
        assertEquals(0, nearestPointIndex(-20f, 100f, 5))
        assertEquals(4, nearestPointIndex(500f, 100f, 5))
        assertEquals(2, nearestPointIndex(50f, 100f, 5))
        assertEquals(0, nearestPointIndex(50f, 100f, 1))
    }
}
