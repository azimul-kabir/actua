package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.ui.components.formatMoneyCents
import kotlin.math.absoluteValue

private val DonutSize = 200.dp
private val RingWidth = 30.dp
private val PopOut = 6.dp
private const val GapDegrees = 2.5f

/**
 * Donut with small gaps between slices and the total in the centre. A tap on a slice or legend
 * row selects it (it moves out slightly, the centre shows its amount and share) and opens its
 * transactions; a long press focuses one category and dims the rest until you tap elsewhere.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DonutChart(
    categories: List<ReportCategory>,
    hideDecimals: Boolean,
    onDrillDown: (ReportCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = remember(categories) { groupSmallCategories(categories) }
    val palette = rememberChartPalette()
    val otherColor = MaterialTheme.colorScheme.outline
    val colors = remember(segments, palette, otherColor) {
        val grouped = categories.size > MAX_DONUT_SLICES
        segments.indices.map { i ->
            if (grouped && i == segments.lastIndex) otherColor else palette.categorical[i % palette.categorical.size]
        }
    }
    val magnitudes = remember(segments) { segments.map { it.spentCents.absoluteValue } }
    val total = magnitudes.sum().coerceAtLeast(1)
    val arcs = remember(magnitudes) { computeDonutArcs(magnitudes) }
    val hitSlices = remember(arcs) { donutHitSlices(arcs) }

    var selected by rememberSaveable(segments.size) { mutableStateOf<Int?>(null) }
    var focused by rememberSaveable(segments.size) { mutableStateOf<Int?>(null) }
    val activeSelected = selected?.takeIf { it in segments.indices }
    val activeFocus = focused?.takeIf { it in segments.indices }
    val shown = activeFocus ?: activeSelected

    fun canDrill(c: ReportCategory) = c.drillDownQuery != null || c.transactionIds.isNotEmpty()
    fun open(i: Int) {
        selected = i
        if (canDrill(segments[i])) onDrillDown(segments[i])
    }

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(DonutSize), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(DonutSize)
                    .semantics {
                        contentDescription = "Donut chart with ${segments.size} segments. Long press a slice to focus it."
                    }
                    .pointerInput(segments) {
                        val half = size.width / 2f
                        val center = Offset(half, size.height / 2f)
                        val outer = half - PopOut.toPx()
                        val inner = outer - RingWidth.toPx()
                        fun hit(pos: Offset) = hitTestDonutSlice(
                            pos, center, inner, outer, hitSlices, minTouchTargetPx = 16.dp.toPx(),
                        )
                        detectTapGestures(
                            onLongPress = { pos -> hit(pos)?.let { focused = it; selected = it } },
                            onTap = { pos ->
                                val hit = hit(pos)
                                val focus = focused
                                when {
                                    focus != null && hit != focus -> focused = null
                                    hit != null -> open(hit)
                                    else -> selected = null
                                }
                            },
                        )
                    },
            ) {
                val stroke = RingWidth.toPx()
                val pop = PopOut.toPx()
                val pad = stroke / 2f + pop
                val base = size.width - 2 * pad
                arcs.forEachIndexed { i, arc ->
                    val gap = if (arcs.size > 1) GapDegrees else 0f
                    val sweep = (arc.sweepAngleDeg - gap).coerceAtLeast(0.5f)
                    val out = if (i == activeSelected) pop else 0f
                    val alpha = if (activeFocus != null && activeFocus != i) 0.25f else 1f
                    drawArc(
                        colors[i].copy(alpha = alpha), arc.startAngleDeg + gap / 2f, sweep, false,
                        topLeft = Offset(pad - out, pad - out), size = Size(base + 2 * out, base + 2 * out),
                        style = Stroke(stroke),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    shown?.let { segments[it].name } ?: "Total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
                Text(
                    formatMoneyCents(shown?.let { segments[it].spentCents } ?: segments.sumOf { it.spentCents }, hideDecimals),
                    fontWeight = FontWeight.SemiBold,
                )
                if (shown != null) Text(
                    "${"%.1f".format(magnitudes[shown] * 100.0 / total)}%",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        segments.forEachIndexed { i, segment ->
            val dim = activeFocus != null && activeFocus != i
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClickLabel = "View transactions",
                        onLongClickLabel = "Focus ${segment.name}",
                        onLongClick = { focused = i; selected = i },
                        onClick = { if (activeFocus != null && activeFocus != i) focused = null else open(i) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(10.dp).background(colors[i].copy(alpha = if (dim) 0.35f else 1f), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    segment.name, maxLines = 1, modifier = Modifier.weight(1f),
                    fontWeight = if (i == shown) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dim) 0.5f else 1f),
                )
                Text(
                    "${"%.1f".format(magnitudes[i] * 100.0 / total)}%", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    formatMoneyCents(segment.spentCents, hideDecimals), fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dim) 0.5f else 1f),
                )
            }
        }
    }
}
