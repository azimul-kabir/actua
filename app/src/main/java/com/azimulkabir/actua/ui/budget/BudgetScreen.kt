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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material.icons.outlined.TrackChanges
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.CategoryDeletePlan
import com.azimulkabir.actua.ui.categories.CategoryDeleteDialog
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
import com.azimulkabir.actua.ui.components.ActuaSwitchMenuItem
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
import com.azimulkabir.actua.ui.navigation.floatingBarClearance

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
    /** Actual's budget months; the month picker offers only these. Null allows any month. */
    monthRange: ClosedRange<java.time.YearMonth>? = null,
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
    showBalancePills: Boolean = true,
    onShowBalancePillsChange: (Boolean) -> Unit = {},
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
    /** Loan-paired category id → the month its payment target is skipped for; template runs pass over it. */
    loanSnoozedMonths: Map<String, String> = emptyMap(),
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
    onDeleteCategory: (group: String, category: String, transferToId: String?, onChanged: () -> Unit) -> Unit =
        { _, _, _, _ -> },
    /** What deleting a category needs ([CategoryDeletePlan]); null when it can't be determined. */
    loadCategoryDeletePlan: suspend (group: String, category: String) -> CategoryDeletePlan? = { _, _ -> null },
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

    CompositionLocalProvider(LocalShowBalancePills provides showBalancePills) { BoxWithConstraints(modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetToolbar(
                month = month,
                onMonthChange = onMonthChange,
                monthRange = monthRange,
                optionsExpanded = optionsExpanded,
                showSpent = showSpent,
                showProgressBars = showProgressBars,
                budgetView = budgetView,
                showOverview = showOverview,
                showBudgetStatusBanners = showBudgetStatusBanners,
                showGroupTotals = showGroupTotals,
                showBalancePills = showBalancePills,
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
                onShowBalancePillsChange = onShowBalancePillsChange,
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

            // One decision for every table header and row, so their columns stay lined up.
            val tableLayout = rememberTableLayout(
                screenWidth - (Spacing.screenHorizontal + TableRowPadding) * 2,
                visibleGroups, showSpent, showGroupTotals, hideDecimalPlaces,
                overview = overview.takeIf { showOverview && budgetView != "Plan" },
            )
            val tableStacked = budgetView != "Plan" && tableLayout == null
            CompositionLocalProvider(LocalTableLayout provides tableLayout) { LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = floatingBarClearance() + if (hasFab) 96.dp else 0.dp),
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
                                stacked = tableStacked,
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
                                    stacked = tableStacked,
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
            } }
        }
    } }

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
            title = stringResource(R.string.budget_set_budgets_zero),
            description = stringResource(
                R.string.budget_zero_intro,
                formatMonth(month),
            ),
            upToDateMessage = stringResource(R.string.budget_zero_up_to_date),
            unchangedPlural = R.plurals.budget_categories_already_current,
        )
    }
    averagePreview?.let { (months, preview) ->
        BudgetTemplatePreviewSheet(
            preview = preview,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { averagePreview = null },
            onApply = { onApplyBudgetTemplate(it); averagePreview = null },
            title = averageBudgetTitle(months),
            description = stringResource(
                R.string.budget_average_intro,
                formatMonth(month),
                months,
            ),
            upToDateMessage = stringResource(R.string.budget_average_up_to_date),
            unchangedPlural = R.plurals.budget_categories_already_current,
        )
    }
    copyToYearEnd?.let { category ->
        AlertDialog(
            onDismissRequest = { copyToYearEnd = null },
            title = { Text(stringResource(R.string.budget_copy_year_end_title)) },
            text = {
                Text(stringResource(
                    R.string.budget_copy_year_end_body,
                    category.name,
                    formatMoneyCents(category.assignedCents, hideDecimalPlaces),
                    formatMonth(month),
                ))
            },
            confirmButton = {
                TextButton(onClick = {
                    category.id?.let { onCategoryBudgetAction(it, CategoryBudgetAction.COPY_TO_YEAR_END) }
                    copyToYearEnd = null
                }) { Text(stringResource(R.string.budget_copy)) }
            },
            dismissButton = { TextButton(onClick = { copyToYearEnd = null }) { Text(stringResource(R.string.common_cancel)) } },
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
            preview = remember(templateGroups, month, overview.toBudgetCents, overwriteTemplates, scheduleFunding, templateHideFraction, loanSnoozedMonths) {
                BudgetTemplatePlanner.preview(
                    templateGroups, month, overview.toBudgetCents ?: Long.MAX_VALUE, overwriteTemplates,
                    scheduleFunding, templateHideFraction,
                    snoozedCategoryIds = loanSnoozedMonths.filterValues { it == month }.keys,
                )
            },
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { templatePreviewOpen = false; templateGroupTarget = null },
            onApply = { preview -> onApplyBudgetTemplate(preview); templatePreviewOpen = false; templateGroupTarget = null },
            title = templateGroupTarget?.let { stringResource(R.string.budget_review_template_named, it.name) }
                ?: stringResource(R.string.budget_review_template),
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
    renamingCategory?.let { (group, category) -> RenameDialog(stringResource(R.string.budget_rename_category), category.name,
        onDismiss = { renamingCategory = null }, onSave = { name ->
            onRenameCategory(group.name, category.name, name); renamingCategory = null
        }) }
    renamingGroup?.let { group -> RenameDialog(stringResource(R.string.budget_rename_group), group.name,
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
        val recentCategoryTransactions = remember(allTransactions, category.name) {
            recentTransactionsForCategory(allTransactions, category.name)
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
            loadDeletePlan = { loadCategoryDeletePlan(group.name, category.name) },
            onDelete = { transferToId ->
                onDeleteCategory(group.name, category.name, transferToId) { categoryDetails = null }
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
    monthRange: ClosedRange<java.time.YearMonth>?,
    optionsExpanded: Boolean,
    showSpent: Boolean,
    showProgressBars: Boolean,
    budgetView: String,
    showOverview: Boolean,
    showBudgetStatusBanners: Boolean,
    showGroupTotals: Boolean,
    showBalancePills: Boolean,
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
    onShowBalancePillsChange: (Boolean) -> Unit,
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
                    contentDescription = stringResource(R.string.budget_choose_month),
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
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.common_search_actua))
                    }
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.budget_add_to_budget))
                    }
                    IconButton(onClick = onManageCategories) {
                        Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = stringResource(R.string.budget_manage_categories))
                    }
                    IconButton(onClick = { onOptionsChange(true) }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.budget_display_options))
                    }
                }
            }
            DropdownMenu(
                expanded = optionsExpanded,
                onDismissRequest = { onOptionsChange(false) },
            ) {
                ActuaSwitchMenuItem(stringResource(R.string.budget_plan_view), budgetView == "Plan", onCheckedChange = {
                    onBudgetViewChange(if (it) "Plan" else "Table")
                    onOptionsChange(false)
                })
                HorizontalDivider()
                ActuaSwitchMenuItem(stringResource(R.string.budget_show_overview), showOverview, onShowOverviewChange)
                ActuaSwitchMenuItem(stringResource(R.string.budget_show_warnings), showBudgetStatusBanners, onShowBudgetStatusBannersChange)
                ActuaSwitchMenuItem(
                    if (budgetView == "Plan") stringResource(R.string.budget_show_spending_details)
                    else stringResource(R.string.budget_show_spent_column),
                    showSpent,
                    onShowSpentChange,
                )
                ActuaSwitchMenuItem(stringResource(R.string.budget_show_progress_bars), showProgressBars, onShowProgressBarsChange)
                ActuaSwitchMenuItem(stringResource(R.string.budget_show_group_totals), showGroupTotals, onShowGroupTotalsChange)
                ActuaSwitchMenuItem(stringResource(R.string.budget_show_balance_pills), showBalancePills, onShowBalancePillsChange)
                HorizontalDivider()
                ActuaSwitchMenuItem(stringResource(R.string.budget_hide_fully_spent), hideFullySpent, onHideFullySpentChange)
                ActuaSwitchMenuItem(stringResource(R.string.budget_show_hidden), showHidden, onShowHiddenChange)
                ActuaSwitchMenuItem(stringResource(R.string.budget_show_filters), showCategoryFilters, onShowCategoryFiltersChange)
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.budget_expand_all_groups)) }, onClick = onExpandAll)
                DropdownMenuItem(text = { Text(stringResource(R.string.budget_collapse_all_groups)) }, onClick = onCollapseAll)
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.budget_copy_last_month)) }, onClick = onCopyPreviousMonth)
            }
        }
    }
    if (monthPickerOpen) {
        BudgetMonthPicker(
            selectedMonth = month,
            range = monthRange,
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
                label = { Text(stringResource(view.labelRes)) },
            )
        }
        FilterChip(
            selected = favoritesOnly,
            onClick = { onFavoritesOnlyChange(!favoritesOnly) },
            label = { Text(stringResource(R.string.budget_favorites)) },
            leadingIcon = { Icon(if (favoritesOnly) Icons.Filled.Star else Icons.Outlined.StarBorder, null) },
        )
    }
}

