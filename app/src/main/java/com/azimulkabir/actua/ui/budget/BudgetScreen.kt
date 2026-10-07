package com.azimulkabir.actua.ui.budget

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Output
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
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
import com.azimulkabir.actua.model.budgetTotalCategories
import com.azimulkabir.actua.model.ZeroBudgetPlanner
import com.azimulkabir.actua.ui.components.CalculatorAmountState
import com.azimulkabir.actua.ui.components.CompactCalculatorPad
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.components.formatStoredDate
import com.azimulkabir.actua.ui.components.ActuaNoteEditorSheet
import com.azimulkabir.actua.ui.components.ActuaSheetTitle
import com.azimulkabir.actua.ui.components.CategoryStatusDot
import com.azimulkabir.actua.ui.components.RenameDialog
import com.azimulkabir.actua.ui.components.ActuaGroupedItem
import com.azimulkabir.actua.ui.components.ActuaHeroAmount
import com.azimulkabir.actua.ui.components.ActuaHeroSize
import com.azimulkabir.actua.ui.components.GroupPosition
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.components.ActuaSecondaryButton
import com.azimulkabir.actua.ui.components.ActuaSheetContent
import com.azimulkabir.actua.ui.components.ActuaSheetCard
import com.azimulkabir.actua.ui.components.ActuaSheetAction
import com.azimulkabir.actua.ui.components.ActuaPrimaryActionBar
import com.azimulkabir.actua.model.BudgetProgressState
import com.azimulkabir.actua.ui.theme.LocalCategoryStatusColors
import com.azimulkabir.actua.ui.theme.categoryStatusColor
import com.azimulkabir.actua.ui.theme.danger
import com.azimulkabir.actua.ui.theme.warning
import com.azimulkabir.actua.ui.theme.PillShape
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.ui.theme.Sizes
import com.azimulkabir.actua.ui.transactions.TransactionDetailsSheet
import com.azimulkabir.actua.ui.transactions.PickerTextField
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

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
    showBudgetStatusBanners: Boolean = true,
    onShowBudgetStatusBannersChange: (Boolean) -> Unit = {},
    showGroupTotals: Boolean = false,
    onShowGroupTotalsChange: (Boolean) -> Unit = {},
    hideFullySpent: Boolean = false,
    onHideFullySpentChange: (Boolean) -> Unit = {},
    categoryView: String = "All",
    onCategoryViewChange: (String) -> Unit = {},
    showCategoryFilters: Boolean = true,
    onShowCategoryFiltersChange: (Boolean) -> Unit = {},
    favoritesOnly: Boolean = false,
    onFavoritesOnlyChange: (Boolean) -> Unit = {},
    favoriteCategoryIds: Set<String> = emptySet(),
    onFavoriteCategoryChange: (String, Boolean) -> Unit = { _, _ -> },
    onSetCategoryHidden: (String, String, Boolean, onChanged: () -> Unit) -> Unit = { _, _, _, _ -> },
    onSetGroupHidden: (String, Boolean, onChanged: () -> Unit) -> Unit = { _, _, _ -> },
    onRenameCategory: (String, String, String) -> Unit = { _, _, _ -> },
    onRenameGroup: (String, String) -> Unit = { _, _ -> },
    onShowCategoryTransactions: (String, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onTransferBudget: (String?, String?, String?, String?, Long) -> Unit = { _, _, _, _, _ -> },
    /**
     * The To Budget sheet's move: funding [category] from To Budget, or (when [covering]) covering a
     * negative To Budget from it. Defaults to a plain transfer.
     */
    onSummaryMove: (group: String, category: String, amount: Long, covering: Boolean) -> Unit = { group, category, amount, covering ->
        if (covering) onTransferBudget(group, category, null, null, amount) else onTransferBudget(null, null, group, category, amount)
    },
    onSetBudgetAmount: (String, String, Long) -> Unit = { _, _, _ -> },
    onSetCategoryNote: (String, String) -> Unit = { _, _ -> },
    onSetCategoryCarryover: (String, Boolean) -> Unit = { _, _ -> },
    onHoldForNextMonth: (Long) -> Unit = {},
    onResetNextMonthBuffer: () -> Unit = {},
    onCopyPreviousMonth: () -> Unit = {},
    /** One category's budget action from its actions sheet (Actual's per-category budget menu). */
    onCategoryBudgetAction: (categoryId: String, action: CategoryBudgetAction) -> Unit = { _, _ -> },
    /** Actual's "Set budgets to N month average" for the whole month, as a preview to confirm. */
    onPreviewAverageBudget: (months: Int) -> BudgetTemplatePreview? = { null },
    /** Actual's `resetIncomeCarryover`: stops every income category holding automatically this month. */
    onResetIncomeHold: () -> Unit = {},
    onEditAutomations: (BudgetGroup, BudgetCategory) -> Unit = { _, _ -> },
    onApplyBudgetTemplate: (BudgetTemplatePreview) -> Unit = {},
    scheduleFunding: List<BudgetScheduleFunding> = emptyList(),
    /** The budget's synced `hideFraction` preference; templates round to whole units with it. */
    templateHideFraction: Boolean = false,
    onPreviewCleanup: () -> CleanupPreview = { CleanupPreview("") },
    /** Re-reads notes-managed templates before a template preview opens, as Actual does (#855). */
    onRefreshNoteTemplates: () -> Unit = {},
    onApplyCleanup: (CleanupPreview) -> Unit = {},
    onSearch: () -> Unit = {},
    onManageCategories: () -> Unit = {},
    transactions: List<Transaction> = emptyList(),
    /** Unfiltered by the Transactions tab's own status/reconciled filters, unlike [transactions]. */
    allTransactions: List<Transaction> = emptyList(),
    onShowUncategorizedTransactions: () -> Unit = {},
    onDeleteCategory: (String, String, onChanged: () -> Unit) -> Unit = { _, _, _ -> },
    onEditTransaction: (Transaction) -> Unit = {},
    onDeleteTransaction: (Transaction) -> Unit = {},
    requestedCategoryDetails: String? = null,
    onCategoryDetailsChange: (String?) -> Unit = {},
    returnToRootRequest: Int = 0,
    showNotes: Boolean = true,
    hideIncomeGroup: Boolean = false,
    hasFab: Boolean = true,
) {
    val context = LocalContext.current
    val budgetUiPreferences = remember(context) {
        context.applicationContext.getSharedPreferences("budget_ui_preferences", android.content.Context.MODE_PRIVATE)
    }
    val activeBudgetId = remember(context) { ActiveBudgetStore(context).budgetId ?: "no-budget" }
    val collapsedGroupsKey = "collapsed_groups_$activeBudgetId"
    var selectedCategory by remember { mutableStateOf<BudgetCategory?>(null) }
    var selectedGroup by remember { mutableStateOf<BudgetGroup?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var collapsedGroups by remember(activeBudgetId) {
        mutableStateOf(budgetUiPreferences.getStringSet(collapsedGroupsKey, emptySet()).orEmpty().toSet())
    }
    fun saveCollapsedGroups(value: Set<String>) {
        collapsedGroups = value
        budgetUiPreferences.edit().putStringSet(collapsedGroupsKey, value).apply()
    }
    var optionsExpanded by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var renamingCategory by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var renamingGroup by remember { mutableStateOf<BudgetGroup?>(null) }
    var movingBudget by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var fundingCategory by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var categoryDetails by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var autoAssignBudget by remember { mutableStateOf<Pair<BudgetGroup, BudgetCategory>?>(null) }
    var budgetSummaryOpen by remember { mutableStateOf(false) }
    var overspentSheetOpen by remember { mutableStateOf(false) }
    var templatePreviewOpen by remember { mutableStateOf(false) }
    var overwriteTemplates by remember { mutableStateOf(false) }
    var templateGroupTarget by remember { mutableStateOf<BudgetGroup?>(null) }
    var zeroBudgetPreviewOpen by remember { mutableStateOf(false) }
    var cleanupPreview by remember { mutableStateOf<CleanupPreview?>(null) }
    var averagePreview by remember { mutableStateOf<Pair<Int, BudgetTemplatePreview>?>(null) }
    var copyToYearEnd by remember { mutableStateOf<BudgetCategory?>(null) }
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

    val overspentCategories = remember(groups) {
        groups.filterNot { it.isIncome }.flatMap { group ->
            group.categories
                .filter { it.balanceCents < 0L && !it.carryoverEnabled && !it.hidden }
                .map { category -> group to category }
        }
    }
    val totalOverspentCents = remember(overspentCategories) {
        overspentCategories.sumOf { it.second.balanceCents }
    }
    val uncategorizedTransactions = remember(allTransactions, month) {
        uncategorizedTransactionsForBudgetMonth(allTransactions, month)
    }
    val totalUncategorizedCents = remember(uncategorizedTransactions) {
        uncategorizedTransactions.sumOf { it.amountCents }
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
            showBudgetStatusBanners = showBudgetStatusBanners,
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
            onShowBudgetStatusBannersChange = onShowBudgetStatusBannersChange,
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
        AnimatedVisibility(
            visible = showCategoryFilters,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
        ) {
            BudgetCategoryFilterRow(selected = categoryView, onSelect = onCategoryViewChange,
                favoritesOnly = favoritesOnly, onFavoritesOnlyChange = onFavoritesOnlyChange)
        }
        val selectedView = BudgetCategoryView.fromLabel(categoryView)
        // Otherwise this filters every group and category on every recomposition of
        // BudgetScreen (e.g. opening/closing any sheet), not just when the budget or these
        // display toggles actually change. Must live outside the LazyColumn content lambda,
        // which isn't a @Composable context.
        // `headerGroup` is precomputed here (once per data/toggle change) rather than via
        // `group.copy(categories = visibleCategories)` inline at each header call site, which
        // would otherwise allocate a new BudgetGroup on every recomposition of this screen.
        val trackingBudget = overview.toBudgetCents == null
        val visibleGroups = remember(groups, showHidden, hideFullySpent, selectedView, scheduleFunding, favoritesOnly, favoriteCategoryIds, hideIncomeGroup, trackingBudget) {
            groups.filter { (showHidden || !it.hidden) && !(hideIncomeGroup && it.isIncome) }.map { group ->
                val visibleCategories = group.categories.filter { category ->
                    (showHidden || !category.hidden) &&
                        (!hideFullySpent || category.available != 0) &&
                        (category.isIncome || selectedView.matches(category, scheduleFunding)) &&
                        (!favoritesOnly || category.id in favoriteCategoryIds)
                }
                // Rows follow the view filters; the header shows Actual's group totals.
                Triple(group, visibleCategories, group.copy(categories = budgetTotalCategories(group, trackingBudget)))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = if (hasFab) 96.dp else 0.dp),
        ) {
            // The Ready to Budget hero and warnings scroll with the categories; the toolbar stays.
            item(key = "budget-overview", contentType = "budget-overview") {
                AnimatedVisibility(
                    visible = showOverview,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
                    exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
                ) {
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
            }
            item(key = "overspent-warning", contentType = "overspent-warning") {
                AnimatedVisibility(
                    visible = showBudgetStatusBanners && overspentCategories.isNotEmpty(),
                    enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
                    exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
                ) {
                    OverspentWarningBanner(
                        totalOverspentCents = totalOverspentCents,
                        categoryCount = overspentCategories.size,
                        hideDecimalPlaces = hideDecimalPlaces,
                        onClick = {
                            if (overspentCategories.size == 1) {
                                val (group, category) = overspentCategories.first()
                                movingBudget = group to category
                            } else {
                                overspentSheetOpen = true
                            }
                        },
                    )
                }
            }
            item(key = "uncategorized-warning", contentType = "uncategorized-warning") {
                AnimatedVisibility(
                    visible = showBudgetStatusBanners && uncategorizedTransactions.isNotEmpty(),
                    enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
                    exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
                ) {
                    UncategorizedWarningBanner(
                        totalUncategorizedCents = totalUncategorizedCents,
                        transactionCount = uncategorizedTransactions.size,
                        hideDecimalPlaces = hideDecimalPlaces,
                        onClick = onShowUncategorizedTransactions,
                    )
                }
            }
            visibleGroups.forEach { (group, visibleCategories, headerGroup) ->
                val collapsed = group.name in collapsedGroups
                stickyHeader(
                    key = "header-${group.name}",
                    // Lets Compose reuse composition slots across the group-header type
                    // boundaries a scroll crosses, instead of diffing incompatible shapes.
                    contentType = when {
                        group.isIncome -> "income-header"
                        budgetView == "Plan" -> "plan-header"
                        else -> "table-header"
                    },
                ) {
                    val onGroupClick = {
                            saveCollapsedGroups(if (collapsed) {
                                collapsedGroups - group.name
                            } else {
                                collapsedGroups + group.name
                            })
                        }
                    if (group.isIncome) {
                        IncomeBudgetGroupHeader(
                            group = headerGroup,
                            collapsed = collapsed,
                            hideDecimalPlaces = hideDecimalPlaces,
                            onClick = onGroupClick,
                            onLongClick = { selectedGroup = group },
                        )
                    } else if (budgetView == "Plan") {
                        PlanBudgetGroupHeader(
                            group = headerGroup,
                            collapsed = collapsed,
                            showTotals = showGroupTotals,
                            hideDecimalPlaces = hideDecimalPlaces,
                            onClick = onGroupClick,
                            onLongClick = { selectedGroup = group },
                        )
                    } else {
                        BudgetGroupHeader(
                            group = headerGroup,
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
                    contentType = { _, category ->
                        when {
                            category.isIncome -> "income-row"
                            budgetView == "Plan" -> "plan-row"
                            else -> "table-row"
                        }
                    },
                ) { index, category ->
                    AnimatedVisibility(
                        visible = !collapsed,
                        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
                    ) {
                        if (category.isIncome) {
                            IncomeBudgetCategoryRow(
                                category = category,
                                position = GroupPosition.of(index, visibleCategories.size),
                                hideDecimalPlaces = hideDecimalPlaces,
                                onClick = { onShowCategoryTransactions(category.name, true, false) },
                                onLongClick = { selectedCategory = category },
                            )
                        } else if (budgetView == "Plan") {
                            PlanBudgetCategoryRow(
                                category = category,
                                showSpendingDetails = showSpent,
                                showProgressBar = showProgressBars,
                                position = GroupPosition.of(index, visibleCategories.size),
                                hideDecimalPlaces = hideDecimalPlaces,
                                onClick = { editingBudget = group to category },
                                onLongClick = { selectedCategory = category },
                                scheduleFunding = scheduleFunding,
                            )
                        } else {
                            CategoryRow(
                                category = category,
                                showSpent = showSpent,
                                showProgressBar = showProgressBars,
                                position = GroupPosition.of(index, visibleCategories.size),
                                onLongClick = { selectedCategory = category },
                                onOpen = { editingBudget = group to category },
                                hideDecimalPlaces = hideDecimalPlaces,
                                scheduleFunding = scheduleFunding,
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
            budgetActions = category.id != null && (!category.isIncome || overview.toBudgetCents == null),
            onBudgetAction = { action ->
                selectedCategory = null
                if (action == CategoryBudgetAction.COPY_TO_YEAR_END) copyToYearEnd = category
                else category.id?.let { onCategoryBudgetAction(it, action) }
            },
            // Envelope income categories hold automatically through their carryover flag.
            incomeHold = category.carryoverEnabled.takeIf { category.isIncome && overview.toBudgetCents != null && category.id != null },
            onSetIncomeHold = { enabled ->
                selectedCategory = null
                onSetCategoryCarryover(category.id.orEmpty(), enabled)
            },
            favorite = category.id in favoriteCategoryIds,
            onFavoriteChange = { favorite ->
                category.id?.let { onFavoriteCategoryChange(it, favorite) }
                selectedCategory = null
            },
            hidden = category.hidden,
            onSetHidden = { hidden ->
                onSetCategoryHidden(parent.name, category.name, hidden) { selectedCategory = null }
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
                onSetGroupHidden(group.name, hidden) { selectedGroup = null }
            },
            onResetIncomeHold = if (group.isIncome && overview.toBudgetCents != null) ({
                selectedGroup = null
                onResetIncomeHold()
            }) else null,
            onApplyTemplate = { overwrite ->
                selectedGroup = null
                templateGroupTarget = group
                overwriteTemplates = overwrite
                onRefreshNoteTemplates()
                templatePreviewOpen = true
            },
        )
    }
    if (showAddSheet) {
        AddBudgetSheet(onDismiss = { showAddSheet = false },
            onApplyTemplate = { overwrite ->
                showAddSheet = false
                overwriteTemplates = overwrite
                onRefreshNoteTemplates()
                templatePreviewOpen = true
            },
            onPreviewCleanup = {
                showAddSheet = false
                cleanupPreview = onPreviewCleanup()
            },
            onPreviewZeroBudget = {
                showAddSheet = false
                zeroBudgetPreviewOpen = true
            },
            onPreviewAverage = { months ->
                showAddSheet = false
                averagePreview = onPreviewAverageBudget(months)?.let { months to it }
            })
    }
    if (zeroBudgetPreviewOpen) {
        // A tracking budget has no "To Budget" amount.
        val trackingBudget = overview.toBudgetCents == null
        val zeroBudgetPreview = remember(groups, month, trackingBudget) {
            ZeroBudgetPlanner.preview(groups, month, trackingBudget)
        }
        BudgetTemplatePreviewSheet(
            preview = zeroBudgetPreview,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { zeroBudgetPreviewOpen = false },
            onApply = { preview -> onApplyBudgetTemplate(preview); zeroBudgetPreviewOpen = false },
            title = "Set budgets to zero",
            description = "Nothing changes until you apply this preview. Every category's budgeted amount for ${formatMonth(month)} will be reset to zero.",
            upToDateMessage = "Every category is already at zero.",
            unchangedLabel = if (zeroBudgetPreview.unchangedCount == 1) "category is" else "categories are",
        )
    }
    averagePreview?.let { (months, preview) ->
        BudgetTemplatePreviewSheet(
            preview = preview,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { averagePreview = null },
            onApply = { onApplyBudgetTemplate(it); averagePreview = null },
            title = averageBudgetTitle(months),
            description = "Nothing changes until you apply this preview. Each visible category's budget for " +
                "${formatMonth(month)} will be set to its average activity over up to $months months.",
            upToDateMessage = "Every category is already at its average.",
            unchangedLabel = if (preview.unchangedCount == 1) "category is" else "categories are",
        )
    }
    copyToYearEnd?.let { category ->
        AlertDialog(
            onDismissRequest = { copyToYearEnd = null },
            title = { Text("Copy to the rest of the year?") },
            text = {
                Text("${category.name}'s budget of ${formatMoneyCents(category.assignedCents, hideDecimalPlaces)} " +
                    "will replace its budget in every month after ${formatMonth(month)} through December.")
            },
            confirmButton = {
                TextButton(onClick = {
                    category.id?.let { onCategoryBudgetAction(it, CategoryBudgetAction.COPY_TO_YEAR_END) }
                    copyToYearEnd = null
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { copyToYearEnd = null }) { Text("Cancel") } },
        )
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
        val templateGroups = templateGroupTarget?.let { target -> groups.filter { it.name == target.name } } ?: groups
        BudgetTemplatePreviewSheet(
            preview = remember(templateGroups, month, overview.toBudgetCents, overwriteTemplates, scheduleFunding, templateHideFraction) {
                BudgetTemplatePlanner.preview(
                    templateGroups, month, overview.toBudgetCents ?: Long.MAX_VALUE, overwriteTemplates,
                    scheduleFunding, templateHideFraction,
                )
            },
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { templatePreviewOpen = false; templateGroupTarget = null },
            onApply = { preview -> onApplyBudgetTemplate(preview); templatePreviewOpen = false; templateGroupTarget = null },
            title = templateGroupTarget?.let { "Review budget template · ${it.name}" } ?: "Review budget template",
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
            scheduleFunding = scheduleFunding,
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
                onSummaryMove(group, category, amount, (overview.toBudgetCents ?: 0L) < 0L)
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
        // Otherwise this filters and sorts the whole transaction list on every recomposition
        // of BudgetScreen while the details sheet is open, to keep only the top 3.
        val recentCategoryTransactions = remember(transactions, category.name) {
            transactions.filter { it.category == category.name }
                .sortedByDescending { it.date }.take(3)
        }
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
            transactions = recentCategoryTransactions,
            onRename = { categoryDetails = null; renamingCategory = group to category },
            onTransactionsThisMonth = {
                categoryDetails = null; onShowCategoryTransactions(category.name, true, true)
            },
            onAllTransactions = {
                categoryDetails = null; onShowCategoryTransactions(category.name, false, true)
            },
            hidden = category.hidden,
            favorite = category.id in favoriteCategoryIds,
            onFavoriteChange = { favorite -> category.id?.let { onFavoriteCategoryChange(it, favorite) } },
            onSetHidden = { hidden ->
                onSetCategoryHidden(group.name, category.name, hidden) { categoryDetails = null }
            },
            onDelete = {
                onDeleteCategory(group.name, category.name) { categoryDetails = null }
            },
            onEditTransaction = onEditTransaction,
            onDeleteTransaction = onDeleteTransaction,
            scheduleFunding = scheduleFunding,
            showNotes = showNotes,
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
            scheduleFunding = scheduleFunding,
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
    if (overspentSheetOpen) {
        OverspentCategoriesSheet(
            categories = overspentCategories,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { overspentSheetOpen = false },
            onSelect = { group, category ->
                overspentSheetOpen = false
                movingBudget = group to category
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
            scheduleFunding = scheduleFunding,
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
    showBudgetStatusBanners: Boolean,
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
    onShowBudgetStatusBannersChange: (Boolean) -> Unit,
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                .clickable { monthPickerOpen = true }
                .padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Text(
                formatMonth(month),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Choose month",
                    modifier = Modifier.padding(4.dp).size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Box {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Row {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search Actua")
                    }
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "Add to budget")
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
                ToggleMenuItem("Show warnings", showBudgetStatusBanners, onShowBudgetStatusBannersChange)
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
    favoritesOnly: Boolean,
    onFavoritesOnlyChange: (Boolean) -> Unit,
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
        FilterChip(
            selected = favoritesOnly,
            onClick = { onFavoritesOnlyChange(!favoritesOnly) },
            label = { Text("Favorites") },
            leadingIcon = { Icon(if (favoritesOnly) Icons.Filled.Star else Icons.Outlined.StarBorder, null) },
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

private fun formatMonth(month: String): String = java.time.YearMonth.parse(month)
    .format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.getDefault()))

/**
 * On-budget transactions with no category in [month] ("yyyy-MM"). Actua's category fallback
 * ([Transaction.category] literally "Uncategorized") already excludes off-budget accounts,
 * transfers ("") and split parents ("Split"), so this only needs to match that literal and date.
 */
internal fun uncategorizedTransactionsForBudgetMonth(transactions: List<Transaction>, month: String): List<Transaction> {
    val monthDigits = month.replace("-", "")
    return transactions.filter { it.category == "Uncategorized" && it.date.filter(Char::isDigit).startsWith(monthDigits) }
}

@Composable
private fun ToggleMenuItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
        onClick = { onChange(!checked) },
    )
}

/** Ready to Budget as the shared medium hero, inside the primary (or error, when negative) container. */
@Composable
private fun PlanBudgetOverview(
    overview: BudgetOverview,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
) {
    val (leadLabel, leadCents) = overview.lead("Ready to Budget")
    val ready = leadCents ?: 0L
    val contentColor = if (ready >= 0L) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onErrorContainer
    Surface(
        onClick = onClick,
        color = if (ready >= 0L) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
    ) {
        ActuaHeroAmount(
            amount = leadCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "—",
            modifier = Modifier.padding(vertical = Spacing.sm),
            caption = leadLabel,
            amountColor = contentColor,
            captionColor = contentColor.copy(alpha = 0.78f),
            size = ActuaHeroSize.Medium,
            action = {
                if (overview.bufferedCents != 0L) {
                    Text(
                        "${formatMoneyCents(overview.bufferedCents, hideDecimalPlaces)} held for next month",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                }
            },
        )
    }
}

@Composable
private fun OverspentWarningBanner(
    totalOverspentCents: Long,
    categoryCount: Int,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
) {
    // Tinted with the (user-configurable) Overspent status color, like an overspent category's
    // balance pill, so the banner and the rows it points to read as the same severity.
    val overspent = categoryStatusColor(BudgetProgressState.OVERSPENT)
    BudgetWarningCard(
        icon = Icons.Outlined.ErrorOutline,
        title = if (categoryCount == 1) "1 category overspent" else "$categoryCount categories overspent",
        subtitle = "Tap to cover overspending",
        amount = formatMoneyCents(totalOverspentCents, hideDecimalPlaces),
        containerColor = overspent.copy(alpha = 0.16f),
        contentColor = overspent,
        onClick = onClick,
    )
}

@Composable
private fun UncategorizedWarningBanner(
    totalUncategorizedCents: Long,
    transactionCount: Int,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
) {
    // A to-do rather than a problem, so it takes the warning tone instead of the overspent red.
    val warning = MaterialTheme.colorScheme.warning
    BudgetWarningCard(
        icon = Icons.Outlined.Category,
        title = if (transactionCount == 1) "1 uncategorized transaction" else "$transactionCount uncategorized transactions",
        subtitle = "Tap to categorize",
        amount = formatMoneyCents(totalUncategorizedCents, hideDecimalPlaces),
        containerColor = warning.copy(alpha = 0.16f),
        contentColor = warning,
        onClick = onClick,
    )
}

/** A tinted icon row card: icon, title over a hint, the amount and a chevron. */
@Composable
private fun BudgetWarningCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    amount: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.formRowMinHeight)
                .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Spacer(Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    color = contentColor)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.85f))
            }
            Text(amount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = contentColor, modifier = Modifier.padding(start = Spacing.sm))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = contentColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverspentCategoriesSheet(
    categories: List<Pair<BudgetGroup, BudgetCategory>>,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    onSelect: (BudgetGroup, BudgetCategory) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ActuaSheetContent {
            Column {
                ActuaSheetTitle("Cover Overspending")
                Text(
                    "Choose a category to cover",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
            ActuaSheetCard {
                categories.forEachIndexed { index, (group, category) ->
                    if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                    PreviewChangeRow(
                        title = category.name,
                        subtitle = group.name,
                        value = formatMoneyCents(category.balanceCents, hideDecimalPlaces),
                        valueColor = MaterialTheme.colorScheme.error,
                        onClick = { onSelect(group, category) },
                    )
                }
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
    val compactHeader = LocalConfiguration.current.screenWidthDp < 400 || LocalDensity.current.fontScale >= 1.5f
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.md)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (collapsed) "Expand ${group.name}" else "Collapse ${group.name}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp).rotate(rotation),
            )
            Text(
                if (group.hidden) "${group.name} · Hidden" else group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (group.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (showTotals) {
                // Budgeted only fits beside Balance on wider screens at normal font sizes.
                if (collapsed && !compactHeader) {
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
    position: GroupPosition,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    scheduleFunding: List<BudgetScheduleFunding> = emptyList(),
) {
    ActuaGroupedItem(position = position, dividerInset = Spacing.lg) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = Sizes.compactRowMinHeight)
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                .padding(start = Spacing.lg, end = Spacing.md, top = Spacing.md, bottom = Spacing.md),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryStatusDot(category.statusState(scheduleFunding), modifier = Modifier.padding(end = 8.dp))
                    Text(
                        if (category.hidden) "${category.name} · Hidden" else category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                category.target?.let {
                    Text(it.type.label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            BalancePill(
                category.balanceCents,
                hideDecimalPlaces,
                status = category.statusState(scheduleFunding),
                colorByStatus = LocalCategoryStatusColors.current?.colorBudgetedAmounts == true,
                textStyle = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                horizontalPadding = 10.dp,
                verticalPadding = 3.dp,
            )
        }
        if (showProgressBar && category.showsProgressBar) {
            CategoryProgressBar(category, scheduleFunding,
                Modifier.fillMaxWidth().padding(top = 9.dp).height(5.dp))
        }
            if (showSpendingDetails) {
                Row(
                    modifier = Modifier.padding(top = if (showProgressBar && category.showsProgressBar) 2.dp else 1.dp),
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
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.md)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (collapsed) "Expand ${group.name}" else "Collapse ${group.name}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp).rotate(rotation),
            )
            Text(
                if (group.hidden) "${group.name} · Hidden" else group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (group.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
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
    position: GroupPosition,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ActuaGroupedItem(position = position, dividerInset = Spacing.lg) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.compactRowMinHeight).combinedClickable(
                role = Role.Button, onClick = onClick, onLongClick = onLongClick,
            ).padding(horizontal = Spacing.lg, vertical = Spacing.md),
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
}

@Composable
private fun BudgetOverviewRow(
    overview: BudgetOverview,
    showSpent: Boolean,
    hideDecimalPlaces: Boolean,
    onToBudgetClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (leadLabel, leadCents) = overview.lead()
                OverviewCell(
                    leadLabel,
                    leadCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "—",
                    Modifier.weight(1.35f),
                    Alignment.Start,
                    positive = leadCents?.let { it > 0 } == true,
                    negative = leadCents?.let { it < 0 } == true,
                    pill = true,
                    pillOffset = (-8).dp,
                    onClick = onToBudgetClick,
                )
                OverviewCell("Budgeted", formatMoneyCents(overview.budgetedCents, hideDecimalPlaces), Modifier.weight(1f), Alignment.End)
                if (showSpent) OverviewCell("Spent", formatMoneyCents(overview.spentCents, hideDecimalPlaces), Modifier.weight(1f), Alignment.End)
                OverviewCell("Balance", formatMoneyCents(overview.availableCents, hideDecimalPlaces), Modifier.weight(1f), Alignment.End,
                    positive = overview.availableCents > 0, negative = overview.availableCents < 0,
                    pill = true, pillOffset = 8.dp)
            }
            if (overview.bufferedCents != 0L) {
                Text(
                    "${formatMoneyCents(overview.bufferedCents, hideDecimalPlaces)} held for next month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
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
    negative: Boolean = false,
    pill: Boolean = false,
    pillOffset: androidx.compose.ui.unit.Dp = 0.dp,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
        if (pill) {
            val pillModifier = Modifier.offset(x = pillOffset)
            // Same rule as a category's BalancePill: only a negative amount takes color.
            val pillColor = when {
                negative -> colors.danger.copy(alpha = 0.16f)
                positive -> colors.surfaceContainerHighest
                else -> Color.Transparent
            }
            val textColor = when {
                negative -> colors.danger
                positive -> colors.onSurface
                else -> colors.onSurfaceVariant.copy(alpha = 0.55f)
            }
            if (onClick != null) {
                Surface(onClick = onClick, modifier = pillModifier, color = pillColor,
                    shape = MaterialTheme.shapes.small) {
                    OverviewPillAmount(amount, textColor)
                }
            } else {
                Surface(modifier = pillModifier, color = pillColor, shape = MaterialTheme.shapes.small) {
                    OverviewPillAmount(amount, textColor)
                }
            }
        } else {
            Text(amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = colors.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun OverviewPillAmount(amount: String, color: Color) {
    Text(amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
        color = color, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
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

    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.md)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1.35f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (collapsed) "Expand ${group.name}" else "Collapse ${group.name}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp).rotate(rotation),
                )
                Text(if (group.hidden) "${group.name} · Hidden" else group.name,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = if (group.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Scoped to just the part that actually changes size (totals shown/hidden) instead
            // of the whole sticky-header row, so the icon/name on the left — which never
            // resizes — doesn't pay for an extra measure/layout pass on every scroll frame.
            Row(
                // This is repeated for every visible category row. Toggling the optional
                // Spent column should update its width once rather than animate remeasurement
                // of the whole LazyColumn.
                modifier = Modifier.weight(if (showSpent) 2f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showTotals) {
                    AmountColumn("Budgeted", budgeted, Modifier.weight(1f), hideDecimalPlaces)
                    if (showSpent) AmountColumn("Spent", -spent, Modifier.weight(1f), hideDecimalPlaces, muted = spent == 0L)
                } else {
                    Spacer(Modifier.weight(if (showSpent) 2f else 1f))
                }
            }
            if (showTotals) {
                AmountColumn("Balance", balance, Modifier.weight(1f), hideDecimalPlaces, balance = true)
            } else {
                Spacer(Modifier.weight(1f))
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
        Text(formatMoneyCents(amount, hideDecimalPlaces), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (balance) FontWeight.Bold else FontWeight.SemiBold,
            color = when {
                balance && amount < 0L -> MaterialTheme.colorScheme.danger
                muted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.onSurface
            }, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    category: BudgetCategory,
    showSpent: Boolean,
    showProgressBar: Boolean,
    position: GroupPosition,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    hideDecimalPlaces: Boolean,
    scheduleFunding: List<BudgetScheduleFunding> = emptyList(),
) {
    ActuaGroupedItem(position = position, dividerInset = Spacing.lg) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.compactRowMinHeight)
                .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1.35f), verticalAlignment = Alignment.CenterVertically) {
                    CategoryStatusDot(category.statusState(scheduleFunding), modifier = Modifier.padding(end = 6.dp))
                    Text(if (category.hidden) "${category.name} · Hidden" else category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (category.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
                        status = category.statusState(scheduleFunding),
                        colorByStatus = LocalCategoryStatusColors.current?.colorBudgetedAmounts == true,
                        modifier = Modifier.offset(x = 10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 10.dp,
                        verticalPadding = 3.dp,
                    )
                }
            }
            AnimatedVisibility(visible = showProgressBar && category.showsProgressBar) {
                CategoryProgressBar(category, scheduleFunding,
                    Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp))
            }
        }
    }
}

@Composable
private fun CategoryProgressBar(
    category: BudgetCategory,
    scheduleFunding: List<BudgetScheduleFunding>,
    modifier: Modifier = Modifier,
) {
    val fraction = category.progressFraction(scheduleFunding)
    val colors = MaterialTheme.colorScheme
    val color = categoryStatusColor(category.progressBarState(scheduleFunding))
    val percent = kotlin.math.round(fraction * 100).toInt()
    val description = if (category.usesGoalProgress) {
        "$percent percent funded toward goal"
    } else "${category.progressState.label}, spent $percent percent of available"
    LinearProgressIndicator(
        progress = { fraction },
        modifier = modifier.clip(PillShape).semantics { stateDescription = description },
        color = color,
        trackColor = if (!category.usesGoalProgress && category.progressState == BudgetProgressState.FUNDED)
            color.copy(alpha = 0.25f) else colors.surfaceContainerHighest,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

private enum class EditBudgetMode { NONE, AUTO_ASSIGN, MOVE }

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
    scheduleFunding: List<BudgetScheduleFunding> = emptyList(),
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onSave: (Long) -> Unit,
    onMove: (String?, String?, String?, String?, Long) -> Unit,
) {
    var moveMode by remember(category, startInMoveMode) { mutableStateOf(startInMoveMode) }
    var autoAssignMode by remember(category, startInMoveMode, startInAutoAssignMode) {
        mutableStateOf(startInAutoAssignMode)
    }
    val autoAssignChoices = remember(category, month, scheduleFunding) {
        buildAutoAssignChoices(category, month, scheduleFunding)
    }
    val options = remember(groups, toBudgetCents) {
        listOf(MoveEndpoint(null, null, toBudgetCents)) + groups.filterNot { it.isIncome }.flatMap { group ->
            group.categories.filterNot { it.hidden }.map { item ->
                MoveEndpoint(group.name, item.name, item.balanceCents)
            }
        }
    }
    // Only categories with balance available can fund a move, so the "From" picker is
    // narrower than the "To" picker (which must still be able to target any category,
    // including an overspent one — that's how covering overspending works).
    val fromOptions = remember(options) { options.filter { it.balanceCents > 0L } }
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
    val coroutineScope = rememberCoroutineScope()
    // Picker rows show a readable label per endpoint; a category name shared by two groups gets
    // its group appended so every label maps back to exactly one endpoint.
    val endpointLabels = remember(options) {
        val duplicateNames = options.groupingBy { it.title }.eachCount().filterValues { it > 1 }.keys
        options.associateWith { endpoint ->
            if (endpoint.title in duplicateNames && endpoint.subtitle != null) "${endpoint.title} (${endpoint.subtitle})"
            else endpoint.title
        }
    }
    val endpointByLabel = remember(endpointLabels) { endpointLabels.entries.associate { (endpoint, label) -> label to endpoint } }
    val endpointBalances = remember(endpointLabels, hideDecimalPlaces) {
        endpointLabels.entries.associate { (endpoint, label) -> label to formatMoneyCents(endpoint.balanceCents, hideDecimalPlaces) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActuaSheetTitle(category.name, Modifier.weight(1f))
                IconButton(onClick = {
                    coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { onDetails() }
                }) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "Details")
                }
            }
            val caretTransition = rememberInfiniteTransition(label = "budget amount cursor")
            val caretAlpha by caretTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(520), repeatMode = RepeatMode.Reverse),
                label = "budget amount cursor alpha",
            )
            ActuaHeroAmount(
                amount = formatMoneyCents(enteredAmount, false),
                caption = if (moveMode) "Amount" else "Budgeted",
                captionColor = MaterialTheme.colorScheme.onSurfaceVariant,
                size = ActuaHeroSize.Medium,
                amountTrailing = {
                    Box(
                        Modifier.padding(start = 3.dp).height(28.dp).width(2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = caretAlpha)),
                    )
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                BudgetModeButton(
                    text = "Auto-Assign",
                    icon = Icons.Outlined.Bolt,
                    selected = autoAssignMode,
                    modifier = Modifier.weight(1f),
                ) {
                    val expand = !autoAssignMode
                    moveMode = false
                    autoAssignMode = expand
                }
                BudgetModeButton(
                    text = "Move Money",
                    icon = Icons.Outlined.SwapHoriz,
                    selected = moveMode,
                    modifier = Modifier.weight(1f),
                ) {
                    val expand = !moveMode
                    autoAssignMode = false
                    moveMode = expand
                }
            }
            val entryMode = when {
                autoAssignMode -> EditBudgetMode.AUTO_ASSIGN
                moveMode -> EditBudgetMode.MOVE
                else -> EditBudgetMode.NONE
            }
            AnimatedContent(
                targetState = entryMode,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 6 }) togetherWith
                        fadeOut(tween(120)) using
                            SizeTransform(sizeAnimationSpec = { _, _ -> snap() }, clip = false)
                },
                label = "Budget entry mode",
            ) { currentMode ->
                when (currentMode) {
                    EditBudgetMode.AUTO_ASSIGN -> ActuaFormCard(
                        Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
                    ) {
                        if (autoAssignChoices.isEmpty()) {
                            Text(
                                "No suggestions available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                            )
                        }
                        autoAssignChoices.forEachIndexed { index, (label, amount) ->
                            if (index > 0) ActuaCardDivider()
                            ActuaFormRow(
                                icon = Icons.Outlined.Bolt,
                                label = label,
                                value = null,
                                supportingValue = formatMoneyCents(amount, hideDecimalPlaces),
                                onClick = { onSave(amount) },
                            )
                        }
                    }
                    EditBudgetMode.MOVE -> Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
                    ) {
                        ActuaFormCard {
                            PickerTextField(
                                label = "From",
                                value = endpointLabels[from] ?: from.title,
                                options = fromOptions.filterNot { it.group == to.group && it.category == to.category }
                                    .map { endpointLabels.getValue(it) },
                                onValueChange = { label -> endpointByLabel[label]?.let { from = it } },
                                supportingValues = endpointBalances,
                                rowIcon = Icons.Outlined.Output,
                            )
                            ActuaCardDivider()
                            PickerTextField(
                                label = "To",
                                value = endpointLabels[to] ?: to.title,
                                options = options.filterNot { it.group == from.group && it.category == from.category }
                                    .map { endpointLabels.getValue(it) },
                                onValueChange = { label -> endpointByLabel[label]?.let { to = it } },
                                supportingValues = endpointBalances,
                                rowIcon = Icons.Outlined.Input,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(start = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Available to move: ${formatMoneyCents(from.balanceCents.coerceAtLeast(0L), hideDecimalPlaces)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            // Moving the whole overspent amount into the target saves keying it in; it is
                            // capped by what the source has available so the move always stays valid.
                            val coverAmount = minOf(-to.balanceCents, from.balanceCents)
                            if (to.balanceCents < 0L && from.balanceCents > 0L) {
                                TextButton(onClick = {
                                    calculator.setCents(coverAmount)
                                    enteredAmount = coverAmount
                                }) {
                                    Text(
                                        "Cover ${formatMoneyCents(coverAmount, hideDecimalPlaces)}",
                                        maxLines = 1,
                                    )
                                }
                            }
                            IconButton(onClick = { val oldFrom = from; from = to; to = oldFrom }) {
                                Icon(Icons.Outlined.SwapVert, contentDescription = "Swap source and destination")
                            }
                        }
                    }
                    EditBudgetMode.NONE -> Box(Modifier.fillMaxWidth())
                }
            }
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

/** A mode toggle for the budget amount sheet: filled while its mode is open, tonal otherwise. */
@Composable
private fun BudgetModeButton(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(Sizes.secondaryButtonHeight),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(icon, contentDescription = null)
            Text(text, maxLines = 1, modifier = Modifier.padding(start = Spacing.sm))
        }
    } else {
        ActuaSecondaryButton(text = text, onClick = onClick, modifier = modifier, icon = icon)
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
        shape = MaterialTheme.shapes.large,
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
    ShrinkToFitText(
        formatMoneyCents(amount, hideDecimalPlaces), style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Normal,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End, modifier = modifier,
    )
}

private val MinShrinkFontSize = 9.sp

/** Single-line text that steps its font size down (to [MinShrinkFontSize]) until it fits its width. */
@Composable
private fun ShrinkToFitText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val baseSize = style.fontSize
    var fontSize by remember(text, baseSize) { mutableStateOf(baseSize) }
    Text(
        text = text,
        style = style,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontSize.value > MinShrinkFontSize.value) {
                fontSize = (fontSize.value - 0.5f).coerceAtLeast(MinShrinkFontSize.value).sp
            }
        },
    )
}

@Composable
/**
 * A category's balance on a pill. By default only a negative balance takes color: a tonal pill
 * tinted with its [status] color, so overspending is what stands out. With [colorByStatus] (opt-in,
 * see Settings > Budget) every balance takes its status color, as in YNAB. Other balances sit on a faint neutral
 * pill, except an unassigned zero, which keeps the pill's footprint (so digits stay aligned) but
 * drops its fill and dims the amount to read as "nothing here".
 */
private fun BalancePill(
    amount: Long,
    hideDecimalPlaces: Boolean,
    status: BudgetProgressState,
    colorByStatus: Boolean = false,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
    fontWeight: FontWeight = FontWeight.SemiBold,
    horizontalPadding: androidx.compose.ui.unit.Dp = 8.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 2.dp,
) {
    val colors = MaterialTheme.colorScheme
    val statusColor = categoryStatusColor(status)
    val tone = balancePillTone(amount, status, colorByStatus)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = when (tone) {
            BalancePillTone.ALERT -> statusColor.copy(alpha = 0.16f)
            BalancePillTone.NEUTRAL -> colors.surfaceContainerHighest
            BalancePillTone.EMPTY -> Color.Transparent
        },
    ) {
        ShrinkToFitText(
            text = formatMoneyCents(amount, hideDecimalPlaces),
            style = textStyle,
            fontWeight = if (tone == BalancePillTone.EMPTY) FontWeight.Normal else fontWeight,
            color = when (tone) {
                BalancePillTone.ALERT -> statusColor
                BalancePillTone.NEUTRAL -> colors.onSurface
                BalancePillTone.EMPTY -> colors.onSurfaceVariant.copy(alpha = 0.55f)
            },
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
        )
    }
}

/** How loudly a category's balance pill is drawn; see [BalancePill]. */
internal enum class BalancePillTone { ALERT, NEUTRAL, EMPTY }

internal fun balancePillTone(
    amount: Long,
    status: BudgetProgressState,
    colorByStatus: Boolean = false,
): BalancePillTone = when {
    amount < 0L -> BalancePillTone.ALERT
    amount == 0L && status == BudgetProgressState.UNASSIGNED -> BalancePillTone.EMPTY
    colorByStatus -> BalancePillTone.ALERT
    else -> BalancePillTone.NEUTRAL
}

private enum class BudgetSummaryAction { MOVE, HOLD }

/**
 * The amount the To Budget sheet actually moves, or null when nothing can move. Funding a category
 * from To Budget is limited to To Budget, as Actual's `transferAvailable` clamps it; covering a
 * negative To Budget is limited to the source category's positive balance, as `coverOverbudgeted`
 * does.
 */
internal fun budgetSummaryMoveAmount(enteredCents: Long, toBudgetCents: Long, sourceBalanceCents: Long?): Long? {
    val limit = if (toBudgetCents < 0L) sourceBalanceCents ?: 0L else toBudgetCents
    return minOf(enteredCents, limit).takeIf { it > 0L }
}

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

    // When covering an overbudgeted "To Budget", only categories with balance available can
    // fund the cover, so the picker is narrowed instead of listing every category.
    val options = remember(groups, covering) {
        groups.filterNot { it.isIncome }.flatMap { group ->
            group.categories.filterNot { it.hidden }
                .filter { !covering || it.balanceCents > 0L }
                .map { group.name to it.name }
        }
    }
    var selectedCategory by remember(options) { mutableStateOf(options.firstOrNull()) }
    val selectedBalance = remember(groups, selectedCategory) {
        selectedCategory?.let { (groupName, categoryName) ->
            groups.firstOrNull { it.name == groupName && !it.isIncome }
                ?.categories?.firstOrNull { it.name == categoryName }?.balanceCents
        }
    }
    val moveLimit = if (covering) (selectedBalance ?: 0L).coerceAtLeast(0L) else toBudgetCents.coerceAtLeast(0L)
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
    val optionLabels = remember(options) { options.associateBy { "${it.first} · ${it.second}" } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ActuaSheetTitle(if (covering) "Cover To Budget" else "Budget Summary")
            ActuaHeroAmount(
                amount = formatMoneyCents(toBudgetCents, hideDecimalPlaces),
                amountColor = if (covering) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                size = ActuaHeroSize.Medium,
                supportingText = if (bufferedCents != 0L) {
                    "${formatMoneyCents(bufferedCents, hideDecimalPlaces)} held for next month"
                } else null,
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                BudgetModeButton(
                    text = if (covering) "Cover From" else "Move to Category",
                    icon = Icons.Outlined.SwapHoriz,
                    selected = action == BudgetSummaryAction.MOVE,
                    modifier = Modifier.fillMaxWidth(),
                ) { action = if (action == BudgetSummaryAction.MOVE) null else BudgetSummaryAction.MOVE }
                if (!covering) {
                    BudgetModeButton(
                        text = "Hold for Next Month",
                        icon = Icons.Outlined.Savings,
                        selected = action == BudgetSummaryAction.HOLD,
                        modifier = Modifier.fillMaxWidth(),
                    ) { action = if (action == BudgetSummaryAction.HOLD) null else BudgetSummaryAction.HOLD }
                }
            }
            if (bufferedCents != 0L) {
                ActuaSheetCard {
                    ActuaSheetAction("Reset Hold", icon = Icons.Outlined.RestartAlt, onClick = onResetNextMonthBuffer)
                }
            }
            AnimatedContent(
                targetState = action,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 6 }) togetherWith
                        fadeOut(tween(120)) using
                            SizeTransform(sizeAnimationSpec = { _, _ -> snap() }, clip = false)
                },
                label = "Budget summary action",
            ) { currentAction ->
                when (currentAction) {
                    BudgetSummaryAction.MOVE -> Column(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            if (covering) "Choose a category to move money from"
                            else "Choose a category to fund from To Budget",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                        )
                        ActuaFormCard {
                            PickerTextField(
                                label = "Category",
                                value = selectedCategory?.let { "${it.first} · ${it.second}" }.orEmpty(),
                                options = optionLabels.keys.toList(),
                                onValueChange = { label -> optionLabels[label]?.let { selectedCategory = it } },
                                rowIcon = Icons.Outlined.Category,
                                placeholder = "No categories available",
                            )
                        }
                        InlineCalculatorAmount("Amount", moveAmount)
                        Text(
                            "Up to ${formatMoneyCents(moveLimit, hideDecimalPlaces)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                        )
                        CompactCalculatorPad(
                            calculator = moveCalculator,
                            horizontalPadding = 0.dp,
                            showDisplay = false,
                            onValueChange = { moveAmount = it },
                            onDone = {
                                selectedCategory?.let { target ->
                                    budgetSummaryMoveAmount(moveCalculator.finish(), toBudgetCents, selectedBalance)
                                        ?.let { onMoveToCategory(target.first, target.second, it) }
                                }
                            },
                        )
                    }
                    BudgetSummaryAction.HOLD -> Column(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            "Set aside part or all of To Budget instead of budgeting it now",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.xs),
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
                    null -> Box(Modifier.fillMaxWidth())
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
    favorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    scheduleFunding: List<BudgetScheduleFunding> = emptyList(),
    showNotes: Boolean = true,
) {
    var note by remember(category) { mutableStateOf(category.note) }
    var noteEditorOpen by remember(category) { mutableStateOf(false) }
    var rollover by remember(category) { mutableStateOf(category.carryoverEnabled) }
    var deleteConfirmOpen by remember(category) { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var overflowOpen by remember(category) { mutableStateOf(false) }
    BackHandler(onBack = onDismiss)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            ActuaScreenHeader(title = category.name, onBack = onDismiss) {
                Box {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Category options")
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (favorite) "Remove from favorites" else "Add to favorites") },
                            leadingIcon = { Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null) },
                            onClick = { overflowOpen = false; onFavoriteChange(!favorite) },
                        )
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
            Text(
                formatMonth(month),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            )
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = Spacing.screenHorizontal)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Spacer(Modifier.height(Spacing.xs))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        ActuaHeroAmount(
                            amount = formatMoneyCents(category.balanceCents, hideDecimalPlaces),
                            caption = "Balance",
                            amountColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            captionColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                            size = ActuaHeroSize.Medium,
                        )
                        if (category.showsProgressBar) {
                            CategoryProgressBar(category, scheduleFunding,
                                Modifier.fillMaxWidth().height(5.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SummaryValue("Budgeted", category.assignedCents, hideDecimalPlaces, Modifier.weight(1f))
                            SummaryValue("Spent", -category.spentCents, hideDecimalPlaces, Modifier.weight(1f))
                        }
                    }
                }
                ActuaFormCard {
                    ActuaFormRow(
                        icon = Icons.Outlined.Add,
                        label = "Budget",
                        value = formatMoneyCents(category.assignedCents, hideDecimalPlaces),
                        onClick = onEditBudget,
                    )
                    ActuaCardDivider()
                    ActuaFormRow(icon = Icons.Outlined.SwapHoriz, label = "Move Money", value = null, onClick = onMoveMoney)
                    ActuaCardDivider()
                    ActuaFormRow(icon = Icons.Outlined.Bolt, label = "Auto-Assign", value = null, onClick = onAutoAssign)
                }
                ActuaFormCard {
                    TargetDetailsRow(
                        category = category,
                        month = month,
                        hideDecimalPlaces = hideDecimalPlaces,
                        scheduleFunding = scheduleFunding,
                        onClick = onEditTarget,
                    )
                    if (showNotes) {
                        ActuaCardDivider()
                        ActuaFormRow(
                            icon = Icons.AutoMirrored.Outlined.Notes,
                            label = "Note",
                            value = note.ifBlank { "Add note" },
                            valueIsPlaceholder = note.isBlank(),
                            onClick = { noteEditorOpen = true },
                        )
                    }
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Replay,
                        label = "Rollover overspending",
                        value = null,
                        caption = "Carry overspending into the next month",
                        checked = rollover,
                        onClick = {
                            val enabled = !rollover
                            rollover = enabled
                            onSetCarryover(enabled)
                        },
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    ActuaGroupLabel("Recent activity", Modifier.weight(1f))
                    TextButton(onClick = onAllTransactions) { Text("View all") }
                }
                ActuaFormCard {
                    if (transactions.isEmpty()) {
                        Text("No recent transactions", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg))
                    } else transactions.forEachIndexed { index, transaction ->
                        if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                        Row(Modifier.fillMaxWidth().heightIn(min = Sizes.compactRowMinHeight)
                            .clickable { selectedTransaction = transaction }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
        ActuaNoteEditorSheet(
            initialNote = note,
            placeholder = "Category note",
            onDismiss = { noteEditorOpen = false },
            onSave = {
                note = it
                onSaveNote(it)
                noteEditorOpen = false
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

/** The category's target as an icon row in Category details' settings card. */
@Composable
private fun TargetDetailsRow(
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
    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.formRowMinHeight)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.TrackChanges, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Target", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            Text(supporting, style = MaterialTheme.typography.bodySmall,
                color = if (target == null && !category.hasUnsupportedTarget) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = "Edit target",
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun buildAutoAssignChoices(
    category: BudgetCategory,
    month: String,
    scheduleFunding: List<BudgetScheduleFunding>,
): List<Pair<String, Long>> = buildList {
    category.target?.takeUnless { it.type == BudgetTarget.Type.LIMIT }?.let { target ->
        add("Target · ${target.type.label}" to target.suggestedBudget(category, month, scheduleFunding))
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
    favorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    hidden: Boolean,
    onSetHidden: (Boolean) -> Unit,
    budgetActions: Boolean = false,
    onBudgetAction: (CategoryBudgetAction) -> Unit = {},
    /** The envelope income category's "automatically hold" flag; null hides the action. */
    incomeHold: Boolean? = null,
    onSetIncomeHold: (Boolean) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle(category.name)
            if (budgetActions) {
                ActuaSheetCard {
                    CategoryBudgetAction.entries.forEachIndexed { index, action ->
                        if (index > 0) ActuaCardDivider()
                        ActuaSheetAction(action.label, icon = action.icon, onClick = { onBudgetAction(action) })
                    }
                }
            }
            incomeHold?.let { holding ->
                ActuaSheetCard {
                    ActuaSheetAction(
                        if (holding) "Stop holding automatically" else "Hold automatically for next month",
                        icon = Icons.Outlined.Replay,
                        onClick = { onSetIncomeHold(!holding) },
                    )
                }
            }
            ActuaSheetCard {
                if (!category.isIncome) {
                    ActuaSheetAction("Edit budgeted amount", icon = Icons.Outlined.Calculate, onClick = onEditBudget)
                    ActuaCardDivider()
                    if (category.available != 0) {
                        ActuaSheetAction(if (category.available < 0) "Cover overspending" else "Move money",
                            icon = Icons.Outlined.SwapHoriz, onClick = onMoveMoney)
                        ActuaCardDivider()
                    }
                    ActuaSheetAction(when {
                        category.hasUnsupportedTarget -> "View target"
                        category.automations.isEmpty() -> "Set automations"
                        else -> "Edit automations"
                    }, icon = Icons.Outlined.AutoAwesome, onClick = onSetTarget)
                    ActuaCardDivider()
                    ActuaSheetAction("Budget details", icon = Icons.Outlined.Info, onClick = onDetails)
                    ActuaCardDivider()
                }
                ActuaSheetAction("Transactions this month", icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    onClick = onTransactionsThisMonth)
                ActuaCardDivider()
                ActuaSheetAction("All transactions", icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                    onClick = onAllTransactions)
            }
            ActuaSheetCard {
                ActuaSheetAction(
                    if (favorite) "Remove from favorites" else "Add to favorites",
                    icon = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    onClick = { onFavoriteChange(!favorite) },
                )
                ActuaCardDivider()
                ActuaSheetAction("Rename category", icon = Icons.Outlined.Edit, onClick = onRename)
                ActuaCardDivider()
                ActuaSheetAction(
                    if (hidden) "Unhide category" else "Hide category",
                    icon = if (hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    destructive = !hidden,
                    onClick = { onSetHidden(!hidden) },
                )
            }
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
        ActuaSheetContent {
            ActuaSheetTitle(category.name)
            ActuaSheetCard {
                ActuaSheetAction("Edit budgeted amount", icon = Icons.Outlined.Calculate, onClick = onEditAssigned)
                ActuaCardDivider()
                ActuaSheetAction(
                    if (category.balanceCents < 0L) "Cover overspending" else "Move money",
                    icon = Icons.Outlined.SwapHoriz,
                    onClick = onMoveMoney,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupActionsSheet(group: BudgetGroup, onDismiss: () -> Unit, onRename: () -> Unit,
    hidden: Boolean, onSetHidden: (Boolean) -> Unit, onApplyTemplate: (Boolean) -> Unit,
    onResetIncomeHold: (() -> Unit)? = null) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle(group.name)
            onResetIncomeHold?.let { reset ->
                ActuaSheetCard {
                    ActuaSheetAction("Stop all automatic holds this month", icon = Icons.Outlined.RestartAlt, onClick = reset)
                }
            }
            if (!group.isIncome && !hidden) {
                ActuaSheetCard {
                    ActuaSheetAction("Apply budget templates", icon = Icons.Outlined.AutoAwesome,
                        onClick = { onApplyTemplate(false) })
                    ActuaCardDivider()
                    ActuaSheetAction("Overwrite budget templates", icon = Icons.Outlined.RestartAlt,
                        onClick = { onApplyTemplate(true) })
                }
            }
            ActuaSheetCard {
                ActuaSheetAction("Rename group", icon = Icons.Outlined.Edit, onClick = onRename)
                if (!group.isIncome || hidden) {
                    ActuaCardDivider()
                    ActuaSheetAction(
                        if (hidden) "Unhide group" else "Hide group",
                        icon = if (hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        destructive = !hidden,
                        onClick = { onSetHidden(!hidden) },
                    )
                }
            }
        }
    }
}

/** Actual's per-category budget menu actions (loot-core `budget/actions.ts`). */
enum class CategoryBudgetAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    COPY_LAST_MONTH("Copy last month's budget", Icons.Outlined.Replay),
    AVERAGE_3("Set to 3-month average", Icons.Outlined.Calculate),
    AVERAGE_6("Set to 6-month average", Icons.Outlined.Calculate),
    AVERAGE_12("Set to yearly average", Icons.Outlined.Calculate),
    COPY_TO_YEAR_END("Copy to the rest of the year", Icons.Outlined.SwapHoriz),
}

private fun averageBudgetTitle(months: Int) =
    if (months == 12) "Set budgets to yearly average" else "Set budgets to $months-month average"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetSheet(onDismiss: () -> Unit,
    onApplyTemplate: (Boolean) -> Unit, onPreviewCleanup: () -> Unit, onPreviewZeroBudget: () -> Unit,
    onPreviewAverage: (Int) -> Unit = {}) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle("Add to budget")
            ActuaSheetCard {
                ActuaSheetAction("Apply budget templates", icon = Icons.Outlined.AutoAwesome,
                    onClick = { onApplyTemplate(false) })
                ActuaCardDivider()
                ActuaSheetAction("Overwrite budget templates", icon = Icons.Outlined.RestartAlt,
                    onClick = { onApplyTemplate(true) })
                ActuaCardDivider()
                ActuaSheetAction("Month-end cleanup", icon = Icons.Outlined.CleaningServices, onClick = onPreviewCleanup)
            }
            ActuaSheetCard {
                listOf(3, 6, 12).forEachIndexed { index, months ->
                    if (index > 0) ActuaCardDivider()
                    ActuaSheetAction(averageBudgetTitle(months), icon = Icons.Outlined.Calculate,
                        onClick = { onPreviewAverage(months) })
                }
            }
            ActuaSheetCard {
                ActuaSheetAction("Set budgets to zero", icon = Icons.Outlined.DeleteSweep, destructive = true,
                    onClick = onPreviewZeroBudget)
            }
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
    val hasChanges = preview.changes.isNotEmpty() || preview.goalChanges.isNotEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PreviewSheetLayout(
            title = "Review month-end cleanup",
            description = "Nothing changes until you confirm. This moves leftover balances between the source and sink " +
                "categories defined by \"#cleanup\" notes for ${formatMonth(preview.month)}.",
            confirmLabel = "Apply cleanup",
            confirmEnabled = hasChanges,
            onConfirm = { onApply(preview) },
        ) {
            if (!hasChanges) {
                PreviewNote(if (preview.isUpToDate) "No cleanup groups need changes." else "No categories are configured for cleanup.",
                    MaterialTheme.colorScheme.onSurface)
            }
            if (preview.changes.isNotEmpty()) ActuaSheetCard {
                preview.changes.forEachIndexed { index, change ->
                    if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                    PreviewChangeRow(
                        title = change.categoryName,
                        subtitle = change.groupName,
                        value = "${formatMoneyCents(change.currentCents, hideDecimalPlaces)} → ${formatMoneyCents(change.proposedCents, hideDecimalPlaces)}",
                    )
                }
            }
            if (preview.goalChanges.isNotEmpty()) ActuaSheetCard {
                preview.goalChanges.forEachIndexed { index, change ->
                    if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                    PreviewChangeRow(
                        title = change.categoryName,
                        subtitle = "${change.groupName} · Goal reset",
                        value = "${change.currentCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "None"} → " +
                            (change.proposedCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "None"),
                    )
                }
            }
            if (preview.warnings.isNotEmpty()) {
                PreviewNote(preview.warnings.joinToString("\n"), MaterialTheme.colorScheme.tertiary)
            }
            if (preview.invalidCategories.isNotEmpty()) {
                PreviewNote(
                    "Left untouched because their cleanup definition is unsupported: ${preview.invalidCategories.joinToString()}.",
                    MaterialTheme.colorScheme.error,
                )
            }
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
    title: String = "Review budget template",
    description: String = if (preview.overwriteExisting) {
        "Nothing changes until you confirm. Supported automations will recalculate existing budgeted amounts for ${formatMonth(preview.month)}."
    } else {
        "Nothing changes until you apply this preview. Categories that already have a budgeted amount will stay unchanged."
    },
    upToDateMessage: String = if (preview.skippedExistingCount > 0) "No unbudgeted categories need changes."
    else "All supported targets are already up to date.",
    unchangedLabel: String = if (preview.unchangedCount == 1) "supported target is" else "supported targets are",
) {
    val hasChanges = preview.changes.isNotEmpty() || preview.goalChanges.isNotEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PreviewSheetLayout(
            title = title,
            description = description,
            confirmLabel = "Apply changes",
            confirmEnabled = hasChanges,
            onConfirm = { onApply(preview) },
        ) {
            if (!hasChanges) PreviewNote(upToDateMessage, MaterialTheme.colorScheme.onSurface)
            if (preview.changes.isNotEmpty()) ActuaSheetCard {
                preview.changes.forEachIndexed { index, change ->
                    if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                    PreviewChangeRow(
                        title = change.categoryName,
                        subtitle = change.groupName,
                        value = "${formatMoneyCents(change.currentCents, hideDecimalPlaces)} → ${formatMoneyCents(change.proposedCents, hideDecimalPlaces)}",
                    )
                }
                ActuaCardDivider(inset = Spacing.none)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Net change", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(formatMoneyCents(preview.netBudgetChangeCents, hideDecimalPlaces), fontWeight = FontWeight.SemiBold)
                }
            }
            if (preview.goalChanges.isNotEmpty()) ActuaSheetCard {
                preview.goalChanges.forEachIndexed { index, change ->
                    if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                    PreviewChangeRow(
                        title = change.categoryName,
                        subtitle = "${change.groupName} · Goal",
                        value = "${change.currentCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "None"} → " +
                            (change.proposedCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "None"),
                    )
                }
            }
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            if (preview.unchangedCount > 0) PreviewNote("${preview.unchangedCount} $unchangedLabel already current.", muted)
            if (preview.skippedExistingCount > 0) PreviewNote(
                "${preview.skippedExistingCount} already-budgeted ${if (preview.skippedExistingCount == 1) "category was" else "categories were"} left unchanged.",
                muted,
            )
            if (preview.unsupportedCategories.isNotEmpty()) PreviewNote(
                "Not applied because these categories use automation types Actua cannot safely evaluate yet: ${preview.unsupportedCategories.joinToString()}.",
                MaterialTheme.colorScheme.error,
            )
            if (preview.limitedCategories.isNotEmpty()) PreviewNote(
                "Available funds limited: ${preview.limitedCategories.joinToString()}. Higher-priority automations were funded first.",
                MaterialTheme.colorScheme.tertiary,
            )
            if (preview.cappedCategories.isNotEmpty()) PreviewNote(
                "Remainder caps apply to: ${preview.cappedCategories.joinToString()}. " +
                    "The preview includes daily, weekly, or monthly cap and carryover behavior.",
                MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/**
 * A preview sheet: title and explanation, the scrollable [content] cards, and the confirm action
 * pinned below them. Nothing is applied until [onConfirm]; dismissing the sheet cancels.
 */
@Composable
private fun PreviewSheetLayout(
    title: String,
    description: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column {
                ActuaSheetTitle(title)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
            content()
        }
        ActuaPrimaryActionBar(text = confirmLabel, onClick = onConfirm, enabled = confirmEnabled,
            icon = Icons.Outlined.Check)
    }
}

/** A category row in a preview or picker card: name over group, and the value on the right. */
@Composable
private fun PreviewChangeRow(
    title: String,
    subtitle: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.compactRowMinHeight)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor,
            modifier = Modifier.padding(start = Spacing.sm))
    }
}

/** An explanatory line under a preview's cards, aligned with the card text. */
@Composable
private fun PreviewNote(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color,
        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal + Spacing.xs))
}
