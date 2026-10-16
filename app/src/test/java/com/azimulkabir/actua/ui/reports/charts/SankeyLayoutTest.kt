package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SankeyLayoutTest {
    @Test
    fun nodeHeightsAddUpToTheirShareOfTheColumn() {
        val values = listOf(200_00L, 100_00L, 50_00L)
        val bars = layoutSankeyBars(values, total = 500_00L, height = 250f)
        assertEquals(175f, bars.sumOf { it.height.toDouble() }.toFloat(), 0.001f)
        assertEquals(100f, bars[0].height, 0.001f)
        assertEquals(50f, bars[1].height, 0.001f)
        // Bars are stacked with no overlap or gap.
        bars.zipWithNext().forEach { (a, b) -> assertEquals(a.bottom, b.top, 0.001f) }
        // A full column of values fills the whole height.
        assertEquals(250f, layoutSankeyBars(listOf(300L, 200L), 500L, 250f).sumOf { it.height.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun nonPositiveValuesTakeNoSpace() {
        val bars = layoutSankeyBars(listOf(100L, 0L, -50L, 100L), 200L, 100f)
        assertEquals(listOf(50f, 0f, 0f, 50f), bars.map { it.height })
        assertEquals(50f, bars[3].top, 0.001f)
    }

    @Test
    fun ribbonsStayInsideTheirNodeAndTheHub() {
        // More expense than income: the ribbons must be scaled to fit the hub.
        val bars = layoutSankeyBars(listOf(600L, 300L, 300L), total = 1_000L, height = 200f)
        val hub = 100f
        val ribbons = layoutSankeyRibbons(bars, hub)
        ribbons.forEachIndexed { i, r ->
            assertTrue(r.srcTop >= bars[i].top - 0.001f && r.srcBottom <= bars[i].bottom + 0.001f)
            assertTrue("ribbon $i starts above the hub", r.dstTop >= -0.001f)
            assertTrue("ribbon $i ends below the hub: ${r.dstBottom}", r.dstBottom <= hub + 0.001f)
            assertTrue(r.dstBottom >= r.dstTop)
        }
        ribbons.zipWithNext().forEach { (a, b) -> assertEquals(a.dstBottom, b.dstTop, 0.001f) }
        assertEquals(hub, ribbons.last().dstBottom, 0.001f)
    }

    @Test
    fun ribbonsAreNotScaledWhenTheyFitTheHub() {
        val bars = layoutSankeyBars(listOf(100L, 100L), total = 400L, height = 200f)
        val ribbons = layoutSankeyRibbons(bars, hubHeight = 100f)
        assertEquals(bars[0].height, ribbons[0].dstBottom - ribbons[0].dstTop, 0.001f)
        assertEquals(100f, ribbons.last().dstBottom, 0.001f)
    }

    @Test
    fun barAtPicksTheNodeUnderTheTap() {
        val bars = layoutSankeyBars(listOf(100L, 0L, 100L), 200L, 100f)
        assertEquals(0, sankeyBarAt(10f, bars))
        assertEquals(2, sankeyBarAt(60f, bars))
        assertEquals(2, sankeyBarAt(50f, bars))
        assertNull(sankeyBarAt(100f, bars))
    }

    @Test
    fun ribbonTapsResolveToTheRightFlow() {
        val bars = layoutSankeyBars(listOf(100L, 100L), 200L, 200f)
        val spans = layoutSankeyRibbons(bars, 200f)
        val bounds = sankeyRibbonBounds(spans, x0 = 80f, x1 = 160f, nodeOnLeft = true)
        // Ribbons run straight across here, so the middle of each is its own flow.
        assertEquals(0, hitTestRibbon(Offset(120f, 50f), bounds, minTouchTargetPx = 0f))
        assertEquals(1, hitTestRibbon(Offset(120f, 150f), bounds, minTouchTargetPx = 0f))
        assertNull(hitTestRibbon(Offset(20f, 50f), bounds, minTouchTargetPx = 0f))
    }
}
