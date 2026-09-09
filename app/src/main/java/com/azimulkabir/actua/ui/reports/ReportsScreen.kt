package com.azimulkabir.actua.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.ui.components.formatMoneyCents
import kotlin.math.max

@Composable
fun ReportsScreen(
    snapshot: ReportSnapshot,
    hideDecimalPlaces: Boolean,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    scrollToTopRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    var selectedPageId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val selected = snapshot.dashboards.firstOrNull { it.id == selectedPageId }
        ?: snapshot.dashboards.firstOrNull()
    LaunchedEffect(snapshot.dashboards.map { it.id }) {
        if (snapshot.dashboards.none { it.id == selectedPageId }) selectedPageId = snapshot.dashboards.firstOrNull()?.id
    }
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 20.dp, 20.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search Actua") }
            }
        }
        if (selected == null) {
            item { EmptyReports() }
        } else {
            item {
                DashboardPicker(selected, snapshot.dashboards, pickerOpen, onOpenChange = { pickerOpen = it }) {
                    selectedPageId = it; pickerOpen = false
                }
            }
            val unsupported = selected.widgets.filter { it.kind == ReportWidgetKind.UNSUPPORTED }
            if (unsupported.isNotEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Text(
                        "${unsupported.size} dashboard widget${if (unsupported.size == 1) " is" else "s are"} not available in Actua yet: " +
                            unsupported.joinToString { it.name },
                        Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val visible = selected.widgets.filterNot { it.kind == ReportWidgetKind.UNSUPPORTED }
            if (visible.isEmpty()) item { EmptyReports() }
            items(visible, key = { it.id }) { widget -> WidgetCard(widget, hideDecimalPlaces) }
        }
    }
}

@Composable
private fun DashboardPicker(
    selected: ReportDashboardPage,
    pages: List<ReportDashboardPage>,
    expanded: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    Box {
        Card(
            Modifier.fillMaxWidth().clickable(enabled = pages.size > 1) { onOpenChange(true) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selected.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                if (pages.size > 1) Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Switch dashboard")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onOpenChange(false) }) {
            pages.forEach { page -> DropdownMenuItem(text = { Text(page.name) }, onClick = { onSelect(page.id) }) }
        }
    }
}

@Composable
private fun WidgetCard(widget: ReportWidget, hideDecimals: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(widget.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (widget.kind) {
                ReportWidgetKind.SUMMARY -> Text(
                    widget.percentage?.let { "${"%.2f".format(it)}%" }
                        ?: formatMoneyCents(widget.valueCents ?: 0, hideDecimals),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                ReportWidgetKind.NET_WORTH -> {
                    Text(formatMoneyCents(widget.valueCents ?: 0, hideDecimals),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    TrendChart(widget.points)
                    PointLabels(widget.points, hideDecimals)
                }
                ReportWidgetKind.CASH_FLOW -> CashFlow(widget.points, hideDecimals)
                ReportWidgetKind.SPENDING -> Spending(widget, hideDecimals)
                ReportWidgetKind.MARKDOWN -> Text(widget.markdown.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReportWidgetKind.UNSUPPORTED -> Unit
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<ReportPoint>) {
    val color = MaterialTheme.colorScheme.primary
    if (points.isEmpty()) return
    val minimum = points.minOf { it.primaryCents }
    val maximum = points.maxOf { it.primaryCents }
    val span = (maximum - minimum).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val step = if (points.size <= 1) 0f else size.width / (points.size - 1)
        points.zipWithNext().forEachIndexed { index, pair ->
            fun y(value: Long) = size.height - ((value - minimum).toFloat() / span * size.height)
            drawLine(color, Offset(step * index, y(pair.first.primaryCents)),
                Offset(step * (index + 1), y(pair.second.primaryCents)), strokeWidth = 5f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun PointLabels(points: List<ReportPoint>, hideDecimals: Boolean) {
    if (points.isEmpty()) Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
    else Row(Modifier.fillMaxWidth()) {
        Text(points.first().period.take(7), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(formatMoneyCents(points.last().primaryCents, hideDecimals), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Text(points.last().period.take(7), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CashFlow(points: List<ReportPoint>, hideDecimals: Boolean) {
    if (points.isEmpty()) { Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val maximum = points.maxOf { max(it.primaryCents, it.secondaryCents) }.coerceAtLeast(1)
    points.forEach { point ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(point.period.take(7), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
                Text("In ${formatMoneyCents(point.primaryCents, hideDecimals)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text("Out ${formatMoneyCents(point.secondaryCents, hideDecimals)}", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.weight(point.primaryCents.toFloat().coerceAtLeast(1f) / maximum).height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
                Spacer(Modifier.weight(point.secondaryCents.toFloat().coerceAtLeast(1f) / maximum).height(6.dp)
                    .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(50)))
            }
        }
    }
}

@Composable
private fun Spending(widget: ReportWidget, hideDecimals: Boolean) {
    val current = widget.valueCents ?: 0
    val comparison = widget.comparisonCents ?: 0
    val maximum = max(current.coerceAtLeast(0), comparison.coerceAtLeast(0)).coerceAtLeast(1)
    Text(formatMoneyCents(current, hideDecimals), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Comparison ${formatMoneyCents(comparison, hideDecimals)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.fillMaxWidth((current.toFloat() / maximum).coerceIn(0f, 1f)).height(9.dp)
        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
}

@Composable
private fun EmptyReports() {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No dashboard widgets", style = MaterialTheme.typography.titleMedium)
        Text("Configure a dashboard in Actual Budget and sync it to Actua.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
