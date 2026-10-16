package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.ui.components.formatMoneyCents

private enum class SankeySide { INCOME, EXPENSE }

private data class SankeyNode(val name: String, val value: Long, val color: Color)

/**
 * Sankey of income sources flowing through an income hub into expense categories (the structure
 * of Actual's `sankey-spreadsheet.ts` in `spent` mode). Names and shares sit inside the bars,
 * long names end in "…" with the full name in the summary above. One tap on a node, ribbon or
 * legend row opens its transactions; a long press focuses its flow and dims the rest.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SankeyChart(
    income: Long,
    categories: List<ReportCategory>,
    incomeCategories: List<ReportCategory>,
    subtitle: String?,
    hideDecimals: Boolean,
    onDrillDown: ((ReportCategory) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val expenses = categories.filter { it.spentCents > 0 }
    val sources = incomeCategories.filter { it.spentCents > 0 }
    if (income <= 0 && expenses.isEmpty() && sources.isEmpty()) {
        Text("No transactions match this report's filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val palette = rememberChartPalette()
    val remainingColor = MaterialTheme.colorScheme.outlineVariant
    val hubColor = palette.semantic.income
    val expenseTotal = expenses.sumOf { it.spentCents }
    val remaining = (income - expenseTotal).coerceAtLeast(0)
    val rightNodes = remember(expenses, remaining, palette, remainingColor) {
        expenses.mapIndexed { i, c -> SankeyNode(c.name, c.spentCents, palette.categorical[i % palette.categorical.size]) } +
            (if (remaining > 0) listOf(SankeyNode("Remaining", remaining, remainingColor)) else emptyList())
    }
    // Income sources take colours from the far end of the palette, so the two sides stay distinct.
    val leftNodes = remember(sources, income, palette) {
        sources.ifEmpty { listOf(ReportCategory("Income", income)) }.mapIndexed { i, c ->
            SankeyNode(c.name, c.spentCents, palette.categorical[palette.categorical.size - 1 - i % palette.categorical.size])
        }
    }
    val hasBreakdown = sources.isNotEmpty()
    val total = maxOf(income, leftNodes.sumOf { it.value }, rightNodes.sumOf { it.value }, 1)

    var selected by rememberSaveable(leftNodes.size, rightNodes.size) { mutableStateOf<Pair<SankeySide, Int>?>(null) }
    val activeRight = selected?.takeIf { it.first == SankeySide.EXPENSE && it.second in rightNodes.indices }?.second
    val activeLeft = selected?.takeIf { it.first == SankeySide.INCOME && it.second in leftNodes.indices }?.second

    fun categoryOf(side: SankeySide, i: Int) = if (side == SankeySide.INCOME) sources.getOrNull(i) else expenses.getOrNull(i)
    fun canDrill(c: ReportCategory?) = onDrillDown != null && c != null && (c.drillDownQuery != null || c.transactionIds.isNotEmpty())
    fun activate(side: SankeySide, i: Int) {
        val category = categoryOf(side, i)
        if (canDrill(category)) {
            selected = side to i
            onDrillDown?.invoke(category!!)
        } else {
            selected = (side to i).takeIf { it != selected }
        }
    }
    fun focus(side: SankeySide, i: Int) { selected = side to i }
    fun share(value: Long) = if (income > 0) "${"%.1f".format(value * 100.0 / income)}%" else ""

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    activeLeft?.let { "${leftNodes[it].name} · ${share(leftNodes[it].value)}" } ?: "Income",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatMoneyCents(activeLeft?.let { leftNodes[it].value } ?: income, hideDecimals),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    activeRight?.let { "${rightNodes[it].name} · ${share(rightNodes[it].value)}" } ?: "Expenses",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatMoneyCents(activeRight?.let { rightNodes[it].value } ?: expenseTotal, hideDecimals),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        val textMeasurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelSmall
        BoxWithConstraints(Modifier.fillMaxWidth().height(240.dp)) {
            val density = LocalDensity.current
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val barW = minOf(with(density) { 76.dp.toPx() }, w * 0.28f)
            val hubW = with(density) { 12.dp.toPx() }
            val midX = w / 2f
            val hubLeft = if (hasBreakdown) midX - hubW / 2f else barW
            val hubRight = if (hasBreakdown) midX + hubW / 2f else barW
            val rightX = w - barW
            val hubHeight = income.coerceAtLeast(0L).toFloat() / total * h

            val leftBars = remember(leftNodes, total, h) { layoutSankeyBars(leftNodes.map { it.value }, total, h) }
            val rightBars = remember(rightNodes, total, h) { layoutSankeyBars(rightNodes.map { it.value }, total, h) }
            val leftRibbons = remember(leftBars, hubHeight, hasBreakdown) {
                if (hasBreakdown) layoutSankeyRibbons(leftBars, hubHeight) else emptyList()
            }
            val rightRibbons = remember(rightBars, hubHeight) { layoutSankeyRibbons(rightBars, hubHeight) }
            val leftBounds = remember(leftRibbons, barW, hubLeft) { sankeyRibbonBounds(leftRibbons, barW, hubLeft, nodeOnLeft = true) }
            val rightBounds = remember(rightRibbons, hubRight, rightX) { sankeyRibbonBounds(rightRibbons, hubRight, rightX, nodeOnLeft = false) }

            fun fit(text: String, maxWidth: Float) = textMeasurer.measure(
                text, labelStyle.copy(fontWeight = FontWeight.SemiBold), overflow = TextOverflow.Ellipsis, maxLines = 1,
                constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(0)),
            )
            val pad = with(density) { 4.dp.toPx() }
            val labelWidth = barW - 2 * pad
            fun labels(nodes: List<SankeyNode>) = nodes.map { n -> fit(n.name, labelWidth) to textMeasurer.measure(share(n.value), labelStyle) }
            val leftLabels = remember(leftNodes, labelWidth, income) { labels(leftNodes) }
            val rightLabels = remember(rightNodes, labelWidth, income) { labels(rightNodes) }

            Canvas(
                Modifier.fillMaxWidth().height(240.dp)
                    .semantics {
                        contentDescription = "Sankey diagram from ${leftNodes.size} income sources to ${rightNodes.size} " +
                            "expense categories. Tap a node or flow to see its transactions; long press to focus it."
                    }
                    .pointerInput(leftBars, rightBars, leftBounds, rightBounds, barW) {
                        fun locate(pos: Offset): Pair<SankeySide, Int>? {
                            val slop = 8.dp.toPx()
                            return when {
                                pos.x <= barW -> sankeyBarAt(pos.y, leftBars)?.let { SankeySide.INCOME to it }
                                pos.x >= rightX -> sankeyBarAt(pos.y, rightBars)?.let { SankeySide.EXPENSE to it }
                                pos.x < hubLeft -> hitTestRibbon(pos, leftBounds, slop)?.let { SankeySide.INCOME to it }
                                pos.x > hubRight -> hitTestRibbon(pos, rightBounds, slop)?.let { SankeySide.EXPENSE to it }
                                else -> null
                            }
                        }
                        detectTapGestures(
                            onLongPress = { pos -> locate(pos)?.let { focus(it.first, it.second) } },
                            onTap = { pos -> locate(pos)?.let { activate(it.first, it.second) } ?: run { selected = null } },
                        )
                    },
            ) {
                val corner = CornerRadius(4.dp.toPx())
                fun ribbon(span: SankeyRibbonSpan, xSrc: Float, xDst: Float, color: Color) {
                    val mx = (xSrc + xDst) / 2f
                    val path = Path().apply {
                        moveTo(xSrc, span.srcTop)
                        cubicTo(mx, span.srcTop, mx, span.dstTop, xDst, span.dstTop)
                        lineTo(xDst, span.dstBottom)
                        cubicTo(mx, span.dstBottom, mx, span.srcBottom, xSrc, span.srcBottom)
                        close()
                    }
                    drawPath(path, color)
                }
                fun bar(x: Float, bar: SankeyBar, node: SankeyNode, alpha: Float, label: Pair<androidx.compose.ui.text.TextLayoutResult, androidx.compose.ui.text.TextLayoutResult>) {
                    if (bar.height <= 0f) return
                    drawRoundRect(node.color.copy(alpha = alpha), Offset(x, bar.top), Size(barW, bar.height), corner)
                    val (name, pct) = label
                    val textColor = (if (node.color.luminance() > 0.5f) Color.Black else Color.White).copy(alpha = alpha)
                    val twoLines = bar.height >= name.size.height + pct.size.height + pad * 2
                    val oneLine = bar.height >= name.size.height + pad
                    if (oneLine) {
                        val y = if (twoLines) bar.top + pad else bar.top + (bar.height - name.size.height) / 2f
                        drawText(name, textColor, Offset(x + pad, y))
                        if (twoLines && pct.layoutInput.text.isNotEmpty()) drawText(pct, textColor, Offset(x + pad, y + name.size.height))
                    }
                }

                leftRibbons.forEachIndexed { i, span ->
                    val alpha = if (activeLeft == null || activeLeft == i) 1f else 0.35f
                    ribbon(span, barW, hubLeft, leftNodes[i].color.copy(alpha = 0.3f * alpha))
                }
                rightRibbons.forEachIndexed { i, span ->
                    val alpha = if (activeRight == null || activeRight == i) 1f else 0.35f
                    ribbon(span, rightX, hubRight, rightNodes[i].color.copy(alpha = 0.3f * alpha))
                }
                leftBars.forEachIndexed { i, b ->
                    bar(0f, b, leftNodes[i], if (activeLeft == null || activeLeft == i) 1f else 0.35f, leftLabels[i])
                }
                if (hasBreakdown) drawRoundRect(hubColor, Offset(hubLeft, 0f), Size(hubW, hubHeight), corner)
                rightBars.forEachIndexed { i, b ->
                    bar(rightX, b, rightNodes[i], if (activeRight == null || activeRight == i) 1f else 0.35f, rightLabels[i])
                }
            }
        }

        if (hasBreakdown) {
            Text("Income sources", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            leftNodes.forEachIndexed { i, node ->
                LegendRow(
                    node = node, selected = i == activeLeft, dimmed = activeLeft != null && activeLeft != i,
                    share = share(node.value), hideDecimals = hideDecimals,
                    canDrill = canDrill(sources.getOrNull(i)),
                    onClick = { activate(SankeySide.INCOME, i) }, onLongClick = { focus(SankeySide.INCOME, i) },
                )
            }
        }
        rightNodes.forEachIndexed { i, node ->
            LegendRow(
                node = node, selected = i == activeRight, dimmed = activeRight != null && activeRight != i,
                share = null, hideDecimals = hideDecimals,
                canDrill = canDrill(expenses.getOrNull(i)),
                onClick = { activate(SankeySide.EXPENSE, i) }, onLongClick = { focus(SankeySide.EXPENSE, i) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LegendRow(
    node: SankeyNode, selected: Boolean, dimmed: Boolean, share: String?, hideDecimals: Boolean,
    canDrill: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val textAlpha = if (dimmed) 0.5f else 1f
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(
                onClickLabel = if (canDrill) "View transactions" else "Highlight",
                onLongClickLabel = "Focus ${node.name}",
                onLongClick = onLongClick, onClick = onClick,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(10.dp).background(node.color.copy(alpha = if (dimmed) 0.35f else 1f), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            node.name, maxLines = 1, modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
        )
        Text(
            (if (!share.isNullOrEmpty()) "$share · " else "") + formatMoneyCents(node.value, hideDecimals),
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
        )
    }
}
