package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.ui.components.formatMoneyCents
import kotlin.math.absoluteValue
import kotlin.math.sqrt

private val TwoRingSize = 248.dp
private val GroupRingWidth = 36.dp
private val CategoryRingWidth = 28.dp
private val RingSpacing = 4.dp
private val RingPopOut = 5.dp
private const val SliceGapDegrees = 1.5f

/**
 * Two-ring donut for a report grouped by category group, as Actual draws `Category+Group`: the
 * inner ring holds the category groups and the outer ring their categories, each category a
 * lighter tint of its group's colour. Tapping a slice or a legend row selects it, shows its total
 * and share in the centre, and opens its transactions. [groups] carry their categories in
 * [ReportCategory.children].
 */
@Composable
fun GroupDonutChart(
    groups: List<ReportCategory>,
    hideDecimals: Boolean,
    onDrillDown: (ReportCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberChartPalette()
    val categories = remember(groups) { groups.flatMap { it.children } }
    val groupColors = remember(groups, palette) { groups.indices.map { palette.categorical[it % palette.categorical.size] } }
    val categoryColors = remember(groups, groupColors) {
        groups.flatMapIndexed { g, group ->
            group.children.indices.map { c -> lerp(groupColors[g], Color.White, categoryShadeFraction(c, group.children.size)) }
        }
    }
    val groupMagnitudes = remember(groups) { groups.map { it.spentCents.absoluteValue } }
    val categoryMagnitudes = remember(categories) { categories.map { it.spentCents.absoluteValue } }
    val total = groupMagnitudes.sum().coerceAtLeast(1)
    val groupArcs = remember(groupMagnitudes) { computeDonutArcs(groupMagnitudes) }
    val categoryArcs = remember(categoryMagnitudes) { computeDonutArcs(categoryMagnitudes) }
    val groupHits = remember(groupArcs) { donutHitSlices(groupArcs) }
    val categoryHits = remember(categoryArcs) { donutHitSlices(categoryArcs) }

    val reveal = rememberChartReveal(groups)
    var selectedGroup by rememberSaveable(groups.size) { mutableStateOf<Int?>(null) }
    var selectedCategory by rememberSaveable(categories.size) { mutableStateOf<Int?>(null) }
    val activeGroup = selectedGroup?.takeIf { it in groups.indices }
    val activeCategory = selectedCategory?.takeIf { it in categories.indices }
    val shown = activeCategory?.let { categories[it] } ?: activeGroup?.let { groups[it] }

    fun canDrill(c: ReportCategory) = c.drillDownQuery != null || c.transactionIds.isNotEmpty()
    fun openGroup(i: Int) {
        selectedGroup = i
        selectedCategory = null
        if (canDrill(groups[i])) onDrillDown(groups[i])
    }
    fun openCategory(i: Int) {
        selectedCategory = i
        selectedGroup = null
        if (canDrill(categories[i])) onDrillDown(categories[i])
    }
    fun share(cents: Long) = "${"%.1f".format(cents.absoluteValue * 100.0 / total)}%"

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(TwoRingSize), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(TwoRingSize)
                    .semantics {
                        contentDescription = "Two-ring donut chart. The inner ring has ${groups.size} category groups and the outer ring " +
                            "${categories.size} categories, total ${formatMoneyCents(groups.sumOf { it.spentCents }, hideDecimals)}. " +
                            groups.joinToString(" ") { "${it.name} ${share(it.spentCents)}, ${formatMoneyCents(it.spentCents, hideDecimals)}." } +
                            " Categories are listed below the chart."
                    }
                    .pointerInput(groups) {
                        val half = size.width / 2f
                        val center = Offset(half, size.height / 2f)
                        val outerEdge = half - RingPopOut.toPx()
                        val categoryInner = outerEdge - CategoryRingWidth.toPx()
                        val groupOuter = categoryInner - RingSpacing.toPx()
                        val groupInner = groupOuter - GroupRingWidth.toPx()
                        detectTapGestures(
                            onTap = { pos ->
                                val dx = pos.x - center.x
                                val dy = pos.y - center.y
                                val distance = sqrt(dx * dx + dy * dy)
                                if (distance >= (categoryInner + groupOuter) / 2f) {
                                    val hit = hitTestDonutSlice(pos, center, categoryInner, outerEdge, categoryHits, minTouchTargetPx = 0f)
                                    if (hit != null) openCategory(hit) else { selectedCategory = null; selectedGroup = null }
                                } else {
                                    val hit = hitTestDonutSlice(pos, center, groupInner, groupOuter, groupHits, minTouchTargetPx = 0f)
                                    if (hit != null) openGroup(hit) else { selectedCategory = null; selectedGroup = null }
                                }
                            },
                        )
                    },
            ) {
                val pop = RingPopOut.toPx()
                val categoryWidth = CategoryRingWidth.toPx()
                val groupWidth = GroupRingWidth.toPx()
                val outerEdge = size.width / 2f - pop
                val categoryRadius = outerEdge - categoryWidth / 2f
                val groupRadius = outerEdge - categoryWidth - RingSpacing.toPx() - groupWidth / 2f
                drawRing(groupArcs, groupColors, groupRadius, groupWidth, reveal.value, activeGroup, pop)
                drawRing(categoryArcs, categoryColors, categoryRadius, categoryWidth, reveal.value, activeCategory, pop)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
                Text(
                    shown?.name ?: "Total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                )
                Text(
                    formatMoneyCents(shown?.spentCents ?: groups.sumOf { it.spentCents }, hideDecimals),
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                if (shown != null) Text(
                    share(shown.spentCents),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val largeFont = LocalDensity.current.fontScale > 1.3f
        var categoryIndex = 0
        CollapsibleBreakdown { groups.forEachIndexed { g, group ->
            LegendRow(
                name = group.name, share = share(group.spentCents), amount = formatMoneyCents(group.spentCents, hideDecimals),
                color = groupColors[g], selected = g == activeGroup, largeFont = largeFont, indent = false,
                onClick = { openGroup(g) },
            )
            group.children.forEach { category ->
                val flatIndex = categoryIndex++
                LegendRow(
                    name = category.name, share = share(category.spentCents), amount = formatMoneyCents(category.spentCents, hideDecimals),
                    color = categoryColors[flatIndex], selected = flatIndex == activeCategory, largeFont = largeFont, indent = true,
                    onClick = { openCategory(flatIndex) },
                )
            }
        } }
    }
}

private fun DrawScope.drawRing(
    arcs: List<DonutSlice>, colors: List<Color>, radius: Float, width: Float, progress: Float, selected: Int?, pop: Float,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    arcs.forEachIndexed { i, arc ->
        val gap = if (arcs.size > 1) SliceGapDegrees else 0f
        // Slices sweep round from 12 o'clock; the progress is read here so frames only redraw.
        val sweep = (arc.sweepAngleDeg - gap).coerceAtLeast(0.5f) * progress
        val r = radius + if (i == selected) pop else 0f
        drawArc(
            colors[i], arc.startAngleDeg * progress + (-90f) * (1f - progress) + gap / 2f, sweep, false,
            topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r), style = Stroke(width),
        )
    }
}

@Composable
private fun LegendRow(
    name: String, share: String, amount: String, color: Color, selected: Boolean, largeFont: Boolean, indent: Boolean,
    onClick: () -> Unit,
) {
    val nameWeight = if (selected || !indent) FontWeight.SemiBold else FontWeight.Normal
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClickLabel = "View transactions", onClick = onClick)
            .padding(start = if (indent) 18.dp else 0.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(if (indent) 8.dp else 10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        if (largeFont) {
            // At large font sizes the name gets a line of its own so it is never cut to a few letters.
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 2, fontWeight = nameWeight)
                Row {
                    Text(share, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(amount, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Text(name, maxLines = 1, modifier = Modifier.weight(1f), fontWeight = nameWeight)
            Text(share, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(amount, fontWeight = FontWeight.SemiBold)
        }
    }
}