@Composable
private fun BudgetMonthPicker(
    selectedMonth: String,
    range: ClosedRange<java.time.YearMonth>?,
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
                IconButton(onClick = { displayedYear-- }, enabled = range == null || displayedYear > range.start.year) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.budget_previous_year))
                }
                Text(
                    displayedYear.toString(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { displayedYear++ }, enabled = range == null || displayedYear < range.endInclusive.year) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = stringResource(R.string.budget_next_year))
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
                                enabled = range == null || java.time.YearMonth.of(displayedYear, monthNumber) in range,
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
            }) { Text(stringResource(R.string.budget_current_month)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun formatMonth(month: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return java.time.YearMonth.parse(month)
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", locale))
}

/** Newest [limit] transactions in [categoryName]; callers pass the unfiltered list (#1207). */
internal fun recentTransactionsForCategory(
    transactions: List<Transaction>,
    categoryName: String,
    limit: Int = 3,
): List<Transaction> =
    transactions.filter { it.category == categoryName }.sortedByDescending { it.date }.take(limit)

/**
 * On-budget transactions with no category in [month] ("yyyy-MM"). Actua's category fallback
 * ([Transaction.category] literally "Uncategorized") already excludes off-budget accounts,
 * transfers ("") and split parents ("Split"), so this only needs to match that literal and date.
 */
internal fun uncategorizedTransactionsForBudgetMonth(transactions: List<Transaction>, month: String): List<Transaction> {
    val monthDigits = month.replace("-", "")
    return transactions.filter { it.category == "Uncategorized" && it.date.filter(Char::isDigit).startsWith(monthDigits) }
}

/** The overview lead cell's localized label; [toBudgetLabelRes] is the envelope variant's label. */
@Composable
private fun overviewLeadLabel(overview: BudgetOverview, toBudgetLabelRes: Int): String =
    when (overview.leadKind) {
        BudgetOverview.LeadKind.TO_BUDGET -> stringResource(toBudgetLabelRes)
        BudgetOverview.LeadKind.PROJECTED_SAVINGS -> stringResource(R.string.budget_projected_savings)
        BudgetOverview.LeadKind.OVERSPENT -> stringResource(R.string.budget_overspent)
        BudgetOverview.LeadKind.SAVED -> stringResource(R.string.budget_saved)
    }

/** Ready to Budget as the shared medium hero, inside the primary (or error, when negative) container. */
@Composable
private fun PlanBudgetOverview(
    overview: BudgetOverview,
    hideDecimalPlaces: Boolean,
    onClick: () -> Unit,
) {
    val leadLabel = overviewLeadLabel(overview, R.string.budget_ready_to_budget)
    val leadCents = overview.leadValue
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
                        stringResource(
                            R.string.budget_held_for_next_month,
                            formatMoneyCents(overview.bufferedCents, hideDecimalPlaces),
                        ),
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
        title = pluralStringResource(R.plurals.budget_overspent_warning, categoryCount, categoryCount),
        subtitle = stringResource(R.string.budget_tap_to_cover),
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
        title = pluralStringResource(R.plurals.budget_uncategorized_warning, transactionCount, transactionCount),
        subtitle = stringResource(R.string.budget_tap_to_categorize),
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
                ActuaSheetTitle(stringResource(R.string.budget_cover_overspending))
                Text(
                    stringResource(R.string.budget_choose_category_to_cover),
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
                contentDescription = if (collapsed) stringResource(R.string.budget_expand_group, group.name)
                else stringResource(R.string.budget_collapse_group, group.name),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp).rotate(rotation),
            )
            Text(
                if (group.hidden) stringResource(R.string.budget_hidden_name, group.name) else group.name,
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
                    AmountColumn(stringResource(R.string.budget_budgeted), assigned, Modifier.widthIn(min = 92.dp), hideDecimalPlaces)
                }
                AmountColumn(
                    stringResource(R.string.budget_balance),
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
                    Text(stringResource(it.type.labelRes), style = MaterialTheme.typography.labelSmall,
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
                hasTarget = category.target != null,
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
                    stringResource(R.string.budget_budgeted_amount, formatMoneyCents(category.assignedCents, hideDecimalPlaces)),
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
                    stringResource(R.string.budget_spent_amount, formatMoneyCents(category.spentCents, hideDecimalPlaces)),
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
                contentDescription = if (collapsed) stringResource(R.string.budget_expand_group, group.name)
                else stringResource(R.string.budget_collapse_group, group.name),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp).rotate(rotation),
            )
            Text(
                if (group.hidden) stringResource(R.string.budget_hidden_name, group.name) else group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (group.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Text(
                stringResource(R.string.budget_received_amount, formatMoneyCents(received, hideDecimalPlaces)),
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
                if (category.hidden) stringResource(R.string.budget_hidden_name, category.name) else category.name,
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
        Column(Modifier.fillMaxWidth().padding(horizontal = TableRowPadding, vertical = Spacing.md)) {
            val leadLabel = overviewLeadLabel(overview, R.string.budget_to_budget)
            val leadCents = overview.leadValue
            val leadAmount = leadCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: "—"
            val budgeted = formatMoneyCents(overview.budgetedCents, hideDecimalPlaces)
            val spent = formatMoneyCents(overview.spentCents, hideDecimalPlaces)
            val balance = formatMoneyCents(overview.availableCents, hideDecimalPlaces)
            val leadCell = @Composable { modifier: Modifier, valueMinHeight: androidx.compose.ui.unit.Dp ->
                OverviewCell(
                    leadLabel,
                    leadAmount,
                    modifier,
                    Alignment.Start,
                    positive = leadCents?.let { it > 0 } == true,
                    negative = leadCents?.let { it < 0 } == true,
                    pill = true,
                    pillOffset = (-8).dp,
                    onClick = onToBudgetClick,
                    valueMinHeight = valueMinHeight,
                )
            }
            val amountCells = @Composable { cell: (Int) -> Modifier, valueMinHeight: androidx.compose.ui.unit.Dp ->
                OverviewCell(stringResource(R.string.budget_budgeted), budgeted, cell(0), Alignment.End, valueMinHeight = valueMinHeight)
                if (showSpent) OverviewCell(stringResource(R.string.budget_spent), spent, cell(1), Alignment.End, valueMinHeight = valueMinHeight)
                OverviewCell(stringResource(R.string.budget_balance), balance, cell(2), Alignment.End,
                    positive = overview.availableCents > 0, negative = overview.availableCents < 0,
                    pill = true, pillOffset = 8.dp, valueMinHeight = valueMinHeight)
            }
            val layout = LocalTableLayout.current
            BoxWithConstraints {
                val fits = layout != null || tableAmountsFit(
                    maxWidth,
                    lead = TableAmount(leadAmount, OverviewPillPadding * 2),
                    amounts = listOfNotNull(
                        TableAmount(budgeted),
                        if (showSpent) TableAmount(spent) else null,
                        TableAmount(balance, OverviewPillPadding * 2),
                    ),
                )
                if (fits) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The To budget chip is a button (Material's 48dp minimum height), so every value gets
                        // that height: the four labels and amounts then share one row.
                        leadCell(Modifier.weight(LeadColumnWeight), OverviewButtonHeight)
                        // Same columns as the table below, so the totals line up with its amounts.
                        amountCells({ column ->
                            when {
                                layout == null -> Modifier.weight(1f)
                                column == 0 -> Modifier.width(layout.budgeted)
                                column == 1 -> Modifier.width(layout.spent)
                                else -> Modifier.width(layout.balance)
                            }
                        }, OverviewButtonHeight)
                    }
                } else {
                    // Too narrow (small screen or large font) for four columns: To budget gets its
                    // own line so the other amounts share the full width.
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        leadCell(Modifier.fillMaxWidth(), OverviewButtonHeight)
                        StackedTableAmounts {
                            Row(verticalAlignment = Alignment.CenterVertically) { amountCells({ Modifier.weight(1f) }, OverviewStackedValueHeight) }
                        }
                    }
                }
            }
            if (overview.bufferedCents != 0L) {
                Text(
                    stringResource(
                        R.string.budget_held_for_next_month,
                        formatMoneyCents(overview.bufferedCents, hideDecimalPlaces),
                    ),
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
    /** The value area's minimum height, so cells of different heights (a button chip, a plain amount) align. */
    valueMinHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
        Box(
            Modifier.heightIn(min = valueMinHeight),
            contentAlignment = if (alignment == Alignment.Start) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            if (pill) {
                val pillModifier = Modifier.offset(x = pillOffset)
                // Same rule as a category's BalancePill: only a negative amount takes color. The To
                // budget chip ([onClick]) keeps its fill without balance pills; it's a button.
                val pillColor = when {
                    onClick == null && !LocalShowBalancePills.current -> Color.Transparent
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
                TableAmountText(amount, fontWeight = FontWeight.SemiBold, color = colors.onSurface,
                    modifier = Modifier.padding(start = TableAmountGap))
            }
        }
    }
}

private val OverviewPillPadding = 8.dp

/** Material's minimum interactive height, which the To budget chip (a button) takes. */
private val OverviewButtonHeight = 48.dp

/** In the stacked overview only the Balance pill adds height; this evens the three amount cells out. */
private val OverviewStackedValueHeight = 28.dp

@Composable
private fun OverviewPillAmount(amount: String, color: Color) {
    TableAmountText(amount, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier.padding(horizontal = OverviewPillPadding, vertical = 2.dp))
}

/**
 * The table view's leading (name / To budget) column weight; each amount column weighs 1. Names
 * ellipsize sooner so amounts keep one line on narrow screens and large fonts.
 */
private const val LeadColumnWeight = 1f

/**
 * How far a table amount may shrink to stay on one line (further than Actuali's
 * `.minimumScaleFactor(0.75)`, so one line holds in more cases). Below this the table's columns
 * count as too narrow and the layout stacks instead.
 */
private const val TableAmountMinScale = 0.6f

/**
 * How far an amount may shrink once its row has already stacked (the last resort): on the smallest
 * phones at large font scales even a full-width third can't hold 7-digit amounts at
 * [TableAmountMinScale], and small digits beat clipped ones.
 */
private const val TableAmountLastResortScale = 0.5f

/** The smallest scale [TableAmountText] may shrink to here; see [StackedTableAmounts]. */
private val LocalTableAmountMinScale = compositionLocalOf { TableAmountMinScale }

/** Amounts in a stacked table layout, which may shrink to [TableAmountLastResortScale]. */
@Composable
private fun StackedTableAmounts(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTableAmountMinScale provides TableAmountLastResortScale, content = content)
}

/** A table-view amount: one line, shrinking down to [TableAmountMinScale] of [style] to fit. */
@Composable
private fun TableAmountText(
    amount: String,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    textAlign: TextAlign? = null,
) {
    val layout = LocalTableLayout.current
    if (layout != null) {
        // Columns were sized to these exact amounts at this scale, so there's nothing to shrink to fit.
        Text(
            amount,
            style = style.copy(fontSize = style.fontSize * layout.scale),
            fontWeight = fontWeight,
            color = color,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            modifier = modifier,
        )
        return
    }
    val minFontSize = style.fontSize * LocalTableAmountMinScale.current
    Text(
        amount,
        style = style,
        fontWeight = fontWeight,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize),
        modifier = modifier,
    )
}

/** Space kept before a plain table amount so a shrunk neighbour never runs into it. */
private val TableAmountGap = 6.dp

/** An amount in a table column, plus the [padding] (a pill's, or [TableAmountGap]) beside it. */
private data class TableAmount(val text: String, val padding: androidx.compose.ui.unit.Dp = TableAmountGap)

/** The category table's balance pill padding (each side); see [CategoryRow]. */
private val CategoryPillPadding = 10.dp

/**
 * Whether [lead] and [amounts] fit the table's columns across [maxWidth] (a [LeadColumnWeight]
 * column, then [columns] amount columns, each of which must hold any one of [amounts]) at no less
 * than [TableAmountMinScale] of bodyMedium. Callers stack their layout when they don't, rather than
 * clipping digits.
 */
@Composable
private fun tableAmountsFit(
    maxWidth: androidx.compose.ui.unit.Dp,
    lead: TableAmount?,
    amounts: List<TableAmount>,
    columns: Int = amounts.size,
): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = MaterialTheme.typography.bodyMedium
    val style = base.copy(fontSize = base.fontSize * TableAmountMinScale, fontWeight = FontWeight.Bold)
    return remember(maxWidth, columns, lead?.text, lead?.padding, amounts, density, style) {
        val unit = maxWidth / (LeadColumnWeight + columns)
        fun TableAmount.fitsIn(width: androidx.compose.ui.unit.Dp): Boolean {
            val textWidth = measurer.measure(text, style, maxLines = 1, softWrap = false, density = density).size.width
            return with(density) { textWidth.toDp() } + padding <= width
        }
        // A whole table has many amounts; only the longest of each padding can be the widest.
        val widest = amounts.groupBy { it.padding }.values.flatMap { same ->
            val longest = same.maxOf { it.text.length }
            same.filter { it.text.length >= longest - 1 }.distinctBy { it.text }
        }
        (lead == null || lead.fitsIn(unit * LeadColumnWeight)) && widest.all { it.fitsIn(unit) }
    }
}

/** Horizontal padding inside a table card or group header, between its edge and its content. */
private val TableRowPadding = Spacing.lg

/** The smallest scale the table's amounts shrink to side by side; below it rows stack instead. */
internal const val TableLayoutMinScale = 0.75f

/** The share of the table's width the name column keeps, however wide the amounts are. */
internal const val TableNameMinShare = 0.32f

/**
 * The table view's columns sized to their content: one [scale] of the amounts' normal font size
 * for every amount, and a width for each amount column. The name column takes what's left.
 */
internal data class TableLayout(
    val scale: Float,
    val budgeted: androidx.compose.ui.unit.Dp,
    val spent: androidx.compose.ui.unit.Dp,
    val balance: androidx.compose.ui.unit.Dp,
)

/** Set while the table renders side by side; null when it is stacked (or isn't the table view). */
private val LocalTableLayout = compositionLocalOf<TableLayout?> { null }

/**
 * Sizes the table's amount columns to the widest amount each holds across [groups], at the largest
 * scale (up to 1) that still leaves the name column about a third of [availableWidth]. Null when that
 * scale would fall under [TableLayoutMinScale]: callers stack their layout rather than shrink digits.
 */
@Composable
private fun rememberTableLayout(
    availableWidth: androidx.compose.ui.unit.Dp,
    groups: List<Triple<BudgetGroup, List<BudgetCategory>, BudgetGroup>>,
    showSpent: Boolean,
    showGroupTotals: Boolean,
    hideDecimalPlaces: Boolean,
    /** The overview card above the table, whose amounts sit in the same columns. */
    overview: BudgetOverview? = null,
): TableLayout? {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    return remember(availableWidth, groups, showSpent, showGroupTotals, hideDecimalPlaces, overview, density, typography) {
        // Every amount shares one size (bodyMedium), so only the weight differs between them.
        val headerStyle = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        val balanceStyle = typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        val rowStyle = typography.bodyMedium
        // A whole table has many amounts; only the longest can be the widest.
        fun widest(texts: List<String>, style: androidx.compose.ui.text.TextStyle): Float {
            val longest = texts.maxOfOrNull { it.length } ?: return 0f
            return texts.filter { it.length >= longest - 1 }.distinct().maxOf {
                with(density) { measurer.measure(it, style, maxLines = 1, softWrap = false, density = density).size.width.toDp().value }
            }
        }
        val headerBudgeted = mutableListOf<String>(); val headerSpent = mutableListOf<String>()
        val headerBalance = mutableListOf<String>()
        val rowBudgeted = mutableListOf<String>(); val rowSpent = mutableListOf<String>()
        val rowBalance = mutableListOf<String>()
        if (overview != null) {
            headerBudgeted += formatMoneyCents(overview.budgetedCents, hideDecimalPlaces)
            headerSpent += formatMoneyCents(overview.spentCents, hideDecimalPlaces)
            headerBalance += formatMoneyCents(overview.availableCents, hideDecimalPlaces)
        }
        groups.forEach { (group, categories, headerGroup) ->
            if (group.isIncome) return@forEach
            if (showGroupTotals) {
                val totals = headerGroup.categories
                headerBudgeted += formatMoneyCents(totals.sumOf { it.assignedCents }, hideDecimalPlaces)
                headerSpent += formatMoneyCents(-totals.sumOf { it.spentCents }, hideDecimalPlaces)
                headerBalance += formatMoneyCents(totals.sumOf { it.balanceCents }, hideDecimalPlaces)
            }
            categories.filterNot { it.isIncome }.forEach {
                rowBudgeted += formatMoneyCents(it.assignedCents, hideDecimalPlaces)
                rowSpent += formatMoneyCents(-it.spentCents, hideDecimalPlaces)
                rowBalance += formatMoneyCents(it.balanceCents, hideDecimalPlaces)
            }
        }
        tableLayoutFor(
            budgetedText = maxOf(widest(headerBudgeted, headerStyle), widest(rowBudgeted, rowStyle)),
            spentText = if (showSpent) maxOf(widest(headerSpent, headerStyle), widest(rowSpent, rowStyle)) else 0f,
            balanceText = maxOf(widest(headerBalance, balanceStyle), widest(rowBalance, balanceStyle)),
            availableWidth = availableWidth.value,
            showSpent = showSpent,
            gap = TableAmountGap.value,
            pillPadding = CategoryPillPadding.value,
        )
    }
}

/**
 * The column widths and shared font scale for amounts whose widest text is [budgetedText],
 * [spentText] and [balanceText] (dp, at the normal font size) in a table [availableWidth] wide.
 * The scale is the largest (up to 1) that leaves the name column [TableNameMinShare] of the width;
 * null when that falls under [TableLayoutMinScale], or when there are no amounts to size.
 * Each column also holds a [gap] before its text and a little slack; the balance column holds the
 * balance pill's [pillPadding] on its left.
 */
internal fun tableLayoutFor(
    budgetedText: Float,
    spentText: Float,
    balanceText: Float,
    availableWidth: Float,
    showSpent: Boolean,
    gap: Float,
    pillPadding: Float,
): TableLayout? {
    val slack = 2f
    val textTotal = budgetedText + spentText + balanceText
    if (textTotal <= 0f) return null
    val fixed = (if (showSpent) 3 else 2) * (gap + slack) + pillPadding
    val nameMin = availableWidth * TableNameMinShare
    val scale = ((availableWidth - nameMin - fixed) / textTotal).coerceAtMost(1f)
    if (scale < TableLayoutMinScale) return null
    fun column(text: Float, extra: Float = 0f) = (text * scale + gap + slack + extra).dp
    return TableLayout(
        scale = scale,
        budgeted = column(budgetedText),
        spent = if (showSpent) column(spentText) else 0.dp,
        balance = column(balanceText, pillPadding),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BudgetGroupHeader(
    group: BudgetGroup,
    collapsed: Boolean,
    showSpent: Boolean,
    showTotals: Boolean,
    /** The table is too narrow for its columns: totals go under the name; see [rememberTableLayout]. */
    stacked: Boolean,
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
        Box(
            modifier = Modifier.fillMaxWidth()
                .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.md)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = TableRowPadding, vertical = Spacing.sm),
        ) {
            val nameRow = @Composable { modifier: Modifier ->
                Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (collapsed) stringResource(R.string.budget_expand_group, group.name)
                        else stringResource(R.string.budget_collapse_group, group.name),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(24.dp).rotate(rotation),
                    )
                    Text(if (group.hidden) stringResource(R.string.budget_hidden_name, group.name) else group.name,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = if (group.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!showTotals) {
                // No totals to line up with the columns: the name takes the whole row.
                nameRow(Modifier.fillMaxWidth())
                return@Box
            }
            val layout = LocalTableLayout.current
            if (stacked || layout == null) {
                // Too narrow for the table's columns: totals go under the name and share the full width.
                Column {
                    nameRow(Modifier.fillMaxWidth())
                    StackedTableAmounts {
                        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            AmountColumn(stringResource(R.string.budget_budgeted), budgeted, Modifier.weight(1f), hideDecimalPlaces)
                            if (showSpent) AmountColumn(stringResource(R.string.budget_spent), -spent, Modifier.weight(1f), hideDecimalPlaces, muted = spent == 0L)
                            AmountColumn(stringResource(R.string.budget_balance), balance, Modifier.weight(1f), hideDecimalPlaces, balance = true)
                        }
                    }
                }
                return@Box
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                nameRow(Modifier.weight(1f))
                AmountColumn(stringResource(R.string.budget_budgeted), budgeted, Modifier.width(layout.budgeted), hideDecimalPlaces)
                if (showSpent) AmountColumn(stringResource(R.string.budget_spent), -spent, Modifier.width(layout.spent), hideDecimalPlaces, muted = spent == 0L)
                AmountColumn(stringResource(R.string.budget_balance), balance, Modifier.width(layout.balance), hideDecimalPlaces, balance = true)
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
        TableAmountText(formatMoneyCents(amount, hideDecimalPlaces),
            fontWeight = if (balance) FontWeight.Bold else FontWeight.SemiBold,
            color = when {
                balance && amount < 0L -> MaterialTheme.colorScheme.danger
                muted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.onSurface
            }, modifier = Modifier.padding(start = TableAmountGap))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    category: BudgetCategory,
    showSpent: Boolean,
    /** As [BudgetGroupHeader]'s: the amounts go under the name, sharing the full width. */
    stacked: Boolean,
    showProgressBar: Boolean,
    position: GroupPosition,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    hideDecimalPlaces: Boolean,
    scheduleFunding: List<BudgetScheduleFunding> = emptyList(),
) {
    ActuaGroupedItem(position = position, dividerInset = TableRowPadding) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.compactRowMinHeight)
                .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
                .padding(horizontal = TableRowPadding, vertical = Spacing.md),
            verticalArrangement = Arrangement.Center,
        ) {
            val name = @Composable { modifier: Modifier ->
                Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                    CategoryStatusDot(category.statusState(scheduleFunding), modifier = Modifier.padding(end = 6.dp))
                    Text(if (category.hidden) stringResource(R.string.budget_hidden_name, category.name) else category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (category.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = if (stacked) 1 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
            val amounts = @Composable { cell: (Int) -> Modifier ->
                CategoryAmount(category.assignedCents, cell(0), hideDecimalPlaces)
                if (showSpent) CategoryAmount(
                    -category.spentCents,
                    cell(1),
                    hideDecimalPlaces,
                    muted = category.spentCents == 0L,
                )
                Box(modifier = cell(2).padding(start = TableAmountGap), contentAlignment = Alignment.CenterEnd) {
                    BalancePill(
                        category.balanceCents,
                        hideDecimalPlaces,
                        status = category.statusState(scheduleFunding),
                        colorByStatus = LocalCategoryStatusColors.current?.colorBudgetedAmounts == true,
                        modifier = Modifier.wrapContentWidth(Alignment.End, unbounded = true).offset(x = CategoryPillPadding),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = CategoryPillPadding,
                        verticalPadding = 3.dp,
                        tableAmount = true,
                    )
                }
            }
            if (stacked) {
                name(Modifier.fillMaxWidth())
                StackedTableAmounts {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        amounts { Modifier.weight(1f) }
                    }
                }
            } else {
                val layout = LocalTableLayout.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    name(Modifier.weight(1f))
                    amounts { column ->
                        when {
                            layout == null -> Modifier.weight(1f)
                            column == 0 -> Modifier.width(layout.budgeted)
                            column == 1 -> Modifier.width(layout.spent)
                            else -> Modifier.width(layout.balance)
                        }
                    }
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
        stringResource(R.string.budget_progress_goal, percent)
    } else stringResource(
        R.string.budget_progress_spending,
        stringResource(category.progressState.labelRes),
        percent,
    )
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
    // Localized once so the endpoint label maps below stay plain String → MoveEndpoint maps.
    val toBudgetTitle = stringResource(R.string.budget_to_budget_endpoint)
    val autoAssignChoices = buildAutoAssignChoices(category, month, scheduleFunding)
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
    val endpointLabels = remember(options, toBudgetTitle) {
        val duplicateNames = options.groupingBy { it.title(toBudgetTitle) }.eachCount().filterValues { it > 1 }.keys
        options.associateWith { endpoint ->
            val title = endpoint.title(toBudgetTitle)
            if (title in duplicateNames && endpoint.subtitle != null) "$title (${endpoint.subtitle})"
            else title
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
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(R.string.budget_details))
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
                caption = if (moveMode) stringResource(R.string.budget_amount) else stringResource(R.string.budget_budgeted),
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
                    text = stringResource(R.string.budget_auto_assign),
                    icon = Icons.Outlined.Bolt,
                    selected = autoAssignMode,
                    modifier = Modifier.weight(1f),
                ) {
                    val expand = !autoAssignMode
                    moveMode = false
                    autoAssignMode = expand
                }
                BudgetModeButton(
                    text = stringResource(R.string.budget_move_money),
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
                                stringResource(R.string.budget_no_suggestions),
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
                                label = stringResource(R.string.budget_from),
                                value = endpointLabels[from] ?: from.title(toBudgetTitle),
                                options = fromOptions.filterNot { it.group == to.group && it.category == to.category }
                                    .map { endpointLabels.getValue(it) },
                                onValueChange = { label -> endpointByLabel[label]?.let { from = it } },
                                supportingValues = endpointBalances,
                                rowIcon = Icons.Outlined.Output,
                            )
                            ActuaCardDivider()
                            PickerTextField(
                                label = stringResource(R.string.budget_to),
                                value = endpointLabels[to] ?: to.title(toBudgetTitle),
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
                                stringResource(
                                    R.string.budget_available_to_move,
                                    formatMoneyCents(from.balanceCents.coerceAtLeast(0L), hideDecimalPlaces),
                                ),
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
                                        stringResource(
                                            R.string.budget_cover_amount,
                                            formatMoneyCents(coverAmount, hideDecimalPlaces),
                                        ),
                                        maxLines = 1,
                                    )
                                }
                            }
                            IconButton(onClick = { val oldFrom = from; from = to; to = oldFrom }) {
                                Icon(Icons.Outlined.SwapVert, contentDescription = stringResource(R.string.budget_swap_source_destination))
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
    TableAmountText(
        formatMoneyCents(amount, hideDecimalPlaces),
        fontWeight = FontWeight.Normal,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End, modifier = modifier.padding(start = TableAmountGap),
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
 * drops its fill and dims the amount to read as "nothing here". Without [LocalShowBalancePills]
 * no pill is filled, but its padding stays so amounts keep their alignment.
 */
internal fun BalancePill(
    amount: Long,
    hideDecimalPlaces: Boolean,
    status: BudgetProgressState,
    colorByStatus: Boolean = false,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
    fontWeight: FontWeight = FontWeight.SemiBold,
    horizontalPadding: androidx.compose.ui.unit.Dp = 8.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 2.dp,
    /** In the table view: shrink like the other [TableAmountText]s rather than [ShrinkToFitText]. */
    tableAmount: Boolean = false,
    /** The category has a target or goal: a small icon leads the amount (not in the table view, which keeps its columns aligned). */
    hasTarget: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val statusColor = categoryStatusColor(status)
    val tone = balancePillTone(amount, status, colorByStatus)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = when {
            !LocalShowBalancePills.current -> Color.Transparent
            tone == BalancePillTone.ALERT -> statusColor.copy(alpha = 0.16f)
            tone == BalancePillTone.NEUTRAL -> colors.surfaceContainerHighest
            else -> Color.Transparent
        },
    ) {
        val text = formatMoneyCents(amount, hideDecimalPlaces)
        val weight = if (tone == BalancePillTone.EMPTY) FontWeight.Normal else fontWeight
        val color = when (tone) {
            BalancePillTone.ALERT -> statusColor
            BalancePillTone.NEUTRAL -> colors.onSurface
            BalancePillTone.EMPTY -> colors.onSurfaceVariant.copy(alpha = 0.55f)
        }
        val padding = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding)
        if (tableAmount) {
            TableAmountText(text, fontWeight = weight, color = color, style = textStyle, modifier = padding)
        } else if (hasTarget) {
            Row(padding, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.TrackChanges,
                    contentDescription = stringResource(R.string.budget_balance_has_target),
                    modifier = Modifier.padding(end = 4.dp).size(14.dp),
                    tint = color,
                )
                ShrinkToFitText(text = text, style = textStyle, fontWeight = weight, color = color)
            }
        } else {
            ShrinkToFitText(text = text, style = textStyle, fontWeight = weight, color = color, modifier = padding)
        }
    }
}

/** Budget display option "Show balance pills": whether balances are drawn on a filled pill. */
private val LocalShowBalancePills = compositionLocalOf { true }

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
            ActuaSheetTitle(if (covering) stringResource(R.string.budget_cover_to_budget) else stringResource(R.string.budget_summary))
            ActuaHeroAmount(
                amount = formatMoneyCents(toBudgetCents, hideDecimalPlaces),
                amountColor = if (covering) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                size = ActuaHeroSize.Medium,
                supportingText = if (bufferedCents != 0L) {
                    stringResource(
                        R.string.budget_held_for_next_month,
                        formatMoneyCents(bufferedCents, hideDecimalPlaces),
                    )
                } else null,
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                BudgetModeButton(
                    text = if (covering) stringResource(R.string.budget_cover_from)
                    else stringResource(R.string.budget_move_to_category),
                    icon = Icons.Outlined.SwapHoriz,
                    selected = action == BudgetSummaryAction.MOVE,
                    modifier = Modifier.fillMaxWidth(),
                ) { action = if (action == BudgetSummaryAction.MOVE) null else BudgetSummaryAction.MOVE }
                if (!covering) {
                    BudgetModeButton(
                        text = stringResource(R.string.budget_hold_for_next_month),
                        icon = Icons.Outlined.Savings,
                        selected = action == BudgetSummaryAction.HOLD,
                        modifier = Modifier.fillMaxWidth(),
                    ) { action = if (action == BudgetSummaryAction.HOLD) null else BudgetSummaryAction.HOLD }
                }
            }
            if (bufferedCents != 0L) {
                ActuaSheetCard {
                    ActuaSheetAction(stringResource(R.string.budget_reset_hold), icon = Icons.Outlined.RestartAlt, onClick = onResetNextMonthBuffer)
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
                            if (covering) stringResource(R.string.budget_choose_category_cover)
                            else stringResource(R.string.budget_choose_category_fund),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                        )
                        ActuaFormCard {
                            PickerTextField(
                                label = stringResource(R.string.budget_category),
                                value = selectedCategory?.let { "${it.first} · ${it.second}" }.orEmpty(),
                                options = optionLabels.keys.toList(),
                                onValueChange = { label -> optionLabels[label]?.let { selectedCategory = it } },
                                rowIcon = Icons.Outlined.Category,
                                placeholder = stringResource(R.string.budget_no_categories),
                            )
                        }
                        InlineCalculatorAmount(stringResource(R.string.budget_amount), moveAmount)
                        Text(
                            stringResource(R.string.budget_up_to, formatMoneyCents(moveLimit, hideDecimalPlaces)),
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
                            stringResource(R.string.budget_set_aside),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                        )
                        InlineCalculatorAmount(stringResource(R.string.budget_amount), holdAmount)
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
    /** The picker row's label; the To Budget endpoint takes the localized [toBudgetLabel]. */
    fun title(toBudgetLabel: String): String = category ?: toBudgetLabel
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
    loadDeletePlan: suspend () -> CategoryDeletePlan?,
    onDelete: (transferToId: String?) -> Unit,
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
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.budget_category_options))
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (favorite) stringResource(R.string.budget_remove_favorite)
                            else stringResource(R.string.budget_add_favorite)) },
                            leadingIcon = { Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null) },
                            onClick = { overflowOpen = false; onFavoriteChange(!favorite) },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.budget_transactions_month)) }, onClick = {
                            overflowOpen = false; onTransactionsThisMonth()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.budget_rename_category)) }, onClick = {
                            overflowOpen = false; onRename()
                        })
                        DropdownMenuItem(text = { Text(if (hidden) stringResource(R.string.budget_unhide_category)
                        else stringResource(R.string.budget_hide_category)) }, onClick = {
                            overflowOpen = false; onSetHidden(!hidden)
                        })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.budget_delete_category), color = MaterialTheme.colorScheme.error) }, onClick = {
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
                            caption = stringResource(R.string.budget_balance),
                            amountColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            captionColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                            size = ActuaHeroSize.Medium,
                        )
                        if (category.showsProgressBar) {
                            CategoryProgressBar(category, scheduleFunding,
                                Modifier.fillMaxWidth().height(5.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SummaryValue(stringResource(R.string.budget_budgeted), category.assignedCents, hideDecimalPlaces, Modifier.weight(1f))
                            SummaryValue(stringResource(R.string.budget_spent), -category.spentCents, hideDecimalPlaces, Modifier.weight(1f))
                        }
                    }
                }
                ActuaFormCard {
                    ActuaFormRow(
                        icon = Icons.Outlined.Add,
                        label = stringResource(R.string.budget_budget),
                        value = formatMoneyCents(category.assignedCents, hideDecimalPlaces),
                        onClick = onEditBudget,
                    )
                    ActuaCardDivider()
                    ActuaFormRow(icon = Icons.Outlined.SwapHoriz, label = stringResource(R.string.budget_move_money), value = null, onClick = onMoveMoney)
                    ActuaCardDivider()
                    ActuaFormRow(icon = Icons.Outlined.Bolt, label = stringResource(R.string.budget_auto_assign), value = null, onClick = onAutoAssign)
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
                            label = stringResource(R.string.budget_note),
                            value = note.ifBlank { stringResource(R.string.budget_add_note) },
                            valueIsPlaceholder = note.isBlank(),
                            onClick = { noteEditorOpen = true },
                        )
                    }
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Replay,
                        label = stringResource(R.string.budget_rollover_overspending),
                        value = null,
                        caption = stringResource(R.string.budget_rollover_description),
                        checked = rollover,
                        onClick = {
                            val enabled = !rollover
                            rollover = enabled
                            onSetCarryover(enabled)
                        },
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    ActuaGroupLabel(stringResource(R.string.budget_recent_activity), Modifier.weight(1f))
                    TextButton(onClick = onAllTransactions) { Text(stringResource(R.string.budget_view_all)) }
                }
                ActuaFormCard {
                    if (transactions.isEmpty()) {
                        Text(stringResource(R.string.budget_no_recent_transactions), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg))
                    } else transactions.forEachIndexed { index, transaction ->
                        if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                        Row(Modifier.fillMaxWidth().heightIn(min = Sizes.compactRowMinHeight)
                            .clickable { selectedTransaction = transaction }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(transaction.payee.ifBlank { stringResource(R.string.budget_unknown_payee) }, fontWeight = FontWeight.SemiBold,
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
            placeholder = stringResource(R.string.budget_category_note),
            onDismiss = { noteEditorOpen = false },
            onSave = {
                note = it
                onSaveNote(it)
                noteEditorOpen = false
            },
        )
    }
    if (deleteConfirmOpen) {
        CategoryDeleteDialog(
            categoryName = category.name,
            loadPlan = loadDeletePlan,
            onDismiss = { deleteConfirmOpen = false },
            onDelete = onDelete,
        )
    }
}

