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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.azimulkabir.actua.model.ReportAccountOption
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportViewFilter
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.transactions.TransactionRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.PillShape
import com.azimulkabir.actua.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ReportsScreen(
    snapshot: ReportSnapshot,
    hideDecimalPlaces: Boolean,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onSearch: () -> Unit = {},
    favoriteReportIds: Set<String> = emptySet(),
    onFavoriteReportChange: (String, Boolean) -> Unit = { _, _ -> },
    loadSavedReports: suspend (ReportViewFilter) -> List<ReportWidget> = { emptyList() },
    loadTransactions: suspend (List<String>) -> List<Transaction> = { emptyList() },
    scrollToTopRequest: Int = 0,
    initialPageId: String? = null,
    initialPageRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    var datePreset by rememberSaveable { mutableStateOf<String?>(null) }
    var accountIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var groupIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var includeOffBudget by rememberSaveable { mutableStateOf(false) }
    val view = ReportViewFilter(datePreset, accountIds.toSet(), groupIds.toSet(), includeOffBudget)
    var filterFailed by remember { mutableStateOf(false) }
    val filteredSaved by produceState<List<ReportWidget>?>(null, view, snapshot.dashboards.size) {
        filterFailed = false
        value = if (view.isDefault) null else try {
            loadSavedReports(view)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            filterFailed = true
            null
        }
    }
    var drill by remember { mutableStateOf<Pair<ReportCategory, String>?>(null) }
    var selectedPageId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val selected = snapshot.dashboards.firstOrNull { it.id == selectedPageId }
        ?: snapshot.dashboards.firstOrNull()
    LaunchedEffect(snapshot.dashboards.map { it.id }) {
        // Dashboards load asynchronously and start empty, including right after process/config
        // recreation restores selectedPageId; skip the reset until there's something to check
        // against, or it would immediately discard the restored (or just-requested) selection.
        if (snapshot.dashboards.isNotEmpty() && snapshot.dashboards.none { it.id == selectedPageId }) {
            selectedPageId = snapshot.dashboards.firstOrNull()?.id
        }
    }
    LaunchedEffect(initialPageRequest) {
        if (initialPageRequest > 0) selectedPageId = initialPageId
    }
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            Spacing.screenHorizontal, Spacing.screenHorizontal, Spacing.screenHorizontal, 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Reports", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                ) {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search Actua")
                    }
                }
            }
        }
        if (isLoading && selected == null) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.testTag("reportsLoadingIndicator"))
                }
            }
        } else if (selected == null) {
            item { EmptyReports() }
        } else {
            item {
                DashboardPicker(selected, snapshot.dashboards, pickerOpen, selected.id in favoriteReportIds,
                    onFavoriteChange = { onFavoriteReportChange(selected.id, it) }, onOpenChange = { pickerOpen = it }) {
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
            if (selected.id == SAVED_PAGE_ID) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ViewFilterBar(view, snapshot.accountOptions, snapshot.groupOptions,
                        onPreset = { datePreset = it }, onAccounts = { accountIds = it.toList() },
                        onGroups = { groupIds = it.toList() }, onOffBudget = { includeOffBudget = it })
                    if (filterFailed) Text("Couldn't apply the filter. Showing the reports as saved.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            val shownWidgets = if (selected.id == SAVED_PAGE_ID && !view.isDefault) filteredSaved ?: selected.widgets
                else selected.widgets
            val visible = shownWidgets.filterNot { it.kind == ReportWidgetKind.UNSUPPORTED }
            if (visible.isEmpty()) item { EmptyReports() }
            items(visible, key = { it.id }) { widget -> WidgetCard(widget, hideDecimalPlaces, onDrillDown = { drill = it to widget.name }) }
        }
    }
    drill?.let { (segment, reportName) ->
        DrillDownSheet(segment, reportName, hideDecimalPlaces, loadTransactions) { drill = null }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DrillDownSheet(
    segment: ReportCategory,
    reportName: String,
    hideDecimals: Boolean,
    load: suspend (List<String>) -> List<Transaction>,
    onDismiss: () -> Unit,
) {
    val limit = 200
    val rows by produceState<List<Transaction>?>(null, segment) { value = load(segment.transactionIds.take(limit)) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
            Text(segment.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("$reportName · ${segment.transactionIds.size} transactions · " +
                formatMoneyCents(segment.spentCents, hideDecimals),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (segment.transactionIds.size > limit) Text("Showing the first $limit.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val loaded = rows
        if (loaded == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else LazyColumn(Modifier.fillMaxWidth()) {
            items(loaded, key = { it.id }) { tx ->
                TransactionRow(tx, hideDecimals, showDate = true, showAccount = true, onClick = {}, onLongClick = {})
            }
        }
    }
}

@Composable
private fun IncomeExpense(widget: ReportWidget, hideDecimals: Boolean, onDrillDown: (ReportCategory) -> Unit) {
    val income = MaterialTheme.colorScheme.primary
    val expense = MaterialTheme.colorScheme.error
    val points = widget.points
    var selected by rememberSaveable(points.size) { mutableStateOf<Int?>(null) }
    val active = selected?.takeIf { it in points.indices }
    widget.subtitle?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("Net ${formatMoneyCents(widget.valueCents ?: 0, hideDecimals)}",
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    if (points.isEmpty() || widget.categories.all { it.spentCents == 0L }) {
        Text("No income or expenses in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text(
        active?.let {
            val p = points[it]
            "${p.period} · In ${formatMoneyCents(p.primaryCents, hideDecimals)} · Out ${formatMoneyCents(p.secondaryCents, hideDecimals)} · Net ${formatMoneyCents(p.primaryCents - p.secondaryCents, hideDecimals)}"
        } ?: "Tap a month for details",
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val maximum = points.maxOf { max(it.primaryCents.absoluteValue, it.secondaryCents.absoluteValue) }.coerceAtLeast(1)
    Canvas(
        Modifier.fillMaxWidth().height(140.dp)
            .semantics { contentDescription = "Income and expenses by month, ${points.size} months. Tap a month for details." }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val i = (tap.x / size.width * points.size).toInt().coerceIn(0, points.size - 1)
                    selected = i.takeIf { it != selected }
                }
            },
    ) {
        val slot = size.width / points.size
        val bar = (slot * 0.36f).coerceAtLeast(1f)
        points.forEachIndexed { i, p ->
            val alpha = if (active == null || active == i) 1f else 0.45f
            val inH = p.primaryCents.absoluteValue.toFloat() / maximum * size.height
            val outH = p.secondaryCents.absoluteValue.toFloat() / maximum * size.height
            val x = slot * i + slot * 0.1f
            drawRect(income.copy(alpha = alpha), Offset(x, size.height - inH), androidx.compose.ui.geometry.Size(bar, inH))
            drawRect(expense.copy(alpha = alpha), Offset(x + bar + 1f, size.height - outH), androidx.compose.ui.geometry.Size(bar, outH))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(points.first().period, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(points.last().period, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    widget.categories.forEach { category ->
        Row(Modifier.fillMaxWidth().clickable(enabled = category.transactionIds.isNotEmpty(),
            onClickLabel = "View transactions") { onDrillDown(category) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(10.dp).background(if (category.name == "Income") income else expense,
                androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(category.name, modifier = Modifier.weight(1f))
            Text(formatMoneyCents(category.spentCents, hideDecimals), fontWeight = FontWeight.SemiBold)
        }
    }
}

private const val SAVED_PAGE_ID = "saved-reports"

@Composable
private fun ViewFilterBar(
    view: ReportViewFilter,
    accounts: List<ReportAccountOption>,
    groups: List<ReportAccountOption>,
    onPreset: (String?) -> Unit,
    onAccounts: (Set<String>) -> Unit,
    onGroups: (Set<String>) -> Unit,
    onOffBudget: (Boolean) -> Unit,
) {
    var dateOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var groupOpen by remember { mutableStateOf(false) }
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            androidx.compose.material3.FilterChip(
                selected = view.datePreset != null, onClick = { dateOpen = true },
                label = { Text(view.datePreset ?: "As saved") },
            )
            DropdownMenu(dateOpen, { dateOpen = false }) {
                DropdownMenuItem(text = { Text("As saved") }, onClick = { onPreset(null); dateOpen = false })
                ReportViewFilter.datePresets.forEach { preset ->
                    DropdownMenuItem(text = { Text(preset) }, onClick = { onPreset(preset); dateOpen = false })
                }
            }
        }
        Box {
            androidx.compose.material3.FilterChip(
                selected = view.accountIds.isNotEmpty(), onClick = { accountOpen = true },
                label = { Text(if (view.accountIds.isEmpty()) "All accounts" else "${view.accountIds.size} accounts") },
            )
            DropdownMenu(accountOpen, { accountOpen = false }) {
                DropdownMenuItem(text = { Text("All accounts") }, onClick = { onAccounts(emptySet()); accountOpen = false })
                accounts.forEach { account ->
                    val checked = account.id in view.accountIds
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(checked, null)
                            Text(account.name)
                        } },
                        onClick = { onAccounts(if (checked) view.accountIds - account.id else view.accountIds + account.id) },
                    )
                }
            }
        }
        Box {
            androidx.compose.material3.FilterChip(
                selected = view.categoryGroupIds.isNotEmpty(), onClick = { groupOpen = true },
                label = { Text(if (view.categoryGroupIds.isEmpty()) "All groups" else "${view.categoryGroupIds.size} groups") },
            )
            DropdownMenu(groupOpen, { groupOpen = false }) {
                DropdownMenuItem(text = { Text("All groups") }, onClick = { onGroups(emptySet()); groupOpen = false })
                groups.forEach { group ->
                    val checked = group.id in view.categoryGroupIds
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(checked, null)
                            Text(group.name)
                        } },
                        onClick = { onGroups(if (checked) view.categoryGroupIds - group.id else view.categoryGroupIds + group.id) },
                    )
                }
            }
        }
        androidx.compose.material3.FilterChip(
            selected = view.includeOffBudget, onClick = { onOffBudget(!view.includeOffBudget) },
            label = { Text("Off-budget") },
            modifier = Modifier.semantics { contentDescription = "Include off-budget accounts" },
        )
    }
}

@Composable
private fun DashboardPicker(
    selected: ReportDashboardPage,
    pages: List<ReportDashboardPage>,
    expanded: Boolean,
    favorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
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
                IconButton(onClick = { onFavoriteChange(!favorite) }) {
                    Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (favorite) "Remove ${selected.name} from favorites" else "Add ${selected.name} to favorites")
                }
                if (pages.size > 1) Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Switch dashboard")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onOpenChange(false) }) {
            pages.forEach { page -> DropdownMenuItem(text = { Text(page.name) }, onClick = { onSelect(page.id) }) }
        }
    }
}

@Composable
private fun WidgetCard(widget: ReportWidget, hideDecimals: Boolean, onDrillDown: (ReportCategory) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(widget.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() })
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
                    TrendChart(widget.points, hideDecimals)
                    PointLabels(widget.points, hideDecimals)
                }
                ReportWidgetKind.CASH_FLOW -> CashFlow(widget.points, hideDecimals)
                ReportWidgetKind.INCOME_EXPENSE -> IncomeExpense(widget, hideDecimals, onDrillDown)
                ReportWidgetKind.SPENDING -> Spending(widget, hideDecimals)
                ReportWidgetKind.MARKDOWN -> Text(widget.markdown.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReportWidgetKind.AGE_OF_MONEY -> AgeOfMoney(widget)
                ReportWidgetKind.FORMULA -> Formula(widget, hideDecimals)
                ReportWidgetKind.CUSTOM_REPORT -> CustomReport(widget, hideDecimals, onDrillDown)
                ReportWidgetKind.CALENDAR -> CalendarReport(widget, hideDecimals)
                ReportWidgetKind.CROSSOVER -> Crossover(widget, hideDecimals)
                ReportWidgetKind.BUDGET_ANALYSIS -> ComparisonSeries(widget.points, "Budgeted", "Spent", hideDecimals)
                ReportWidgetKind.SANKEY -> Sankey(widget, hideDecimals)
                ReportWidgetKind.BALANCE_FORECAST -> {
                    Text(formatMoneyCents(widget.valueCents ?: 0, hideDecimals),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    TrendChart(widget.points, hideDecimals)
                    PointLabels(widget.points, hideDecimals)
                }
                ReportWidgetKind.MONTE_CARLO -> {
                    Text("Projected ${formatMoneyCents(widget.valueCents ?: 0, hideDecimals)}",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    ComparativeTrendChart(widget.points)
                    Text("Median and conservative projection", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ReportWidgetKind.UNSUPPORTED -> Unit
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<ReportPoint>, hideDecimals: Boolean = false, isMoney: Boolean = true) {
    val color = MaterialTheme.colorScheme.primary
    if (points.isEmpty()) return
    var selected by rememberSaveable(points.size) { mutableStateOf<Int?>(null) }
    val active = selected?.takeIf { it in points.indices }
    val minimum = points.minOf { it.primaryCents }
    val maximum = points.maxOf { it.primaryCents }
    val span = (maximum - minimum).coerceAtLeast(1)
    active?.let {
        val point = points[it]
        Text(
            "${point.period.take(10)} · " + if (isMoney) formatMoneyCents(point.primaryCents, hideDecimals) else "${point.primaryCents} days",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
        )
    }
    Canvas(
        Modifier.fillMaxWidth().height(130.dp)
            .semantics { contentDescription = "Trend chart with ${points.size} points. Touch to read a value." }
            .pointerInput(points) {
                fun index(x: Float) = if (points.size <= 1) 0 else
                    (x / size.width * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
                detectTapGestures { selected = index(it.x).takeIf { i -> i != selected } }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures { change, _ ->
                    selected = if (points.size <= 1) 0 else
                        (change.position.x / size.width * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
                }
            },
    ) {
        val step = if (points.size <= 1) 0f else size.width / (points.size - 1)
        fun y(value: Long) = size.height - ((value - minimum).toFloat() / span * size.height)
        points.zipWithNext().forEachIndexed { index, pair ->
            drawLine(color, Offset(step * index, y(pair.first.primaryCents)),
                Offset(step * (index + 1), y(pair.second.primaryCents)), strokeWidth = 5f, cap = StrokeCap.Round)
        }
        active?.let {
            drawLine(color.copy(alpha = 0.4f), Offset(step * it, 0f), Offset(step * it, size.height), strokeWidth = 2f)
            drawCircle(color, 8.dp.toPx() / 1.5f, Offset(step * it, y(points[it].primaryCents)))
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
                    .background(MaterialTheme.colorScheme.primary, PillShape))
                Spacer(Modifier.weight(point.secondaryCents.toFloat().coerceAtLeast(1f) / maximum).height(6.dp)
                    .background(MaterialTheme.colorScheme.tertiary, PillShape))
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
        .background(MaterialTheme.colorScheme.primary, PillShape))
}

@Composable
private fun AgeOfMoney(widget: ReportWidget) {
    val days = widget.valueCents
    Text(if (days == null) "No age available" else "$days days",
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)
    TrendChart(widget.points, isMoney = false)
    if (widget.points.isNotEmpty()) Row(Modifier.fillMaxWidth()) {
        Text(widget.points.first().period.take(7), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(widget.points.last().period.take(7), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Formula(widget: ReportWidget, hideDecimals: Boolean) {
    widget.valueCents?.let {
        Text(formatMoneyCents(it, hideDecimals), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
    } ?: Text(widget.markdown ?: "Formula unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun IntervalBars(points: List<ReportPoint>, hideDecimals: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    var selected by rememberSaveable(points.size) { mutableStateOf<Int?>(null) }
    val active = selected?.takeIf { it in points.indices }
    val maximum = points.maxOf { it.primaryCents.absoluteValue }.coerceAtLeast(1)
    Text(
        active?.let { "${points[it].period} · ${formatMoneyCents(points[it].primaryCents, hideDecimals)}" }
            ?: "Tap a bar for details",
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Canvas(
        Modifier.fillMaxWidth().height(140.dp)
            .semantics { contentDescription = "Bar chart with ${points.size} periods. Tap a bar to read its value." }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val i = (tap.x / size.width * points.size).toInt().coerceIn(0, points.size - 1)
                    selected = i.takeIf { it != selected }
                }
            },
    ) {
        val slot = size.width / points.size
        val barWidth = (slot * 0.7f).coerceAtLeast(1f)
        points.forEachIndexed { i, p ->
            val h = p.primaryCents.absoluteValue.toFloat() / maximum * size.height
            drawRect(if (i == active) color else color.copy(alpha = 0.6f),
                Offset(slot * i + (slot - barWidth) / 2, size.height - h),
                androidx.compose.ui.geometry.Size(barWidth, h))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(points.first().period, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(points.last().period, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val donutHues = floatArrayOf(210f, 20f, 140f, 280f, 50f, 350f, 175f, 320f, 100f, 240f)

@Composable
private fun CustomReport(widget: ReportWidget, hideDecimals: Boolean, onDrillDown: (ReportCategory) -> Unit) {
    widget.subtitle?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(formatMoneyCents(widget.valueCents ?: 0, hideDecimals),
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    val segments = widget.categories.filter { it.spentCents != 0L }
    if (segments.isEmpty() && widget.points.all { it.primaryCents == 0L }) {
        Text("No transactions match this report's filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    when {
        widget.graphType == "DonutGraph" && segments.isNotEmpty() -> DonutSegments(segments, hideDecimals, onDrillDown)
        widget.timeMode && (widget.graphType == "BarGraph" || widget.graphType == "StackedBarGraph") &&
            widget.points.isNotEmpty() -> IntervalBars(widget.points, hideDecimals)
        (widget.graphType == "LineGraph" || widget.graphType == "AreaGraph") && widget.points.size > 1 -> {
            TrendChart(widget.points, hideDecimals)
            PointLabels(widget.points, hideDecimals)
        }
        else -> CategoryBars(widget, hideDecimals, onDrillDown)
    }
}

@Composable
private fun DonutSegments(segments: List<ReportCategory>, hideDecimals: Boolean, onDrillDown: (ReportCategory) -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = segments.indices.map {
        androidx.compose.ui.graphics.Color.hsl(donutHues[it % donutHues.size], 0.55f, if (dark) 0.62f else 0.48f)
    }
    val magnitudes = segments.map { it.spentCents.absoluteValue }
    val total = magnitudes.sum().coerceAtLeast(1)
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val active = selected?.takeIf { it in segments.indices }
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.size(200.dp)
                .semantics { contentDescription = "Donut chart with ${segments.size} segments" }
                .pointerInput(segments) {
                    detectTapGestures { tap ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val dx = tap.x - c.x; val dy = tap.y - c.y
                        var angle = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()) + 90.0
                        if (angle < 0) angle += 360.0
                        var acc = 0.0
                        val hit = magnitudes.indexOfFirst { m -> acc += m * 360.0 / total; angle <= acc }
                        selected = if (hit == selected) null else hit.takeIf { it >= 0 }
                    }
                },
        ) {
            val stroke = 34.dp.toPx()
            val inset = stroke / 2
            var start = -90f
            magnitudes.forEachIndexed { i, m ->
                val sweep = m * 360f / total
                drawArc(colors[i], start, (sweep - 1f).coerceAtLeast(0.5f), false,
                    topLeft = Offset(inset, inset), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(if (i == active) stroke + 8.dp.toPx() else stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(active?.let { segments[it].name } ?: "Total", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(formatMoneyCents(active?.let { segments[it].spentCents } ?: segments.sumOf { it.spentCents }, hideDecimals),
                fontWeight = FontWeight.SemiBold)
            if (active != null) Text("${"%.1f".format(magnitudes[active] * 100.0 / total)}%",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    segments.forEachIndexed { i, segment ->
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "View transactions") { selected = i; onDrillDown(segment) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(10.dp).background(colors[i], androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(segment.name, maxLines = 1, modifier = Modifier.weight(1f),
                fontWeight = if (i == active) FontWeight.Bold else FontWeight.Normal)
            Text("${"%.1f".format(magnitudes[i] * 100.0 / total)}%", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(formatMoneyCents(segment.spentCents, hideDecimals), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CategoryBars(widget: ReportWidget, hideDecimals: Boolean, onDrillDown: ((ReportCategory) -> Unit)? = null) {
    if (widget.categories.isEmpty()) {
        Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val maximum = widget.categories.maxOf { it.spentCents.absoluteValue }.coerceAtLeast(1)
    widget.categories.take(10).forEach { category ->
        Column(Modifier.clickable(enabled = onDrillDown != null && category.transactionIds.isNotEmpty(),
            onClickLabel = "View transactions") { onDrillDown?.invoke(category) }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(category.name, maxLines = 1, modifier = Modifier.weight(1f))
                Text(formatMoneyCents(category.spentCents, hideDecimals), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.fillMaxWidth((category.spentCents.absoluteValue.toFloat() / maximum).coerceIn(0f, 1f))
                .height(7.dp).background(MaterialTheme.colorScheme.primary, PillShape))
        }
    }
}

@Composable
private fun CalendarReport(widget: ReportWidget, hideDecimals: Boolean) {
    val dated = widget.points.mapNotNull { point ->
        runCatching { LocalDate.parse(point.period) }.getOrNull()?.let { it to point }
    }
    if (dated.isEmpty()) {
        Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val month = YearMonth.from(dated.last().first)
    Row(Modifier.fillMaxWidth()) {
        Text(month.month.name.lowercase().replaceFirstChar(Char::uppercase) + " ${month.year}",
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("▲ ${formatMoneyCents(widget.valueCents ?: 0, hideDecimals)}", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("▼ ${formatMoneyCents(widget.comparisonCents ?: 0, hideDecimals)}", color = MaterialTheme.colorScheme.error)
    }
    val values = dated.filter { YearMonth.from(it.first) == month }.associate { it.first.dayOfMonth to it.second }
    Row(Modifier.fillMaxWidth()) {
        listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
            Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    val leading = month.atDay(1).dayOfWeek.value % 7
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { it }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (week + List(7 - week.size) { null }).forEach { day ->
                val point = day?.let(values::get)
                Column(
                    Modifier.weight(1f).height(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(day?.toString().orEmpty(), style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if ((point?.primaryCents ?: 0) > 0) Spacer(Modifier.weight(1f).height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, PillShape))
                        if ((point?.secondaryCents ?: 0) > 0) Spacer(Modifier.weight(1f).height(3.dp)
                            .background(MaterialTheme.colorScheme.error, PillShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun Crossover(widget: ReportWidget, hideDecimals: Boolean) {
    val months = widget.valueCents
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(if (months == null) "Not reached" else "${"%.1f".format(months / 12.0)} years",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("Years to retire", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ComparativeTrendChart(widget.points)
    Text("Investment income vs ${formatMoneyCents(widget.comparisonCents ?: 0, hideDecimals)} monthly expenses",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ComparisonSeries(points: List<ReportPoint>, primary: String, secondary: String, hideDecimals: Boolean) {
    if (points.isEmpty()) { Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    ComparativeTrendChart(points)
    Row(Modifier.fillMaxWidth()) {
        Text("● $primary", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text("● $secondary", color = MaterialTheme.colorScheme.tertiary)
    }
    val last = points.last()
    Text("${last.period.take(7)} · ${formatMoneyCents(last.primaryCents, hideDecimals)} / ${formatMoneyCents(last.secondaryCents, hideDecimals)}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Sankey(widget: ReportWidget, hideDecimals: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(0.8f)) {
            Text("Income", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoneyCents(widget.valueCents ?: 0, hideDecimals), fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            widget.categories.take(8).forEach { category ->
                Row(Modifier.fillMaxWidth()) {
                    Text(category.name, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(formatMoneyCents(category.spentCents, hideDecimals), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ComparativeTrendChart(points: List<ReportPoint>) {
    if (points.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val minimum = points.minOf { minOf(it.primaryCents, it.secondaryCents) }
    val maximum = points.maxOf { maxOf(it.primaryCents, it.secondaryCents) }
    val span = (maximum - minimum).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val step = if (points.size <= 1) 0f else size.width / (points.size - 1)
        fun y(value: Long) = size.height - ((value - minimum).toFloat() / span * size.height)
        points.zipWithNext().forEachIndexed { index, pair ->
            drawLine(primary, Offset(step * index, y(pair.first.primaryCents)),
                Offset(step * (index + 1), y(pair.second.primaryCents)), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(secondary, Offset(step * index, y(pair.first.secondaryCents)),
                Offset(step * (index + 1), y(pair.second.secondaryCents)), strokeWidth = 5f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun EmptyReports() {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No dashboard widgets", style = MaterialTheme.typography.titleMedium)
        Text("Configure a dashboard in Actual Budget and sync it to Actua.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
