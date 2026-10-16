package com.azimulkabir.actua.ui.reports.charts


/** A node bar in one Sankey column: [top] and [height] in px, in the order of the node values. */
data class SankeyBar(val index: Int, val top: Float, val height: Float) {
    val bottom: Float get() = top + height
}

/**
 * A ribbon from a node bar to the hub bar: the source span on the node and the destination span
 * on the hub.
 */
data class SankeyRibbonSpan(
    val index: Int,
    val srcTop: Float,
    val srcBottom: Float,
    val dstTop: Float,
    val dstBottom: Float,
)

/**
 * Stacks one bar per value down a column of [height] px where [total] fills the whole column, so
 * the bar heights add up to `sum(values) / total * height`. Non-positive values get zero height.
 */
fun layoutSankeyBars(values: List<Long>, total: Long, height: Float): List<SankeyBar> {
    val safeTotal = total.coerceAtLeast(1L).toFloat()
    var top = 0f
    return values.mapIndexed { i, v ->
        val h = if (v > 0L) v / safeTotal * height else 0f
        SankeyBar(i, top, h).also { top += h }
    }
}

/**
 * Ribbons from [bars] onto a hub bar of [hubHeight] px starting at the top. When the bars are
 * taller in total than the hub they are scaled down to fit, so a ribbon always ends inside the hub
 * and starts inside its own bar.
 */
fun layoutSankeyRibbons(bars: List<SankeyBar>, hubHeight: Float): List<SankeyRibbonSpan> {
    val sum = bars.sumOf { it.height.toDouble() }.toFloat()
    val scale = if (sum > hubHeight && sum > 0f) hubHeight / sum else 1f
    var dstTop = 0f
    return bars.map { bar ->
        val h = bar.height * scale
        SankeyRibbonSpan(bar.index, bar.top, bar.bottom, dstTop, dstTop + h).also { dstTop += h }
    }
}

/** Index of the bar containing [y], or null. A bar owns its top edge, so adjacent bars never both match. */
fun sankeyBarAt(y: Float, bars: List<SankeyBar>): Int? =
    bars.firstOrNull { it.height > 0f && y >= it.top && y < it.bottom }?.index

/** Vertical position on the S-shaped ribbon edge going from [y0] at [x0] to [y1] at [x1], at [x]. */
fun sankeyEdgeAt(x: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
    if (x1 <= x0) return y0
    val t = ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)
    val s = t * t * (3f - 2f * t)
    return y0 + (y1 - y0) * s
}

/** Ribbon bounds for tap detection between the node column edge [x0] and the hub edge [x1]. */
fun sankeyRibbonBounds(spans: List<SankeyRibbonSpan>, x0: Float, x1: Float, nodeOnLeft: Boolean): List<RibbonBounds> {
    val left = minOf(x0, x1)
    val right = maxOf(x0, x1)
    return spans.filter { it.srcBottom > it.srcTop }.map { r ->
        val (startX, endX) = if (nodeOnLeft) left to right else right to left
        fun edge(x: Float, srcY: Float, dstY: Float) = sankeyEdgeAt(x, startX, srcY, endX, dstY)
        RibbonBounds(
            index = r.index, left = left, right = right,
            topAtX = { x -> edge(x, r.srcTop, r.dstTop) },
            bottomAtX = { x -> edge(x, r.srcBottom, r.dstBottom) },
        )
    }
}

