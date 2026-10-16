package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.ui.geometry.Offset
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DonutMathTest {
    private fun category(i: Int, cents: Long) = ReportCategory(
        "Cat $i", cents, listOf("tx$i"),
        drillDownQuery = DrillDownQuery(categoryIds = setOf("c$i"), startDate = 20260101),
    )

    @Test
    fun twelveOrFewerCategoriesAreUntouched() {
        val list = (1..12).map { category(it, it * 100L) }
        assertSame(list, groupSmallCategories(list))
    }

    @Test
    fun smallestCategoriesFoldIntoOtherKeepingTheTotal() {
        val list = (1..15).map { category(it, it * 100L) }
        val grouped = groupSmallCategories(list)
        assertEquals(12, grouped.size)
        assertEquals(list.sumOf { it.spentCents }, grouped.sumOf { it.spentCents })
        // 15 categories: the 11 largest (5-15) stay in order, the 4 smallest (1-4) fold into Other.
        val other = grouped.last()
        assertEquals("Other", other.name)
        assertEquals((1..4).sumOf { it * 100L }, other.spentCents)
        assertEquals((1..4).map { "tx$it" }, other.transactionIds)
        assertEquals((1..4).map { "c$it" }.toSet(), other.drillDownQuery!!.categoryIds)
        assertEquals((5..15).map { "Cat $it" }, grouped.dropLast(1).map { it.name })
    }

    @Test
    fun otherHasNoMergedQueryWhenQueriesDifferBeyondCategory() {
        val list = (1..13).map { category(it, it * 100L) }.mapIndexed { i, c ->
            if (i == 0) c.copy(drillDownQuery = c.drillDownQuery!!.copy(startDate = 20250101)) else c
        }
        val other = groupSmallCategories(list).last()
        assertNull(other.drillDownQuery)
        assertEquals(2, other.transactionIds.size)
    }

    @Test
    fun arcsCoverTheCircleInProportion() {
        val arcs = computeDonutArcs(listOf(25, 25, 50))
        assertEquals(listOf(90f, 90f, 180f), arcs.map { it.sweepAngleDeg })
        assertEquals(-90f, arcs[0].startAngleDeg, 0.001f)
        assertEquals(0f, arcs[1].startAngleDeg, 0.001f)
        assertEquals(360f, arcs.sumOf { it.sweepAngleDeg.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun tinySlicesGetAMinimumTapArea() {
        val arcs = computeDonutArcs(listOf(1000, 1000, 5))
        val hit = donutHitSlices(arcs, minSweepDeg = 10f)
        assertEquals(2, hit.first().index)
        assertEquals(10f, hit.first().sweepAngleDeg, 0.001f)
        // A tap just outside the 1.8 degree slice but inside its widened area picks the tiny slice.
        val tiny = arcs[2]
        val mid = Math.toRadians((tiny.startAngleDeg + tiny.sweepAngleDeg / 2f + 3f).toDouble())
        val center = Offset(100f, 100f)
        val tap = Offset(center.x + 80f * Math.cos(mid).toFloat(), center.y + 80f * Math.sin(mid).toFloat())
        assertEquals(2, hitTestDonutSlice(tap, center, 60f, 95f, hit, minTouchTargetPx = 0f))
        assertTrue(hit.map { it.sweepAngleDeg }.zipWithNext().all { (a, b) -> a <= b })
    }
}
