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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetCategoryView
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.BudgetTarget
import com.azimulkabir.actua.model.BudgetTemplatePlanner
import com.azimulkabir.actua.model.BudgetTemplatePreview
import com.azimulkabir.actua.model.BudgetScheduleFunding
import com.azimulkabir.actua.model.CleanupPreview
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
    showSpent: Boolean = false,
    onShowSpentChange: (Boolean) -> Unit = {},
    showProgressBars: Boolean = true,
    onShowProgressBarsChange: (Boolean) -> Unit = {},
    budgetView: String = "Plan",
    onBudgetViewChange: (String) -> Unit = {},
    showOverview: Boolean = true,
    onShowOverviewChange: (Boolean) -> Unit = {},
    showGroupTotals: Boolean = false,
    onShowGroupTotalsChange: (Boolean) -> Unit = {},
    hideFullySpent: Boolean = false,
    onHideFullySpentChange: (Boolean) -> Unit = {},
    categoryView: String = "All",
    onCategoryViewChange: (String) -> Unit = {},
    showCategoryFilters: Boolean = true,
    onShowCategoryFiltersChange: (Boolean) -> Unit = {},
    onSetCategoryHidden: (String, String, Boolean) -> Boolean = { _, _, _ -> false },
    onSetGroupHidden: (String, Boolean) -> Boolean = { _, _ -> false },
    onRenameCategory: (String, String, String) -> Unit = { _, _, _ -> },
    onRenameGroup: (String, String) -> Unit = { _, _ -> },
    onCreateCategory: (String, String) -> Unit = { _, _ -> },
    onCreateGroup: (String) -> Unit = {},
    onShowCategoryTransactions: (String, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onTransferBudget: (String?, String?, String?, String?, Long) -> Unit = { _, _, _, _, _ -> },
    onSetBudgetAmount: (String, String, Long) -> Unit = { _, _, _ -> },
    onSetCategoryNote: (String, String) -> Unit = { _, _ -> },
    onSetCategoryCarryover: (String, Boolean) -> Unit = { _, _ -> },
    onHoldForNextMonth: (Long) -> Unit = {},
    onResetNextMonthBuffer: () -> Unit = {},
    onCopyPreviousMonth: () -> Unit = {},
    onEditAutomations: (BudgetGroup, BudgetCategory) -> Unit = { _, _ -> },
    onApplyBudgetTemplate: (BudgetTemplatePreview) -> Unit = {},
    scheduleFunding: List<BudgetScheduleFunding> = emptyList(),
    onPreviewCleanup: () -> CleanupPreview = { CleanupPreview("") },
    onApplyCleanup: (CleanupPreview) -> Unit = {},
    onSearch: () -> Unit = {},
    onManageCategories: () -> Unit = {},
    transactions: List<Transaction> = emptyList(),
    onDeleteCategory: (String, String) -> Boolean = { _, _ -> false },
    onEditTransaction: (Transaction) -> Unit = {},
    onDeleteTransaction: (Transaction) -> Unit = {},
    requestedCategoryDetails: String? = null,
    onCategoryDetailsChange: (String?) -> Unit = {},
    returnToRootRequest: Int = 0,
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
    var autoAssignBudget by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var budgetSummaryOpen by remember { mutableStateOf(false) }
    var templatePreviewOpen by remember { mutableStateOf(false) }
    var overwriteTemplates by remember { mutableStateOf(false) }
    var cleanupPreview by remember { mutableStateOf<CleanupPreview?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(returnToRootRequest) {
        if (returnToRootRequest > 0) {
            if (categoryDetails != null) categoryDetails = null
            else listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(requestedCategoryDetails, groups) {
        requestedCategoryDetails?.let { requested ->
            groups.firstNotNullOfOrNull { group ->
                group.categories.firstOrNull { it.name == requested }?.let { group to it }
            }?.let { categoryDetails = it }
        }
    }
    LaunchedEffect(categoryDetails?.second?.name) {
        onCategoryDetailsChange(categoryDetails?.second?.name)
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
            showCategoryFilters = showCategoryFilters,
            onOptionsChange = { optionsExpanded = it },
            onAdd = { showAddSheet = true },
            onShowSpentChange = onShowSpentChange,
            onShowProgressBarsChange = onShowProgressBarsChange,
            onBudgetViewChange = onBudgetViewChange,
            onShowOverviewChange = onShowOverviewChange,
            onShowGroupTotalsChange = onShowGroupTotalsChange,
            onHideFullySpentChange = onHideFullySpentChange,
            onShowHiddenChange = onShowHiddenChange,
            onShowCategoryFiltersChange = onShowCategoryFiltersChange,
            onExpandAll = {
                saveCollapsedGroups(emptySet())
                optionsExpanded = false
            },
            onCollapseAll = {
                saveCollapsedGroups(groups.mapTo(mutableSetOf()) { it.name })
                optionsExpanded = false
            },
            onCopyPreviousMonth = {
                onCopyPreviousMonth()
                optionsExpanded = false
            },
            onSearch = onSearch,
            onManageCategories = onManageCategories,
        )
        AnimatedVisibility(visible = showCategoryFilters) {
            BudgetCategoryFilterRow(selected = categoryView, onSelect = onCategoryViewChange)
        }
        AnimatedVisibility(visible = showOverview) {
            if (budgetView == "Plan") {
                PlanBudgetOverview(
                    overview = overview,
                    hideDecimalPlaces = hideDecimalPlaces,
                    onClick = { budgetSummaryOpen = true },
                )
            } else {
                BudgetOverviewRow(
                    overview,
                    showSpent = showSpent,
                    hideDecimalPlaces = hideDecimalPlaces,
                    onToBudgetClick = { budgetSummaryOpen = true },
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            val selectedView = BudgetCategoryView.fromLabel(categoryView)
            groups.filter { showHidden || !it.hidden }.forEach { group ->
                val collapsed = group.name in collapsedGroups
                val visibleCategories = group.categories.filter { category ->
                    (showHidden || !category.hidden) &&
                        (!hideFullySpent || category.available != 0) &&
                        (category.isIncome || selectedView.matches(category))
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
                            showTotals = showGroupTotals,
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
                                onClick = { onShowCategoryTransactions(category.name, true, false) },
                                onLongClick = { selectedCategory = category },
                            )
                        } else if (budgetView == "Plan") {
                            PlanBudgetCategoryRow(
                                category = category,
                                showSpendingDetails = showSpent,
                                showProgressBar = showProgressBars,
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
            onSetTarget = { selectedCategory = null; onEditAutomations(parent, category) },
            onDetails = { selectedCategory = null; categoryDetails = parent to category },
            onTransactionsThisMonth = { selectedCategory = null; onShowCategoryTransactions(category.name, true, false) },
            onAllTransactions = { selectedCategory = null; onShowCategoryTransactions(category.name, false, false) },
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
            onNewGroup = { showAddSheet = false; creatingGroup = true },
            onApplyTemplate = { overwrite ->
                showAddSheet = false
                overwriteTemplates = overwrite
                templatePreviewOpen = true
            },
            onPreviewCleanup = {
                showAddSheet = false
                cleanupPreview = onPreviewCleanup()
            })
    }
    cleanupPreview?.let { preview ->
        CleanupPreviewSheet(
            preview = preview,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { cleanupPreview = null },
            onApply = { onApplyCleanup(it); cleanupPreview = null },
        )
    }
    if (templatePreviewOpen) {
        BudgetTemplatePreviewSheet(
            preview = remember(groups, month, overview.toBudgetCents, overwriteTemplates, scheduleFunding) {
                BudgetTemplatePlanner.preview(
                    groups, month, overview.toBudgetCents ?: Long.MAX_VALUE, overwriteTemplates,
                    scheduleFunding,
                )
            },
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { templatePreviewOpen = false },
            onApply = { preview -> onApplyBudgetTemplate(preview); templatePreviewOpen = false },
        )
    }
    editingBudget?.let { (group, category) ->
        EditBudgetAmountSheet(
            sourceGroup = group,
            category = category,
            month = month,
            groups = groups,
            toBudgetCents = overview.toBudgetCents ?: 0L,
            hideDecimalPlaces = hideDecimalPlaces,
            startInMoveMode = false,
            startInAutoAssignMode = false,
            onDismiss = { editingBudget = null },
            onDetails = { editingBudget = null; categoryDetails = group to category },
            onSave = { amount ->
                onSetBudgetAmount(group.name, category.name, amount)
                editingBudget = null
            },
            onMove = { fromGroup, fromCategory, toGroup, toCategory, amount ->
                onTransferBudget(fromGroup, fromCategory, toGroup, toCategory, amount)
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
    if (budgetSummaryOpen) {
        BudgetSummarySheet(
            toBudgetCents = overview.toBudgetCents ?: 0L,
            bufferedCents = overview.bufferedCents,
            groups = groups,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { budgetSummaryOpen = false },
            onMoveToCategory = { group, category, amount ->
                if ((overview.toBudgetCents ?: 0L) < 0L) {
                    onTransferBudget(group, category, null, null, amount)
                } else {
                    onTransferBudget(null, null, group, category, amount)
                }
                budgetSummaryOpen = false
            },
            onHoldForNextMonth = { amount ->
                onHoldForNextMonth(amount)
                budgetSummaryOpen = false
            },
            onResetNextMonthBuffer = {
                onResetNextMonthBuffer()
                budgetSummaryOpen = false
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
            onEditBudget = { categoryDetails = null; editingBudget = group to category },
            onMoveMoney = { categoryDetails = null; movingBudget = group to category },
            onAutoAssign = { categoryDetails = null; autoAssignBudget = group to category },
            onEditTarget = { onEditAutomations(group, category) },
            transactions = transactions.filter { it.category == category.name }
                .sortedByDescending { it.date }.take(3),
            onRename = { categoryDetails = null; renamingCategory = group to category },
            onTransactionsThisMonth = {
                categoryDetails = null; onShowCategoryTransactions(category.name, true, true)
            },
            onAllTransactions = {
                categoryDetails = null; onShowCategoryTransactions(category.name, false, true)
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
            scheduleFunding = scheduleFunding,
        )
    }
    movingBudget?.let { (group, category) ->
        EditBudgetAmountSheet(
            sourceGroup = group,
            category = category,
            month = month,
            groups = groups,
            toBudgetCents = overview.toBudgetCents ?: 0L,
            hideDecimalPlaces = hideDecimalPlaces,
            startInMoveMode = true,
            startInAutoAssignMode = false,
            onDismiss = { movingBudget = null },
            onDetails = { movingBudget = null; categoryDetails = group to category },
            onSave = { amount ->
                onSetBudgetAmount(group.name, category.name, amount)
                movingBudget = null
            },
            onMove = { fromGroup, fromCategory, toGroup, toCategory, amount ->
                onTransferBudget(fromGroup, fromCategory, toGroup, toCategory, amount)
                movingBudget = null
            },
        )
    }
    autoAssignBudget?.let { (group, category) ->
        EditBudgetAmountSheet(
            sourceGroup = group,
            category = category,
            month = month,
            groups = groups,
            toBudgetCents = overview.toBudgetCents ?: 0L,
            hideDecimalPlaces = hideDecimalPlaces,
            startInMoveMode = false,
            startInAutoAssignMode = true,
            onDismiss = { autoAssignBudget = null },
            onDetails = { autoAssignBudget = null; categoryDetails = group to category },
            onSave = { amount ->
                onSetBudgetAmount(group.name, category.name, amount)
                autoAssignBudget = null
            },
            onMove = { fromGroup, fromCategory, toGroup, toCategory, amount ->
                onTransferBudget(fromGroup, fromCategory, toGroup, toCategory, amount)
                autoAssignBudget = null
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
    showCategoryFilters: Boolean,
    onOptionsChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onShowSpentChange: (Boolean) -> Unit,
    onShowProgressBarsChange: (Boolean) -> Unit,
    onBudgetViewChange: (String) -> Unit,
    onShowOverviewChange: (Boolean) -> Unit,
    onShowGroupTotalsChange: (Boolean) -> Unit,
    onHideFullySpentChange: (Boolean) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onShowCategoryFiltersChange: (Boolean) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onCopyPreviousMonth: () -> Unit,
    onSearch: () -> Unit,
    onManageCategories: () -> Unit,
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
                    IconButton(onClick = onManageCategories) {
                        Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Manage Categories")
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
                ToggleMenuItem(
                    if (budgetView == "Plan") "Show spending details" else "Show spent column",
                    showSpent,
                    onShowSpentChange,
                )
                ToggleMenuItem("Show progress bars", showProgressBars, onShowProgressBarsChange)
                ToggleMenuItem("Show group totals", showGroupTotals, onShowGroupTotalsChange)
                HorizontalDivider()
                ToggleMenuItem("Hide fully spent", hideFullySpent, onHideFullySpentChange)
                ToggleMenuItem("Show hidden categories and groups", showHidden, onShowHiddenChange)
                ToggleMenuItem("Show category filters", showCategoryFilters, onShowCategoryFiltersChange)
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Expand all groups") }, onClick = onExpandAll)
                DropdownMenuItem(text = { Text("Collapse all groups") }, onClick = onCollapseAll)
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Copy last month's budget") }, onClick = onCopyPreviousMonth)
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
private fun BudgetCategoryFilterRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BudgetCategoryView.entries.forEach { view ->
            val isSelected = view.label == selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    onSelect(if (view == BudgetCategoryView.ALL || isSelected) BudgetCategoryView.ALL.label else view.label)
                },
                label = { Text(view.label) },
            )
        }
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
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            if (overview.bufferedCents != 0L) {
                Text(
                    "${formatMoneyCents(overview.bufferedCents, hideDecimalPlaces)} held for next month",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanBudgetGroupHeader(
    group: BudgetGroup,
    collapsed: Boolean,
    showTotals: Boolean,
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
            if (showTotals) {
                if (collapsed) {
                    AmountColumn("Budgeted", assigned, Modifier.widthIn(min = 92.dp), hideDecimalPlaces)
                }
                AmountColumn(
                    "Balance",
                    available,
                    Modifier.widthIn(min = 92.dp),
                    hideDecimalPlaces,
                    balance = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanBudgetCategoryRow(
    category: BudgetCategory,
    showSpendingDetails: Boolean,
    showProgressBar: Boolean,
    showTopDivider: Boolean,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        if (showTopDivider) HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
        )
        Column(
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (category.hidden) "${category.name} · Hidden" else category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                category.target?.let {
                    Text(it.type.label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            BalancePill(
                category.balanceCents,
                hideDecimalPlaces,
                textStyle = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                horizontalPadding = 10.dp,
                verticalPadding = 3.dp,
            )
        }
        if (showProgressBar) {
            LinearProgressIndicator(
                progress = { category.progressFraction },
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(5.dp)
                    .clip(RoundedCornerShape(100)),
                color = if (category.balanceCents < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
            if (showSpendingDetails) {
                Row(
                    modifier = Modifier.padding(top = if (showProgressBar) 2.dp else 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            if (overview.bufferedCents != 0L) {
                Text(
                    "${formatMoneyCents(overview.bufferedCents, hideDecimalPlaces)} held for next month",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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
            LinearProgressIndicator(
                progress = { category.progressFraction },
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
    sourceGroup: BudgetGroup,
    category: BudgetCategory,
    month: String,
    groups: List<BudgetGroup>,
    toBudgetCents: Long,
    hideDecimalPlaces: Boolean,
    startInMoveMode: Boolean,
    startInAutoAssignMode: Boolean,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onSave: (Long) -> Unit,
    onMove: (String?, String?, String?, String?, Long) -> Unit,
) {
    var moveMode by remember(category, startInMoveMode) { mutableStateOf(startInMoveMode) }
    var autoAssignMode by remember(category, startInMoveMode, startInAutoAssignMode) {
        mutableStateOf(startInAutoAssignMode)
    }
    val autoAssignChoices = remember(category, month) { buildAutoAssignChoices(category, month) }
    val options = remember(groups, toBudgetCents) {
        listOf(MoveEndpoint(null, null, toBudgetCents)) + groups.filterNot { it.isIncome }.flatMap { group ->
            group.categories.filterNot { it.hidden }.map { item ->
                MoveEndpoint(group.name, item.name, item.balanceCents)
            }
        }
    }
    val anchor = remember(sourceGroup, category) {
        MoveEndpoint(sourceGroup.name, category.name, category.balanceCents)
    }
    var from by remember(anchor) {
        mutableStateOf(if (category.balanceCents < 0) options.first() else anchor)
    }
    var to by remember(anchor) {
        mutableStateOf(if (category.balanceCents < 0) anchor else options.first())
    }
    val calculator = remember(category, moveMode) {
        if (moveMode) CalculatorAmountState(0L, allowsNegative = false, conventionalAmountEntry = true)
        else CalculatorAmountState(category.assignedCents, allowsNegative = true)
    }
    var enteredAmount by remember(category, moveMode) {
        mutableStateOf(if (moveMode) 0L else category.assignedCents)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BudgetEntryAction(
                    Icons.Outlined.Bolt,
                    "Auto-Assign",
                    Modifier.weight(1f),
                    onClick = {
                        val expand = !autoAssignMode
                        moveMode = false
                        autoAssignMode = expand
                    },
                    selected = autoAssignMode,
                )
                BudgetEntryAction(
                    Icons.Outlined.SwapHoriz,
                    "Move Money",
                    Modifier.weight(1f),
                    onClick = {
                        val expand = !moveMode
                        autoAssignMode = false
                        moveMode = expand
                    },
                    selected = moveMode,
                )
                BudgetEntryAction(Icons.Outlined.MoreHoriz, "Details", Modifier.weight(1f), onDetails)
            }
            AnimatedVisibility(
                visible = autoAssignMode,
                enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(100)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (autoAssignChoices.isEmpty()) {
                        Text(
                            "No suggestions available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                        )
                    }
                    autoAssignChoices.forEach { (label, amount) ->
                        Surface(
                            onClick = { onSave(amount) },
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    formatMoneyCents(amount, hideDecimalPlaces),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = moveMode,
                enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(100)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MoveEndpointSelector(
                        label = "From",
                        selected = from,
                        options = options.filterNot { it.group == to.group && it.category == to.category },
                        hideDecimalPlaces = hideDecimalPlaces,
                        onSelect = { from = it },
                    )
                    IconButton(
                        onClick = { val oldFrom = from; from = to; to = oldFrom },
                        modifier = Modifier.align(Alignment.CenterHorizontally).height(30.dp),
                    ) {
                        Icon(
                            Icons.Outlined.SwapHoriz,
                            contentDescription = "Swap source and destination",
                            modifier = Modifier.height(20.dp),
                        )
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
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            InlineCalculatorAmount(
                if (moveMode) "Amount" else "Budgeted",
                enteredAmount,
                Modifier.padding(horizontal = 20.dp),
            )
            CompactCalculatorPad(
                calculator = calculator,
                conventionalAmountEntry = moveMode,
                allowSign = !moveMode,
                moveMoneyMode = moveMode,
                showDisplay = false,
                onValueChange = { enteredAmount = it },
                canFinish = { amount ->
                    !moveMode || (amount > 0L && amount <= from.balanceCents.coerceAtLeast(0L) &&
                        (from.group != to.group || from.category != to.category))
                },
                onDone = {
                    if (moveMode) {
                        calculator.finish().takeIf { it > 0L }?.let { amount ->
                            onMove(from.group, from.category, to.group, to.category, amount)
                        }
                    } else {
                        onSave(calculator.finish())
                    }
                },
            )
        }
    }
}

@Composable
private fun BudgetEntryAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
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

private enum class BudgetSummaryAction { MOVE, HOLD }

/**
 * Single entry point for the "To Budget" amount: shows the summary up top and lets the user
 * drill into "move to a category" or "hold for next month" inline, without an overflow menu or
 * a nested popup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSummarySheet(
    toBudgetCents: Long,
    bufferedCents: Long,
    groups: List<BudgetGroup>,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    onMoveToCategory: (String, String, Long) -> Unit,
    onHoldForNextMonth: (Long) -> Unit,
    onResetNextMonthBuffer: () -> Unit,
) {
    val covering = toBudgetCents < 0L
    var action by remember { mutableStateOf<BudgetSummaryAction?>(BudgetSummaryAction.MOVE) }

    val options = remember(groups) {
        groups.filterNot { it.isIncome }.flatMap { group ->
            group.categories.filterNot { it.hidden }.map { group.name to it.name }
        }
    }
    var selectedCategory by remember(options) { mutableStateOf(options.firstOrNull()) }
    var categoryPickerExpanded by remember { mutableStateOf(false) }
    val moveCalculator = remember(toBudgetCents) {
        CalculatorAmountState(kotlin.math.abs(toBudgetCents), allowsNegative = false)
    }
    var moveAmount by remember(toBudgetCents) { mutableStateOf(kotlin.math.abs(toBudgetCents)) }

    val maxHoldable = remember(toBudgetCents, bufferedCents) { maxOf(0L, toBudgetCents + bufferedCents) }
    val holdCalculator = remember(maxHoldable, bufferedCents) {
        CalculatorAmountState(bufferedCents.coerceIn(0L, maxHoldable), allowsNegative = false)
    }
    var holdAmount by remember(maxHoldable, bufferedCents) {
        mutableStateOf(bufferedCents.coerceIn(0L, maxHoldable))
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                if (covering) "Cover To Budget" else "Budget Summary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                formatMoneyCents(toBudgetCents, hideDecimalPlaces),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (covering) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (bufferedCents != 0L) {
                Text(
                    "${formatMoneyCents(bufferedCents, hideDecimalPlaces)} held for next month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BudgetEntryAction(
                    Icons.Outlined.SwapHoriz,
                    if (covering) "Cover From" else "Move to Category",
                    Modifier.weight(1f),
                    onClick = { action = if (action == BudgetSummaryAction.MOVE) null else BudgetSummaryAction.MOVE },
                    selected = action == BudgetSummaryAction.MOVE,
                )
                if (!covering) {
                    BudgetEntryAction(
                        Icons.Outlined.Savings,
                        "Hold for Next Month",
                        Modifier.weight(1f),
                        onClick = { action = if (action == BudgetSummaryAction.HOLD) null else BudgetSummaryAction.HOLD },
                        selected = action == BudgetSummaryAction.HOLD,
                    )
                }
                if (bufferedCents != 0L) {
                    BudgetEntryAction(
                        Icons.Outlined.RestartAlt,
                        "Reset Hold",
                        Modifier.weight(1f),
                        onClick = onResetNextMonthBuffer,
                    )
                }
            }
            AnimatedVisibility(
                visible = action == BudgetSummaryAction.MOVE,
                enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(100)),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Text(
                        if (covering) "Choose a category to move money from"
                        else "Choose a category to fund from To Budget",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Box(Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { categoryPickerExpanded = true },
                            enabled = options.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(selectedCategory?.let { "${it.first} · ${it.second}" } ?: "No categories available")
                        }
                        DropdownMenu(expanded = categoryPickerExpanded, onDismissRequest = { categoryPickerExpanded = false }) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("${option.first} · ${option.second}") },
                                    onClick = { selectedCategory = option; categoryPickerExpanded = false },
                                )
                            }
                        }
                    }
                    InlineCalculatorAmount("Amount", moveAmount, Modifier.padding(top = 10.dp))
                    CompactCalculatorPad(
                        calculator = moveCalculator,
                        horizontalPadding = 0.dp,
                        showDisplay = false,
                        onValueChange = { moveAmount = it },
                        onDone = {
                            selectedCategory?.let { target ->
                                moveCalculator.finish().takeIf { it > 0L }
                                    ?.let { onMoveToCategory(target.first, target.second, it) }
                            }
                        },
                    )
                }
            }
            AnimatedVisibility(
                visible = action == BudgetSummaryAction.HOLD,
                enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(100)),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Text(
                        "Set aside part or all of To Budget instead of budgeting it now",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    InlineCalculatorAmount("Amount", holdAmount)
                    CompactCalculatorPad(
                        calculator = holdCalculator,
                        horizontalPadding = 0.dp,
                        showDisplay = false,
                        onValueChange = { holdAmount = it },
                        onDone = { onHoldForNextMonth(holdCalculator.finish().coerceIn(0L, maxHoldable)) },
                    )
                }
            }
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
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
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
    onEditBudget: () -> Unit,
    onMoveMoney: () -> Unit,
    onAutoAssign: () -> Unit,
    onEditTarget: () -> Unit,
    transactions: List<Transaction>,
    onRename: () -> Unit,
    onTransactionsThisMonth: () -> Unit,
    onAllTransactions: () -> Unit,
    hidden: Boolean,
    onSetHidden: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    scheduleFunding: List<BudgetScheduleFunding> = emptyList(),
) {
    var note by remember(category) { mutableStateOf(category.note) }
    var noteEditorOpen by remember(category) { mutableStateOf(false) }
    var rollover by remember(category) { mutableStateOf(category.carryoverEnabled) }
    var deleteConfirmOpen by remember(category) { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var overflowOpen by remember(category) { mutableStateOf(false) }
    val progress = category.progressFraction
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
                Box {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Category options")
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(text = { Text("Transactions this month") }, onClick = {
                            overflowOpen = false; onTransactionsThisMonth()
                        })
                        DropdownMenuItem(text = { Text("Rename category") }, onClick = {
                            overflowOpen = false; onRename()
                        })
                        DropdownMenuItem(text = { Text(if (hidden) "Unhide category" else "Hide category") }, onClick = {
                            overflowOpen = false; onSetHidden(!hidden)
                        })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Delete category", color = MaterialTheme.colorScheme.error) }, onClick = {
                            overflowOpen = false; deleteConfirmOpen = true
                        })
                    }
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(22.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Balance", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f))
                        Text(formatMoneyCents(category.balanceCents, hideDecimalPlaces),
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(100)),
                            color = if (category.balanceCents < 0L) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SummaryValue("Budgeted", category.assignedCents, hideDecimalPlaces, Modifier.weight(1f))
                            SummaryValue("Spent", -category.spentCents, hideDecimalPlaces, Modifier.weight(1f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BudgetEntryAction(Icons.Outlined.Add, "Budget", Modifier.weight(1f), onEditBudget)
                    BudgetEntryAction(Icons.Outlined.SwapHoriz, "Move Money", Modifier.weight(1f), onMoveMoney)
                    BudgetEntryAction(Icons.Outlined.Bolt, "Auto-Assign", Modifier.weight(1f), onAutoAssign)
                }
                TargetDetailsCard(
                    category = category,
                    month = month,
                    hideDecimalPlaces = hideDecimalPlaces,
                    scheduleFunding = scheduleFunding,
                    onClick = onEditTarget,
                )
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { noteEditorOpen = true }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Note", fontWeight = FontWeight.SemiBold)
                                Text(note.ifBlank { "Add note" }, style = MaterialTheme.typography.bodySmall,
                                    color = if (note.isBlank()) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null,
                                modifier = Modifier.rotate(-90f))
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Rollover overspending", fontWeight = FontWeight.SemiBold)
                                Text("Carry overspending into the next month",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(rollover, { enabled -> rollover = enabled; onSetCarryover(enabled) })
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent activity", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = onAllTransactions) { Text("View all") }
                        }
                        if (transactions.isEmpty()) {
                            Text("No recent transactions", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                        } else transactions.forEachIndexed { index, transaction ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                            Row(Modifier.fillMaxWidth().clickable { selectedTransaction = transaction }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(transaction.payee.ifBlank { "Unknown payee" }, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOf(formatStoredDate(transaction.date), transaction.account)
                                        .filter(String::isNotBlank).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(formatMoneyCents(transaction.amountCents, hideDecimalPlaces),
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Spacer(Modifier.height(88.dp))
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
private fun TargetDetailsCard(
    category: BudgetCategory,
    month: String,
    hideDecimalPlaces: Boolean,
    scheduleFunding: List<BudgetScheduleFunding> = emptyList(),
    onClick: () -> Unit,
) {
    val target = category.target
    val linkedSchedule = target?.takeIf { it.type == BudgetTarget.Type.SCHEDULE }?.let { scheduled ->
        scheduleFunding.firstOrNull { funding ->
            val reference = scheduled.scheduleId?.takeIf(String::isNotBlank) ?: scheduled.scheduleName?.trim().orEmpty()
            reference in funding.referenceNames
        }
    }
    val title: String
    val detail: String
    val supporting: String
    when {
        category.hasUnsupportedTarget -> {
            title = if (category.automationReadOnly) "Notes-managed target" else "Advanced target"
            detail = if (category.automationReadOnly) "Managed from category notes" else "Managed in Actual Budget"
            supporting = "View target information"
        }
        category.automations.size > 1 -> {
            title = "${category.automations.size} automations"
            detail = category.automations.joinToString { it.type.label }
            supporting = "Edit automation list · Apply after whole-budget preview"
        }
        target == null -> {
            title = "Set a target"
            detail = "Plan how much to budget"
            supporting = "Auto-Assign can use your target"
        }
        else -> {
            title = target.type.label
            detail = when (target.type) {
                BudgetTarget.Type.HISTORICAL -> when (target.historicalMode) {
                    BudgetTarget.HistoricalMode.AVERAGE -> "Average of ${target.historicalMonths} recent months"
                    BudgetTarget.HistoricalMode.COPY -> "Copy ${target.historicalMonths} months ago"
                }
                BudgetTarget.Type.REMAINDER -> buildString {
                    append("Weight ${target.weight}")
                    target.limitAmountCents?.let {
                        append(" · capped ${formatMoneyCents(it, hideDecimalPlaces)} ${target.limitPeriod?.jsonValue}")
                        if (target.limitHold) append(" · hold")
                    }
                }
                BudgetTarget.Type.PERCENTAGE -> "${target.percentage}% of ${if (target.percentagePrevious) "last" else "this"} month's ${target.percentageSource}"
                BudgetTarget.Type.SCHEDULE -> target.scheduleName ?: linkedSchedule?.name ?: "No schedule linked"
                BudgetTarget.Type.REFILL -> "Refill to the category's balance cap"
                else -> formatMoneyCents(target.amountCents, hideDecimalPlaces)
            }
            val timing = when (target.type) {
                BudgetTarget.Type.FIXED -> "Every ${if (target.everyCount > 1) "${target.everyCount} " else ""}${target.period.jsonValue}${if (target.everyCount > 1) "s" else ""}"
                BudgetTarget.Type.LIMIT -> when (target.limitPeriod ?: BudgetTarget.LimitPeriod.MONTHLY) {
                    BudgetTarget.LimitPeriod.DAILY -> "Daily balance cap"
                    BudgetTarget.LimitPeriod.WEEKLY -> "Weekly balance cap"
                    BudgetTarget.LimitPeriod.MONTHLY -> "Monthly balance cap"
                }
                BudgetTarget.Type.REFILL -> "Resets every month"
                BudgetTarget.Type.BY_DATE -> target.targetMonth?.let { "Target month ${formatMonth(it)}" }
                    ?: "Target date"
                BudgetTarget.Type.HISTORICAL -> "Recalculates every month"
                BudgetTarget.Type.GOAL -> "Target only"
                BudgetTarget.Type.REMAINDER -> "After other automations"
                BudgetTarget.Type.PERCENTAGE -> "At this priority"
                BudgetTarget.Type.SCHEDULE -> linkedSchedule?.let {
                    "Due in ${formatMoneyCents(it.amountCents, hideDecimalPlaces)}"
                } ?: "Schedule-driven"
            }
            supporting = when {
                target.type == BudgetTarget.Type.LIMIT -> "$timing · Does not request funding automatically"
                target.type == BudgetTarget.Type.GOAL -> "$timing · Does not budget funds automatically"
                target.type == BudgetTarget.Type.REMAINDER -> "$timing · Applied in whole-budget preview"
                target.type == BudgetTarget.Type.PERCENTAGE -> "$timing · Applied in whole-budget preview"
                target.type == BudgetTarget.Type.REFILL -> "$timing · Applied in whole-budget preview"
                target.type == BudgetTarget.Type.SCHEDULE && linkedSchedule == null ->
                    "$timing · Linked schedule not found — tap to relink"
                target.type == BudgetTarget.Type.SCHEDULE -> "$timing · Applied in whole-budget preview"
                else -> "$timing · Auto-Assign ${formatMoneyCents(target.suggestedBudget(category, month), hideDecimalPlaces)}"
            }
        }
    }
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Target", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
                Text(supporting, style = MaterialTheme.typography.bodySmall,
                    color = if (target == null && !category.hasUnsupportedTarget) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Edit target",
                modifier = Modifier.rotate(-90f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryValue(label: String, amount: Long, hideDecimals: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text(formatMoneyCents(amount, hideDecimals), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1, textAlign = TextAlign.Center)
    }
}

private fun buildAutoAssignChoices(category: BudgetCategory, month: String): List<Pair<String, Long>> = buildList {
    category.target?.takeUnless { it.type == BudgetTarget.Type.LIMIT }?.let { target ->
        add("Target · ${target.type.label}" to target.suggestedBudget(category, month))
    }
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
    if (category.balanceCents != 0L) {
        add("Reset balance to zero" to (category.assignedCents - category.balanceCents))
    }
    if (category.assignedCents != 0L) add("Set budgeted to zero" to 0L)
}.distinctBy { it.first }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryActionsSheet(
    category: BudgetCategory,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onEditBudget: () -> Unit,
    onSetTarget: () -> Unit,
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
                SheetAction(when {
                    category.hasUnsupportedTarget -> "View target"
                    category.automations.isEmpty() -> "Set automations"
                    else -> "Edit automations"
                }, onSetTarget)
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
private fun AddBudgetSheet(onDismiss: () -> Unit, onNewCategory: () -> Unit, onNewGroup: () -> Unit,
    onApplyTemplate: (Boolean) -> Unit, onPreviewCleanup: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Text("Add to budget", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            SheetAction("New category", onNewCategory)
            SheetAction("New category group", onNewGroup)
            SheetAction("Apply budget templates", onClick = { onApplyTemplate(false) })
            SheetAction("Overwrite budget templates", onClick = { onApplyTemplate(true) })
            SheetAction("Month-end cleanup", onClick = onPreviewCleanup)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanupPreviewSheet(
    preview: CleanupPreview,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    onApply: (CleanupPreview) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Review month-end cleanup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Nothing changes until you confirm. This moves leftover balances between the source and sink " +
                    "categories defined by \"#cleanup\" notes for ${formatMonth(preview.month)}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.changes.isEmpty() && preview.goalChanges.isEmpty()) {
                Text(if (preview.isUpToDate) "No cleanup groups need changes." else "No categories are configured for cleanup.")
            } else {
                preview.changes.forEach { change ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(change.categoryName, fontWeight = FontWeight.Medium)
                            Text(change.groupName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${formatMoneyCents(change.currentCents, hideDecimalPlaces)} → ${formatMoneyCents(change.proposedCents, hideDecimalPlaces)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            preview.goalChanges.forEach { change ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(change.categoryName, fontWeight = FontWeight.Medium)
                        Text("${change.groupName} · Goal reset", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${change.currentCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "None"} → " +
                            (change.proposedCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "None"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (preview.warnings.isNotEmpty()) {
                Text(
                    preview.warnings.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (preview.invalidCategories.isNotEmpty()) {
                Text(
                    "Left untouched because their cleanup definition is unsupported: ${preview.invalidCategories.joinToString()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(enabled = preview.changes.isNotEmpty() || preview.goalChanges.isNotEmpty(),
                    onClick = { onApply(preview) }) { Text("Apply cleanup") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetTemplatePreviewSheet(
    preview: BudgetTemplatePreview,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    onApply: (BudgetTemplatePreview) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Review budget template", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (preview.overwriteExisting) {
                    "Nothing changes until you confirm. Supported automations will recalculate existing budgeted amounts for ${formatMonth(preview.month)}."
                } else {
                    "Nothing changes until you apply this preview. Categories that already have a budgeted amount will stay unchanged."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.changes.isEmpty() && preview.goalChanges.isEmpty()) {
                Text(
                    if (preview.skippedExistingCount > 0) "No unbudgeted categories need changes."
                    else "All supported targets are already up to date.",
                )
            } else {
                preview.changes.forEach { change ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(change.categoryName, fontWeight = FontWeight.Medium)
                            Text(change.groupName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${formatMoneyCents(change.currentCents, hideDecimalPlaces)} → ${formatMoneyCents(change.proposedCents, hideDecimalPlaces)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth()) {
                    Text("Net change", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(formatMoneyCents(preview.netBudgetChangeCents, hideDecimalPlaces), fontWeight = FontWeight.SemiBold)
                }
            }
            preview.goalChanges.forEach { change ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(change.categoryName, fontWeight = FontWeight.Medium)
                        Text("${change.groupName} · Goal", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${change.currentCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "None"} → " +
                            (change.proposedCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "None"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (preview.unchangedCount > 0) Text(
                "${preview.unchangedCount} supported ${if (preview.unchangedCount == 1) "target is" else "targets are"} already current.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.skippedExistingCount > 0) Text(
                "${preview.skippedExistingCount} already-budgeted ${if (preview.skippedExistingCount == 1) "category was" else "categories were"} left unchanged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.unsupportedCategories.isNotEmpty()) {
                Text(
                    "Not applied because these categories use automation types Actua cannot safely evaluate yet: ${preview.unsupportedCategories.joinToString()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (preview.limitedCategories.isNotEmpty()) {
                Text(
                    "Available funds limited: ${preview.limitedCategories.joinToString()}. Higher-priority automations were funded first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (preview.cappedCategories.isNotEmpty()) {
                Text(
                    "Remainder caps apply to: ${preview.cappedCategories.joinToString()}. " +
                        "The preview includes daily, weekly, or monthly cap and carryover behavior.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(enabled = preview.changes.isNotEmpty() || preview.goalChanges.isNotEmpty(),
                    onClick = { onApply(preview) }) { Text("Apply changes") }
            }
            Spacer(Modifier.height(16.dp))
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
