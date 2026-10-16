package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.ui.components.formatMoneyCents
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val BUDGETED = 0
private const val SPENT = 1
private const val OVERSPENDING = 2

/**
 * Budget Analysis: budgeted (up), spent and overspending (down) bars per month around a zero
 * line, plus the running balance as a line. A press-and-drag tooltip shows all four values, and a
 * tap on a Spent bar opens that month's spending. Point values: `primaryCents` budgeted,
 * `secondaryCents` spent (positive), `overspendingCents`, `tertiaryCents` balance.
 */
@Composable
fun BudgetAnalysisChart(
    points: List<ReportPoint>,
    hideDecimals: Boolean,
    onDrillDown: ((ReportCategory) -> Unit)?,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 180.dp,
) {
    if (points.isEmpty()) {
        Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val n = points.size
    val palette = rememberChartPalette()
    val budgetedColor = palette.semantic.income
    val spentColor = palette.semantic.expense
    val overColor = palette.semantic.overspending
    val balanceColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall

    val reveal = rememberChartReveal(points)
    var selectedIndex by rememberSaveable(n) { mutableStateOf<Int?>(null) }
    val activeIndex = selectedIndex?.takeIf { it in 0 until n }

    val labels = remember(points) { points.map { it.period.take(7) } }
    val (axisMinValue, axisMaxValue) = remember(points) {
        val lo = points.minOf { minOf(-it.secondaryCents, -it.overspendingCents, it.tertiaryCents, 0L) }
        val hi = points.maxOf { maxOf(it.primaryCents, it.tertiaryCents, 0L) }
        lo to hi
    }
    val gridlines = remember(axisMinValue, axisMaxValue) {
        calculateNiceGridlines(axisMinValue, if (axisMaxValue == axisMinValue) axisMinValue + 1 else axisMaxValue)
            .let { if (it.size >= 2) it else listOf(axisMinValue, axisMinValue + 1) }
    }
    val axisMin = gridlines.first()
    val axisMax = gridlines.last()
    val textMeasurer = rememberTextMeasurer()
    val axis = rememberChartValueAxis(gridlines, labelStyle, textMeasurer) { formatCompactMoney(it, hideDecimals = true) }

    fun drillable(p: ReportPoint) = p.drillDownQuery != null || p.transactionIds.isNotEmpty()
    fun openSpending(i: Int) {
        val p = points[i]
        selectedIndex = i
        if (onDrillDown != null && drillable(p)) {
            onDrillDown(
                ReportCategory(
                    "${p.period.take(7)} · Spent", p.secondaryCents, p.transactionIds,
                    expenseCents = p.secondaryCents, drillDownQuery = p.drillDownQuery,
                ),
            )
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        activeIndex?.let { i ->
            val p = points[i]
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.then(
                    if (onDrillDown != null && drillable(p)) Modifier.clickable(onClickLabel = "View spending") { openSpending(i) } else Modifier,
                ),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(labels[i], style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Budgeted ${formatMoneyCents(p.primaryCents, hideDecimals)} · Spent ${formatMoneyCents(p.secondaryCents, hideDecimals)}",
                        style = MaterialTheme.typography.bodySmall, color = textColor,
                    )
                    Text(
                        "Overspending ${formatMoneyCents(p.overspendingCents, hideDecimals)} · Balance ${formatMoneyCents(p.tertiaryCents, hideDecimals)}",
                        style = MaterialTheme.typography.bodySmall, color = textColor,
                    )
                    if (onDrillDown != null && drillable(p)) {
                        Text("Tap to view spending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().height(chartHeight * LocalChartScale.current)) {
            val density = LocalDensity.current
            val totalW = constraints.maxWidth.toFloat()
            val totalH = constraints.maxHeight.toFloat()
            val pad = with(density) { 8.dp.toPx() }
            val plotW = (totalW - axis.maxLabelWidth - pad).coerceAtLeast(1f)
            val topInset = axis.maxLabelHeight / 2f
            val plotH = (totalH - axis.maxLabelHeight - pad - topInset).coerceAtLeast(1f)
            val currentPlotW by rememberUpdatedState(plotW)
            val slot = plotW / n
            val barW = min(slot * 0.8f / 3f, with(density) { 14.dp.toPx() }).coerceAtLeast(2f)
            val span = (axisMax - axisMin).coerceAtLeast(1L).toFloat()
            fun y(v: Long) = plotH - (v - axisMin) / span * plotH
            val zeroY = y(0)
            val balanceStroke = remember(density) { Stroke(with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round) }

            val bars = remember(points, plotW, plotH, axisMin, axisMax, barW) {
                buildList {
                    points.forEachIndexed { i, p ->
                        val left = slot * i + (slot - barW * 3f) / 2f
                        add(SegmentBounds(i, BUDGETED, left, y(p.primaryCents), left + barW, zeroY))
                        add(SegmentBounds(i, SPENT, left + barW, zeroY, left + 2 * barW, y(-p.secondaryCents)))
                        add(SegmentBounds(i, OVERSPENDING, left + 2 * barW, zeroY, left + 3 * barW, y(-p.overspendingCents)))
                    }
                }
            }
            val balancePath = remember(points, plotW, plotH, axisMin, axisMax) {
                Path().apply { addMonotoneCubicSpline(points.mapIndexed { i, p -> Offset(slot * i + slot / 2f, y(p.tertiaryCents)) }) }
            }
            val visibleLabels = remember(labels, plotW, textMeasurer, labelStyle) {
                val sample = textMeasurer.measure(labels.first(), labelStyle).size.width.toFloat()
                thinMonthLabels(labels, plotW - slot, sample, minSpacingPx = pad).map { it to textMeasurer.measure(labels[it], labelStyle) }
            }

            Box(Modifier.fillMaxSize()) {
                Canvas(
                    Modifier.fillMaxSize()
                        .semantics {
                            contentDescription = "Budget analysis with $n months: budgeted, spent, overspending and balance. " +
                                "Total budgeted ${formatMoneyCents(points.sumOf { it.primaryCents }, hideDecimals)}, " +
                                "total spent ${formatMoneyCents(points.sumOf { it.secondaryCents }, hideDecimals)}, " +
                                "latest balance ${formatMoneyCents(points.last().tertiaryCents, hideDecimals)}. " +
                                "Tap a Spent bar to see that month's spending."
                            customActions = listOf(
                                CustomAccessibilityAction("Next period") { selectedIndex = stepPeriodIndex(selectedIndex, 1, n); true },
                                CustomAccessibilityAction("Previous period") { selectedIndex = stepPeriodIndex(selectedIndex, -1, n); true },
                            )
                        }
                        .chartScrubber(
                            onScrub = { pos -> selectedIndex = (pos.x.coerceIn(0f, currentPlotW) / (currentPlotW / n)).toInt().coerceIn(0, n - 1) },
                            onTap = { pos ->
                                val local = Offset(pos.x, pos.y - topInset)
                                val hit = hitTestSegment(local, bars, minTouchTargetPx = with(density) { 40.dp.toPx() })
                                when {
                                    hit != null && hit.second == SPENT -> openSpending(hit.first)
                                    pos.x <= currentPlotW + pad -> {
                                        val i = (pos.x.coerceIn(0f, currentPlotW) / (currentPlotW / n)).toInt().coerceIn(0, n - 1)
                                        selectedIndex = i.takeIf { it != selectedIndex }
                                    }
                                }
                            },
                        ),
                ) {
                    translate(top = topInset) {
                        drawChartGridAndValues(
                            axis = axis, chartAreaWidth = plotW, chartAreaHeight = plotH,
                            minVal = axisMin, maxVal = axisMax, gridColor = gridColor, textColor = textColor,
                        )
                        drawLine(textColor.copy(alpha = 0.6f), Offset(0f, zeroY), Offset(plotW, zeroY), 1.dp.toPx())
                        val corner = min(barW / 2f, 3.dp.toPx())
                        bars.forEach { seg ->
                            val color = when (seg.segmentIndex) { BUDGETED -> budgetedColor; SPENT -> spentColor; else -> overColor }
                            val alpha = if (activeIndex == null || activeIndex == seg.groupIndex) 1f else 0.4f
                            val h = (seg.bottom - seg.top) * reveal.value
                            if (h > 0.5f) {
                                val up = seg.segmentIndex == BUDGETED
                                val r = min(corner, h)
                                drawPath(
                                    Path().apply {
                                        addRoundRect(
                                            RoundRect(
                                                if (up) Rect(seg.left, seg.bottom - h, seg.right, seg.bottom) else Rect(seg.left, seg.top, seg.right, seg.top + h),
                                                topLeft = if (up) CornerRadius(r) else CornerRadius.Zero,
                                                topRight = if (up) CornerRadius(r) else CornerRadius.Zero,
                                                bottomLeft = if (up) CornerRadius.Zero else CornerRadius(r),
                                                bottomRight = if (up) CornerRadius.Zero else CornerRadius(r),
                                            ),
                                        )
                                    },
                                    color.copy(alpha = alpha),
                                )
                            }
                        }
                        clipRect(right = plotW * reveal.value + 8.dp.toPx(), top = -topInset, bottom = plotH + 8.dp.toPx()) {
                            drawPath(balancePath, balanceColor, style = balanceStroke)
                        }
                        points.forEachIndexed { i, p ->
                            val r = if (i == activeIndex) 4.5.dp.toPx() else 2.5.dp.toPx()
                            drawCircle(balanceColor, r, Offset(slot * i + slot / 2f, y(p.tertiaryCents)))
                        }
                        activeIndex?.let { i ->
                            drawLine(textColor.copy(alpha = 0.4f), Offset(slot * i + slot / 2f, 0f), Offset(slot * i + slot / 2f, plotH), 1.5.dp.toPx())
                        }
                        visibleLabels.forEach { (idx, layout) ->
                            val x = (slot * idx + slot / 2f - layout.size.width / 2f).coerceIn(0f, max(0f, plotW - layout.size.width))
                            drawText(layout, textColor, Offset(x, plotH + pad))
                        }
                    }
                }

                // Semantics only, sized to each bar: TalkBack reads every bar, and clicks still reach the canvas.
                if (n <= 40) bars.forEach { seg ->
                    val p = points[seg.groupIndex]
                    val (name, cents) = when (seg.segmentIndex) {
                        BUDGETED -> "Budgeted" to p.primaryCents
                        SPENT -> "Spent" to p.secondaryCents
                        else -> "Overspending" to p.overspendingCents
                    }
                    val w = (seg.right - seg.left).coerceAtLeast(1f)
                    val h = (seg.bottom - seg.top).coerceAtLeast(1f)
                    Box(
                        Modifier
                            .offset { IntOffset(seg.left.roundToInt(), (seg.top + topInset).roundToInt()) }
                            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                            .semantics {
                                contentDescription = "${labels[seg.groupIndex]} $name: ${formatMoneyCents(cents, hideDecimals)}"
                                if (seg.segmentIndex == SPENT && onDrillDown != null && drillable(p)) {
                                    role = Role.Button
                                    onClick(label = "View spending") { openSpending(seg.groupIndex); true }
                                }
                            },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Budgeted" to budgetedColor, "Spent" to spentColor, "Overspending" to overColor, "Balance" to balanceColor).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = textColor)
                }
            }
        }
    }
}
