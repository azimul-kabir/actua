package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** One line of a [LineChart]. [values] line up with the chart's periods. */
data class LineChartSeries(
    val label: String,
    val color: Color,
    val values: List<Long>,
    /** Fades an area fill under the line. */
    val fill: Boolean = false,
    /** Points from this index on are drawn dashed as a forecast. */
    val forecastFromIndex: Int? = null,
)

/** A shaded band between [lower] and [upper] values, e.g. a range of simulated outcomes. */
data class LineChartBand(
    val lower: List<Long>,
    val upper: List<Long>,
    val color: Color,
    val alpha: Float = 0.2f,
)

/**
 * Smooth line chart with a monotone spline that never overshoots the data, optional area fill,
 * right-hand value labels, thinned period labels, and a press-and-drag tooltip that reports the
 * real value at each point. [onDrillDown], when set, adds a "View transactions" action to the
 * tooltip for points where [canDrill] holds.
 *
 * [lowValue] draws a labelled horizontal marker, [crossoverPosition] a dashed vertical marker at a
 * fractional point index, and a series' [LineChartSeries.forecastFromIndex] dashes its forecast part.
 */
@Composable
fun LineChart(
    periods: List<String>,
    series: List<LineChartSeries>,
    formatValue: (Long) -> String,
    formatAxis: (Long) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 160.dp,
    lowValue: Long? = null,
    lowLabel: String = "Low",
    crossoverPosition: Float? = null,
    crossoverLabel: String? = null,
    forecastLabel: String = "Forecast",
    bands: List<LineChartBand> = emptyList(),
    detail: ((Int) -> String)? = null,
    canDrill: (Int) -> Boolean = { false },
    onDrillDown: ((Int) -> Unit)? = null,
) {
    val n = periods.size
    if (n == 0 || series.isEmpty()) {
        Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = MaterialTheme.colorScheme.outline
    val lowColor = rememberChartPalette().semantic.expense
    val labelStyle = MaterialTheme.typography.labelSmall

    var selectedIndex by rememberSaveable(n) { mutableStateOf<Int?>(null) }
    val activeIndex = selectedIndex?.takeIf { it in 0 until n }

    val labels = remember(periods) {
        val months = periods.map { it.take(7) }
        if (months.distinct().size == months.size) months else periods.map { it.take(10) }
    }
    val allValues = remember(series, lowValue, bands) {
        series.flatMap { it.values } + listOfNotNull(lowValue) + bands.flatMap { it.lower + it.upper }
    }
    val gridlines = remember(allValues) {
        val lo = allValues.min()
        val hi = allValues.max()
        calculateNiceGridlines(lo, if (hi == lo) lo + 1 else hi).let { if (it.size >= 2) it else listOf(lo, lo + 1) }
    }
    val axisMin = gridlines.first()
    val axisMax = gridlines.last()
    val textMeasurer = rememberTextMeasurer()
    val axis = rememberChartValueAxis(gridlines, labelStyle, textMeasurer, formatAxis)
    val forecastBoundary = remember(series) { series.firstNotNullOfOrNull { it.forecastFromIndex } }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        activeIndex?.let { i ->
            val drillable = onDrillDown != null && canDrill(i)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().then(
                    if (drillable) Modifier.clickable(onClickLabel = "View transactions") { onDrillDown?.invoke(i) }
                    else Modifier,
                ),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(labels[i], style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        series.joinToString(" · ") { s ->
                            val v = formatValue(s.values[i])
                            if (series.size == 1) v else "${s.label} $v"
                        } + if (forecastBoundary != null && i >= forecastBoundary) " · $forecastLabel" else "",
                        style = MaterialTheme.typography.bodySmall, color = textColor,
                    )
                    detail?.invoke(i)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = textColor)
                    }
                    if (drillable) {
                        Text(
                            "Tap to view transactions",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().height(chartHeight)) {
            val density = LocalDensity.current
            val totalW = constraints.maxWidth.toFloat()
            val totalH = constraints.maxHeight.toFloat()
            val pad = with(density) { 8.dp.toPx() }
            val plotW = (totalW - axis.maxLabelWidth - pad).coerceAtLeast(1f)
            val plotWDp = with(density) { plotW.toDp() }
            val labelBandH = axis.maxLabelHeight + pad
            val topInset = axis.maxLabelHeight / 2f
            val plotH = (totalH - labelBandH - topInset).coerceAtLeast(1f)
            val currentPlotW by rememberUpdatedState(plotW)

            val geometry = remember(series, plotW, plotH, axisMin, axisMax) {
                val span = (axisMax - axisMin).coerceAtLeast(1L).toFloat()
                val step = if (n <= 1) 0f else plotW / (n - 1)
                fun y(v: Long) = plotH - (v - axisMin) / span * plotH
                series.map { s ->
                    val pts = s.values.mapIndexed { i, v -> Offset(step * i, y(v)) }
                    val segments = computeMonotoneCubicSpline(pts)
                    val boundary = s.forecastFromIndex
                    val solidEnd = if (boundary == null) segments.size else (boundary - 1).coerceIn(0, segments.size)
                    fun pathOf(range: IntRange) = Path().apply {
                        range.forEachIndexed { k, idx ->
                            val seg = segments[idx]
                            if (k == 0) moveTo(seg.start.x, seg.start.y)
                            cubicTo(seg.control1.x, seg.control1.y, seg.control2.x, seg.control2.y, seg.end.x, seg.end.y)
                        }
                    }
                    val solid = pathOf(0 until solidEnd)
                    val dashed = pathOf(solidEnd until segments.size)
                    val fill = if (s.fill && segments.isNotEmpty()) Path().apply {
                        addPath(pathOf(segments.indices))
                        lineTo(pts.last().x, plotH); lineTo(pts.first().x, plotH); close()
                    } else null
                    LineGeometry(pts, solid, dashed, fill)
                }
            }

            val bandPaths = remember(bands, plotW, plotH, axisMin, axisMax) {
                val span = (axisMax - axisMin).coerceAtLeast(1L).toFloat()
                val step = if (n <= 1) 0f else plotW / (n - 1)
                fun y(v: Long) = plotH - (v - axisMin) / span * plotH
                bands.map { band ->
                    val upper = computeMonotoneCubicSpline(band.upper.mapIndexed { i, v -> Offset(step * i, y(v)) })
                    val lower = computeMonotoneCubicSpline(band.lower.mapIndexed { i, v -> Offset(step * i, y(v)) })
                    Path().apply {
                        if (upper.isEmpty() || lower.isEmpty()) return@apply
                        moveTo(upper.first().start.x, upper.first().start.y)
                        upper.forEach { cubicTo(it.control1.x, it.control1.y, it.control2.x, it.control2.y, it.end.x, it.end.y) }
                        lineTo(lower.last().end.x, lower.last().end.y)
                        lower.asReversed().forEach { cubicTo(it.control2.x, it.control2.y, it.control1.x, it.control1.y, it.start.x, it.start.y) }
                        close()
                    }
                }
            }

            val visibleLabels = remember(labels, plotW, textMeasurer, labelStyle) {
                val sample = textMeasurer.measure(labels.maxBy { it.length }, labelStyle).size.width.toFloat()
                // Labels are centred on points, so the first and last clamp inward: leave a label width spare.
                thinMonthLabels(labels, plotW - 2 * sample, sample, minSpacingPx = pad).map { it to textMeasurer.measure(labels[it], labelStyle) }
            }
            val forecastText = remember(forecastLabel, textMeasurer, labelStyle) { textMeasurer.measure(forecastLabel, labelStyle) }
            val lowText = remember(lowValue, lowLabel, textMeasurer, labelStyle) {
                lowValue?.let { textMeasurer.measure("$lowLabel ${formatAxis(it)}", labelStyle) }
            }
            val crossText = remember(crossoverLabel, textMeasurer, labelStyle) {
                crossoverLabel?.let { textMeasurer.measure(it, labelStyle) }
            }
            val dash = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) }
            val strokeWidth = with(density) { 2.5.dp.toPx() }

            Box(Modifier.fillMaxSize()) {
                Canvas(
                    Modifier.fillMaxSize()
                        .semantics { this.contentDescription = contentDescription }
                        .chartScrubber(
                            onScrub = { pos -> selectedIndex = nearestPointIndex(pos.x.coerceIn(0f, currentPlotW), currentPlotW, n) },
                            onTap = { pos ->
                                if (pos.x <= currentPlotW + pad) {
                                    val i = nearestPointIndex(pos.x.coerceIn(0f, currentPlotW), currentPlotW, n)
                                    selectedIndex = i.takeIf { it != selectedIndex }
                                }
                            },
                        ),
                ) {
                    translate(top = topInset) {
                        drawChartGridAndValues(
                            axis = axis, chartAreaWidth = plotW, chartAreaHeight = plotH,
                            minVal = axisMin, maxVal = axisMax, gridColor = gridColor, textColor = textColor,
                        )
                        val xStep = if (n <= 1) 0f else plotW / (n - 1)
                        val span = (axisMax - axisMin).coerceAtLeast(1L).toFloat()

                        bandPaths.forEachIndexed { k, p -> drawPath(p, bands[k].color.copy(alpha = bands[k].alpha)) }
                        geometry.forEachIndexed { k, g ->
                            val s = series[k]
                            g.fill?.let { drawPath(it, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.3f), s.color.copy(alpha = 0f)), 0f, plotH)) }
                            drawPath(g.solid, s.color, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                            drawPath(g.dashed, s.color, style = Stroke(strokeWidth, cap = StrokeCap.Round, pathEffect = dash))
                            if (n == 1) drawCircle(s.color, strokeWidth * 1.5f, g.points[0])
                        }

                        forecastBoundary?.let { b ->
                            if (b < n) {
                                val x = xStep * b
                                drawLine(markerColor.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, plotH), 1.dp.toPx(), pathEffect = dash)
                                val tx = min(x + 4.dp.toPx(), plotW - forecastText.size.width)
                                drawText(forecastText, textColor, Offset(max(0f, tx), 2.dp.toPx()))
                            }
                        }
                        lowValue?.let { low ->
                            val y = plotH - (low - axisMin) / span * plotH
                            drawLine(lowColor.copy(alpha = 0.7f), Offset(0f, y), Offset(plotW, y), 1.dp.toPx(), pathEffect = dash)
                            lowText?.let { t ->
                                val ty = if (y - t.size.height - 2.dp.toPx() >= 0f) y - t.size.height - 2.dp.toPx() else y + 2.dp.toPx()
                                drawText(t, lowColor, Offset(4.dp.toPx(), ty))
                            }
                        }
                        crossoverPosition?.let { pos ->
                            val x = (pos * xStep).coerceIn(0f, plotW)
                            drawLine(markerColor, Offset(x, 0f), Offset(x, plotH), 1.5.dp.toPx(), pathEffect = dash)
                            crossText?.let { t ->
                                val tx = if (x + t.size.width + 4.dp.toPx() <= plotW) x + 4.dp.toPx() else x - t.size.width - 4.dp.toPx()
                                drawText(t, textColor, Offset(max(0f, tx), 2.dp.toPx()))
                            }
                        }
                        activeIndex?.let { i ->
                            val x = xStep * i
                            drawLine(markerColor.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, plotH), 1.5.dp.toPx())
                            geometry.forEachIndexed { k, g -> drawCircle(series[k].color, 4.5.dp.toPx(), g.points[i]) }
                        }
                        visibleLabels.forEach { (idx, layout) ->
                            val x = (xStep * idx - layout.size.width / 2f).coerceIn(0f, max(0f, plotW - layout.size.width))
                            drawText(layout, textColor, Offset(x, plotH + pad))
                        }
                    }
                }

                // Semantics only (no pointer input): TalkBack can read and open every point while
                // touches still reach the scrubber underneath.
                if (n <= 60) {
                    Row(Modifier.width(plotWDp).fillMaxHeight()) {
                        repeat(n) { i ->
                            val drillable = onDrillDown != null && canDrill(i)
                            Box(
                                Modifier.weight(if (n > 1 && (i == 0 || i == n - 1)) 0.5f else 1f).fillMaxHeight()
                                    .semantics {
                                        this.contentDescription = labels[i] + ": " +
                                            series.joinToString(", ") { s ->
                                                if (series.size == 1) formatValue(s.values[i]) else "${s.label} ${formatValue(s.values[i])}"
                                            }
                                        if (drillable) {
                                            onClick(label = "View transactions") {
                                                selectedIndex = i
                                                onDrillDown?.invoke(i)
                                                true
                                            }
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }

        if (series.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(s.color, CircleShape))
                        Text(s.label, style = MaterialTheme.typography.bodySmall, color = textColor)
                    }
                }
            }
        }
    }
}

private class LineGeometry(
    val points: List<Offset>,
    val solid: Path,
    val dashed: Path,
    val fill: Path?,
)