/** The period word in "Every …" timing, pluralized to [count] (Every day / Every 3 days). */
@Composable
private fun periodWord(period: BudgetTarget.Period, count: Int): String = when (period) {
    BudgetTarget.Period.DAY ->
        stringResource(if (count > 1) R.string.automation_period_day_plural else R.string.automation_period_day_singular)
    BudgetTarget.Period.WEEK ->
        stringResource(if (count > 1) R.string.automation_period_week_plural else R.string.automation_period_week_singular)
    BudgetTarget.Period.MONTH ->
        stringResource(if (count > 1) R.string.automation_period_month_plural else R.string.automation_period_month_singular)
    BudgetTarget.Period.YEAR ->
        stringResource(if (count > 1) R.string.automation_period_year_plural else R.string.automation_period_year_singular)
}

/** The adverb naming a balance cap's period ("capped R$10 monthly"). */
@StringRes
private fun limitPeriodLabel(period: BudgetTarget.LimitPeriod): Int = when (period) {
    BudgetTarget.LimitPeriod.DAILY -> R.string.automation_limit_daily
    BudgetTarget.LimitPeriod.WEEKLY -> R.string.automation_limit_weekly
    BudgetTarget.LimitPeriod.MONTHLY -> R.string.automation_limit_monthly
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
            title = if (category.automationReadOnly) stringResource(R.string.budget_notes_managed_target)
            else stringResource(R.string.budget_advanced_target)
            detail = if (category.automationReadOnly) stringResource(R.string.budget_managed_notes)
            else stringResource(R.string.budget_managed_actual)
            supporting = stringResource(R.string.budget_view_target_info)
        }
        category.automations.size > 1 -> {
            title = pluralStringResource(R.plurals.budget_automation_count, category.automations.size, category.automations.size)
            detail = category.automations.map { stringResource(it.type.labelRes) }.joinToString()
            supporting = stringResource(R.string.budget_edit_automation_list)
        }
        target == null -> {
            title = stringResource(R.string.budget_set_target)
            detail = stringResource(R.string.budget_plan_amount)
            supporting = stringResource(R.string.budget_auto_assign_target_hint)
        }
        else -> {
            title = stringResource(target.type.labelRes)
            detail = when (target.type) {
                BudgetTarget.Type.HISTORICAL -> when (target.historicalMode) {
                    BudgetTarget.HistoricalMode.AVERAGE ->
                        pluralStringResource(R.plurals.automation_average_recent_plural, target.historicalMonths, target.historicalMonths)
                    BudgetTarget.HistoricalMode.COPY -> stringResource(R.string.budget_copy_months_ago, target.historicalMonths)
                }
                BudgetTarget.Type.REMAINDER -> buildString {
                    append(stringResource(R.string.automation_weight_summary, target.weight))
                    target.limitAmountCents?.let {
                        append(" · ")
                        append(stringResource(
                            R.string.budget_capped_amount,
                            formatMoneyCents(it, hideDecimalPlaces),
                            target.limitPeriod?.let { period -> stringResource(limitPeriodLabel(period)) } ?: "",
                        ))
                        if (target.limitHold) append(" · ").append(stringResource(R.string.budget_hold_short))
                    }
                }
                BudgetTarget.Type.PERCENTAGE -> if (target.percentagePrevious) {
                    stringResource(R.string.budget_percent_last_month, target.percentage, target.percentageSource)
                } else {
                    stringResource(R.string.budget_percent_this_month, target.percentage, target.percentageSource)
                }
                BudgetTarget.Type.SCHEDULE -> target.scheduleName ?: linkedSchedule?.name
                ?: stringResource(R.string.budget_no_schedule_linked)
                BudgetTarget.Type.REFILL -> stringResource(R.string.budget_refill_balance_cap)
                else -> formatMoneyCents(target.amountCents, hideDecimalPlaces)
            }
            val timing = when (target.type) {
                BudgetTarget.Type.FIXED -> if (target.everyCount > 1) {
                    stringResource(R.string.budget_every_count_period, target.everyCount, periodWord(target.period, target.everyCount))
                } else {
                    stringResource(R.string.budget_every_period, periodWord(target.period, 1))
                }
                BudgetTarget.Type.LIMIT -> when (target.limitPeriod ?: BudgetTarget.LimitPeriod.MONTHLY) {
                    BudgetTarget.LimitPeriod.DAILY -> stringResource(R.string.budget_daily_cap)
                    BudgetTarget.LimitPeriod.WEEKLY -> stringResource(R.string.budget_weekly_cap)
                    BudgetTarget.LimitPeriod.MONTHLY -> stringResource(R.string.budget_monthly_cap)
                }
                BudgetTarget.Type.REFILL -> stringResource(R.string.budget_resets_monthly)
                BudgetTarget.Type.BY_DATE -> target.targetMonth?.let {
                    stringResource(R.string.budget_target_month, formatMonth(it))
                } ?: stringResource(R.string.budget_target_date)
                BudgetTarget.Type.HISTORICAL -> stringResource(R.string.budget_recalculates_monthly)
                BudgetTarget.Type.GOAL -> stringResource(R.string.budget_target_only)
                BudgetTarget.Type.REMAINDER -> stringResource(R.string.budget_after_other_automations)
                BudgetTarget.Type.PERCENTAGE -> stringResource(R.string.budget_at_priority)
                BudgetTarget.Type.SCHEDULE -> linkedSchedule?.let {
                    stringResource(R.string.budget_due_amount, formatMoneyCents(it.amountCents, hideDecimalPlaces))
                } ?: stringResource(R.string.budget_schedule_driven)
            }
            supporting = when {
                target.type == BudgetTarget.Type.LIMIT ->
                    "$timing · " + stringResource(R.string.budget_no_auto_funding)
                target.type == BudgetTarget.Type.GOAL ->
                    "$timing · " + stringResource(R.string.budget_no_auto_budgeting)
                target.type == BudgetTarget.Type.REMAINDER ->
                    "$timing · " + stringResource(R.string.budget_applied_preview)
                target.type == BudgetTarget.Type.PERCENTAGE ->
                    "$timing · " + stringResource(R.string.budget_applied_preview)
                target.type == BudgetTarget.Type.REFILL ->
                    "$timing · " + stringResource(R.string.budget_applied_preview)
                target.type == BudgetTarget.Type.SCHEDULE && linkedSchedule == null ->
                    "$timing · " + stringResource(R.string.budget_schedule_missing)
                target.type == BudgetTarget.Type.SCHEDULE ->
                    "$timing · " + stringResource(R.string.budget_applied_preview)
                else -> "$timing · " + stringResource(
                    R.string.budget_auto_assign_amount,
                    formatMoneyCents(target.suggestedBudget(category, month), hideDecimalPlaces),
                )
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
            Text(stringResource(R.string.budget_target), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            Text(supporting, style = MaterialTheme.typography.bodySmall,
                color = if (target == null && !category.hasUnsupportedTarget) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.budget_edit_target),
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

/** Auto-Assign suggestions as label → amount; localized, so called from composition directly. */
@Composable
private fun buildAutoAssignChoices(
    category: BudgetCategory,
    month: String,
    scheduleFunding: List<BudgetScheduleFunding>,
): List<Pair<String, Long>> = buildList {
    category.target?.takeUnless { it.type == BudgetTarget.Type.LIMIT }?.let { target ->
        add("${stringResource(R.string.budget_target)} · ${stringResource(target.type.labelRes)}"
            to target.suggestedBudget(category, month, scheduleFunding))
    }
    category.history.firstOrNull()?.let { last ->
        val spent = kotlin.math.abs(minOf(last.spentCents, 0L))
        if (spent > 0) add(stringResource(R.string.budget_spent_last_month) to spent)
        if (last.assignedCents != 0L) add(stringResource(R.string.budget_budgeted_last_month) to last.assignedCents)
    }
    val spending = category.history.map { kotlin.math.abs(minOf(it.spentCents, 0L)) }
    if (spending.size >= 2) {
        val average = (spending.sum().toDouble() / spending.size).toLong()
        if (average > 0) add(pluralStringResource(R.plurals.budget_average_spent, spending.size, spending.size) to average)
    }
    if (category.balanceCents != 0L) {
        add(stringResource(R.string.budget_reset_balance_zero) to (category.assignedCents - category.balanceCents))
    }
    if (category.assignedCents != 0L) add(stringResource(R.string.budget_set_budgeted_zero) to 0L)
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
            ActuaSheetCard {
                if (!category.isIncome) {
                    ActuaSheetAction(stringResource(R.string.budget_edit_budgeted), icon = Icons.Outlined.Calculate, onClick = onEditBudget)
                    ActuaCardDivider()
                    if (category.available != 0) {
                        ActuaSheetAction(if (category.available < 0) stringResource(R.string.budget_cover_overspending)
                        else stringResource(R.string.budget_move_money_plain),
                            icon = Icons.Outlined.SwapHoriz, onClick = onMoveMoney)
                        ActuaCardDivider()
                    }
                    ActuaSheetAction(when {
                        category.hasUnsupportedTarget -> stringResource(R.string.budget_view_target)
                        category.automations.isEmpty() -> stringResource(R.string.budget_set_automations)
                        else -> stringResource(R.string.budget_edit_automations)
                    }, icon = Icons.Outlined.AutoAwesome, onClick = onSetTarget)
                    ActuaCardDivider()
                    ActuaSheetAction(stringResource(R.string.budget_budget_details), icon = Icons.Outlined.Info, onClick = onDetails)
                    ActuaCardDivider()
                }
                ActuaSheetAction(stringResource(R.string.budget_transactions_month), icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    onClick = onTransactionsThisMonth)
                ActuaCardDivider()
                ActuaSheetAction(stringResource(R.string.budget_all_transactions), icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                    onClick = onAllTransactions)
            }
            ActuaSheetCard {
                ActuaSheetAction(
                    if (favorite) stringResource(R.string.budget_remove_favorite)
                    else stringResource(R.string.budget_add_favorite),
                    icon = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    onClick = { onFavoriteChange(!favorite) },
                )
                ActuaCardDivider()
                ActuaSheetAction(stringResource(R.string.budget_rename_category), icon = Icons.Outlined.Edit, onClick = onRename)
                ActuaCardDivider()
                ActuaSheetAction(
                    if (hidden) stringResource(R.string.budget_unhide_category) else stringResource(R.string.budget_hide_category),
                    icon = if (hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    destructive = !hidden,
                    onClick = { onSetHidden(!hidden) },
                )
            }
            // Below the existing actions, so they keep their place in the half-open sheet.
            if (budgetActions) {
                ActuaSheetCard {
                    CategoryBudgetAction.entries.forEachIndexed { index, action ->
                        if (index > 0) ActuaCardDivider()
                        ActuaSheetAction(stringResource(action.labelRes), icon = action.icon, onClick = { onBudgetAction(action) })
                    }
                }
            }
            incomeHold?.let { holding ->
                ActuaSheetCard {
                    ActuaSheetAction(
                        if (holding) stringResource(R.string.budget_stop_holding_automatically)
                        else stringResource(R.string.budget_hold_automatically_next_month),
                        icon = Icons.Outlined.Replay,
                        onClick = { onSetIncomeHold(!holding) },
                    )
                }
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
                ActuaSheetAction(stringResource(R.string.budget_edit_budgeted), icon = Icons.Outlined.Calculate, onClick = onEditAssigned)
                ActuaCardDivider()
                ActuaSheetAction(
                    if (category.balanceCents < 0L) stringResource(R.string.budget_cover_overspending)
                    else stringResource(R.string.budget_move_money_plain),
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
                    ActuaSheetAction(stringResource(R.string.budget_stop_all_automatic_holds), icon = Icons.Outlined.RestartAlt, onClick = reset)
                }
            }
            if (!group.isIncome && !hidden) {
                ActuaSheetCard {
                    ActuaSheetAction(stringResource(R.string.budget_apply_templates), icon = Icons.Outlined.AutoAwesome,
                        onClick = { onApplyTemplate(false) })
                    ActuaCardDivider()
                    ActuaSheetAction(stringResource(R.string.budget_overwrite_templates), icon = Icons.Outlined.RestartAlt,
                        onClick = { onApplyTemplate(true) })
                }
            }
            ActuaSheetCard {
                ActuaSheetAction(stringResource(R.string.budget_rename_group), icon = Icons.Outlined.Edit, onClick = onRename)
                if (!group.isIncome || hidden) {
                    ActuaCardDivider()
                    ActuaSheetAction(
                        if (hidden) stringResource(R.string.budget_unhide_group) else stringResource(R.string.budget_hide_group),
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
enum class CategoryBudgetAction(@StringRes val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    COPY_LAST_MONTH(R.string.budget_copy_last_month, Icons.Outlined.Replay),
    AVERAGE_3(R.string.budget_action_average_3, Icons.Outlined.Calculate),
    AVERAGE_6(R.string.budget_action_average_6, Icons.Outlined.Calculate),
    AVERAGE_12(R.string.budget_action_yearly_average, Icons.Outlined.Calculate),
    COPY_TO_YEAR_END(R.string.budget_action_copy_year_end, Icons.Outlined.SwapHoriz),
}

@Composable
private fun averageBudgetTitle(months: Int) =
    if (months == 12) stringResource(R.string.budget_set_yearly_average)
    else stringResource(R.string.budget_set_month_average, months)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetSheet(onDismiss: () -> Unit,
    onApplyTemplate: (Boolean) -> Unit, onPreviewCleanup: () -> Unit, onPreviewZeroBudget: () -> Unit,
    onPreviewAverage: (Int) -> Unit = {}) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle(stringResource(R.string.budget_add_to_budget))
            ActuaSheetCard {
                ActuaSheetAction(stringResource(R.string.budget_apply_templates), icon = Icons.Outlined.AutoAwesome,
                    onClick = { onApplyTemplate(false) })
                ActuaCardDivider()
                ActuaSheetAction(stringResource(R.string.budget_overwrite_templates), icon = Icons.Outlined.RestartAlt,
                    onClick = { onApplyTemplate(true) })
                ActuaCardDivider()
                ActuaSheetAction(stringResource(R.string.budget_month_end_cleanup), icon = Icons.Outlined.CleaningServices, onClick = onPreviewCleanup)
            }
            ActuaSheetCard {
                listOf(3, 6, 12).forEachIndexed { index, months ->
                    if (index > 0) ActuaCardDivider()
                    ActuaSheetAction(averageBudgetTitle(months), icon = Icons.Outlined.Calculate,
                        onClick = { onPreviewAverage(months) })
                }
            }
            ActuaSheetCard {
                ActuaSheetAction(stringResource(R.string.budget_set_budgets_zero), icon = Icons.Outlined.DeleteSweep, destructive = true,
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
            title = stringResource(R.string.budget_review_cleanup),
            description = stringResource(R.string.budget_cleanup_intro, formatMonth(preview.month)),
            confirmLabel = stringResource(R.string.budget_apply_cleanup),
            confirmEnabled = hasChanges,
            onConfirm = { onApply(preview) },
        ) {
            if (!hasChanges) {
                PreviewNote(
                    if (preview.isUpToDate) stringResource(R.string.budget_cleanup_current)
                    else stringResource(R.string.budget_cleanup_none),
                    MaterialTheme.colorScheme.onSurface,
                )
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
                        subtitle = stringResource(R.string.budget_goal_reset, change.groupName),
                        value = goalChangeValue(change.currentCents, change.proposedCents, hideDecimalPlaces),
                    )
                }
            }
            if (preview.warnings.isNotEmpty()) {
                PreviewNote(preview.warnings.joinToString("\n"), MaterialTheme.colorScheme.tertiary)
            }
            if (preview.invalidCategories.isNotEmpty()) {
                PreviewNote(
                    stringResource(R.string.budget_cleanup_unsupported, preview.invalidCategories.joinToString()),
                    MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** "None → R$10" (or an amount → "None") for a cleanup goal-reset row, with a localized None. */
@Composable
private fun goalChangeValue(currentCents: Long?, proposedCents: Long?, hideDecimalPlaces: Boolean): String {
    val none = stringResource(R.string.common_none)
    val current = currentCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: none
    val proposed = proposedCents?.let { formatMoneyCents(it, hideDecimalPlaces) } ?: none
    return "$current → $proposed"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetTemplatePreviewSheet(
    preview: BudgetTemplatePreview,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    onApply: (BudgetTemplatePreview) -> Unit,
    title: String = stringResource(R.string.budget_review_template),
    description: String = if (preview.overwriteExisting) {
        stringResource(R.string.budget_template_overwrite_intro, formatMonth(preview.month))
    } else {
        stringResource(R.string.budget_template_apply_intro)
    },
    upToDateMessage: String = if (preview.skippedExistingCount > 0) stringResource(R.string.budget_no_unbudgeted_changes)
    else stringResource(R.string.budget_targets_current),
    /** Plural formatting "${count} <kind> already current." for the unchanged-categories note. */
    @PluralsRes unchangedPlural: Int = R.plurals.budget_targets_already_current,
) {
    val hasChanges = preview.changes.isNotEmpty() || preview.goalChanges.isNotEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PreviewSheetLayout(
            title = title,
            description = description,
            confirmLabel = stringResource(R.string.budget_apply_changes),
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
                    Text(stringResource(R.string.budget_net_change), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(formatMoneyCents(preview.netBudgetChangeCents, hideDecimalPlaces), fontWeight = FontWeight.SemiBold)
                }
            }
            if (preview.goalChanges.isNotEmpty()) ActuaSheetCard {
                preview.goalChanges.forEachIndexed { index, change ->
                    if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                    PreviewChangeRow(
                        title = change.categoryName,
                        subtitle = stringResource(R.string.budget_group_goal, change.groupName),
                        value = goalChangeValue(change.currentCents, change.proposedCents, hideDecimalPlaces),
                    )
                }
            }
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            if (preview.unchangedCount > 0) PreviewNote(
                pluralStringResource(unchangedPlural, preview.unchangedCount, preview.unchangedCount),
                muted,
            )
            if (preview.skippedExistingCount > 0) PreviewNote(
                pluralStringResource(R.plurals.budget_categories_unchanged, preview.skippedExistingCount, preview.skippedExistingCount),
                muted,
            )
            if (preview.unsupportedCategories.isNotEmpty()) PreviewNote(
                stringResource(R.string.budget_unsupported_automations, preview.unsupportedCategories.joinToString()),
                MaterialTheme.colorScheme.error,
            )
            if (preview.limitedCategories.isNotEmpty()) PreviewNote(
                stringResource(R.string.budget_available_funds_limited, preview.limitedCategories.joinToString()),
                MaterialTheme.colorScheme.tertiary,
            )
            if (preview.snoozedCategories.isNotEmpty()) PreviewNote(
                stringResource(R.string.budget_loan_targets_snoozed, preview.snoozedCategories.joinToString()),
                MaterialTheme.colorScheme.tertiary,
            )
            if (preview.cappedCategories.isNotEmpty()) PreviewNote(
                stringResource(R.string.budget_remainder_caps, preview.cappedCategories.joinToString()),
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
