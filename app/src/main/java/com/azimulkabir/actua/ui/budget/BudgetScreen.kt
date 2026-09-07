package com.azimulkabir.actua.ui.budget

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.components.CalculatorAmountState
import com.azimulkabir.actua.ui.components.CompactCalculatorPad
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.components.formatStoredDate
import com.azimulkabir.actua.ui.components.RenameDialog
import com.azimulkabir.actua.ui.components.NewCategoryDialog
import com.azimulkabir.actua.ui.transactions.TransactionDetailsSheet
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue

private val sampleGroups = listOf(
    BudgetGroup("Monthly bills", listOf(
        BudgetCategory("Rent", 35_000, 35_000),
        BudgetCategory("Electricity", 3_500, 2_700),
        BudgetCategory("Internet", 1_500, 1_500),
        BudgetCategory("Mobile phone", 1_000, 720),
    )),
    BudgetGroup("Daily spending", listOf(
        BudgetCategory("Groceries", 8_000, 4_760),
        BudgetCategory("Dining", 4_000, 1_900),
        BudgetCategory("Transport", 5_000, 4_100),
        BudgetCategory("Household", 2_500, 850),
    )),
    BudgetGroup("Quality of life", listOf(
        BudgetCategory("Health & fitness", 3_000, 1_250),
        BudgetCategory("Entertainment", 2_500, 2_800),
        BudgetCategory("Personal care", 2_000, 620),
    )),
    BudgetGroup("Savings goals", listOf(
        BudgetCategory("Emergency fund", 10_000, 0),
        BudgetCategory("Travel", 6_000, 0),
    )),
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    modifier: Modifier = Modifier,
    groups: List<BudgetGroup> = sampleGroups,
    overview: BudgetOverview = BudgetOverview(1_245_000, 8_400_000, -5_620_000, 2_780_000),
    month: String = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date()),
    onMonthChange: (String) -> Unit = {},
    hideDecimalPlaces: Boolean = false,
    showHidden: Boolean = false,
    onShowHiddenChange: (Boolean) -> Unit = {},
    showSpent: Boolean = true,
    onShowSpentChange: (Boolean) -> Unit = {},
    showProgressBars: Boolean = false,
    onShowProgressBarsChange: (Boolean) -> Unit = {},
    budgetView: String = "Table",
    onBudgetViewChange: (String) -> Unit = {},
    showOverview: Boolean = true,
    onShowOverviewChange: (Boolean) -> Unit = {},
    showGroupTotals: Boolean = true,
    onShowGroupTotalsChange: (Boolean) -> Unit = {},
    hideFullySpent: Boolean = false,
    onHideFullySpentChange: (Boolean) -> Unit = {},
    onSetCategoryHidden: (String, String, Boolean) -> Boolean = { _, _, _ -> false },
    onSetGroupHidden: (String, Boolean) -> Boolean = { _, _ -> false },
    onRenameCategory: (String, String, String) -> Unit = { _, _, _ -> },
    onRenameGroup: (String, String) -> Unit = { _, _ -> },
    onCreateCategory: (String, String) -> Unit = { _, _ -> },
    onCreateGroup: (String) -> Unit = {},
    onShowCategoryTransactions: (String, Boolean) -> Unit = { _, _ -> },
    onTransferBudget: (String?, String?, String?, String?, Long) -> Unit = { _, _, _, _, _ -> },
    onSetBudgetAmount: (String, String, Long) -> Unit = { _, _, _ -> },
    onSetCategoryNote: (String, String) -> Unit = { _, _ -> },
    onSetCategoryCarryover: (String, Boolean) -> Unit = { _, _ -> },
    onSearch: () -> Unit = {},
    transactions: List<Transaction> = emptyList(),
    onDeleteCategory: (String, String) -> Boolean = { _, _ -> false },
    onEditTransaction: (Transaction) -> Unit = {},
    onDeleteTransaction: (Transaction) -> Unit = {},
) {
    val context = LocalContext.current
    val budgetUiPreferences = remember(context) {
        context.applicationContext.getSharedPreferences("budget_ui_preferences", android.content.Context.MODE_PRIVATE)
    }
    var selectedCategory by remember { mutableStateOf<BudgetCategory?>(null) }
    var selectedGroup by remember { mutableStateOf<BudgetGroup?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var collapsedGroups by remember {
        mutableStateOf(budgetUiPreferences.getStringSet("collapsed_groups", emptySet()).orEmpty().toSet())
    }
    fun saveCollapsedGroups(value: Set<String>) {
        collapsedGroups = value
        budgetUiPreferences.edit().putStringSet("collapsed_groups", value).apply()
    }
    var optionsExpanded by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var renamingCategory by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var renamingGroup by remember { mutableStateOf<BudgetGroup?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    var creatingGroup by remember { mutableStateOf(false) }
    var movingBudget by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var fundingCategory by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var categoryDetails by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var autoAssignCategory by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var assignFromBudgetOpen by remember { mutableStateOf(false) }

    movingBudget?.let { (group, category) ->
        MoveBudgetScreen(
            modifier = modifier,
            sourceGroup = group,
            source = category,
            groups = groups,
            toBudgetCents = overview.toBudgetCents ?: 0L,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { movingBudget = null },
            onSave = { fromGroup, fromCategory, toGroup, toCategory, amount ->
                onTransferBudget(fromGroup, fromCategory, toGroup, toCategory, amount)
                movingBudget = null
            },
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        BudgetToolbar(
            month = month,
            onMonthChange = onMonthChange,
            optionsExpanded = optionsExpanded,
            showSpent = showSpent,
            showProgressBars = showProgressBars,
            budgetView = budgetView,
            showOverview = showOverview,
            showGroupTotals = showGroupTotals,
            hideFullySpent = hideFullySpent,
            showHidden = showHidden,
            onOptionsChange = { optionsExpanded = it },
            onAdd = { showAddSheet = true },
            onShowSpentChange = onShowSpentChange,
            onShowProgressBarsChange = onShowProgressBarsChange,
            onBudgetViewChange = onBudgetViewChange,
            onShowOverviewChange = onShowOverviewChange,
            onShowGroupTotalsChange = onShowGroupTotalsChange,
            onHideFullySpentChange = onHideFullySpentChange,
            onShowHiddenChange = onShowHiddenChange,
            onExpandAll = {
                saveCollapsedGroups(emptySet())
                optionsExpanded = false
            },
            onCollapseAll = {
                saveCollapsedGroups(groups.mapTo(mutableSetOf()) { it.name })
                optionsExpanded = false
            },
            onSearch = onSearch,
        )
        AnimatedVisibility(visible = showOverview) {
            if (budgetView == "Plan") {
                PlanBudgetOverview(
                    overview = overview,
                    hideDecimalPlaces = hideDecimalPlaces,
                    onClick = { assignFromBudgetOpen = true },
                )
            } else {
                BudgetOverviewRow(
                    overview,
                    showSpent = showSpent,
                    hideDecimalPlaces = hideDecimalPlaces,
                    onToBudgetClick = { assignFromBudgetOpen = true },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            groups.filter { showHidden || !it.hidden }.forEach { group ->
                val collapsed = group.name in collapsedGroups
                val visibleCategories = group.categories.filter { category ->
                    (showHidden || !category.hidden) &&
                        (!hideFullySpent || category.available != 0)
                }
                stickyHeader(key = "header-${group.name}") {
                    val onGroupClick = {
                            saveCollapsedGroups(if (collapsed) {
                                collapsedGroups - group.name
                            } else {
                                collapsedGroups + group.name
                            })
                        }
                    if (group.isIncome) {
                        IncomeBudgetGroupHeader(
                            group = group.copy(categories = visibleCategories),
                            collapsed = collapsed,
                            hideDecimalPlaces = hideDecimalPlaces,
                            onClick = onGroupClick,
                            onLongClick = { selectedGroup = group },
                        )
                    } else if (budgetView == "Plan") {
                        PlanBudgetGroupHeader(
                            group = group.copy(categories = visibleCategories),
                            collapsed = collapsed,
                            hideDecimalPlaces = hideDecimalPlaces,
                            onClick = onGroupClick,
                            onLongClick = { selectedGroup = group },
                        )
                    } else {
                        BudgetGroupHeader(
                            group = group.copy(categories = visibleCategories),
                            collapsed = collapsed,
                            showSpent = showSpent,
                            showTotals = showGroupTotals,
                            hideDecimalPlaces = hideDecimalPlaces,
                            onClick = onGroupClick,
                            onLongClick = { selectedGroup = group },
                        )
                    }
                }
                itemsIndexed(
                    visibleCategories,
                    key = { _, category -> "${group.name}-${category.name}" },
                ) { index, category ->
                    AnimatedVisibility(
                        visible = !collapsed,
                        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
                    ) {
                        if (category.isIncome) {
                            IncomeBudgetCategoryRow(
                                category = category,
                                showTopDivider = index > 0,
                                hideDecimalPlaces = hideDecimalPlaces,
                                onClick = { onShowCategoryTransactions(category.name, true) },
                                onLongClick = { selectedCategory = category },
                            )
                        } else if (budgetView == "Plan") {
                            PlanBudgetCategoryRow(
                                category = category,
                                showTopDivider = index > 0,
                                hideDecimalPlaces = hideDecimalPlaces,
                                onClick = { editingBudget = group to category },
                                onLongClick = { selectedCategory = category },
                            )
                        } else {
                            CategoryRow(
                                category = category,
                                showSpent = showSpent,
                                showProgressBar = showProgressBars,
                                showTopDivider = index > 0,
                                onLongClick = { selectedCategory = category },
                                onOpen = { editingBudget = group to category },
                                hideDecimalPlaces = hideDecimalPlaces,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    selectedCategory?.let { category ->
        val parent = groups.first { category in it.categories }
        CategoryActionsSheet(
            category = category,
            onDismiss = { selectedCategory = null },
            onRename = { selectedCategory = null; renamingCategory = parent to category },
            onEditBudget = { selectedCategory = null; editingBudget = parent to category },
            onDetails = { selectedCategory = null; categoryDetails = parent to category },
            onTransactionsThisMonth = { selectedCategory = null; onShowCategoryTransactions(category.name, true) },
            onAllTransactions = { selectedCategory = null; onShowCategoryTransactions(category.name, false) },
            onMoveMoney = { selectedCategory = null; movingBudget = parent to category },
            hidden = category.hidden,
            onSetHidden = { hidden ->
                if (onSetCategoryHidden(parent.name, category.name, hidden)) selectedCategory = null
            },
        )
    }
    selectedGroup?.let { group ->
        GroupActionsSheet(
            group = group,
            onDismiss = { selectedGroup = null },
            onRename = { selectedGroup = null; renamingGroup = group },
            hidden = group.hidden,
            onSetHidden = { hidden ->
                if (onSetGroupHidden(group.name, hidden)) selectedGroup = null
            },
        )
    }
    if (showAddSheet) {
        AddBudgetSheet(onDismiss = { showAddSheet = false },
            onNewCategory = { showAddSheet = false; creatingCategory = true },
            onNewGroup = { showAddSheet = false; creatingGroup = true })
    }
    editingBudget?.let { (group, category) ->
        EditBudgetAmountSheet(
            category = category,
            onDismiss = { editingBudget = null },
            onAutoAssign = { editingBudget = null; autoAssignCategory = group to category },
            onMoveMoney = { editingBudget = null; movingBudget = group to category },
            onDetails = { editingBudget = null; categoryDetails = group to category },
            onSave = { amount ->
                onSetBudgetAmount(group.name, category.name, amount)
                editingBudget = null
            },
        )
    }
    renamingCategory?.let { (group, category) -> RenameDialog("Rename category", category.name,
        onDismiss = { renamingCategory = null }, onSave = { name ->
            onRenameCategory(group.name, category.name, name); renamingCategory = null
        }) }
    renamingGroup?.let { group -> RenameDialog("Rename group", group.name,
        onDismiss = { renamingGroup = null }, onSave = { name -> onRenameGroup(group.name, name); renamingGroup = null }) }
    if (creatingCategory) NewCategoryDialog(groups.map { it.name }, { creatingCategory = false }) { group, name ->
        onCreateCategory(group, name); creatingCategory = false
    }
    if (creatingGroup) RenameDialog("New category group", "", { creatingGroup = false }) { name ->
        onCreateGroup(name); creatingGroup = false
    }
    fundingCategory?.let { (group, category) ->
        FundingActionsSheet(
            category = category,
            onDismiss = { fundingCategory = null },
            onEditAssigned = {
                fundingCategory = null
                editingBudget = group to category
            },
            onMoveMoney = {
                fundingCategory = null
                movingBudget = group to category
            },
        )
    }
    if (assignFromBudgetOpen) {
        AssignBudgetSheet(
            toBudgetCents = overview.toBudgetCents ?: 0L,
            groups = groups,
            onDismiss = { assignFromBudgetOpen = false },
            onSave = { group, category, amount ->
                if ((overview.toBudgetCents ?: 0L) < 0L) {
                    onTransferBudget(group, category, null, null, amount)
                } else {
                    onTransferBudget(null, null, group, category, amount)
                }
                assignFromBudgetOpen = false
            },
        )
    }
    categoryDetails?.let { (group, category) ->
        CategoryDetailsScreen(
            modifier = modifier,
            category = category,
            month = month,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { categoryDetails = null },
            onSaveNote = { note -> onSetCategoryNote(category.id.orEmpty(), note) },
            onSetCarryover = { enabled -> onSetCategoryCarryover(category.id.orEmpty(), enabled) },
            onAssign = { amount ->
                onSetBudgetAmount(group.name, category.name, amount)
                categoryDetails = null
            },
            transactions = transactions.filter { it.category == category.name }
                .sortedByDescending { it.date }.take(3),
            onRename = { categoryDetails = null; renamingCategory = group to category },
            onTransactionsThisMonth = {
                categoryDetails = null; onShowCategoryTransactions(category.name, true)
            },
            onAllTransactions = {
                categoryDetails = null; onShowCategoryTransactions(category.name, false)
            },
            hidden = category.hidden,
            onSetHidden = { hidden ->
                if (onSetCategoryHidden(group.name, category.name, hidden)) categoryDetails = null
            },
            onDelete = {
                if (onDeleteCategory(group.name, category.name)) categoryDetails = null
            },
            onEditTransaction = onEditTransaction,
            onDeleteTransaction = onDeleteTransaction,
        )
    }
    autoAssignCategory?.let { (group, category) ->
        CategoryAutoAssignSheet(
            category = category,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { autoAssignCategory = null },
            onAssign = { amount ->
                onSetBudgetAmount(group.name, category.name, amount)
                autoAssignCategory = null
            },
        )
    }
}

@Composable
private fun BudgetToolbar(
    month: String,
    onMonthChange: (String) -> Unit,
    optionsExpanded: Boolean,
    showSpent: Boolean,
    showProgressBars: Boolean,
    budgetView: String,
    showOverview: Boolean,
    showGroupTotals: Boolean,
    hideFullySpent: Boolean,
    showHidden: Boolean,
    onOptionsChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onShowSpentChange: (Boolean) -> Unit,
    onShowProgressBarsChange: (Boolean) -> Unit,
    onBudgetViewChange: (String) -> Unit,
    onShowOverviewChange: (Boolean) -> Unit,
    onShowGroupTotalsChange: (Boolean) -> Unit,
    onHideFullySpentChange: (Boolean) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onSearch: () -> Unit,
) {
    var monthPickerOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(shiftMonth(month, -1)) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Previous month")
            }
            Text(
                formatMonth(month),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .clickable { monthPickerOpen = true }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
            IconButton(onClick = { onMonthChange(shiftMonth(month, 1)) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Next month")
            }
        }
        Box {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Row {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search Actua")
                    }
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add category")
                    }
                    IconButton(onClick = { onOptionsChange(true) }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Budget display options")
                    }
                }
            }
            DropdownMenu(
                expanded = optionsExpanded,
                onDismissRequest = { onOptionsChange(false) },
            ) {
                ToggleMenuItem("Plan view", budgetView == "Plan") {
                    onBudgetViewChange(if (it) "Plan" else "Table")
                    onOptionsChange(false)
                }
                HorizontalDivider()
                ToggleMenuItem("Show overview", showOverview, onShowOverviewChange)
                ToggleMenuItem("Show spent column", showSpent, onShowSpentChange)
                ToggleMenuItem("Show progress bars", showProgressBars, onShowProgressBarsChange)
                ToggleMenuItem("Show group totals", showGroupTotals, onShowGroupTotalsChange)
                HorizontalDivider()
                ToggleMenuItem("Hide fully spent", hideFullySpent, onHideFullySpentChange)
                ToggleMenuItem("Show hidden categories and groups", showHidden, onShowHiddenChange)
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Expand all groups") }, onClick = onExpandAll)
                DropdownMenuItem(text = { Text("Collapse all groups") }, onClick = onCollapseAll)
            }
        }
    }
    if (monthPickerOpen) {
        BudgetMonthPicker(
            selectedMonth = month,
            onDismiss = { monthPickerOpen = false },
            onSelect = {
                onMonthChange(it)
                monthPickerOpen = false
            },
        )
    }
}

@Composable
private fun BudgetMonthPicker(
    selectedMonth: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val selected = remember(selectedMonth) { java.time.YearMonth.parse(selectedMonth) }
    var displayedYear by remember(selectedMonth) { mutableStateOf(selected.year) }
    val monthNames = remember {
        (1..12).map { monthNumber ->
            java.time.Month.of(monthNumber).getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault(),
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { displayedYear-- }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Previous year")
                }
                Text(
                    displayedYear.toString(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { displayedYear++ }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Next year")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                monthNames.chunked(3).forEachIndexed { rowIndex, rowMonths ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowMonths.forEachIndexed { columnIndex, label ->
                            val monthNumber = rowIndex * 3 + columnIndex + 1
                            val isSelected = displayedYear == selected.year && monthNumber == selected.monthValue
                            TextButton(
                                onClick = {
                                    onSelect(java.time.YearMonth.of(displayedYear, monthNumber).toString())
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else androidx.compose.ui.graphics.Color.Transparent,
                                ),
                            ) {
                                Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelect(java.time.YearMonth.now().toString())
            }) { Text("Current month") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun shiftMonth(month: String, amount: Long): String =
    java.time.YearMonth.parse(month).plusMonths(amount).toString()

private fun formatMonth(month: String): String = java.time.YearMonth.parse(month)
    .format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.getDefault()))

@Composable
private fun ToggleMenuItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
        onClick = { onChange(!checked) },
    )
}

@Composable
private fun PlanBudgetOverview(
    overview: BudgetOverview,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
) {
    val ready = overview.toBudgetCents ?: 0L
    Surface(
        onClick = onClick,
        color = if (ready >= 0L) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                overview.toBudgetCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Ready to Budget",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.End,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanBudgetGroupHeader(
    group: BudgetGroup,
    collapsed: Boolean,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        animationSpec = tween(220),
        label = "plan group chevron",
    )
    val assigned = group.categories.sumOf { it.assignedCents }
    val available = group.categories.sumOf { it.balanceCents }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                role = Role.Button, onClick = onClick, onLongClick = onLongClick,
            ).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (collapsed) "Expand ${group.name}" else "Collapse ${group.name}",
                modifier = Modifier.width(24.dp).rotate(rotation),
            )
            Text(
                if (group.hidden) "${group.name} · Hidden" else group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (collapsed) AmountColumn("Budgeted", assigned, Modifier.width(92.dp), hideDecimalPlaces)
            AmountColumn("Balance", available, Modifier.width(92.dp), hideDecimalPlaces, balance = true)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanBudgetCategoryRow(
    category: BudgetCategory,
    showTopDivider: Boolean,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    if (showTopDivider) HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (category.hidden) "${category.name} · Hidden" else category.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BalancePill(
                category.balanceCents,
                hideDecimalPlaces,
                textStyle = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                horizontalPadding = 10.dp,
                verticalPadding = 3.dp,
            )
        }
        val fraction = if (category.assignedCents <= 0L) 0f else
            (category.spentCents.toFloat() / category.assignedCents).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(5.dp)
                .clip(RoundedCornerShape(100)),
            color = if (category.balanceCents < 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Row(modifier = Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Budgeted ${formatMoneyCents(category.assignedCents, hideDecimalPlaces)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                " · ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Spent ${formatMoneyCents(category.spentCents, hideDecimalPlaces)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IncomeBudgetGroupHeader(
    group: BudgetGroup,
    collapsed: Boolean,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        animationSpec = tween(220),
        label = "income group chevron",
    )
    val received = group.categories.sumOf { it.balanceCents }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                role = Role.Button, onClick = onClick, onLongClick = onLongClick,
            ).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (collapsed) "Expand ${group.name}" else "Collapse ${group.name}",
                modifier = Modifier.width(24.dp).rotate(rotation),
            )
            Text(
                if (group.hidden) "${group.name} · Hidden" else group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Text(
                "Received ${formatMoneyCents(received, hideDecimalPlaces)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (received > 0L) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IncomeBudgetCategoryRow(
    category: BudgetCategory,
    showTopDivider: Boolean,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    if (showTopDivider) HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            role = Role.Button, onClick = onClick, onLongClick = onLongClick,
        ).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (category.hidden) "${category.name} · Hidden" else category.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatMoneyCents(category.balanceCents, hideDecimalPlaces),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (category.balanceCents > 0L) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetOverviewRow(
    overview: BudgetOverview,
    showSpent: Boolean,
    hideDecimalPlaces: Boolean,
    onToBudgetClick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverviewCell(
                "To budget",
                overview.toBudgetCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "—",
                Modifier.weight(1.35f),
                Alignment.Start,
                positive = overview.toBudgetCents?.let { it > 0 } == true,
                pill = true,
                pillOffset = (-8).dp,
                onClick = onToBudgetClick,
            )
            OverviewCell("Budgeted", formatMoneyCents(overview.budgetedCents, hideDecimalPlaces), Modifier.weight(1f), Alignment.End)
            if (showSpent) OverviewCell("Spent", formatMoneyCents(overview.spentCents, hideDecimalPlaces), Modifier.weight(1f), Alignment.End)
            OverviewCell("Balance", formatMoneyCents(overview.availableCents, hideDecimalPlaces), Modifier.weight(1f), Alignment.End,
                positive = overview.availableCents >= 0, pill = true, pillOffset = 8.dp)
        }
    }
}

@Composable
private fun OverviewCell(
    label: String,
    amount: String,
    modifier: Modifier,
    alignment: Alignment.Horizontal,
    positive: Boolean = false,
    pill: Boolean = false,
    pillOffset: androidx.compose.ui.unit.Dp = 0.dp,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.68f), maxLines = 1)
        if (pill) {
            val pillModifier = Modifier.offset(x = pillOffset)
            val pillColor = if (positive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            if (onClick != null) {
                Surface(onClick = onClick, modifier = pillModifier, color = pillColor,
                    shape = RoundedCornerShape(8.dp)) {
                    OverviewPillAmount(amount, positive)
                }
            } else {
                Surface(modifier = pillModifier, color = pillColor, shape = RoundedCornerShape(8.dp)) {
                    OverviewPillAmount(amount, positive)
                }
            }
        } else {
            Text(amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1)
        }
    }
}

@Composable
private fun OverviewPillAmount(amount: String, positive: Boolean) {
    Text(amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
        color = if (positive) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BudgetGroupHeader(
    group: BudgetGroup,
    collapsed: Boolean,
    showSpent: Boolean,
    showTotals: Boolean,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        animationSpec = tween(220),
        label = "group chevron",
    )
    val budgeted = group.categories.sumOf { it.assignedCents }
    val spent = group.categories.sumOf { it.spentCents }
    val balance = group.categories.sumOf { it.balanceCents }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                role = Role.Button, onClick = onClick, onLongClick = onLongClick,
            ).animateContentSize().padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1.35f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (collapsed) "Expand ${group.name}" else "Collapse ${group.name}",
                    modifier = Modifier.width(24.dp).rotate(rotation),
                )
                Text(if (group.hidden) "${group.name} · Hidden" else group.name,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = if (group.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showTotals) {
                AmountColumn("Budgeted", budgeted, Modifier.weight(1f), hideDecimalPlaces)
                if (showSpent) AmountColumn("Spent", -spent, Modifier.weight(1f), hideDecimalPlaces, muted = spent == 0L)
                AmountColumn("Balance", balance, Modifier.weight(1f), hideDecimalPlaces, balance = true)
            } else {
                Spacer(Modifier.weight(if (showSpent) 3f else 2f))
            }
        }
    }
}

@Composable
private fun AmountColumn(
    label: String,
    amount: Long,
    modifier: Modifier,
    hideDecimalPlaces: Boolean,
    balance: Boolean = false,
    muted: Boolean = false,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (balance) {
            BalancePill(
                amount,
                hideDecimalPlaces,
                modifier = Modifier.offset(x = 8.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(formatMoneyCents(amount, hideDecimalPlaces), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    category: BudgetCategory,
    showSpent: Boolean,
    showProgressBar: Boolean,
    showTopDivider: Boolean,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    hideDecimalPlaces: Boolean,
) {
    if (showTopDivider) {
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
    Column(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (category.hidden) "${category.name} · Hidden" else category.name,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.35f),
                color = if (category.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            CategoryAmount(category.assignedCents, Modifier.weight(1f), hideDecimalPlaces)
            if (showSpent) CategoryAmount(
                -category.spentCents,
                Modifier.weight(1f),
                hideDecimalPlaces,
                muted = category.spentCents == 0L,
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                BalancePill(
                    category.balanceCents,
                    hideDecimalPlaces,
                    modifier = Modifier.offset(x = 10.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    horizontalPadding = 10.dp,
                    verticalPadding = 3.dp,
                )
            }
        }
        AnimatedVisibility(visible = showProgressBar) {
            val fraction = if (category.assigned <= 0) 0f
            else (category.spent.toFloat() / category.assigned).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp)
                    .clip(RoundedCornerShape(100)),
                color = if (category.available < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBudgetAmountSheet(
    category: BudgetCategory,
    onDismiss: () -> Unit,
    onAutoAssign: () -> Unit,
    onMoveMoney: () -> Unit,
    onDetails: () -> Unit,
    onSave: (Long) -> Unit,
) {
    val calculator = remember(category) {
        CalculatorAmountState(category.assignedCents, allowsNegative = true)
    }
    var enteredAmount by remember(category) { mutableStateOf(category.assignedCents) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BudgetEntryAction(Icons.Outlined.Bolt, "Auto-Assign", Modifier.weight(1f), onAutoAssign)
                BudgetEntryAction(Icons.Outlined.SwapHoriz, "Move Money", Modifier.weight(1f), onMoveMoney)
                BudgetEntryAction(Icons.Outlined.MoreHoriz, "Details", Modifier.weight(1f), onDetails)
            }
            InlineCalculatorAmount("Budgeted", enteredAmount, Modifier.padding(horizontal = 20.dp))
            CompactCalculatorPad(
                calculator = calculator,
                allowSign = true,
                showDisplay = false,
                onValueChange = { enteredAmount = it },
                onDone = { onSave(calculator.finish()) },
            )
        }
    }
}

@Composable
private fun BudgetEntryAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
            modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun InlineCalculatorAmount(label: String, amount: Long, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "$label cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(520), repeatMode = RepeatMode.Reverse),
        label = "$label cursor alpha",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatMoneyCents(amount, false), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Box(
                Modifier.padding(start = 3.dp).height(24.dp).width(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha)),
            )
        }
    }
}

@Composable
private fun CategoryAmount(amount: Long, modifier: Modifier, hideDecimalPlaces: Boolean, muted: Boolean = false) {
    Text(
        formatMoneyCents(amount, hideDecimalPlaces), style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Normal,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End, maxLines = 1, modifier = modifier,
    )
}

@Composable
private fun BalancePill(
    amount: Long,
    hideDecimalPlaces: Boolean,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
    fontWeight: FontWeight = FontWeight.SemiBold,
    horizontalPadding: androidx.compose.ui.unit.Dp = 8.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 2.dp,
) {
    val positive = amount > 0
    val negative = amount < 0
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = when {
            positive -> MaterialTheme.colorScheme.primaryContainer
            negative -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Text(
            text = formatMoneyCents(amount, hideDecimalPlaces),
            style = textStyle,
            fontWeight = fontWeight,
            color = when {
                positive -> MaterialTheme.colorScheme.onPrimaryContainer
                negative -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignBudgetSheet(
    toBudgetCents: Long,
    groups: List<BudgetGroup>,
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Unit,
) {
    val options = remember(groups) {
        groups.filterNot { it.isIncome }.flatMap { group ->
            group.categories.filterNot { it.hidden }.map { group.name to it.name }
        }
    }
    var selected by remember(options) { mutableStateOf(options.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    val calculator = remember(toBudgetCents) {
        CalculatorAmountState(kotlin.math.abs(toBudgetCents), allowsNegative = false)
    }
    var enteredAmount by remember(toBudgetCents) { mutableStateOf(kotlin.math.abs(toBudgetCents)) }
    val covering = toBudgetCents < 0L
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                if (covering) "Cover To Budget" else "Budget category",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (covering) "Choose a category to move money from"
                else "Choose a category to fund from To Budget",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Box(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { expanded = true },
                    enabled = options.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(selected?.let { "${it.first} · ${it.second}" } ?: "No categories available")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${option.first} · ${option.second}") },
                            onClick = { selected = option; expanded = false },
                        )
                    }
                }
            }
            InlineCalculatorAmount("Amount", enteredAmount)
            CompactCalculatorPad(
                calculator = calculator,
                horizontalPadding = 0.dp,
                showDisplay = false,
                onValueChange = { enteredAmount = it },
                onDone = {
                    selected?.let { target ->
                        calculator.finish().takeIf { it > 0L }?.let { onSave(target.first, target.second, it) }
                    }
                },
            )
        }
    }
}

private data class MoveEndpoint(
    val group: String?,
    val category: String?,
    val balanceCents: Long,
) {
    val title: String get() = category ?: "To Budget"
    val subtitle: String? get() = group
}

@Composable
private fun MoveBudgetScreen(
    modifier: Modifier,
    sourceGroup: BudgetGroup,
    source: BudgetCategory,
    groups: List<BudgetGroup>,
    toBudgetCents: Long,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    onSave: (String?, String?, String?, String?, Long) -> Unit,
) {
    val options = remember(groups, toBudgetCents) {
        listOf(MoveEndpoint(null, null, toBudgetCents)) + groups.filterNot { it.isIncome }.flatMap { group ->
            group.categories.filterNot { it.hidden }.map { category ->
                MoveEndpoint(group.name, category.name, category.balanceCents)
            }
        }
    }
    val anchor = remember(sourceGroup, source) {
        MoveEndpoint(sourceGroup.name, source.name, source.balanceCents)
    }
    var from by remember(anchor) { mutableStateOf(if (source.balanceCents < 0) options.first() else anchor) }
    var to by remember(anchor) { mutableStateOf(if (source.balanceCents < 0) anchor else options.first()) }
    val calculator = remember(anchor) {
        CalculatorAmountState(0L, allowsNegative = true, conventionalAmountEntry = true)
    }
    var enteredAmount by remember(anchor) { mutableStateOf(0L) }
    BackHandler(onBack = onDismiss)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close Move Money")
                }
                Text(
                    "Move Money",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(48.dp))
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MoveEndpointSelector(
                    label = "From",
                    selected = from,
                    options = options.filterNot { it.group == to.group && it.category == to.category },
                    hideDecimalPlaces = hideDecimalPlaces,
                    onSelect = { from = it },
                )
                IconButton(
                    onClick = { val previousFrom = from; from = to; to = previousFrom },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = "Swap source and destination")
                }
                MoveEndpointSelector(
                    label = "To",
                    selected = to,
                    options = options.filterNot { it.group == from.group && it.category == from.category },
                    hideDecimalPlaces = hideDecimalPlaces,
                    onSelect = { to = it },
                )
                Text(
                    "Available to move: ${formatMoneyCents(from.balanceCents.coerceAtLeast(0L), hideDecimalPlaces)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            InlineCalculatorAmount("Amount", enteredAmount, Modifier.padding(horizontal = 16.dp))
            CompactCalculatorPad(
                calculator = calculator,
                moveMoneyMode = true,
                showDisplay = false,
                onValueChange = { enteredAmount = it },
                canFinish = { amount ->
                    amount > 0L && amount <= from.balanceCents.coerceAtLeast(0L) &&
                        (from.group != to.group || from.category != to.category)
                },
                onDone = {
                    calculator.finish().takeIf { it > 0L }?.let { amount ->
                        onSave(from.group, from.category, to.group, to.category, amount)
                    }
                },
            )
        }
    }
}

@Composable
private fun MoveEndpointSelector(
    label: String,
    selected: MoveEndpoint,
    options: List<MoveEndpoint>,
    hideDecimalPlaces: Boolean,
    onSelect: (MoveEndpoint) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selected.title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    selected.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(formatMoneyCents(selected.balanceCents, hideDecimalPlaces),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.title)
                            Text(
                                listOfNotNull(option.subtitle, formatMoneyCents(option.balanceCents, hideDecimalPlaces))
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun CategoryDetailsScreen(
    modifier: Modifier = Modifier,
    category: BudgetCategory,
    month: String,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    onSaveNote: (String) -> Unit,
    onSetCarryover: (Boolean) -> Unit,
    onAssign: (Long) -> Unit,
    transactions: List<Transaction>,
    onRename: () -> Unit,
    onTransactionsThisMonth: () -> Unit,
    onAllTransactions: () -> Unit,
    hidden: Boolean,
    onSetHidden: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
) {
    var note by remember(category) { mutableStateOf(category.note) }
    var noteEditorOpen by remember(category) { mutableStateOf(false) }
    var rollover by remember(category) { mutableStateOf(category.carryoverEnabled) }
    var deleteConfirmOpen by remember(category) { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    val suggestions = remember(category) {
        buildList {
            category.history.firstOrNull()?.let { last ->
                val spent = kotlin.math.abs(minOf(last.spentCents, 0L))
                if (spent > 0) add("Spent last month" to spent)
                if (last.assignedCents != 0L) add("Budgeted last month" to last.assignedCents)
            }
            val spending = category.history.map { kotlin.math.abs(minOf(it.spentCents, 0L)) }
            if (spending.size >= 2) {
                val average = (spending.sum().toDouble() / spending.size).toLong()
                if (average > 0) add("Average spent (${spending.size} months)" to average)
            }
            if (category.balanceCents != 0L) add("Reset balance to zero" to
                (category.assignedCents - category.balanceCents))
            if (category.assignedCents != 0L) add("Set budgeted to zero" to 0L)
        }.distinctBy { it.first }
    }
    BackHandler(onBack = onDismiss)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(category.name, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatMonth(month), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(48.dp))
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailSummary("Budgeted", category.assignedCents, hideDecimalPlaces, Modifier.weight(1f))
                DetailSummary("Spent", -category.spentCents, hideDecimalPlaces, Modifier.weight(1f))
                DetailSummary("Balance", category.balanceCents, hideDecimalPlaces, Modifier.weight(1f))
            }
            Text("Note", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Surface(
                onClick = { noteEditorOpen = true },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = note.ifBlank { "Add note" },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (note.isBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (note.isBlank()) 1 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rollover overspending", fontWeight = FontWeight.SemiBold)
                    Text("Carry overspending into the next month instead of taking it from To Budget.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(rollover, { enabled -> rollover = enabled; onSetCarryover(enabled) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-assign", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Budgeted ${formatMoneyCents(category.assignedCents, hideDecimalPlaces)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (suggestions.isEmpty()) Text("No suggestions available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (suggestions.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                    Column {
                        suggestions.forEachIndexed { index, (label, amount) ->
                            if (index > 0) HorizontalDivider()
                            Row(Modifier.fillMaxWidth().clickable { onAssign(amount) }
                                .padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Text(label, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(formatMoneyCents(amount, hideDecimalPlaces), fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Text("Recent transactions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (transactions.isEmpty()) {
                Text("No recent transactions", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else transactions.forEach { transaction ->
                Row(Modifier.fillMaxWidth().clickable { selectedTransaction = transaction }
                    .padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(transaction.payee.ifBlank { "Unknown payee" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatStoredDate(transaction.date), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatMoneyCents(transaction.amountCents, hideDecimalPlaces), fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider()
            SheetAction("Transactions this month", onTransactionsThisMonth)
            SheetAction("All transactions", onAllTransactions)
            SheetAction("Rename category", onRename)
            SheetAction(if (hidden) "Unhide category" else "Hide category", { onSetHidden(!hidden) }, destructive = !hidden)
            SheetAction("Delete category", { deleteConfirmOpen = true }, destructive = true)
            Spacer(Modifier.height(12.dp))
            }
        }
    }
    selectedTransaction?.let { transaction ->
        TransactionDetailsSheet(
            transaction = transaction,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { selectedTransaction = null },
            onEdit = { selectedTransaction = null; onEditTransaction(transaction) },
            onDelete = { selectedTransaction = null; onDeleteTransaction(transaction) },
        )
    }
    if (noteEditorOpen) {
        var noteDraft by remember(category, noteEditorOpen) { mutableStateOf(note) }
        AlertDialog(
            onDismissRequest = { noteEditorOpen = false },
            title = { Text(if (note.isBlank()) "Add note" else "Edit note") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    placeholder = { Text("Category note") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    note = noteDraft
                    onSaveNote(noteDraft)
                    noteEditorOpen = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { noteEditorOpen = false }) { Text("Cancel") }
            },
        )
    }
    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text("Delete ${category.name}?") },
            text = { Text("Existing transactions will become uncategorized. This cannot be undone.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConfirmOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailSummary(label: String, amount: Long, hideDecimals: Boolean, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoneyCents(amount, hideDecimals), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryAutoAssignSheet(
    category: BudgetCategory,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    onAssign: (Long) -> Unit,
) {
    val choices = remember(category) {
        buildList {
            category.history.firstOrNull()?.let { last ->
                val spent = kotlin.math.abs(minOf(last.spentCents, 0L))
                if (spent > 0) add("Spent last month" to spent)
                if (last.assignedCents != 0L) add("Budgeted last month" to last.assignedCents)
            }
            val spending = category.history.map { kotlin.math.abs(minOf(it.spentCents, 0L)) }
            if (spending.size >= 2) {
                val average = (spending.sum().toDouble() / spending.size).toLong()
                if (average > 0) add("Average spent (${spending.size} months)" to average)
            }
            if (category.balanceCents != 0L) add("Reset balance to zero" to
                (category.assignedCents - category.balanceCents))
            if (category.assignedCents != 0L) add("Set budgeted to zero" to 0L)
        }.distinctBy { it.first }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Auto-Assign", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(category.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (choices.isEmpty()) Text("No suggestions available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            choices.forEach { (label, amount) ->
                Surface(onClick = { onAssign(amount) }, color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(label, modifier = Modifier.weight(1f))
                        Text(formatMoneyCents(amount, hideDecimalPlaces), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryActionsSheet(
    category: BudgetCategory,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onEditBudget: () -> Unit,
    onDetails: () -> Unit,
    onTransactionsThisMonth: () -> Unit,
    onAllTransactions: () -> Unit,
    onMoveMoney: () -> Unit,
    hidden: Boolean,
    onSetHidden: (Boolean) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(category.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            SheetAction("Rename category", onRename)
            if (!category.isIncome) {
                SheetAction("Budget details", onDetails)
                SheetAction("Edit budgeted amount", onEditBudget)
            }
            SheetAction("Transactions this month", onTransactionsThisMonth)
            SheetAction("All transactions", onAllTransactions)
            if (!category.isIncome && category.available != 0) {
                SheetAction(if (category.available < 0) "Cover overspending" else "Move money", onMoveMoney)
            }
            SheetAction(if (hidden) "Unhide category" else "Hide category", { onSetHidden(!hidden) }, destructive = !hidden)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FundingActionsSheet(
    category: BudgetCategory,
    onDismiss: () -> Unit,
    onEditAssigned: () -> Unit,
    onMoveMoney: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                category.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            SheetAction("Edit budgeted amount", onEditAssigned)
            SheetAction(
                if (category.balanceCents < 0L) "Cover overspending" else "Move money",
                onMoveMoney,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupActionsSheet(group: BudgetGroup, onDismiss: () -> Unit, onRename: () -> Unit,
    hidden: Boolean, onSetHidden: (Boolean) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            SheetAction("Rename group", onRename)
            if (!group.isIncome || hidden) {
                SheetAction(if (hidden) "Unhide group" else "Hide group", { onSetHidden(!hidden) }, destructive = !hidden)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetSheet(onDismiss: () -> Unit, onNewCategory: () -> Unit, onNewGroup: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Text("Add to budget", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            SheetAction("New category", onNewCategory)
            SheetAction("New category group", onNewGroup)
            SheetAction("Apply budget template", onDismiss)
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    DropdownMenuItem(
        text = {
            Text(label, color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface)
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}
