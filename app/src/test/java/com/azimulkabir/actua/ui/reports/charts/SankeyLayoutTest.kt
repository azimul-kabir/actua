package com.azimulkabir.actua.ui.reports.charts

import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.SankeyDiagram
import com.azimulkabir.actua.model.SankeyDiagramLink
import com.azimulkabir.actua.model.SankeyDiagramNode
import com.azimulkabir.actua.model.SankeyLayer
import com.azimulkabir.actua.model.SankeyNodeRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SankeyLayoutTest {
    private fun node(name: String, layer: SankeyLayer, column: Int, cents: Long, drill: Boolean = true) = SankeyDiagramNode(
        key = name, name = name, layer = layer, role = SankeyNodeRole.NORMAL, column = column, valueCents = cents, share = 0.0,
        drillDownQuery = if (drill) DrillDownQuery(categoryName = name) else null,
    )

    private fun link(source: Int, target: Int, cents: Long) = SankeyDiagramLink(source, target, cents, colorFromSource = true)

    private fun diagram(nodes: List<SankeyDiagramNode>, links: List<SankeyDiagramLink>) = SankeyDiagram(
        nodes, links, List(nodes.maxOf { it.column } + 1) { SankeyLayer.ACCOUNT },
    )

    // Two income categories flow into one account, which feeds a group that splits into two categories.
    private val sample = diagram(
        listOf(
            node("Salary", SankeyLayer.INCOME_CATEGORY, 0, 300_00),
            node("Bonus", SankeyLayer.INCOME_CATEGORY, 0, 100_00),
            node("Checking", SankeyLayer.ACCOUNT, 1, 400_00),
            node("Bills", SankeyLayer.CATEGORY_GROUP, 2, 300_00),
            node("Rent", SankeyLayer.CATEGORY, 3, 200_00),
            node("Power", SankeyLayer.CATEGORY, 3, 100_00),
        ),
        listOf(link(0, 2, 300_00), link(1, 2, 100_00), link(2, 3, 300_00), link(3, 4, 200_00), link(3, 5, 100_00)),
    )

    @Test fun oneScaleServesEveryColumnAndTheFullestColumnFillsTheHeight() {
        val layout = layoutSankey(sample, height = 440f, nodePadding = 20f)
        // Column 0 is the fullest: 400 of value plus one gap of 20 in 440 gives 1.05 px per 1.00.
        val salary = layout.nodes[0]; val bonus = layout.nodes[1]
        assertEquals(315f, salary.height, 0.001f)
        assertEquals(105f, bonus.height, 0.001f)
        assertEquals(0f, salary.top, 0.001f)
        assertEquals(440f, bonus.bottom, 0.001f)
        // The same px per cent everywhere: the account (400) is as tall as both sources together.
        assertEquals(420f, layout.nodes[2].height, 0.001f)
        // A column shorter than the others is centred.
        assertEquals((440f - 315f) / 2f, layout.nodes[3].top, 0.001f)
    }

    @Test fun barsInAColumnKeepTheDiagramOrderWithoutOverlapping() {
        val layout = layoutSankey(sample, height = 300f, nodePadding = 10f)
        val column = layout.nodes.filter { it.column == 3 }
        assertEquals(listOf(4, 5), column.map { it.index })
        assertTrue(column[0].bottom + 10f <= column[1].top + 0.001f)
        layout.nodes.forEach { assertTrue(it.top >= -0.001f && it.bottom <= 300.001f) }
    }

    @Test fun bandsStayInsideTheirBarsAndAreAsThickAsTheirFlow() {
        val layout = layoutSankey(sample, height = 440f, nodePadding = 20f)
        layout.bands.forEach { band ->
            val src = layout.nodes[band.source]; val dst = layout.nodes[band.target]
            assertTrue(band.srcTop >= src.top - 0.001f && band.srcBottom <= src.bottom + 0.001f)
            assertTrue(band.dstTop >= dst.top - 0.001f && band.dstBottom <= dst.bottom + 0.001f)
            assertEquals(band.srcBottom - band.srcTop, band.dstBottom - band.dstTop, 0.001f)
        }
        val salary = layout.bands[0]
        assertEquals(315f, salary.srcBottom - salary.srcTop, 0.001f)
        // The account's two incoming bands sit one under the other in the order of their sources.
        assertEquals(layout.bands[0].dstBottom, layout.bands[1].dstTop, 0.001f)
    }

    @Test fun flowsLeavingABarStackInTheOrderOfTheirTargetsSoTheyDoNotCross() {
        val d = diagram(
            listOf(
                node("Account", SankeyLayer.ACCOUNT, 0, 100_00),
                node("Upper", SankeyLayer.CATEGORY, 1, 60_00), // the first bar of its column
                node("Lower", SankeyLayer.CATEGORY, 1, 40_00),
            ),
            // Listed lower-first: the stacking follows the targets' positions, not the list.
            listOf(link(0, 2, 40_00), link(0, 1, 60_00)),
        )
        val layout = layoutSankey(d, 200f, 0f)
        val toUpper = layout.bands.first { it.target == 1 }
        val toLower = layout.bands.first { it.target == 2 }
        assertTrue(toUpper.srcTop < toLower.srcTop)
        assertEquals(toUpper.srcBottom, toLower.srcTop, 0.001f)
    }

    @Test fun anEmptyDiagramOrHeightHasNoLayout() {
        val empty = SankeyDiagram(emptyList(), emptyList(), emptyList())
        assertTrue(layoutSankey(empty, 100f, 4f).nodes.isEmpty())
        assertTrue(layoutSankey(sample, 0f, 4f).bands.isEmpty())
    }

    @Test fun nodeAtPicksTheBarUnderTheTapInItsColumn() {
        val layout = layoutSankey(sample, 440f, 20f)
        assertEquals(0, sankeyNodeAt(10f, 0, layout.nodes))
        assertEquals(1, sankeyNodeAt(400f, 0, layout.nodes))
        // The gap between the two bars, and a column with no bar there, are not nodes.
        assertNull(sankeyNodeAt(325f, 0, layout.nodes))
        assertNull(sankeyNodeAt(5f, 3, layout.nodes))
        // A bar owns its top edge.
        assertEquals(1, sankeyNodeAt(layout.nodes[1].top, 0, layout.nodes))
    }

    @Test fun edgeRunsSmoothlyFromOneEndToTheOther() {
        assertEquals(10f, sankeyEdgeAt(0f, 0f, 10f, 100f, 50f), 0.001f)
        assertEquals(50f, sankeyEdgeAt(100f, 0f, 10f, 100f, 50f), 0.001f)
        assertEquals(30f, sankeyEdgeAt(50f, 0f, 10f, 100f, 50f), 0.001f)
        assertEquals(10f, sankeyEdgeAt(5f, 100f, 10f, 100f, 50f), 0.001f)
    }

    @Test fun bandBoundsFollowTheBandBetweenItsBars() {
        val layout = layoutSankey(sample, 440f, 20f)
        val bounds = sankeyBandBounds(layout.bands, srcRight = { 100f * (layout.nodes[it].column + 1) }, dstLeft = { 100f * layout.nodes[it].column + 60f })
        val first = bounds.first { it.index == 0 }
        assertEquals(100f, first.left, 0.001f)
        assertEquals(160f, first.right, 0.001f)
        assertEquals(layout.bands[0].srcTop, first.topAtX(100f), 0.001f)
        assertEquals(layout.bands[0].dstBottom, first.bottomAtX(160f), 0.001f)
    }

    @Test fun aTappedRibbonBelongsToTheExpenseSideNodeOrItsSource() {
        // Salary > Checking opens Salary; Bills > Rent opens Rent; Checking > Bills opens Bills.
        assertEquals(0, sankeyLinkOwner(sample, 0))
        assertEquals(4, sankeyLinkOwner(sample, 3))
        assertEquals(3, sankeyLinkOwner(sample, 2))
        // With no drill-down on the owner, the other end is used.
        val noDrill = sample.copy(nodes = sample.nodes.mapIndexed { i, n -> if (i == 3) n.copy(drillDownQuery = null) else n })
        assertEquals(2, sankeyLinkOwner(noDrill, 2))
    }

    @Test fun focusKeepsTheLinksTouchingANode() {
        assertEquals(setOf(0), sankeyLinksOf(sample, 0))
        assertEquals(setOf(0, 1, 2), sankeyLinksOf(sample, 2))
    }
}
