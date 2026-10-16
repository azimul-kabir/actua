package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Cubic Bezier curve segment defined by start point, two control points, and end point.
 */
data class CubicBezierSegment(
    val start: Offset,
    val control1: Offset,
    val control2: Offset,
    val end: Offset,
)

/**
 * Computes a smooth cubic spline using the Fritsch–Carlson monotone cubic interpolation algorithm.
 * Guarantees that the resulting curve never overshoots or undershoots the real data points.
 */
fun computeMonotoneCubicSpline(points: List<Offset>): List<CubicBezierSegment> {
    val n = points.size
    if (n < 2) return emptyList()

    if (n == 2) {
        val p0 = points[0]
        val p1 = points[1]
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        return listOf(
            CubicBezierSegment(
                start = p0,
                control1 = Offset(p0.x + dx / 3f, p0.y + dy / 3f),
                control2 = Offset(p0.x + 2f * dx / 3f, p0.y + 2f * dy / 3f),
                end = p1,
            ),
        )
    }

    val deltas = DoubleArray(n - 1)
    val dxs = DoubleArray(n - 1)
    for (k in 0 until n - 1) {
        val dx = (points[k + 1].x - points[k].x).toDouble()
        val dy = (points[k + 1].y - points[k].y).toDouble()
        dxs[k] = dx
        deltas[k] = if (dx == 0.0) 0.0 else dy / dx
    }

    val m = DoubleArray(n)
    m[0] = deltas[0]
    m[n - 1] = deltas[n - 2]
    for (k in 1 until n - 1) {
        m[k] = (deltas[k - 1] + deltas[k]) / 2.0
    }

    for (k in 0 until n - 1) {
        val delta = deltas[k]
        if (delta == 0.0) {
            m[k] = 0.0
            m[k + 1] = 0.0
        } else {
            // A slope against the segment's direction is zeroed (local extremum); the clamp below
            // must then use the zeroed value, not the stale ratio, or it reintroduces an overshoot.
            val alpha = (m[k] / delta).coerceAtLeast(0.0)
            val beta = (m[k + 1] / delta).coerceAtLeast(0.0)
            m[k] = alpha * delta
            m[k + 1] = beta * delta
            val hyp = alpha * alpha + beta * beta
            if (hyp > 9.0) {
                val tau = 3.0 / sqrt(hyp)
                m[k] = tau * alpha * delta
                m[k + 1] = tau * beta * delta
            }
        }
    }

    return (0 until n - 1).map { k ->
        val p0 = points[k]
        val p1 = points[k + 1]
        val dx = dxs[k].toFloat()
        val cp1 = Offset(
            x = p0.x + dx / 3f,
            y = (p0.y + m[k] * (dx / 3.0)).toFloat(),
        )
        val cp2 = Offset(
            x = p1.x - dx / 3f,
            y = (p1.y - m[k + 1] * (dx / 3.0)).toFloat(),
        )
        CubicBezierSegment(start = p0, control1 = cp1, control2 = cp2, end = p1)
    }
}

/**
 * Adds a smooth monotone cubic spline through [points] to this [Path].
 */
fun Path.addMonotoneCubicSpline(points: List<Offset>) {
    if (points.isEmpty()) return
    moveTo(points.first().x, points.first().y)
    val segments = computeMonotoneCubicSpline(points)
    for (seg in segments) {
        cubicTo(seg.control1.x, seg.control1.y, seg.control2.x, seg.control2.y, seg.end.x, seg.end.y)
    }
}

/**
 * Evaluates position on [segment] at parameter [t] in [0, 1].
 */
fun evaluateCubicBezier(segment: CubicBezierSegment, t: Float): Offset {
    val u = 1f - t
    val tt = t * t
    val uu = u * u
    val uuu = uu * u
    val ttt = tt * t

    val x = uuu * segment.start.x + 3f * uu * t * segment.control1.x + 3f * u * tt * segment.control2.x + ttt * segment.end.x
    val y = uuu * segment.start.y + 3f * uu * t * segment.control1.y + 3f * u * tt * segment.control2.y + ttt * segment.end.y
    return Offset(x, y)
}

/**
 * Computes the top-left offset for a tooltip anchored at ([anchorX], [anchorY]),
 * automatically flipping sides to stay completely inside the container.
 */
fun computeTooltipPosition(
    anchorX: Float,
    anchorY: Float,
    tooltipWidth: Float,
    tooltipHeight: Float,
    containerWidth: Float,
    containerHeight: Float,
    spacing: Float = 8f,
    margin: Float = 8f,
): Offset {
    val maxX = max(margin, containerWidth - tooltipWidth - margin)
    val maxY = max(margin, containerHeight - tooltipHeight - margin)

    val rightX = anchorX + spacing
    val leftX = anchorX - spacing - tooltipWidth

    val x = if (rightX + tooltipWidth + margin <= containerWidth) {
        rightX
    } else if (leftX >= margin) {
        leftX
    } else {
        if (anchorX > containerWidth / 2f) leftX.coerceIn(margin, maxX) else rightX.coerceIn(margin, maxX)
    }

    val centeredY = anchorY - tooltipHeight / 2f
    val y = centeredY.coerceIn(margin, maxY)

    return Offset(x, y)
}

/**
 * Pointer input modifier for press-and-drag chart scrubbing with vertical drag passthrough.
 * If the user's initial motion is mostly vertical, the gesture is not consumed and the parent
 * scroll container scrolls normally.
 */
fun Modifier.chartScrubber(
    onScrub: (Offset) -> Unit,
    onScrubEnd: () -> Unit = {},
    onTap: ((Offset) -> Unit)? = null,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var isDragging = false
        val touchSlop = viewConfiguration.touchSlop

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (!change.pressed) {
                if (isDragging) {
                    onScrubEnd()
                } else if (change.changedToUp()) {
                    onTap?.invoke(change.position)
                }
                break
            }

            if (!isDragging) {
                val delta = change.position - down.position
                if (delta.getDistance() > touchSlop) {
                    if (abs(delta.y) > abs(delta.x)) {
                        // Mostly vertical: let scroll container handle it
                        break
                    } else {
                        // Mostly horizontal: claim for scrubbing
                        isDragging = true
                        change.consume()
                        onScrub(change.position)
                    }
                }
            } else {
                change.consume()
                onScrub(change.position)
            }
        }
    }
}
