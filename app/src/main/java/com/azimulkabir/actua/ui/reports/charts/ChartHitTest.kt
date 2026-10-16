package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Bounds of a chart bar for hit testing.
 */
data class BarBounds(
    val index: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Bounds of a stacked or grouped segment for hit testing.
 */
data class SegmentBounds(
    val groupIndex: Int,
    val segmentIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Donut or pie chart slice definition for hit testing.
 */
data class DonutSlice(
    val index: Int,
    val startAngleDeg: Float,
    val sweepAngleDeg: Float,
)

/**
 * Calendar or heatmap day cell bounds for hit testing.
 */
data class DayCellBounds(
    val dayIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Ribbon bounds (e.g. Sankey flow or stream ribbon) for hit testing.
 */
data class RibbonBounds(
    val index: Int,
    val left: Float,
    val right: Float,
    val topAtX: (Float) -> Float,
    val bottomAtX: (Float) -> Float,
)

/**
 * Maps a tap to the closest node point within a minimum touch radius (default 24px, representing 48dp diameter).
 */
fun hitTestNode(
    tap: Offset,
    nodes: List<Offset>,
    minTouchRadiusPx: Float = 24f,
): Int? {
    if (nodes.isEmpty()) return null
    var closestIndex: Int? = null
    var minDistanceSq = minTouchRadiusPx * minTouchRadiusPx

    nodes.forEachIndexed { index, node ->
        val dx = tap.x - node.x
        val dy = tap.y - node.y
        val distSq = dx * dx + dy * dy
        if (distSq <= minDistanceSq) {
            minDistanceSq = distSq
            closestIndex = index
        }
    }
    return closestIndex
}

/**
 * Maps a tap to a bar with a minimum touch target expansion (default 48px).
 */
fun hitTestBar(
    tap: Offset,
    bars: List<BarBounds>,
    minTouchTargetPx: Float = 48f,
): Int? {
    if (bars.isEmpty()) return null
    var closestIndex: Int? = null
    var minDistanceSq = Float.MAX_VALUE

    bars.forEach { bar ->
        val width = bar.right - bar.left
        val height = bar.bottom - bar.top
        val expandX = max(0f, (minTouchTargetPx - width) / 2f)
        val expandY = max(0f, (minTouchTargetPx - height) / 2f)

        val hitLeft = bar.left - expandX
        val hitRight = bar.right + expandX
        val hitTop = bar.top - expandY
        val hitBottom = bar.bottom + expandY

        if (tap.x in hitLeft..hitRight && tap.y in hitTop..hitBottom) {
            val centerX = (bar.left + bar.right) / 2f
            val centerY = (bar.top + bar.bottom) / 2f
            val dx = tap.x - centerX
            val dy = tap.y - centerY
            val distSq = dx * dx + dy * dy
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestIndex = bar.index
            }
        }
    }
    return closestIndex
}

/**
 * Maps a tap to a segment (groupIndex, segmentIndex) with minimum touch target expansion.
 */
fun hitTestSegment(
    tap: Offset,
    segments: List<SegmentBounds>,
    minTouchTargetPx: Float = 48f,
): Pair<Int, Int>? {
    if (segments.isEmpty()) return null
    var closestPair: Pair<Int, Int>? = null
    var minDistanceSq = Float.MAX_VALUE

    segments.forEach { seg ->
        val width = seg.right - seg.left
        val height = seg.bottom - seg.top
        val expandX = max(0f, (minTouchTargetPx - width) / 2f)
        val expandY = max(0f, (minTouchTargetPx - height) / 2f)

        val hitLeft = seg.left - expandX
        val hitRight = seg.right + expandX
        val hitTop = seg.top - expandY
        val hitBottom = seg.bottom + expandY

        if (tap.x in hitLeft..hitRight && tap.y in hitTop..hitBottom) {
            val centerX = (seg.left + seg.right) / 2f
            val centerY = (seg.top + seg.bottom) / 2f
            val dx = tap.x - centerX
            val dy = tap.y - centerY
            val distSq = dx * dx + dy * dy
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestPair = seg.groupIndex to seg.segmentIndex
            }
        }
    }
    return closestPair
}

/**
 * Maps a tap to a donut or pie slice with radial tolerance.
 */
fun hitTestDonutSlice(
    tap: Offset,
    center: Offset,
    innerRadiusPx: Float,
    outerRadiusPx: Float,
    slices: List<DonutSlice>,
    minTouchTargetPx: Float = 48f,
): Int? {
    if (slices.isEmpty()) return null

    val dx = tap.x - center.x
    val dy = tap.y - center.y
    val dist = sqrt(dx * dx + dy * dy)

    val halfPad = minTouchTargetPx / 2f
    val minR = max(0f, innerRadiusPx - halfPad)
    val maxR = outerRadiusPx + halfPad

    if (dist !in minR..maxR) return null

    val angleDeg = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f

    for (slice in slices) {
        val start = (slice.startAngleDeg % 360f + 360f) % 360f
        val sweep = slice.sweepAngleDeg
        if (sweep >= 360f) return slice.index

        val end = start + sweep
        val angleMatches = if (end > 360f) {
            angleDeg >= start || angleDeg <= (end % 360f)
        } else {
            angleDeg in start..end
        }

        if (angleMatches) {
            return slice.index
        }
    }
    return null
}

/**
 * Maps a tap to a day cell in a calendar report or heatmap.
 */
fun hitTestDay(
    tap: Offset,
    days: List<DayCellBounds>,
    minTouchTargetPx: Float = 48f,
): Int? {
    if (days.isEmpty()) return null
    var closestIndex: Int? = null
    var minDistanceSq = Float.MAX_VALUE

    days.forEach { day ->
        val width = day.right - day.left
        val height = day.bottom - day.top
        val expandX = max(0f, (minTouchTargetPx - width) / 2f)
        val expandY = max(0f, (minTouchTargetPx - height) / 2f)

        val hitLeft = day.left - expandX
        val hitRight = day.right + expandX
        val hitTop = day.top - expandY
        val hitBottom = day.bottom + expandY

        if (tap.x in hitLeft..hitRight && tap.y in hitTop..hitBottom) {
            val centerX = (day.left + day.right) / 2f
            val centerY = (day.top + day.bottom) / 2f
            val dx = tap.x - centerX
            val dy = tap.y - centerY
            val distSq = dx * dx + dy * dy
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestIndex = day.dayIndex
            }
        }
    }
    return closestIndex
}

/**
 * Maps a tap to a flow ribbon in a Sankey diagram.
 */
fun hitTestRibbon(
    tap: Offset,
    ribbons: List<RibbonBounds>,
    minTouchTargetPx: Float = 48f,
): Int? {
    if (ribbons.isEmpty()) return null
    var closestIndex: Int? = null
    var minDistance = Float.MAX_VALUE

    ribbons.forEach { ribbon ->
        if (tap.x in (ribbon.left - minTouchTargetPx / 2f)..(ribbon.right + minTouchTargetPx / 2f)) {
            val clampedX = tap.x.coerceIn(ribbon.left, ribbon.right)
            val topY = ribbon.topAtX(clampedX)
            val bottomY = ribbon.bottomAtX(clampedX)
            val minY = min(topY, bottomY)
            val maxY = max(topY, bottomY)

            val expandY = max(0f, (minTouchTargetPx - (maxY - minY)) / 2f)
            if (tap.y in (minY - expandY)..(maxY + expandY)) {
                val centerY = (minY + maxY) / 2f
                val dist = kotlin.math.abs(tap.y - centerY)
                if (dist < minDistance) {
                    minDistance = dist
                    closestIndex = ribbon.index
                }
            }
        }
    }
    return closestIndex
}
