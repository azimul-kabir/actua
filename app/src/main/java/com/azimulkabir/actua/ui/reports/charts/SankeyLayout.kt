package com.azimulkabir.actua.ui.reports.charts

import com.azimulkabir.actua.model.SankeyDiagram
import com.azimulkabir.actua.model.SankeyLayer

/** A node bar of a layered Sankey: [top] and [height] in px, for [SankeyDiagram.nodes] entry [index]. */
data class SankeyNodeBox(val index: Int, val column: Int, val top: Float, val height: Float) {
    val bottom: Float get() = top + height
}

/**
 * A flow from one node bar to another: the span it covers on the source bar's right edge
 * ([srcTop] to [srcBottom]) and on the target bar's left edge ([dstTop] to [dstBottom]).
 */
data class SankeyBand(
    val link: Int,
    val source: Int,
    val target: Int,
    val srcTop: Float,
    val srcBottom: Float,
    val dstTop: Float,
    val dstBottom: Float,
)

data class SankeyLayoutResult(val nodes: List<SankeyNodeBox>, val bands: List<SankeyBand>)

/**
 * Lays [diagram] out in a column of [height] px, the way `d3-sankey` does: one scale for every
 * column, chosen so the fullest column (its nodes plus [nodePadding] between them) fits, so a flow
 * is as thick at either end. Columns are centred vertically and keep the diagram's node order;
 * each bar is as tall as the larger of what flows in and out of it. Where several flows leave or
 * enter a bar they are stacked in the vertical order of the bar at the other end, which keeps
 * them from crossing.
 */
fun layoutSankey(diagram: SankeyDiagram, height: Float, nodePadding: Float): SankeyLayoutResult {
    val byColumn = diagram.nodes.indices.groupBy { diagram.nodes[it].column }
    if (byColumn.isEmpty() || height <= 0f) return SankeyLayoutResult(emptyList(), emptyList())

    val scale = byColumn.values.minOf { members ->
        val total = members.sumOf { diagram.nodes[it].valueCents.coerceAtLeast(0L) }.toDouble()
        val room = height - (members.size - 1) * nodePadding
        if (total <= 0.0) Double.MAX_VALUE else (room.coerceAtLeast(0f) / total)
    }.let { if (it == Double.MAX_VALUE) 0.0 else it }

    val boxes = arrayOfNulls<SankeyNodeBox>(diagram.nodes.size)
    byColumn.forEach { (column, members) ->
        val heights = members.map { (diagram.nodes[it].valueCents.coerceAtLeast(0L) * scale).toFloat() }
        val used = heights.sum() + (members.size - 1) * nodePadding
        var top = ((height - used) / 2f).coerceAtLeast(0f)
        members.forEachIndexed { i, node ->
            boxes[node] = SankeyNodeBox(node, column, top, heights[i])
            top += heights[i] + nodePadding
        }
    }
    val nodes = boxes.map { it!! }

    // Stack each bar's outgoing flows by target position and its incoming flows by source position.
    val outgoing = diagram.links.indices.groupBy { diagram.links[it].source }
    val incoming = diagram.links.indices.groupBy { diagram.links[it].target }
    val srcTop = FloatArray(diagram.links.size)
    val dstTop = FloatArray(diagram.links.size)
    val thickness = FloatArray(diagram.links.size) { (diagram.links[it].cents.coerceAtLeast(0L) * scale).toFloat() }
    outgoing.forEach { (node, links) ->
        var y = nodes[node].top
        links.sortedWith(compareBy({ nodes[diagram.links[it].target].top }, { it })).forEach {
            srcTop[it] = y; y += thickness[it]
        }
    }
    incoming.forEach { (node, links) ->
        var y = nodes[node].top
        links.sortedWith(compareBy({ nodes[diagram.links[it].source].top }, { it })).forEach {
            dstTop[it] = y; y += thickness[it]
        }
    }
    val bands = diagram.links.mapIndexed { i, link ->
        SankeyBand(i, link.source, link.target, srcTop[i], srcTop[i] + thickness[i], dstTop[i], dstTop[i] + thickness[i])
    }
    return SankeyLayoutResult(nodes, bands)
}

/** Index of the node bar containing [y], or null. A bar owns its top edge, so adjacent bars never both match. */
fun sankeyNodeAt(y: Float, column: Int, nodes: List<SankeyNodeBox>): Int? =
    nodes.firstOrNull { it.column == column && it.height > 0f && y >= it.top && y < it.bottom }?.index

/** Vertical position on the S-shaped ribbon edge going from [y0] at [x0] to [y1] at [x1], at [x]. */
fun sankeyEdgeAt(x: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
    if (x1 <= x0) return y0
    val t = ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)
    val s = t * t * (3f - 2f * t)
    return y0 + (y1 - y0) * s
}

/**
 * Ribbon bounds for tap detection: each band runs from the right edge of its source bar
 * ([srcRight]) to the left edge of its target bar ([dstLeft]), indexed by link.
 */
fun sankeyBandBounds(bands: List<SankeyBand>, srcRight: (Int) -> Float, dstLeft: (Int) -> Float): List<RibbonBounds> =
    bands.filter { it.srcBottom > it.srcTop }.map { band ->
        val x0 = srcRight(band.source)
        val x1 = dstLeft(band.target)
        RibbonBounds(
            index = band.link, left = x0, right = x1,
            topAtX = { x -> sankeyEdgeAt(x, x0, band.srcTop, x1, band.dstTop) },
            bottomAtX = { x -> sankeyEdgeAt(x, x0, band.srcBottom, x1, band.dstBottom) },
        )
    }

/**
 * The node a tap on [link] belongs to: the end nearer the expense side (a category, group or
 * budget node) when the flow ends there, otherwise its source, so tapping a ribbon opens the
 * transactions behind it.
 */
fun sankeyLinkOwner(diagram: SankeyDiagram, link: Int): Int {
    val l = diagram.links[link]
    val target = diagram.nodes[l.target]
    val expenseSide = target.layer == SankeyLayer.CATEGORY || target.layer == SankeyLayer.CATEGORY_GROUP || target.layer == SankeyLayer.BUDGET
    val owner = if (expenseSide) l.target else l.source
    return if (diagram.nodes[owner].drillDownQuery != null) owner
    else listOf(l.source, l.target).firstOrNull { diagram.nodes[it].drillDownQuery != null } ?: owner
}

/** The links that touch [node], which a focus on it keeps bright. */
fun sankeyLinksOf(diagram: SankeyDiagram, node: Int): Set<Int> =
    diagram.links.indices.filter { diagram.links[it].source == node || diagram.links[it].target == node }.toSet()
