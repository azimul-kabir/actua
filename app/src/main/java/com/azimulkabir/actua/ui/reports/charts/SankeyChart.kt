package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import com.azimulkabir.actua.model.SankeyDiagram
import com.azimulkabir.actua.model.SankeyDiagramNode
import com.azimulkabir.actua.model.SankeyNodeRole
import com.azimulkabir.actua.ui.components.formatMoneyCents

private fun SankeyDiagramNode.shareText() = "${"%.1f".format(share * 100)}%"

/**
 * A layered Sankey, as Actual draws it: one column per layer (payees, income categories,
 * accounts, budget, category groups, categories) over the card's saved layer range, with transfers
 * chaining account columns. Names and amounts sit inside the bars, long names end in "…" with the
 * full name in the legend below. One tap on a node, ribbon or legend row opens its transactions;
 * a long press focuses its flows and dims the rest. A diagram with more columns than fit scrolls sideways.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SankeyChart(
    diagram: SankeyDiagram,
    subtitle: String?,
    hideDecimals: Boolean,
    onDrillDown: ((ReportCategory) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (diagram.isEmpty) {
        Text(
            if (diagram.budgeted) "No budget flows for this report." else "No transactions match this report's filters.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val palette = rememberChartPalette()
    val gray = MaterialTheme.colorScheme.outlineVariant
    val colors = remember(diagram, palette, gray) {
        diagram.nodes.map { node ->
            when (node.role) {
                SankeyNodeRole.POOL -> palette.semantic.balance
                SankeyNodeRole.NEGATIVE, SankeyNodeRole.OVERSPENT -> palette.semantic.expense
                SankeyNodeRole.TO_BUDGET -> palette.semantic.income
                SankeyNodeRole.CARRYOVER -> gray
                // A stable colour per node, so it keeps its colour when the diagram's order changes.
                SankeyNodeRole.NORMAL -> palette.categorical[(node.key.hashCode() and Int.MAX_VALUE) % palette.categorical.size]
            }
        }
    }
    val columns = diagram.columnCount
    val first = diagram.columnLayers.first()
    val last = diagram.columnLayers.last()

    val reveal = rememberChartReveal(diagram)
    var selected by rememberSaveable(diagram.nodes.size) { mutableStateOf<Int?>(null) }
    val active = selected?.takeIf { it in diagram.nodes.indices }
    val activeLinks = remember(diagram, active) { active?.let { sankeyLinksOf(diagram, it) }.orEmpty() }
    val connectedNodes = remember(diagram, active) {
        active?.let { a -> activeLinksNodes(diagram, a) }.orEmpty()
    }
    val currentDrill by rememberUpdatedState(onDrillDown)

    fun canDrill(i: Int) = currentDrill != null && diagram.nodes[i].drillDownQuery != null
    fun activate(i: Int) {
        if (canDrill(i)) {
            selected = i
            val node = diagram.nodes[i]
            currentDrill?.invoke(ReportCategory(node.name, node.valueCents, drillDownQuery = node.drillDownQuery))
        } else {
            selected = i.takeIf { it != selected }
        }
    }
    fun focus(i: Int) { selected = i }
    fun amount(node: SankeyDiagramNode) = formatMoneyCents(node.valueCents, hideDecimals)
    fun detail(node: SankeyDiagramNode) = if (diagram.showPercentages) node.shareText() else amount(node)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (active != null) {
            val node = diagram.nodes[active]
            Column {
                Text(
                    "${node.layer.title} · ${node.name} · ${node.shareText()}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(amount(node), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(first.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatMoneyCents(diagram.inflowCents, hideDecimals),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(last.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoneyCents(diagram.outflowCents, hideDecimals), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        val textMeasurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelSmall
        val largestColumn = diagram.nodes.groupingBy { it.column }.eachCount().values.max()
        // Tall enough that the fullest column keeps a readable bar for every node.
        val chartHeight = maxOf(240.dp * LocalChartScale.current, 30.dp * largestColumn)
        val scroll = rememberScrollState()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val minBar = 64.dp
            val minGap = 36.dp
            val needed = minBar * columns + minGap * (columns - 1)
            val chartWidth = maxOf(maxWidth, needed)
            val w = with(density) { chartWidth.toPx() }
            val h = with(density) { chartHeight.toPx() }
            val barW = minOf(with(density) { 76.dp.toPx() }, (w - with(density) { minGap.toPx() } * (columns - 1)) / columns)
            val gap = if (columns > 1) (w - columns * barW) / (columns - 1) else 0f
            val pad = with(density) { 4.dp.toPx() }
            fun barX(column: Int) = column * (barW + gap)

            val layout = remember(diagram, h) { layoutSankey(diagram, h, nodePadding = pad * 1.5f) }
            val bounds = remember(layout, barW, gap) {
                sankeyBandBounds(
                    layout.bands,
                    srcRight = { barX(diagram.nodes[it].column) + barW },
                    dstLeft = { barX(diagram.nodes[it].column) },
                )
            }
            fun fit(text: String) = textMeasurer.measure(
                text, labelStyle.copy(fontWeight = FontWeight.SemiBold), overflow = TextOverflow.Ellipsis, maxLines = 1,
                constraints = Constraints(maxWidth = (barW - 2 * pad).toInt().coerceAtLeast(0)),
            )
            val labels = remember(diagram, barW, hideDecimals) {
                diagram.nodes.map { node -> fit(node.name) to textMeasurer.measure(detail(node), labelStyle) }
            }

            Box(Modifier.fillMaxWidth().then(if (needed > maxWidth) Modifier.horizontalScroll(scroll) else Modifier)) {
            Canvas(
                Modifier
                    .width(chartWidth).height(chartHeight)
                    .semantics {
                        contentDescription = "Sankey diagram of ${diagram.nodes.size} nodes in $columns columns, from ${first.title} " +
                            "to ${last.title}. ${first.title} ${formatMoneyCents(diagram.inflowCents, hideDecimals)}, " +
                            "${last.title} ${formatMoneyCents(diagram.outflowCents, hideDecimals)}. " +
                            "Tap a node or flow to see its transactions; long press to focus it."
                    }
                    .pointerInput(layout, bounds, barW, gap) {
                        fun locate(pos: Offset): Int? {
                            val column = (pos.x / (barW + gap)).toInt().coerceIn(0, columns - 1)
                            if (pos.x - barX(column) in 0f..barW) {
                                sankeyNodeAt(pos.y, column, layout.nodes)?.let { return it }
                            }
                            return hitTestRibbon(pos, bounds, 24.dp.toPx())?.let { sankeyLinkOwner(diagram, it) }
                        }
                        detectTapGestures(
                            onLongPress = { pos -> locate(pos)?.let(::focus) },
                            onTap = { pos -> locate(pos)?.let(::activate) ?: run { selected = null } },
                        )
                    },
            ) {
                val corner = CornerRadius(4.dp.toPx())
                diagram.links.forEachIndexed { i, link ->
                    val band = layout.bands[i]
                    if (band.srcBottom <= band.srcTop) return@forEachIndexed
                    val xSrc = barX(diagram.nodes[link.source].column) + barW
                    val xDst = barX(diagram.nodes[link.target].column)
                    val mx = (xSrc + xDst) / 2f
                    val path = Path().apply {
                        moveTo(xSrc, band.srcTop)
                        cubicTo(mx, band.srcTop, mx, band.dstTop, xDst, band.dstTop)
                        lineTo(xDst, band.dstBottom)
                        cubicTo(mx, band.dstBottom, mx, band.srcBottom, xSrc, band.srcBottom)
                        close()
                    }
                    val alpha = if (active == null || i in activeLinks) 1f else 0.35f
                    val tint = colors[if (link.colorFromSource) link.source else link.target]
                    drawPath(path, tint.copy(alpha = 0.3f * alpha * reveal.value))
                }
                layout.nodes.forEachIndexed { i, box ->
                    if (box.height <= 0f) return@forEachIndexed
                    val x = barX(box.column)
                    val color = colors[i]
                    val alpha = if (active == null || i == active || i in connectedNodes) 1f else 0.35f
                    drawRoundRect(color.copy(alpha = alpha), Offset(x, box.top), Size(barW, box.height), corner)
                    val (name, detailText) = labels[i]
                    val textColor = (if (color.luminance() > 0.5f) Color.Black else Color.White).copy(alpha = alpha)
                    val twoLines = box.height >= name.size.height + detailText.size.height + pad * 2
                    val oneLine = box.height >= name.size.height + pad
                    if (oneLine) {
                        val y = if (twoLines) box.top + pad else box.top + (box.height - name.size.height) / 2f
                        drawText(name, textColor, Offset(x + pad, y))
                        if (twoLines) drawText(detailText, textColor, Offset(x + pad, y + name.size.height))
                    }
                }
            }
            }
        }

        // The legend gives every node its full name and a row to tap, grouped by layer.
        CollapsibleBreakdown { diagram.nodes.withIndex().groupBy { it.value.layer }.toSortedMap().forEach { (layer, rows) ->
            Text(layer.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach { (i, node) ->
                LegendRow(
                    node = node, color = colors[i], selected = i == active,
                    dimmed = active != null && i != active && i !in connectedNodes,
                    detail = if (diagram.showPercentages) node.shareText() else "${node.shareText()} · ${amount(node)}",
                    canDrill = canDrill(i),
                    onClick = { activate(i) }, onLongClick = { focus(i) },
                )
            }
        } }
    }
}

/** The nodes at the other end of every flow that touches [node]. */
private fun activeLinksNodes(diagram: SankeyDiagram, node: Int): Set<Int> =
    sankeyLinksOf(diagram, node).flatMap { listOf(diagram.links[it].source, diagram.links[it].target) }.toSet()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LegendRow(
    node: SankeyDiagramNode, color: Color, selected: Boolean, dimmed: Boolean, detail: String,
    canDrill: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val textAlpha = if (dimmed) 0.5f else 1f
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
    val nameWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(
                onClickLabel = if (canDrill) "View transactions" else "Highlight",
                onLongClickLabel = "Focus ${node.name}",
                onLongClick = onLongClick, onClick = onClick,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(10.dp).background(color.copy(alpha = if (dimmed) 0.35f else 1f), CircleShape))
        Spacer(Modifier.width(8.dp))
        val name: @Composable (Modifier, Int) -> Unit = { m, lines ->
            Column(m) {
                Text(node.name, maxLines = lines, fontWeight = nameWeight, color = textColor)
                if (node.foldedNames.isNotEmpty()) {
                    Text(
                        node.foldedNames.joinToString(", "), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = textAlpha * 0.7f),
                    )
                }
            }
        }
        if (LocalDensity.current.fontScale > 1.3f) {
            // At large font sizes the name gets a line of its own so it is never cut to a few letters.
            Column(Modifier.weight(1f)) {
                name(Modifier, 2)
                Text(detail, fontWeight = FontWeight.SemiBold, color = textColor)
            }
        } else {
            name(Modifier.weight(1f), 1)
            Text(detail, fontWeight = FontWeight.SemiBold, color = textColor)
        }
    }
}
