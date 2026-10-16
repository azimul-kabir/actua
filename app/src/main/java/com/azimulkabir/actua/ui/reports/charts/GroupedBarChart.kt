package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.DrillDownDirection
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.ui.components.formatMoneyCents
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/**
 * Grouped income and expense bar chart with rounded tops, right-aligned value axis,
 * thinned month labels, legend toggling, press-and-drag scrubbing tooltip,
 * and one-tap drill-down on bars.
 */
@Composable
fun GroupedBarChart(
    points: List<ReportPoint>,
    hideDecimals: Boolean,
    onDrillDown: (ReportCategory) -> Unit,
    modifier: Modifier = Modifier,
    primaryLabel: String = "Income",
    secondaryLabel: String = "Expense",
    chartHeight: Dp = 150.dp,
    chartContentDescription: String? = null,
) {
    if (points.isEmpty()) {
        Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val palette = rememberChartPalette()
    val incomeColor = palette.semantic.income
    val expenseColor = palette.semantic.expense
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    var showIncome by rememberSaveable { mutableStateOf(true) }
    var showExpense by rememberSaveable { mutableStateOf(true) }
    var selectedIndex by rememberSaveable(points.size) { mutableStateOf<Int?>(null) }
    val activeIndex = selectedIndex?.takeIf { it in points.indices }

    val defaultContentDesc = "$primaryLabel and $secondaryLabel by month, ${points.size} months."
    val effectiveContentDesc = chartContentDescription ?: defaultContentDesc

    // Calculate maximum value based on active series
    val maxVal = when {
        showIncome && showExpense -> points.maxOfOrNull { max(it.primaryCents.absoluteValue, it.secondaryCents.absoluteValue) } ?: 0L
        showIncome -> points.maxOfOrNull { it.primaryCents.absoluteValue } ?: 0L
        showExpense -> points.maxOfOrNull { it.secondaryCents.absoluteValue } ?: 0L
        else -> 0L
    }.coerceAtLeast(1L)

    val gridlines = calculateNiceGridlines(0L, maxVal, targetSteps = 4)
    val chartMaxVal = gridlines.lastOrNull()?.coerceAtLeast(maxVal) ?: maxVal
    val textMeasurer = rememberTextMeasurer()
    val axisMeasurements = rememberChartValueAxis(
        gridlines = gridlines,
        textMeasurer = textMeasurer,
        formatLabel = { formatCompactMoney(it, hideDecimals) },
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Active month detail card / tooltip
        activeIndex?.let { i ->
            val point = points[i]
            val netCents = point.primaryCents - point.secondaryCents
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = point.drillDownQuery != null || point.transactionIds.isNotEmpty(),
                        onClickLabel = "View transactions",
                    ) {
                        onDrillDown(
                            ReportCategory(
                                name = point.period.take(7),
                                spentCents = netCents,
                                transactionIds = point.transactionIds,
                                incomeCents = point.primaryCents,
                                expenseCents = point.secondaryCents,
                                drillDownQuery = point.drillDownQuery,
                            )
                        )
                    },
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        point.period.take(7),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "$primaryLabel ${formatMoneyCents(point.primaryCents, hideDecimals)} · " +
                            "$secondaryLabel ${formatMoneyCents(point.secondaryCents, hideDecimals)} · " +
                            "Net ${formatMoneyCents(netCents, hideDecimals)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight * LocalChartScale.current),
        ) {
            val density = LocalDensity.current
            val totalWidthPx = constraints.maxWidth.toFloat()
            val totalHeightPx = constraints.maxHeight.toFloat()
            val labelPaddingPx = with(density) { 8.dp.toPx() }
            val axisWidthPx = axisMeasurements.maxLabelWidth + labelPaddingPx
            val chartAreaWidthPx = (totalWidthPx - axisWidthPx).coerceAtLeast(1f)
            val chartAreaWidthDp = with(density) { chartAreaWidthPx.toDp() }

            val n = points.size
            val slotWidthPx = chartAreaWidthPx / n
            val bothShown = showIncome && showExpense
            val barWidthPx = if (bothShown) {
                (slotWidthPx * 0.35f).coerceIn(2f, with(density) { 24.dp.toPx() })
            } else {
                (slotWidthPx * 0.5f).coerceIn(2f, with(density) { 36.dp.toPx() })
            }
            val gapPx = if (bothShown) with(density) { 2.dp.toPx() } else 0f
            val cornerRadiusPx = min(barWidthPx / 2f, with(density) { 4.dp.toPx() })

            // Compute hit-test segment bounds
            val segments = remember(points, showIncome, showExpense, chartAreaWidthPx, totalHeightPx, chartMaxVal) {
                val list = mutableListOf<SegmentBounds>()
                points.forEachIndexed { i, p ->
                    val groupCenter = slotWidthPx * i + slotWidthPx / 2f
                    if (showIncome) {
                        val inH = (p.primaryCents.absoluteValue.toFloat() / chartMaxVal * totalHeightPx).coerceAtLeast(0f)
                        val inLeft = if (bothShown) groupCenter - barWidthPx - gapPx / 2f else groupCenter - barWidthPx / 2f
                        val inRight = inLeft + barWidthPx
                        val inTop = totalHeightPx - inH
                        list.add(SegmentBounds(groupIndex = i, segmentIndex = 0, inLeft, inTop, inRight, totalHeightPx))
                    }
                    if (showExpense) {
                        val outH = (p.secondaryCents.absoluteValue.toFloat() / chartMaxVal * totalHeightPx).coerceAtLeast(0f)
                        val outLeft = if (bothShown) groupCenter + gapPx / 2f else groupCenter - barWidthPx / 2f
                        val outRight = outLeft + barWidthPx
                        val outTop = totalHeightPx - outH
                        list.add(SegmentBounds(groupIndex = i, segmentIndex = 1, outLeft, outTop, outRight, totalHeightPx))
                    }
                }
                list
            }

            Box(Modifier.fillMaxSize()) {
                // Drawing layer (bars, gridlines, axis labels) with scrubber
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            contentDescription = effectiveContentDesc
                            onClick(label = "Select period") {
                                selectedIndex = if (points.size == 1) 0 else (selectedIndex?.plus(1) ?: 0).coerceIn(0, points.size - 1)
                                true
                            }
                        }
                        .chartScrubber(
                            onScrub = { pos ->
                                if (pos.x in 0f..chartAreaWidthPx) {
                                    val idx = (pos.x / chartAreaWidthPx * n).toInt().coerceIn(0, n - 1)
                                    selectedIndex = idx
                                }
                            },
                            onTap = { pos ->
                                val hit = hitTestSegment(pos, segments, minTouchTargetPx = with(density) { 48.dp.toPx() })
                                if (hit != null) {
                                    val point = points[hit.first]
                                    if (hit.second == 0) {
                                        // Income tapped
                                        onDrillDown(
                                            ReportCategory(
                                                name = "${point.period.take(7)} · $primaryLabel",
                                                spentCents = point.primaryCents,
                                                transactionIds = point.transactionIds,
                                                incomeCents = point.primaryCents,
                                                drillDownQuery = point.drillDownQuery?.copy(
                                                    direction = DrillDownDirection.INCOME,
                                                    month = point.period.take(7),
                                                ) ?: DrillDownQuery(
                                                    direction = DrillDownDirection.INCOME,
                                                    month = point.period.take(7),
                                                ),
                                            )
                                        )
                                    } else {
                                        // Expense tapped
                                        onDrillDown(
                                            ReportCategory(
                                                name = "${point.period.take(7)} · $secondaryLabel",
                                                spentCents = point.secondaryCents,
                                                transactionIds = point.transactionIds,
                                                expenseCents = point.secondaryCents,
                                                drillDownQuery = point.drillDownQuery?.copy(
                                                    direction = DrillDownDirection.EXPENSE,
                                                    month = point.period.take(7),
                                                ) ?: DrillDownQuery(
                                                    direction = DrillDownDirection.EXPENSE,
                                                    month = point.period.take(7),
                                                ),
                                            )
                                        )
                                    }
                                } else if (pos.x in 0f..chartAreaWidthPx) {
                                    val idx = (pos.x / chartAreaWidthPx * n).toInt().coerceIn(0, n - 1)
                                    selectedIndex = idx.takeIf { it != selectedIndex }
                                }
                            },
                        )
                ) {
                    // Draw gridlines and right-hand value labels
                    drawChartGridAndValues(
                        axis = axisMeasurements,
                        chartAreaWidth = chartAreaWidthPx,
                        chartAreaHeight = totalHeightPx,
                        minVal = 0L,
                        maxVal = chartMaxVal,
                        gridColor = gridColor,
                        textColor = textColor,
                    )

                    // Draw bars
                    segments.forEach { seg ->
                        val isIncome = seg.segmentIndex == 0
                        val color = if (isIncome) incomeColor else expenseColor
                        val alpha = if (activeIndex == null || activeIndex == seg.groupIndex) 1f else 0.4f
                        val h = seg.bottom - seg.top
                        if (h > 0f) {
                            val r = min(cornerRadiusPx, h)
                            val path = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(seg.left, seg.top, seg.right, seg.bottom),
                                        topLeft = CornerRadius(r, r),
                                        topRight = CornerRadius(r, r),
                                        bottomLeft = CornerRadius.Zero,
                                        bottomRight = CornerRadius.Zero,
                                    )
                                )
                            }
                            drawPath(path, color.copy(alpha = alpha))
                        }
                    }
                }

                // Transparent accessibility overlay: TalkBack reads and opens each bar directly
                Row(
                    modifier = Modifier
                        .width(chartAreaWidthDp)
                        .fillMaxHeight(),
                ) {
                    points.forEachIndexed { i, p ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            if (showIncome) {
                                val monthLabel = p.period.take(7)
                                val desc = "$monthLabel $primaryLabel: ${formatMoneyCents(p.primaryCents, hideDecimals)}"
                                val incomeCategory = ReportCategory(
                                    name = "$monthLabel · $primaryLabel",
                                    spentCents = p.primaryCents,
                                    transactionIds = p.transactionIds,
                                    incomeCents = p.primaryCents,
                                    drillDownQuery = p.drillDownQuery?.copy(
                                        direction = DrillDownDirection.INCOME,
                                        month = monthLabel,
                                    ) ?: DrillDownQuery(
                                        direction = DrillDownDirection.INCOME,
                                        month = monthLabel,
                                    ),
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            onClickLabel = "View $primaryLabel transactions",
                                            onClick = {
                                                selectedIndex = i
                                                onDrillDown(incomeCategory)
                                            },
                                        )
                                        .semantics {
                                            contentDescription = desc
                                            role = Role.Button
                                        }
                                )
                            }
                            if (showExpense) {
                                val monthLabel = p.period.take(7)
                                val desc = "$monthLabel $secondaryLabel: ${formatMoneyCents(p.secondaryCents, hideDecimals)}"
                                val expenseCategory = ReportCategory(
                                    name = "$monthLabel · $secondaryLabel",
                                    spentCents = p.secondaryCents,
                                    transactionIds = p.transactionIds,
                                    expenseCents = p.secondaryCents,
                                    drillDownQuery = p.drillDownQuery?.copy(
                                        direction = DrillDownDirection.EXPENSE,
                                        month = monthLabel,
                                    ) ?: DrillDownQuery(
                                        direction = DrillDownDirection.EXPENSE,
                                        month = monthLabel,
                                    ),
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            onClickLabel = "View $secondaryLabel transactions",
                                            onClick = {
                                                selectedIndex = i
                                                onDrillDown(expenseCategory)
                                            },
                                        )
                                        .semantics {
                                            contentDescription = desc
                                            role = Role.Button
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Thinned month labels along the bottom aligned with chartAreaWidth
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val totalWidthPx = constraints.maxWidth.toFloat()
            val labelPaddingPx = with(density) { 8.dp.toPx() }
            val axisWidthPx = axisMeasurements.maxLabelWidth + labelPaddingPx
            val chartAreaWidthPx = (totalWidthPx - axisWidthPx).coerceAtLeast(1f)
            val chartAreaWidthDp = with(density) { chartAreaWidthPx.toDp() }

            val monthLabels = remember(points) { points.map { it.period.take(7) } }
            val labelStyle = MaterialTheme.typography.labelSmall
            val sampleLayout = remember(textMeasurer, monthLabels, labelStyle) {
                textMeasurer.measure(
                    text = monthLabels.firstOrNull() ?: "2026-00",
                    style = labelStyle,
                )
            }
            val labelWidthPx = sampleLayout.size.width.toFloat()
            val visibleIndices = remember(monthLabels, chartAreaWidthPx, labelWidthPx) {
                thinMonthLabels(monthLabels, chartAreaWidthPx, labelWidthPx, minSpacingPx = with(density) { 8.dp.toPx() })
            }

            Box(modifier = Modifier.width(chartAreaWidthDp)) {
                val n = points.size
                if (n > 0) {
                    val slotWidthDp = chartAreaWidthDp / n
                    visibleIndices.forEach { idx ->
                        Box(
                            modifier = Modifier
                                .width(slotWidthDp)
                                .padding(start = slotWidthDp * idx),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = monthLabels[idx],
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // Tappable Legend: toggle Income / Expense series
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendSeriesToggle(
                label = primaryLabel,
                color = incomeColor,
                enabled = showIncome,
                onToggle = {
                    if (showIncome && !showExpense) {
                        // Keep at least one visible
                    } else {
                        showIncome = !showIncome
                    }
                },
            )
            LegendSeriesToggle(
                label = secondaryLabel,
                color = expenseColor,
                enabled = showExpense,
                onToggle = {
                    if (showExpense && !showIncome) {
                        // Keep at least one visible
                    } else {
                        showExpense = !showExpense
                    }
                },
            )
        }
    }
}

@Composable
private fun LegendSeriesToggle(
    label: String,
    color: Color,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onToggle)
            .clearAndSetSemantics {
                contentDescription = if (enabled) "Hide $label series" else "Show $label series"
                role = Role.Switch
                onClick(label = if (enabled) "Hide $label series" else "Show $label series") {
                    onToggle()
                    true
                }
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (enabled) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
        } else {
            Box(
                Modifier
                    .size(10.dp)
                    .border(1.5.dp, color.copy(alpha = 0.5f), CircleShape)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}
