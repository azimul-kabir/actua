package com.azimulkabir.actua.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.azimulkabir.actua.model.DrillDownQuery
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportSummary
import com.azimulkabir.actua.model.ReportSummaryKind
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaHeroAmount
import com.azimulkabir.actua.ui.components.ActuaHeroSize
import com.azimulkabir.actua.ui.reports.charts.ChartCard
import com.azimulkabir.actua.ui.reports.charts.ChartTrend
import com.azimulkabir.actua.ui.reports.charts.SankeyChart
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.azimulkabir.actua.ui.reports.charts.calendarBarFraction
import com.azimulkabir.actua.ui.reports.charts.calendarSwipeStep
import com.azimulkabir.actua.ui.reports.charts.clampMonthIndex
import com.azimulkabir.actua.ui.reports.charts.DonutChart
import com.azimulkabir.actua.ui.reports.charts.LineChart
import com.azimulkabir.actua.ui.reports.charts.LineChartSeries
import com.azimulkabir.actua.ui.reports.charts.findCrossoverPosition
import com.azimulkabir.actua.ui.reports.charts.formatCompactMoney
import com.azimulkabir.actua.ui.reports.charts.rememberChartPalette
import com.azimulkabir.actua.ui.reports.charts.ChartTrendDirection
import com.azimulkabir.actua.ui.reports.charts.GroupedBarChart
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.PillShape
import com.azimulkabir.actua.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt

enum class DrillDownSortOrder {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC,
}

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
    loadDrillDownTransactions: (suspend (DrillDownQuery) -> List<Transaction>)? = null,
    onEditTransaction: ((Transaction) -> Unit)? = null,
    onOpenInTransactions: ((DrillDownQuery?, ReportCategory) -> Unit)? = null,
    activeDrillDown: Pair<ReportCategory, String>? = null,
    onDrillDownChange: ((Pair<ReportCategory, String>?) -> Unit)? = null,
    showReportSummary: Boolean = false,
    onShowReportSummaryChange: (Boolean) -> Unit = {},
    scrollToTopRequest: Int = 0,
    initialPageId: String? = null,
    initialPageRequest: Int = 0,
    hasFab: Boolean = true,
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
    var internalDrill by remember { mutableStateOf<Pair<ReportCategory, String>?>(null) }
    val currentDrill = activeDrillDown ?: internalDrill
    fun updateDrill(target: Pair<ReportCategory, String>?) {
        if (onDrillDownChange != null) {
            onDrillDownChange(target)
        } else {
            internalDrill = target
        }
    }
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
            Spacing.screenHorizontal, Spacing.screenHorizontal, Spacing.screenHorizontal,
            if (hasFab) 96.dp else 0.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Reports", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search Actua")
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
                ActuaFormCard {
                    Text(
                        "${unsupported.size} dashboard widget${if (unsupported.size == 1) " is" else "s are"} not available in Actua yet: " +
                            unsupported.joinToString { it.name },
                        Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
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
            items(visible, key = { it.id }) { widget ->
                WidgetCard(widget, hideDecimalPlaces, showReportSummary, onShowReportSummaryChange,
                    onDrillDown = { updateDrill(it to widget.name) })
            }
        }
    }
    if (currentDrill != null) {
        androidx.activity.compose.BackHandler {
            updateDrill(null)
        }
    }
    currentDrill?.let { (segment, reportName) ->
        DrillDownSheet(
            segment = segment,
            reportName = reportName,
            hideDecimals = hideDecimalPlaces,
            load = loadTransactions,
            loadDrillDown = loadDrillDownTransactions,
            onEditTransaction = onEditTransaction,
            onOpenInTransactions = onOpenInTransactions,
            onDismiss = { updateDrill(null) },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DrillDownSheet(
    segment: ReportCategory,
    reportName: String,
    hideDecimals: Boolean,
    load: suspend (List<String>) -> List<Transaction>,
    loadDrillDown: (suspend (DrillDownQuery) -> List<Transaction>)? = null,
    onEditTransaction: ((Transaction) -> Unit)? = null,
    onOpenInTransactions: ((DrillDownQuery?, ReportCategory) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(DrillDownSortOrder.DATE_DESC) }

    val rows by produceState<List<Transaction>?>(null, segment) {
        value = if (segment.drillDownQuery != null && loadDrillDown != null) {
            loadDrillDown(segment.drillDownQuery)
        } else {
            val limit = 200
            load(segment.transactionIds.take(limit))
        }
    }

    val filteredRows = remember(rows, searchQuery, sortOrder) {
        val base = rows.orEmpty().filter { tx ->
            if (searchQuery.isBlank()) true
            else {
                val q = searchQuery.trim().lowercase()
                tx.payee.lowercase().contains(q) ||
                    tx.category.lowercase().contains(q) ||
                    tx.notes.orEmpty().lowercase().contains(q) ||
                    tx.account.lowercase().contains(q) ||
                    formatMoneyCents(tx.amountCents, hideDecimals).lowercase().contains(q) ||
                    tx.amountCents.toString().contains(q)
            }
        }
        when (sortOrder) {
            DrillDownSortOrder.DATE_DESC -> base.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
            DrillDownSortOrder.DATE_ASC -> base.sortedWith(compareBy<Transaction> { it.date }.thenBy { it.id })
            DrillDownSortOrder.AMOUNT_DESC -> base.sortedWith(compareByDescending<Transaction> { it.amountCents }.thenByDescending { it.date })
            DrillDownSortOrder.AMOUNT_ASC -> base.sortedWith(compareBy<Transaction> { it.amountCents }.thenByDescending { it.date })
        }
    }

    val totalAmountCents = remember(rows) {
        rows?.sumOf { it.amountCents } ?: segment.spentCents
    }

    val title = segment.drillDownQuery?.categoryName ?: segment.name
    val totalCount = rows?.size ?: segment.transactionIds.size

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("drillDownSheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val breakdown = if (segment.incomeCents != null && segment.expenseCents != null) {
                        "In ${formatMoneyCents(segment.incomeCents, hideDecimals)} · Out ${formatMoneyCents(segment.expenseCents, hideDecimals)} (${formatMoneyCents(totalAmountCents, hideDecimals)} net)"
                    } else {
                        formatMoneyCents(totalAmountCents, hideDecimals)
                    }
                    Text(
                        "$reportName · $totalCount transaction${if (totalCount == 1) "" else "s"} · $breakdown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onOpenInTransactions != null) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            onOpenInTransactions(segment.drillDownQuery, segment)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("openInTransactionsButton"),
                    ) {
                        Icon(
                            Icons.Outlined.OpenInFull,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Open in Transactions")
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search transactions") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search") },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
                        }
                    }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drillDownSearchField"),
            )

            Spacer(Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sort:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val isDate = sortOrder == DrillDownSortOrder.DATE_DESC || sortOrder == DrillDownSortOrder.DATE_ASC
                FilterChip(
                    selected = isDate,
                    onClick = {
                        sortOrder = if (sortOrder == DrillDownSortOrder.DATE_DESC) {
                            DrillDownSortOrder.DATE_ASC
                        } else {
                            DrillDownSortOrder.DATE_DESC
                        }
                    },
                    label = {
                        Text(if (sortOrder == DrillDownSortOrder.DATE_ASC) "Date ↑" else "Date ↓")
                    },
                    modifier = Modifier.testTag("sortDateChip"),
                )
                val isAmount = sortOrder == DrillDownSortOrder.AMOUNT_DESC || sortOrder == DrillDownSortOrder.AMOUNT_ASC
                FilterChip(
                    selected = isAmount,
                    onClick = {
                        sortOrder = if (sortOrder == DrillDownSortOrder.AMOUNT_DESC) {
                            DrillDownSortOrder.AMOUNT_ASC
                        } else {
                            DrillDownSortOrder.AMOUNT_DESC
                        }
                    },
                    label = {
                        Text(if (sortOrder == DrillDownSortOrder.AMOUNT_ASC) "Amount ↑" else "Amount ↓")
                    },
                    modifier = Modifier.testTag("sortAmountChip"),
                )
            }

            if (segment.drillDownQuery == null && segment.transactionIds.size > 200) {
                Text(
                    "Showing the first 200.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xs))

        val loaded = rows
        if (loaded == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredRows.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isNotBlank()) "No transactions matching \"$searchQuery\"" else "No transactions",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drillDownTransactionList"),
            ) {
                items(filteredRows, key = { it.id }) { tx ->
                    TransactionRow(
                        tx,
                        hideDecimals,
                        showDate = true,
                        showAccount = true,
                        onClick = { onEditTransaction?.invoke(tx) },
                        onLongClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomeExpense(widget: ReportWidget, hideDecimals: Boolean, onDrillDown: (ReportCategory) -> Unit) {
    val income = MaterialTheme.colorScheme.primary
    val expense = MaterialTheme.colorScheme.error
    val points = widget.points
    widget.subtitle?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("Net ${formatMoneyCents(widget.valueCents ?: 0, hideDecimals)}",
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    if (points.isEmpty() || (points.all { it.primaryCents == 0L && it.secondaryCents == 0L } && (widget.categories.isEmpty() || widget.categories.all { it.spentCents == 0L }))) {
        Text("No income or expenses in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    GroupedBarChart(
        points = widget.points,
        hideDecimals = hideDecimals,
        onDrillDown = onDrillDown,
        primaryLabel = "Income",
        secondaryLabel = "Expense",
        chartContentDescription = "Income and expenses by month, ${points.size} months. Tap a month for details.",
    )
    widget.categories.forEach { category ->
        Row(Modifier.fillMaxWidth().clickable(enabled = category.drillDownQuery != null || category.transactionIds.isNotEmpty(),
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
    var anchorWidth by remember { mutableStateOf(0) }
    val anchorWidthDp = with(LocalDensity.current) { anchorWidth.toDp() }
    Box {
        ActuaFormCard(Modifier.onSizeChanged { anchorWidth = it.width }) {
            ActuaFormRow(
                icon = Icons.Outlined.SpaceDashboard,
                label = "Dashboard",
                value = selected.name,
                trailing = {
                    IconButton(onClick = { onFavoriteChange(!favorite) }) {
                        Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (favorite) "Remove ${selected.name} from favorites" else "Add ${selected.name} to favorites")
                    }
                    if (pages.size > 1) Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Switch dashboard",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                onClick = if (pages.size > 1) ({ onOpenChange(true) }) else null,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onOpenChange(false) },
            modifier = Modifier.width(anchorWidthDp),
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            pages.forEach { page ->
                DropdownMenuItem(
                    text = {
                        Text(page.name, style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (page.id == selected.id) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    onClick = { onSelect(page.id) },
                )
            }
        }
    }
}

internal data class WidgetHeaderData(
    val headlineValue: String? = null,
    val caption: String? = null,
    val trend: ChartTrend? = null,
)

internal fun calculateWidgetHeader(widget: ReportWidget, hideDecimals: Boolean): WidgetHeaderData {
    return when (widget.kind) {
        ReportWidgetKind.NET_WORTH -> WidgetHeaderData(
            headlineValue = formatMoneyCents(widget.valueCents ?: 0, hideDecimals),
        )
        ReportWidgetKind.AGE_OF_MONEY -> {
            val headlineValue = widget.valueCents?.let { "$it days" } ?: "No age available"
            val trend = if (widget.points.size >= 2) {
                val latest = widget.points.last().primaryCents
                val previous = widget.points[widget.points.size - 2].primaryCents
                when {
                    latest > previous -> ChartTrend(ChartTrendDirection.UP, isPositiveGood = true)
                    latest < previous -> ChartTrend(ChartTrendDirection.DOWN, isPositiveGood = true)
                    else -> ChartTrend(ChartTrendDirection.NEUTRAL, isPositiveGood = true)
                }
            } else null
            WidgetHeaderData(
                headlineValue = headlineValue,
                trend = trend,
            )
        }
        ReportWidgetKind.CROSSOVER -> {
            val months = widget.valueCents
            val headlineValue = if (months == null) "Not reached" else "${"%.1f".format(months / 12.0)} years"
            WidgetHeaderData(
                headlineValue = headlineValue,
                caption = "Years to retire",
            )
        }
        ReportWidgetKind.BALANCE_FORECAST -> {
            val headlineValue = formatMoneyCents(widget.valueCents ?: 0, hideDecimals)
            val caption = if (widget.comparisonCents != null && widget.comparisonCents != widget.valueCents) {
                "Low: ${formatMoneyCents(widget.comparisonCents, hideDecimals)}"
            } else null
            WidgetHeaderData(
                headlineValue = headlineValue,
                caption = caption,
            )
        }
        ReportWidgetKind.MONTE_CARLO -> {
            val headlineValue = "${"%.1f".format(widget.percentage ?: 0.0)}%"
            WidgetHeaderData(
                headlineValue = headlineValue,
                caption = widget.subtitle,
            )
        }
        else -> WidgetHeaderData()
    }
}

@Composable
private fun WidgetCard(
    widget: ReportWidget,
    hideDecimals: Boolean,
    showSummary: Boolean,
    onShowSummaryChange: (Boolean) -> Unit,
    onDrillDown: (ReportCategory) -> Unit,
) {
    val header = calculateWidgetHeader(widget, hideDecimals)

    ChartCard(
        title = widget.name,
        headlineValue = header.headlineValue,
        caption = header.caption,
        trend = header.trend,
    ) {
        when (widget.kind) {
            ReportWidgetKind.SUMMARY -> ActuaHeroAmount(
                amount = widget.percentage?.let { "${"%.2f".format(it)}%" }
                    ?: formatMoneyCents(widget.valueCents ?: 0, hideDecimals),
                size = ActuaHeroSize.Medium,
            )
            ReportWidgetKind.NET_WORTH -> {
                TrendChart(widget.points, hideDecimals, onDrillDown = onDrillDown)
            }
            ReportWidgetKind.CASH_FLOW -> CashFlow(widget.points, hideDecimals, onDrillDown)
            ReportWidgetKind.INCOME_EXPENSE -> IncomeExpense(widget, hideDecimals, onDrillDown)
            ReportWidgetKind.SPENDING -> Spending(widget, hideDecimals, onDrillDown)
            ReportWidgetKind.MARKDOWN -> MarkdownWidget(widget.markdown.orEmpty(), widget.textAlign)
            ReportWidgetKind.AGE_OF_MONEY -> AgeOfMoney(widget)
            ReportWidgetKind.FORMULA -> Formula(widget, hideDecimals)
            ReportWidgetKind.CUSTOM_REPORT -> {
                CustomReport(widget, hideDecimals, onDrillDown)
                widget.summary?.let { CustomReportSummary(it, hideDecimals, showSummary, onShowSummaryChange) }
            }
            ReportWidgetKind.CALENDAR -> CalendarReport(widget, hideDecimals, onDrillDown)
            ReportWidgetKind.CROSSOVER -> Crossover(widget, hideDecimals)
            ReportWidgetKind.BUDGET_ANALYSIS -> BudgetAnalysis(widget, hideDecimals)
            ReportWidgetKind.SANKEY -> Sankey(widget, hideDecimals, onDrillDown = onDrillDown)
            ReportWidgetKind.BALANCE_FORECAST -> {
                TrendChart(
                    widget.points, hideDecimals, onDrillDown = onDrillDown,
                    lowValue = widget.comparisonCents,
                    description = "Balance forecast with ${widget.points.size} months. The dashed part is a forecast.",
                )
                widget.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ReportWidgetKind.MONTE_CARLO -> {
                ComparativeTrendChart(widget.points, "Median", "Conservative", hideDecimals)
            }
            ReportWidgetKind.MISSING_REPORT -> Text(widget.markdown.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReportWidgetKind.UNSUPPORTED -> Unit
        }
    }
}

@Composable
private fun TrendChart(
    points: List<ReportPoint>,
    hideDecimals: Boolean = false,
    isMoney: Boolean = true,
    onDrillDown: ((ReportCategory) -> Unit)? = null,
    lowValue: Long? = null,
    description: String = "Trend chart with ${points.size} points. Touch to read a value.",
) {
    if (points.isEmpty()) {
        Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val palette = rememberChartPalette()
    val forecastFrom = points.indexOfFirst { it.isForecast }.takeIf { it >= 0 }
    fun drillable(point: ReportPoint) = point.drillDownQuery != null || point.transactionIds.isNotEmpty()
    LineChart(
        periods = points.map { it.period },
        series = listOf(
            LineChartSeries(
                label = "Value",
                color = if (forecastFrom != null) palette.semantic.forecast else palette.semantic.balance,
                values = points.map { it.primaryCents },
                fill = true,
                forecastFromIndex = forecastFrom,
            ),
        ),
        formatValue = { if (isMoney) formatMoneyCents(it, hideDecimals) else "$it days" },
        formatAxis = { if (isMoney) formatCompactMoney(it, hideDecimals = true) else "$it d" },
        contentDescription = description,
        lowValue = lowValue,
        canDrill = { drillable(points[it]) },
        onDrillDown = onDrillDown?.let { drill ->
            { i ->
                val point = points[i]
                drill(ReportCategory(point.period.take(10), point.primaryCents, point.transactionIds, drillDownQuery = point.drillDownQuery))
            }
        },
    )
}

@Composable
private fun CashFlow(points: List<ReportPoint>, hideDecimals: Boolean, onDrillDown: (ReportCategory) -> Unit) {
    if (points.isEmpty()) { Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    GroupedBarChart(
        points = points,
        hideDecimals = hideDecimals,
        onDrillDown = onDrillDown,
        primaryLabel = "Income",
        secondaryLabel = "Expense",
        chartContentDescription = "Cash flow chart with ${points.size} periods. Tap a period to read income and expense.",
    )
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Spending(widget: ReportWidget, hideDecimals: Boolean, onDrillDown: (ReportCategory) -> Unit) {
    val current = widget.valueCents ?: 0
    val comparison = widget.comparisonCents ?: 0
    val maximum = max(current.coerceAtLeast(0), comparison.coerceAtLeast(0)).coerceAtLeast(1)
    val delta = current - comparison
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(
            Modifier.weight(1f).clickable(enabled = widget.valueDrillDownQuery != null || widget.valueTransactionIds.isNotEmpty(), onClickLabel = "View transactions") {
                onDrillDown(ReportCategory("This month", current, widget.valueTransactionIds, drillDownQuery = widget.valueDrillDownQuery))
            },
        ) {
            Text("This month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoneyCents(current, hideDecimals), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.clickable(enabled = widget.comparisonDrillDownQuery != null || widget.comparisonTransactionIds.isNotEmpty(), onClickLabel = "View transactions") {
                onDrillDown(ReportCategory("Comparison", comparison, widget.comparisonTransactionIds, drillDownQuery = widget.comparisonDrillDownQuery))
            },
        ) {
            Text("vs comparison", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoneyCents(comparison, hideDecimals), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (delta != 0L) Text(
        (if (delta > 0) "↑ " else "↓ ") + "${formatMoneyCents(delta.absoluteValue, hideDecimals)} " +
            if (delta > 0) "more spent" else "less spent",
        color = if (delta > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val currentFraction = (current.toFloat() / maximum).coerceIn(0.02f, 0.98f)
        Spacer(Modifier.weight(currentFraction).height(9.dp)
            .background(MaterialTheme.colorScheme.primary, PillShape))
        Spacer(Modifier.weight(1f - currentFraction).height(9.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, PillShape))
    }
}

@Composable
private fun AgeOfMoney(widget: ReportWidget) {
    TrendChart(widget.points, isMoney = false, description = "Age of money over ${widget.points.size} periods. Touch to read the age.")
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

@Composable
private fun StackedIntervalBars(points: List<ReportPoint>, hideDecimals: Boolean, onDrillDown: (ReportCategory) -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val names = points.firstOrNull { it.segments.isNotEmpty() }?.segments?.map { it.name }.orEmpty()
    val colorByName = names.withIndex().associate { (i, name) ->
        name to androidx.compose.ui.graphics.Color.hsl(donutHues[i % donutHues.size], 0.55f, if (dark) 0.62f else 0.48f)
    }
    val fallbackColor = MaterialTheme.colorScheme.outlineVariant
    var selected by rememberSaveable(points.size) { mutableStateOf<Int?>(null) }
    val active = selected?.takeIf { it in points.indices }
    val maximum = points.maxOf { it.primaryCents.absoluteValue }.coerceAtLeast(1)

    active?.let { i ->
        val point = points[i]
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(point.period, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                point.segments.filter { it.spentCents != 0L }.forEach { segment ->
                    Text("${segment.name}: ${formatMoneyCents(segment.spentCents, hideDecimals)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } ?: Text("Tap a bar for details, or a segment to view its transactions",
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Canvas(
        Modifier.fillMaxWidth().height(160.dp)
            .semantics { contentDescription = "Stacked bar chart with ${points.size} periods. Tap a bar for its breakdown, or a segment to view its transactions." }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val i = (tap.x / size.width * points.size).toInt().coerceIn(0, points.size - 1)
                    val point = points[i]
                    var y = size.height.toFloat()
                    var hit: ReportCategory? = null
                    for (segment in point.segments) {
                        val h = segment.spentCents.absoluteValue.toFloat() / maximum * size.height
                        if (tap.y in (y - h)..y) { hit = segment; break }
                        y -= h
                    }
                    if (hit != null && (hit.drillDownQuery != null || hit.transactionIds.isNotEmpty())) onDrillDown(hit) else selected = i.takeIf { it != selected }
                }
            },
    ) {
        val slot = size.width / points.size
        val barWidth = (slot * 0.7f).coerceAtLeast(1f)
        points.forEachIndexed { i, point ->
            val alpha = if (active == null || active == i) 1f else 0.45f
            var y = size.height
            point.segments.forEach { segment ->
                val h = segment.spentCents.absoluteValue.toFloat() / maximum * size.height
                if (h > 0f) {
                    val color = colorByName[segment.name] ?: fallbackColor
                    drawRect(color.copy(alpha = alpha), Offset(slot * i + (slot - barWidth) / 2, y - h),
                        androidx.compose.ui.geometry.Size(barWidth, h))
                }
                y -= h
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(points.first().period, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(points.last().period, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    names.forEach { name ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(10.dp).background(colorByName[name] ?: fallbackColor,
                androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(name, maxLines = 1, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val donutHues = floatArrayOf(210f, 20f, 140f, 280f, 50f, 350f, 175f, 320f, 100f, 240f)

/**
 * Upstream's custom-report Summary panel (`ReportSummary.tsx`): the range total and its average
 * per interval, behind the same device-local show/hide preference as the PWA's summary toggle.
 */
@Composable
private fun CustomReportSummary(
    summary: ReportSummary,
    hideDecimals: Boolean,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
) {
    if (visible) {
        val (totalLabel, averageLabel) = when (summary.kind) {
            ReportSummaryKind.SPENDING -> "Total spending" to "Average spending"
            ReportSummaryKind.DEPOSITS -> "Total deposits" to "Average deposit"
            ReportSummaryKind.BUDGETED -> "Total budgeted" to "Average budgeted"
            ReportSummaryKind.NET_PAYMENT -> "Net payment" to "Average net"
            ReportSummaryKind.NET_DEPOSIT -> "Net deposit" to "Average net"
        }
        val per = when (summary.interval) {
            "Daily" -> "Per day"
            "Weekly" -> "Per week"
            "Yearly" -> "Per year"
            else -> "Per month"
        }
        Row(Modifier.fillMaxWidth().testTag("customReportSummary"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryFigure(totalLabel, formatMoneyCents(summary.totalCents, hideDecimals), null, Modifier.weight(1f))
            SummaryFigure(averageLabel, formatMoneyCents(summary.averageCents, hideDecimals), per, Modifier.weight(1f))
        }
    }
    androidx.compose.material3.TextButton(onClick = { onVisibleChange(!visible) }) {
        Text(if (visible) "Hide summary" else "Show summary")
    }
}

@Composable
private fun SummaryFigure(label: String, amount: String, caption: String?, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        caption?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CustomReport(widget: ReportWidget, hideDecimals: Boolean, onDrillDown: (ReportCategory) -> Unit) {
    widget.subtitle?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val segments = widget.categories.filter { it.spentCents != 0L }
    if (segments.isEmpty() && widget.points.all { it.primaryCents == 0L }) {
        Text("No transactions match this report's filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    when {
        widget.graphType == "DonutGraph" && segments.isNotEmpty() -> DonutChart(segments, hideDecimals, onDrillDown)
        widget.timeMode && widget.graphType == "StackedBarGraph" && widget.points.any { it.segments.isNotEmpty() } ->
            StackedIntervalBars(widget.points, hideDecimals, onDrillDown)
        widget.timeMode && (widget.graphType == "BarGraph" || widget.graphType == "StackedBarGraph") &&
            widget.points.isNotEmpty() -> IntervalBars(widget.points, hideDecimals)
        (widget.graphType == "LineGraph" || widget.graphType == "AreaGraph") && widget.points.size > 1 -> {
            TrendChart(widget.points, hideDecimals)
        }
        else -> CategoryBars(widget, hideDecimals, onDrillDown)
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
        Column(Modifier.clickable(enabled = onDrillDown != null && (category.drillDownQuery != null || category.transactionIds.isNotEmpty()),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarReport(widget: ReportWidget, hideDecimals: Boolean, onDrillDown: (ReportCategory) -> Unit) {
    val dated = remember(widget.points) {
        widget.points.mapNotNull { point -> runCatching { LocalDate.parse(point.period) }.getOrNull()?.let { it to point } }
    }
    if (dated.isEmpty()) {
        Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val palette = rememberChartPalette()
    val months = remember(dated) { dated.map { YearMonth.from(it.first) }.distinct().sorted() }
    var monthIndex by rememberSaveable(months) { mutableStateOf(months.lastIndex) }
    val month = months[monthIndex.coerceIn(months.indices)]
    val values = remember(dated, month) {
        dated.filter { YearMonth.from(it.first) == month }.associate { it.first.dayOfMonth to it.second }
    }
    val monthIncome = values.values.sumOf { it.primaryCents }
    val monthExpense = values.values.sumOf { it.secondaryCents }
    var selectedDay by rememberSaveable(month) { mutableStateOf<Int?>(null) }
    var breakdownDay by rememberSaveable(month) { mutableStateOf<Int?>(null) }
    val today = remember { LocalDate.now() }
    fun stepMonth(step: Int) {
        monthIndex = clampMonthIndex(monthIndex, step, months.lastIndex)
        selectedDay = null
        breakdownDay = null
    }
    val monthName = remember(month) { month.month.name.lowercase().replaceFirstChar(Char::uppercase) }
    fun dayCategory(day: Int, point: ReportPoint) = ReportCategory(
        "$monthName $day", point.primaryCents - point.secondaryCents, point.transactionIds,
        incomeCents = point.primaryCents, expenseCents = point.secondaryCents, drillDownQuery = point.drillDownQuery,
    )

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { stepMonth(-1) }, enabled = monthIndex > 0) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(monthName + " ${month.year}",
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { stepMonth(1) }, enabled = monthIndex < months.lastIndex) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text("▲ ${formatMoneyCents(monthIncome, hideDecimals)}", color = palette.semantic.income, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Text("▼ ${formatMoneyCents(monthExpense, hideDecimals)}", color = palette.semantic.expense, fontWeight = FontWeight.SemiBold)
    }
    selectedDay?.let { day ->
        val point = values[day]
        val dayLabel = "$monthName $day"
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small,
            modifier = Modifier.clickable(
                enabled = point != null && (point.drillDownQuery != null || point.transactionIds.isNotEmpty()),
                onClickLabel = "see transactions",
            ) { point?.let { onDrillDown(dayCategory(day, it)) } },
        ) {
            Text(
                "$dayLabel · In ${formatMoneyCents(point?.primaryCents ?: 0, hideDecimals)} · Out ${formatMoneyCents(point?.secondaryCents ?: 0, hideDecimals)}",
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    breakdownDay?.let { day ->
        val point = values[day]
        if (point != null) DayBreakdown(
            title = "$monthName $day", point = point, hideDecimals = hideDecimals,
            incomeColor = palette.semantic.income, expenseColor = palette.semantic.expense,
            onViewTransactions = { onDrillDown(dayCategory(day, point)) },
            onDismiss = { breakdownDay = null },
        )
    }
    Row(Modifier.fillMaxWidth()) {
        val weekDays = listOf("S", "M", "T", "W", "T", "F", "S")
        (0 until 7).map { weekDays[(it + widget.weekStart) % 7] }.forEach { day ->
            Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    val dayMax = values.values.maxOfOrNull { max(it.primaryCents, it.secondaryCents) } ?: 0L
    val leading = (month.atDay(1).dayOfWeek.value % 7 - widget.weekStart + 7) % 7
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { it }
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    Column(
        Modifier.fillMaxWidth()
            .semantics { contentDescription = "Calendar for $monthName ${month.year}. Swipe left or right to change month." }
            .pointerInput(months) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { stepMonth(calendarSwipeStep(total, swipeThreshold)) },
                    onDragCancel = { total = 0f },
                ) { _, delta -> total += delta }
            },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (week + List(7 - week.size) { null }).forEach { day ->
                    val point = day?.let(values::get)
                    val hasTransactions = point != null && (point.drillDownQuery != null || point.transactionIds.isNotEmpty())
                    val inFormatted = formatMoneyCents(point?.primaryCents ?: 0, hideDecimals)
                    val outFormatted = formatMoneyCents(point?.secondaryCents ?: 0, hideDecimals)
                    val isToday = day != null && month.atDay(day) == today
                    val dayContentDescription = if (day != null) {
                        "$monthName $day, in $inFormatted, out $outFormatted" + if (isToday) ", today" else ""
                    } else null
                    val clickModifier = if (day != null) {
                        Modifier.combinedClickable(
                            onClickLabel = if (hasTransactions) "see transactions" else null,
                            onLongClickLabel = if (point != null) "Show breakdown" else null,
                            onLongClick = { if (point != null) { selectedDay = day; breakdownDay = day } },
                        ) {
                            if (point != null && hasTransactions) {
                                selectedDay = day
                                onDrillDown(dayCategory(day, point))
                            } else {
                                selectedDay = day.takeIf { it != selectedDay }
                            }
                        }
                    } else Modifier
                    val semanticsModifier = if (dayContentDescription != null) {
                        Modifier.semantics { contentDescription = dayContentDescription }
                    } else Modifier
                    Column(
                        Modifier.weight(1f).height(46.dp)
                            .background(
                                if (day != null && selectedDay == day) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.shapes.small,
                            )
                            .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                            .then(clickModifier)
                            .then(semanticsModifier)
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            day?.toString().orEmpty(), style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            val incomeFraction = calendarBarFraction(point?.primaryCents ?: 0, dayMax)
                            val expenseFraction = calendarBarFraction(point?.secondaryCents ?: 0, dayMax)
                            if (incomeFraction > 0f) Spacer(Modifier.width(6.dp).fillMaxHeight(incomeFraction)
                                .background(palette.semantic.income, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                            if (expenseFraction > 0f) Spacer(Modifier.width(6.dp).fillMaxHeight(expenseFraction)
                                .background(palette.semantic.expense, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBreakdown(
    title: String, point: ReportPoint, hideDecimals: Boolean,
    incomeColor: androidx.compose.ui.graphics.Color, expenseColor: androidx.compose.ui.graphics.Color,
    onViewTransactions: () -> Unit, onDismiss: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$title breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val incomes = point.segments.mapNotNull { c -> c.incomeCents?.takeIf { it > 0 }?.let { c.name to it } }
            val expenses = point.segments.mapNotNull { c -> c.expenseCents?.takeIf { it > 0 }?.let { c.name to it } }
            if (incomes.isNotEmpty()) breakdownSection("Income", incomeColor, point.primaryCents, incomes, hideDecimals)
            if (expenses.isNotEmpty()) breakdownSection("Expense", expenseColor, point.secondaryCents, expenses, hideDecimals)
            if (point.segments.isEmpty()) Text(
                "In ${formatMoneyCents(point.primaryCents, hideDecimals)} · Out ${formatMoneyCents(point.secondaryCents, hideDecimals)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
                androidx.compose.material3.TextButton(
                    onClick = onViewTransactions,
                    enabled = point.drillDownQuery != null || point.transactionIds.isNotEmpty(),
                ) { Text("View transactions") }
            }
        }
    }
}

@Composable
private fun breakdownSection(
    label: String, color: androidx.compose.ui.graphics.Color, total: Long,
    amounts: List<Pair<String, Long>>, hideDecimals: Boolean,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(formatMoneyCents(total, hideDecimals), color = color, fontWeight = FontWeight.SemiBold)
    }
    amounts.forEach { (name, cents) ->
        Row(Modifier.fillMaxWidth().padding(start = 8.dp)) {
            Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
            Text(formatMoneyCents(cents, hideDecimals), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Crossover(widget: ReportWidget, hideDecimals: Boolean) {
    val points = widget.points
    val palette = rememberChartPalette()
    val incomes = points.map { it.primaryCents }
    val expenses = points.map { it.secondaryCents }
    val crossing = findCrossoverPosition(incomes, expenses)
    LineChart(
        periods = points.map { it.period },
        series = listOf(
            LineChartSeries("Investment income", palette.semantic.income, incomes),
            LineChartSeries("Monthly expenses", palette.semantic.expense, expenses),
        ),
        formatValue = { formatMoneyCents(it, hideDecimals) },
        formatAxis = { formatCompactMoney(it, hideDecimals = true) },
        contentDescription = "Crossover chart with ${points.size} points" +
            (crossing?.let { ", lines meet around ${points[it.roundToInt().coerceIn(0, points.lastIndex)].period.take(7)}" } ?: ""),
        crossoverPosition = crossing,
        crossoverLabel = crossing?.let { "Crossover ${points[it.roundToInt().coerceIn(0, points.lastIndex)].period.take(7)}" },
    )
    Text("Investment income vs ${formatMoneyCents(widget.comparisonCents ?: 0, hideDecimals)} monthly expenses",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BudgetAnalysis(widget: ReportWidget, hideDecimals: Boolean) {
    ComparisonSeries(widget.points, "Budgeted", "Spent", hideDecimals)
    widget.balanceCents?.let {
        Text(
            "Balance: ${formatMoneyCents(it, hideDecimals)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComparisonSeries(points: List<ReportPoint>, primary: String, secondary: String, hideDecimals: Boolean) {
    if (points.isEmpty()) { Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    ComparativeTrendChart(points, primary, secondary, hideDecimals)
    val last = points.last()
    Text("${last.period.take(7)} · ${formatMoneyCents(last.primaryCents, hideDecimals)} / ${formatMoneyCents(last.secondaryCents, hideDecimals)}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Sankey(
    widget: ReportWidget,
    hideDecimals: Boolean,
    onDrillDown: ((ReportCategory) -> Unit)? = null,
) = SankeyChart(
    income = widget.valueCents ?: 0,
    categories = widget.categories,
    incomeCategories = widget.incomeCategories,
    subtitle = widget.subtitle,
    hideDecimals = hideDecimals,
    onDrillDown = onDrillDown,
)

@Composable
private fun ComparativeTrendChart(
    points: List<ReportPoint>,
    primaryLabel: String = "Primary",
    secondaryLabel: String = "Secondary",
    hideDecimals: Boolean = false,
) {
    if (points.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val scrubColor = MaterialTheme.colorScheme.outline
    var selected by rememberSaveable(points.size) { mutableStateOf<Int?>(null) }
    val active = selected?.takeIf { it in points.indices }
    val minimum = points.minOf { minOf(it.primaryCents, it.secondaryCents) }
    val maximum = points.maxOf { maxOf(it.primaryCents, it.secondaryCents) }
    val span = (maximum - minimum).coerceAtLeast(1)

    active?.let {
        val point = points[it]
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(point.period.take(10), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("$primaryLabel ${formatMoneyCents(point.primaryCents, hideDecimals)} · " +
                    "$secondaryLabel ${formatMoneyCents(point.secondaryCents, hideDecimals)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Canvas(
        Modifier.fillMaxWidth().height(140.dp)
            .semantics { contentDescription = "Comparison chart with ${points.size} points. Touch to read $primaryLabel and $secondaryLabel." }
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
        val dashed = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val lineY = size.height * fraction
            drawLine(gridColor, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 1.5f, pathEffect = dashed)
        }
        points.zipWithNext().forEachIndexed { index, pair ->
            drawLine(primary, Offset(step * index, y(pair.first.primaryCents)),
                Offset(step * (index + 1), y(pair.second.primaryCents)), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(secondary, Offset(step * index, y(pair.first.secondaryCents)),
                Offset(step * (index + 1), y(pair.second.secondaryCents)), strokeWidth = 5f, cap = StrokeCap.Round)
        }
        active?.let {
            drawLine(scrubColor.copy(alpha = 0.4f), Offset(step * it, 0f), Offset(step * it, size.height), strokeWidth = 2f)
            drawCircle(primary, 7.dp.toPx() / 1.5f, Offset(step * it, y(points[it].primaryCents)))
            drawCircle(secondary, 7.dp.toPx() / 1.5f, Offset(step * it, y(points[it].secondaryCents)))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(primary, primaryLabel)
        LegendDot(secondary, secondaryLabel)
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
