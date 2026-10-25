package com.azimulkabir.actua.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.diagnostics.DiagnosticsLog
import com.azimulkabir.actua.data.importing.NotificationImportPreferences
import com.azimulkabir.actua.ui.accounts.AccountsScreen
import com.azimulkabir.actua.ui.accounts.pendingCaptureBadge
import com.azimulkabir.actua.ui.accounts.CloseAccountOptions
import com.azimulkabir.actua.ui.accounts.CloseCategoryGroup
import com.azimulkabir.actua.ui.automation.BudgetAutomationScreen
import com.azimulkabir.actua.ui.budget.BudgetScreen
import com.azimulkabir.actua.ui.budget.CategoryBudgetAction
import com.azimulkabir.actua.ui.settings.BudgetSelectionItem
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.azimulkabir.actua.ui.settings.SettingsScreen
import com.azimulkabir.actua.ui.settings.ConnectionScreen
import com.azimulkabir.actua.ui.settings.CreditCardsScreen
import com.azimulkabir.actua.ui.settings.LoansScreen
import com.azimulkabir.actua.ui.settings.RulesScreen
import com.azimulkabir.actua.ui.settings.SchedulesScreen
import com.azimulkabir.actua.ui.settings.FindSchedulesScreen
import com.azimulkabir.actua.ui.settings.BillsCalendarScreen
import com.azimulkabir.actua.ui.settings.ImportTransactionsScreen
import com.azimulkabir.actua.ui.settings.ManagePayeesScreen
import com.azimulkabir.actua.ui.settings.PayeeLocationsScreen
import com.azimulkabir.actua.ui.categories.ManageCategoriesScreen
import com.azimulkabir.actua.ui.categories.ReorderGroupsScreen
import com.azimulkabir.actua.ui.accounts.ReorderAccountsScreen
import com.azimulkabir.actua.ui.home.CustomizeHomeScreen
import com.azimulkabir.actua.ui.transactions.AddTransactionScreen
import com.azimulkabir.actua.ui.transactions.NearbyPayeeOption
import com.azimulkabir.actua.ui.transactions.NearbyPayeeSearchResult
import com.azimulkabir.actua.ui.transactions.PayeeLocationSaveResult
import com.azimulkabir.actua.ui.transactions.TransactionsScreen
import com.azimulkabir.actua.ui.transactions.upcomingTransactionsFrom
import com.azimulkabir.actua.ui.transactions.recurringScheduleIds
import com.azimulkabir.actua.ui.transactions.UpcomingTransactionAction
import com.azimulkabir.actua.ui.reports.ReportsScreen
import com.azimulkabir.actua.ui.search.GlobalSearchScreen
import com.azimulkabir.actua.ui.home.HomeScreen
import com.azimulkabir.actua.ui.home.HomeDashboardProjection
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.model.PickerChoices
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.TransactionStatusFilter
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportViewFilter
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.asDuplicate
import com.azimulkabir.actua.model.asTransferDraft
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.location.AndroidLocationProvider
import com.azimulkabir.actua.data.location.CurrentLocationResult
import com.azimulkabir.actua.data.location.LocationUtils
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import com.azimulkabir.actua.data.sync.ActualSyncScheduler
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_AFTER_CHANGE
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_PULL_REFRESH
import com.azimulkabir.actua.data.sync.SyncRunResult
import com.azimulkabir.actua.data.sync.SyncSignals
import com.azimulkabir.actua.data.sync.SyncStatus
import com.azimulkabir.actua.data.sync.SyncStatusStore
import com.azimulkabir.actua.data.home.HomeLayout
import com.azimulkabir.actua.data.home.HolidayCodec
import com.azimulkabir.actua.data.home.MonthStartRule
import com.azimulkabir.actua.data.navigation.TabItem
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.EffectiveDisplayFormats
import com.azimulkabir.actua.data.preferences.FavoritePreferences
import com.azimulkabir.actua.data.preferences.HomePreferences
import com.azimulkabir.actua.data.preferences.TabBarPreferences
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.bank.NewBankTransactions
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.preferences.LocationPreferences
import com.azimulkabir.actua.data.notifications.CapturedTransactionNotifications
import com.azimulkabir.actua.data.notifications.CreditCardDueLaunch
import com.azimulkabir.actua.data.notifications.CreditCardDueNotificationScheduler
import com.azimulkabir.actua.data.notifications.CreditCardNotificationSettings
import com.azimulkabir.actua.data.notifications.ScheduleReminderLaunch
import com.azimulkabir.actua.data.notifications.ScheduleReminderScheduler
import com.azimulkabir.actua.data.notifications.ScheduleReminderSettings
import com.azimulkabir.actua.ui.components.ActuaSnackbarHost
import com.azimulkabir.actua.ui.components.BalanceVisibility
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.components.TransactionImpactCue
import com.azimulkabir.actua.ui.components.TransactionImpactPopup
import com.azimulkabir.actua.ui.components.findTagOccurrences
import com.azimulkabir.actua.ui.components.applyDisplayFormats
import com.azimulkabir.actua.AppLaunchRequest
import com.azimulkabir.actua.SHARED_IMPORT_ACTION
import com.azimulkabir.actua.widget.WidgetActions
import com.azimulkabir.actua.widget.WidgetUpdater
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import com.azimulkabir.actua.data.security.CredentialStore

/**
 * Localized label of a bottom tab. [TabItem.label] is the stable English name (used in
 * preferences and planner logic); screens render [labelRes] instead so the tab bar
 * localizes.
 */
@StringRes
internal fun TabItem.labelRes(): Int = when (this) {
    TabItem.HOME -> R.string.navigation_home
    TabItem.BUDGET -> R.string.navigation_budget
    TabItem.TRANSACTIONS -> R.string.navigation_transactions
    TabItem.ACCOUNTS -> R.string.navigation_accounts
    TabItem.REPORTS -> R.string.navigation_reports
    TabItem.ADD -> R.string.navigation_add
    TabItem.MANAGE -> R.string.navigation_manage
}

/**
 * The bottom destinations. [label] is the persisted start-page value (compared against
 * `DisplayPreferences.startPage`), so it stays English; [labelRes] localizes what is shown.
 */
private enum class MainDestination(
    val label: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home("Home", R.string.navigation_home, Icons.Outlined.Home),
    Budget("Budget", R.string.navigation_budget, Icons.Outlined.PieChartOutline),
    Transactions("Transactions", R.string.navigation_transactions, Icons.Outlined.ReceiptLong),
    Accounts("Accounts", R.string.navigation_accounts, Icons.Outlined.AccountBalanceWallet),
    Reports("Reports", R.string.navigation_reports, Icons.Outlined.BarChart),
    Manage("Manage", R.string.navigation_manage, Icons.Outlined.Tune),
}

/**
 * The localized label resource for a persisted start-page value, matched against
 * [MainDestination.label] (which stays English). Null when no destination matches, so callers
 * fall back to the raw stored value.
 */
internal fun mainDestinationLabelRes(startPage: String): Int? =
    MainDestination.entries.firstOrNull { it.label == startPage }?.labelRes

/**
 * Maps a configured [TabItem] to the root destination it drives. [TabItem.ADD] is a pseudo-tab
 * (an action shortcut to the add-transaction flow, wired in a later slice) and never resolves to a
 * navigable [MainDestination].
 */
private fun TabItem.toMainDestination(): MainDestination? = when (this) {
    TabItem.HOME -> MainDestination.Home
    TabItem.BUDGET -> MainDestination.Budget
    TabItem.TRANSACTIONS -> MainDestination.Transactions
    TabItem.ACCOUNTS -> MainDestination.Accounts
    TabItem.REPORTS -> MainDestination.Reports
    TabItem.MANAGE -> MainDestination.Manage
    TabItem.ADD -> null
}

internal enum class DetailDestination { Main, Reports, Transactions, EditTransaction, Search, Connection, CreditCards, CreditCardStatements, CreditCardStatementDetail, Loans, Rules, Schedules, ImportTransactions, ReviewCaptured, PayeeLocations, ManagePayees, BillsCalendar, FindSchedules, NewSchedule, EditSchedule, ManageCategories, ReorderGroups, ReorderAccounts, BudgetAutomation, CustomizeHome, CustomizeTabBar, BankSync }

private data class TabSnapshot(
    val detail: DetailDestination = DetailDestination.Main,
    val transactionAccount: String? = null,
    val transactionCategory: String? = null,
    val transactionMonth: String? = null,
    val transactionSearch: String = "",
    val activeBudgetCategory: String? = null,
    val transactionsReturnCategory: String? = null,
)

internal fun shouldRequestForegroundSync(foregroundGeneration: Int): Boolean =
    foregroundGeneration > 0

/**
 * An "After change" sync only uploads a local edit already reflected on screen, so it never
 * needs the banner; "App open"/"Background" syncs can bring in data the screen doesn't have yet.
 */
internal fun shouldShowSyncBanner(status: SyncStatus): Boolean =
    status.running && status.activeTrigger != SYNC_TRIGGER_AFTER_CHANGE

/**
 * Shown only when the server rejected the saved session and the open budget is server-backed.
 * Standalone budgets and the demo budget do not sync with a server and should never show this banner.
 */
internal fun shouldShowSessionExpiredBanner(
    sessionExpired: Boolean,
    isServerBackedBudget: Boolean,
    detail: DetailDestination,
): Boolean = sessionExpired && isServerBackedBudget && detail != DetailDestination.Connection

/** Shown when the server rejected the saved session, so nothing syncs until the user signs in again. */
@Composable
internal fun SessionExpiredBanner(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .clickable(onClickLabel = stringResource(R.string.navigation_sign_in_again), role = Role.Button, onClick = onSignIn),
    ) {
        Text(
            stringResource(R.string.navigation_signed_out_message),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .testTag("sessionExpiredBanner"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
internal fun SyncStatusBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .testTag("syncStatusBannerContent"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(16.dp)
                    .testTag("syncStatusBannerIndicator"),
            )
            Text(
                stringResource(R.string.navigation_syncing_budget),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun BudgetSwitchDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.navigation_loading_budget), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    foregroundGeneration: Int = 0,
    launchRequest: AppLaunchRequest? = null,
    onLaunchRequestConsumed: () -> Unit = {},
    onAppearanceChange: (String) -> Unit = {},
    onUseDynamicColorChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val locationPreferences = remember { LocationPreferences(context) }
    val displayPreferences = remember { DisplayPreferences(context) }
    // The full tag (not just the language) so pt-BR keeps its region; empty means "follow system".
    val languageTag = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag().orEmpty()
    val favoritePreferences = remember { FavoritePreferences(context) }
    val homePreferences = remember { HomePreferences(context) }
    var homeLayout by remember { mutableStateOf(homePreferences.layout()) }

    val tabBarPreferences = remember { TabBarPreferences(context) }
    var tabBarLayout by remember { mutableStateOf(tabBarPreferences.layout()) }
    // The bottom bar's rendered destinations, in configured order. The "+ Add" pseudo-tab isn't a
    // navigable destination (its tap behavior is wired in a later slice), so it's excluded here.
    val visibleMainDestinations = remember(tabBarLayout) {
        tabBarLayout.visibleTabs.mapNotNull { it.toMainDestination() }
    }
    val creditCardNotificationSettings = remember { CreditCardNotificationSettings(context) }
    var creditCardNotificationsEnabled by remember {
        mutableStateOf(creditCardNotificationSettings.isEnabled)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        creditCardNotificationSettings.isEnabled = granted
        creditCardNotificationsEnabled = granted
        CreditCardDueNotificationScheduler.refreshAsync(context)
    }
    val scheduleReminderSettings = remember { ScheduleReminderSettings(context) }
    var scheduleRemindersEnabled by remember { mutableStateOf(scheduleReminderSettings.isEnabled) }
    var scheduleReminderLeadDays by remember { mutableStateOf(scheduleReminderSettings.leadDays) }
    val scheduleReminderPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scheduleReminderSettings.isEnabled = granted
        scheduleRemindersEnabled = granted
    }
    var repositoryVersion by remember { mutableStateOf(0) }
    var budgetReplacementInProgress by remember { mutableStateOf(false) }
    var budgetReplacementCompleted by remember { mutableStateOf(false) }
    val activeBudgetId = remember(repositoryVersion) { ActiveBudgetStore(context).budgetId }
    val favoriteBudgetId = activeBudgetId ?: "no-budget"
    var homeMonthStart by remember(favoriteBudgetId) { mutableStateOf(homePreferences.monthStartRule(favoriteBudgetId)) }
    var homeHolidays by remember(favoriteBudgetId) { mutableStateOf(homePreferences.holidays(favoriteBudgetId)) }
    val availableBudgetOptions = remember(repositoryVersion) {
        BudgetFileManager(context).listLocalBudgets().map { budget ->
            BudgetSelectionItem(
                id = budget.id,
                name = budget.budgetName ?: budget.id,
            )
        }
    }
    val repository = remember(repositoryVersion) { ActuaRepository(context) }
    var dataVersion by remember { mutableStateOf(0) }
    var sharedImportText by remember { mutableStateOf<String?>(null) }
    // Each request reloads the capture queue, even when Review captured is already open.
    var capturedReviewRequest by remember { mutableIntStateOf(0) }
    var capturedReviewReturnsToImport by remember { mutableStateOf(false) }
    val captureQueue = remember { NotificationImportPreferences(context) }
    var pendingCaptureCount by remember { mutableIntStateOf(0) }
    DisposableEffect(captureQueue) {
        pendingCaptureCount = captureQueue.queued().size
        onDispose(captureQueue.observeQueue { pendingCaptureCount = captureQueue.queued().size })
    }
    val syncStatusStore = remember { SyncStatusStore(context) }
    val syncStatusGeneration by SyncSignals.statusGeneration.collectAsState()
    val syncDataGeneration by SyncSignals.dataGeneration.collectAsState()
    var syncStatus by remember { mutableStateOf(syncStatusStore.read()) }
    var budgetMonth by rememberSaveable {
        mutableStateOf(java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date()))
    }
    val budgetMonthRange = remember(dataVersion, repository) { repository.budgetMonthRange() }
    // Actual has no budget outside its range, so keep the selected month inside it.
    LaunchedEffect(budgetMonthRange) {
        val range = budgetMonthRange ?: return@LaunchedEffect
        val selected = java.time.YearMonth.parse(budgetMonth)
        if (selected !in range) budgetMonth = selected.coerceIn(range.start, range.endInclusive).toString()
    }
    val budgetGroups = remember(dataVersion, budgetMonth) { repository.budgetGroups(budgetMonth) }
    // Marks the first frame where the landing (Budget) screen has real data to show, so
    // Macrobenchmark's StartupTimingMetric can capture time-to-full-display instead of only
    // time-to-initial-display (which fires on the first empty/placeholder frame).
    var reportedFullyDrawn by remember { mutableStateOf(false) }
    LaunchedEffect(repository.isUsingActualBudget, budgetGroups) {
        if (!reportedFullyDrawn && repository.isUsingActualBudget) {
            reportedFullyDrawn = true
            (context as? Activity)?.reportFullyDrawn()
        }
    }
    val budgetScheduleFunding = remember(dataVersion, budgetMonth) {
        repository.budgetScheduleFunding(budgetMonth)
    }
    val budgetOverview = remember(dataVersion, budgetMonth) { repository.budgetOverview(budgetMonth) }
    val budgetHidesFraction = remember(dataVersion) { repository.budgetHidesFraction() }
    val budgetType = remember(dataVersion, repository) { repository.budgetType() }
    // Set during composition, before any amount below is formatted or parsed.
    CurrencyDisplay.decimalPlaces = remember(dataVersion, repository) { repository.budgetDecimalPlaces() }
    val accounts = remember(dataVersion) { repository.accounts() }
    // Transactions bank sync added that haven't been looked at yet, by account id (#1066).
    val newBankTransactionStore = remember { NewBankTransactions(context) }
    var newBankTransactions by remember(activeBudgetId) {
        mutableStateOf(activeBudgetId?.let(newBankTransactionStore::all).orEmpty())
    }
    DisposableEffect(newBankTransactionStore, activeBudgetId) {
        onDispose(newBankTransactionStore.observe {
            newBankTransactions = activeBudgetId?.let(newBankTransactionStore::all).orEmpty()
        })
    }
    val newBankTransactionCounts = remember(newBankTransactions) { newBankTransactions.mapValues { it.value.size } }
    // Actual's synced per-account `hide-reconciled-<accountId>` preference, shared with the PWA.
    val hideReconciledAccountIds = remember(dataVersion) { repository.hideReconciledAccountIds() }
    var showUpcomingTransactions by remember {
        mutableStateOf(displayPreferences.showUpcomingTransactions)
    }
    var transactionStatusFilter by rememberSaveable { mutableStateOf(TransactionStatusFilter.ALL) }
    val transactions = remember(dataVersion) { repository.transactions() }
    // Hiding reconciled rows is per account, so it's applied by TransactionsScreen for the open
    // account rather than here, where the list backs every account and the Transactions tab.
    val filteredTransactions = remember(dataVersion, transactionStatusFilter) {
        repository.transactions(statusFilter = transactionStatusFilter)
    }
    val searchTransactions: suspend (String) -> List<Transaction> = remember(repository, transactionStatusFilter) {
        { query -> withContext(Dispatchers.IO) {
            repository.transactions(query, statusFilter = transactionStatusFilter)
        } }
    }
    val categoryNames = remember(dataVersion) { repository.categoryNames() }
    // Transaction pickers show these labels and save the ids behind them (#747).
    val categoryChoices = remember(dataVersion) { repository.categoryChoices() }
    val accountChoices = remember(accounts) {
        PickerChoices.accounts(accounts.filterNot { it.closed }.map { it.id to it.name })
    }
    val payeeNames = remember(dataVersion) { repository.payeeNames() }
    val suggestedPayees = remember(dataVersion) { repository.suggestedPayeeNames() }
    val creditCards = remember(dataVersion) { repository.creditCards() }
    val loans = remember(dataVersion) { repository.loans() }
    val loanSnoozedMonths = remember(dataVersion) { repository.loanSnoozedMonths() }
    // reportSnapshot, rules/rulesSupported/scheduleOwnedRuleIds/ruleEditorData, and
    // reorderCategoryGroups are each read by exactly one destination (Reports, Rules,
    // ManageCategories/ReorderGroups) that isn't always on screen, unlike the values above. Per
    // #325 ("small mutations should produce correspondingly small UI/data work"), they're
    // remember()ed at their point of use instead of unconditionally here, so e.g. marking a
    // transaction cleared while on the Budget tab no longer also recomputes Reports' aggregation
    // or refetches rule-editor data (accounts/categories/payees) that nothing is currently
    // showing — matching the existing pattern `payeeLocations` already used below.
    val schedules = remember(dataVersion, homeMonthStart.nonWorkingDays) {
        repository.schedules(nonWorkingDays = homeMonthStart.nonWorkingDays)
    }
    // Replan schedule reminders after any local write, sync or budget switch, so posted, skipped,
    // matched, edited and deleted occurrences stop notifying. Rapid successive writes replan once.
    LaunchedEffect(dataVersion, repositoryVersion, scheduleRemindersEnabled, scheduleReminderLeadDays, homeMonthStart.nonWorkingDays) {
        delay(1_000)
        withContext(Dispatchers.IO) { ScheduleReminderScheduler.refresh(appContext) }
    }
    val upcomingTransactions = remember(schedules, homeMonthStart.nonWorkingDays) {
        upcomingTransactionsFrom(
            schedules,
            defaultUpcomingLength = repository.upcomingScheduleLength(),
            nonWorkingDays = homeMonthStart.nonWorkingDays,
        )
    }
    val upcomingRecurringScheduleIds = remember(schedules) { recurringScheduleIds(schedules) }
    val linkableSchedules = remember(schedules) {
        schedules.filterNot { it.schedule.completed }.map {
            com.azimulkabir.actua.ui.transactions.ScheduleOption(it.schedule.id, it.title)
        }
    }
    var editingScheduleId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingAutomationCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var destination by rememberSaveable {
        mutableStateOf(
            visibleMainDestinations.firstOrNull { it.label == displayPreferences.startPage }
                ?: visibleMainDestinations.firstOrNull()
                ?: MainDestination.Accounts,
        )
    }
    // Bottom navigation selection is deliberately separate from the displayed destination. A
    // tap can then draw its selected state before the next destination starts composing. This is
    // particularly important for destinations whose local cached data still needs preparation.
    var selectedTab by rememberSaveable { mutableStateOf(destination) }
    var tabSwitchJob by remember { mutableStateOf<Job?>(null) }
    var detail by rememberSaveable { mutableStateOf(DetailDestination.Main) }
    fun openCapturedReview(fromImport: Boolean = false) {
        capturedReviewRequest += 1
        capturedReviewReturnsToImport = fromImport
        detail = DetailDestination.ReviewCaptured
    }
    fun returnFromCapturedReview() {
        detail = if (capturedReviewReturnsToImport) DetailDestination.ImportTransactions else DetailDestination.Main
        capturedReviewReturnsToImport = false
    }
    // Diagnostics: which tab or detail screen the user was on before a problem (#225).
    LaunchedEffect(selectedTab, detail) {
        DiagnosticsLog.screen(if (detail == DetailDestination.Main) "Tab $selectedTab" else "Screen $detail")
    }
    // Re-read after sync status changes (a sync can expire the session) and after leaving Connection.
    val sessionExpired = remember(syncStatusGeneration, detail) { CredentialStore(context).sessionExpired }
    var transactionAccount by rememberSaveable { mutableStateOf<String?>(null) }
    // Editing one of the register's transactions doesn't leave the account.
    val openAccountId = if (detail == DetailDestination.Transactions || detail == DetailDestination.EditTransaction) {
        accounts.firstOrNull { it.name == transactionAccount }?.id
    } else null
    // Leaving an account's register marks its new transactions seen, as Actual's `markAccountRead`.
    var lastOpenAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openAccountId) {
        val left = lastOpenAccountId
        if (left != null && left != openAccountId) activeBudgetId?.let { newBankTransactionStore.clear(it, left) }
        lastOpenAccountId = openAccountId
    }
    var transactionCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionSearch by rememberSaveable { mutableStateOf("") }
    var searchReturnsToReports by rememberSaveable { mutableStateOf(false) }
    // Search may be entered from Reports, but a tab switch must not leave that return target
    // behind for a later search started elsewhere.
    LaunchedEffect(selectedTab, destination, detail) {
        if (detail != DetailDestination.Search || selectedTab != destination) {
            searchReturnsToReports = false
        }
    }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var newTransactionType by remember { mutableStateOf(com.azimulkabir.actua.model.Type.EXPENSE) }
    // Starting values for a new transfer opened from "Pay statement"; cleared whenever the editor closes.
    var transactionPrefill by remember { mutableStateOf<com.azimulkabir.actua.ui.transactions.TransactionPrefill?>(null) }
    var editorReturnsToTransactions by rememberSaveable { mutableStateOf(false) }
    var editorReturnsToCategory by rememberSaveable { mutableStateOf(false) }
    var editorReturnsToReports by rememberSaveable { mutableStateOf(false) }
    var editorReturnsToReportsDetail by rememberSaveable { mutableStateOf<DetailDestination?>(null) }
    var activeDrillDown by remember { mutableStateOf<Pair<ReportCategory, String>?>(null) }
    var activeBudgetCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var reopenBudgetCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionsReturnCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val tabSnapshots = remember { mutableStateMapOf<MainDestination, TabSnapshot>() }
    val rootRequests = remember { mutableStateMapOf<MainDestination, Int>() }
    val tabStateHolder = rememberSaveableStateHolder()
    var reportSnapshot by remember(repository) { mutableStateOf<ReportSnapshot?>(null) }
    var reportSnapshotVersion by remember(repository) { mutableStateOf(-1) }
    var reportSnapshotMonthStart by remember(repository) { mutableStateOf<MonthStartRule?>(null) }
    var addOrigin by rememberSaveable { mutableStateOf(MainDestination.Accounts) }
    var transactionFabExpanded by rememberSaveable { mutableStateOf(true) }
    var transactionsRefreshing by remember { mutableStateOf(false) }
    var accountsRefreshing by remember { mutableStateOf(false) }
    var simpleFinConfigured by remember { mutableStateOf(false) }
    var goCardlessConfigured by remember { mutableStateOf(false) }
    var simpleFinDiscovery by remember { mutableStateOf<com.azimulkabir.actua.ui.banksync.DiscoveryState>(com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle) }
    var goCardlessDiscovery by remember { mutableStateOf<com.azimulkabir.actua.ui.banksync.DiscoveryState>(com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle) }
    var goCardlessInstitutions by remember { mutableStateOf<List<com.azimulkabir.actua.ui.banksync.GoCardlessInstitutionUi>>(emptyList()) }
    var goCardlessInstitutionsLoading by remember { mutableStateOf(false) }
    var goCardlessRequisitionId by remember { mutableStateOf<String?>(null) }
    val experimentalPreferences = remember { com.azimulkabir.actua.data.preferences.ExperimentalPreferences(context) }
    var enableBankingExperiment by remember { mutableStateOf(experimentalPreferences.enableBanking) }
    var enableBankingConfigured by remember { mutableStateOf(false) }
    var reconcileOpen by remember { mutableStateOf(false) }
    var scheduleReturnsToBills by rememberSaveable { mutableStateOf(false) }
    var scheduleReturnsToTransactions by rememberSaveable { mutableStateOf(false) }
    var scheduleReturnsToTransactionsTab by rememberSaveable { mutableStateOf(false) }
    var billsCalendarReturnsToSchedules by rememberSaveable { mutableStateOf(false) }
    // Set while Rules is open on a schedule's own rule via "Edit as rule"; closing it returns to the schedule.
    var scheduleRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var creditCardsReturnToBills by rememberSaveable { mutableStateOf(false) }
    var statementsAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var statementsReturnToTransactions by rememberSaveable { mutableStateOf(false) }
    var selectedStatement by remember { mutableStateOf<com.azimulkabir.actua.model.CreditCardCycle.StatementRecord?>(null) }
    var editorReturnsToStatementDetail by rememberSaveable { mutableStateOf(false) }
    var decimalPlacesMode by remember { mutableStateOf(displayPreferences.decimalPlacesMode) }
    var currencyCode by remember { mutableStateOf(displayPreferences.currencyCode) }
    var currencySymbolOnly by remember { mutableStateOf(displayPreferences.currencySymbolOnly) }
    var currencySymbolPosition by remember { mutableStateOf(displayPreferences.currencySymbolPosition) }
    var currencySymbolSpacing by remember { mutableStateOf(displayPreferences.currencySymbolSpacing) }
    var dateFormat by remember { mutableStateOf(displayPreferences.dateFormat) }
    var numberFormat by remember { mutableStateOf(displayPreferences.numberFormat) }
    var showHiddenCategories by remember { mutableStateOf(displayPreferences.showHiddenCategories) }
    var showSpentColumn by remember { mutableStateOf(displayPreferences.showSpentColumn) }
    var showBudgetProgressBars by remember { mutableStateOf(displayPreferences.showBudgetProgressBars) }
    var budgetView by remember { mutableStateOf(displayPreferences.budgetView) }
    var showBudgetOverview by remember { mutableStateOf(displayPreferences.showBudgetOverview) }
    var showBudgetStatusBanners by remember { mutableStateOf(displayPreferences.showBudgetStatusBanners) }
    var showGroupTotals by remember { mutableStateOf(displayPreferences.showGroupTotals) }
    var showBalancePills by remember { mutableStateOf(displayPreferences.showBalancePills) }
    var hideFullySpentCategories by remember { mutableStateOf(displayPreferences.hideFullySpentCategories) }
    var budgetCategoryView by remember { mutableStateOf(displayPreferences.budgetCategoryView) }
    var showCategoryFilters by remember { mutableStateOf(displayPreferences.showCategoryFilters) }
    var favoritesOnly by remember { mutableStateOf(displayPreferences.favoritesOnly) }
    var favoriteCategoryIds by remember(favoriteBudgetId) {
        mutableStateOf(favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.CATEGORY))
    }
    var favoriteAccountIds by remember(favoriteBudgetId) {
        mutableStateOf(favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.ACCOUNT))
    }
    var favoriteReportIds by remember(favoriteBudgetId) {
        mutableStateOf(favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.REPORT))
    }
    var requestedReportPageId by remember { mutableStateOf<String?>(null) }
    var requestedReportPageRequest by remember { mutableStateOf(0) }
    var privacyMode by remember { mutableStateOf(displayPreferences.privacyMode) }
    var appearance by remember { mutableStateOf(displayPreferences.appearance) }
    var useDynamicColor by remember { mutableStateOf(displayPreferences.useDynamicColor) }
    var startPage by remember { mutableStateOf(displayPreferences.startPage) }
    var defaultAccount by remember { mutableStateOf(displayPreferences.defaultAccount) }
    var groupTransactionsByDate by remember { mutableStateOf(displayPreferences.groupTransactionsByDate) }
    var showAccountsMonthlySummary by remember { mutableStateOf(displayPreferences.showAccountsMonthlySummary) }
    var conventionalAmountEntry by remember { mutableStateOf(displayPreferences.conventionalAmountEntry) }
    val tabBarScroll = remember { TabBarScrollState(displayPreferences) }
    val showBottomNavigationLabels = tabBarScroll.showLabels
    var showCurrentBalanceSummary by remember { mutableStateOf(displayPreferences.showCurrentBalanceSummary) }
    var showRunningBalance by remember { mutableStateOf(displayPreferences.showRunningBalance) }
    var showNotes by remember { mutableStateOf(displayPreferences.showNotes) }
    var hideIncomeGroupInBudget by remember { mutableStateOf(displayPreferences.hideIncomeGroupInBudget) }
    var showReportSummary by remember { mutableStateOf(displayPreferences.showReportSummary) }
    val budgetDisplayFormats = remember(dataVersion, repository) { repository.budgetDisplayFormats() }
    // Device choices, or the budget's synced preferences where the device is set to "Same as budget".
    val displayFormats = EffectiveDisplayFormats.resolve(
        currencyCode, currencySymbolOnly, currencySymbolPosition, currencySymbolSpacing,
        dateFormat, numberFormat, decimalPlacesMode, privacyMode, budgetDisplayFormats,
    )
    val hideDecimalPlaces = displayFormats.hideDecimals
    val hideBalances = displayFormats.hideBalances
    BalanceVisibility.hidden = hideBalances
    applyDisplayFormats(displayFormats)
    val snackbarHostState = remember { SnackbarHostState() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var transactionImpactCues by remember { mutableStateOf<List<TransactionImpactCue>>(emptyList()) }

    fun budgetCategoriesOf(transaction: Transaction): Set<String> {
        if (transaction.type == com.azimulkabir.actua.model.Type.TRANSFER) return emptySet()
        return buildSet {
            if (transaction.category.isNotBlank() && transaction.category != "Uncategorized") add(transaction.category)
            transaction.splits.forEach {
                if (it.category.isNotBlank() && it.category != "Uncategorized") add(it.category)
            }
        }
    }

    fun withTagAppended(transaction: Transaction, tag: String): Transaction? {
        if (findTagOccurrences(transaction.notes).any { it.name == tag }) return null
        val notes = if (transaction.notes.isBlank()) "#$tag" else "${transaction.notes} #$tag"
        return transaction.copy(notes = notes)
    }

    fun categoryAvailableCents(names: Set<String>): Map<String, Long> {
        if (names.isEmpty()) return emptyMap()
        return repository.budgetGroups()
            .asSequence()
            .flatMap { it.categories.asSequence() }
            .filter { it.name in names }
            .associate { it.name to it.balanceCents }
    }

    fun impactCues(before: Map<String, Long>, after: Map<String, Long>): List<TransactionImpactCue> =
        (before.keys + after.keys).distinct().mapNotNull { name ->
            val balanceBefore = before[name] ?: return@mapNotNull null
            val balanceAfter = after[name] ?: return@mapNotNull null
            if (balanceBefore == balanceAfter) null
            else TransactionImpactCue(name, balanceBeforeCents = balanceBefore, balanceAfterCents = balanceAfter)
        }

    // All strings used by the mutation/refresh helpers below, resolved once at composition
    // (configuration-aware) and bundled into a single holder so the content switch below
    // captures one object instead of one parameter per string.
    val navStrings = navigationStrings()

    // The (disk I/O) mutation runs off the main thread; [onChanged] fires afterwards on the
    // main thread once the local write has durably completed, so callers that need to react to
    // success (closing a dialog, navigating away) do so there instead of on a return value.
    // labelRes is a runtime @StringRes parameter, so only Context.getString can resolve it here.
    @SuppressLint("LocalContextGetResourceValueCall")
    fun mutate(@StringRes labelRes: Int, onChanged: () -> Unit = {}, action: () -> Boolean) {
        val label = context.getString(labelRes)
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            result.fold(
                onSuccess = { changed ->
                    if (changed) {
                        dataVersion += 1
                        onChanged()
                    } else {
                        DiagnosticsLog.actionRejected(label)
                        errorMessage = navStrings.mutationIncompleteFormat.format(label)
                    }
                },
                onFailure = { error ->
                    DiagnosticsLog.actionFailed(label, error)
                    errorMessage = error.message?.takeIf(String::isNotBlank)
                        ?: navStrings.mutationFailedFormat.format(label)
                },
            )
        }
    }

    val transactionLoanAccount = accounts.firstOrNull { it.name == transactionAccount }
    val transactionLoanActions = remember(repository, transactionLoanAccount) {
        LoanScreenActions(repository, transactionLoanAccount) { res, action -> mutate(res, action = action) }
    }

    // Drag-to-reorder screens need to know synchronously whether a move was accepted, to decide
    // whether to keep the just-dropped optimistic order or snap back to the last confirmed one.
    // Reorder writes are single-row updates (not the batched writes this main-thread-blocking fix
    // targets), so this stays synchronous rather than gaining the same async/onChanged shape as
    // [mutate]. labelRes is a runtime @StringRes parameter (see [mutate]).
    @SuppressLint("LocalContextGetResourceValueCall")
    fun mutateSync(@StringRes labelRes: Int, action: () -> Boolean): Boolean {
        val label = context.getString(labelRes)
        return runCatching(action).fold(
            onSuccess = { changed ->
                if (changed) dataVersion += 1 else {
                    DiagnosticsLog.actionRejected(label)
                    errorMessage = navStrings.mutationIncompleteFormat.format(label)
                }
                changed
            },
            onFailure = { error ->
                DiagnosticsLog.actionFailed(label, error)
                errorMessage = error.message?.takeIf(String::isNotBlank)
                    ?: navStrings.mutationFailedFormat.format(label)
                false
            },
        )
    }

    /** Like [mutate], but also shows [transactionImpactCues] for every budget category [categories] touches. */
    fun onUpcomingAction(transaction: Transaction, action: UpcomingTransactionAction) {
        val id = transaction.scheduleId ?: return
        when (action) {
            UpcomingTransactionAction.POST ->
                mutate(R.string.navigation_posting_schedule) {
                    repository.postScheduleTransaction(id, today = false, nonWorkingDays = homeMonthStart.nonWorkingDays)
                }
            UpcomingTransactionAction.POST_TODAY ->
                mutate(R.string.navigation_posting_schedule_today) {
                    repository.postScheduleTransaction(id, today = true, nonWorkingDays = homeMonthStart.nonWorkingDays)
                }
            UpcomingTransactionAction.SKIP ->
                mutate(R.string.navigation_skipping_next_date) { repository.skipScheduleNextDate(id) }
            UpcomingTransactionAction.COMPLETE ->
                mutate(R.string.navigation_completing_schedule) { repository.setScheduleCompleted(id, true) }
        }
    }

    // labelRes is a runtime @StringRes parameter (see [mutate]).
    @SuppressLint("LocalContextGetResourceValueCall")
    fun mutateWithImpactCue(@StringRes labelRes: Int, categories: Set<String>, onChanged: () -> Unit = {}, action: () -> Boolean) {
        val label = context.getString(labelRes)
        coroutineScope.launch {
            val before = withContext(Dispatchers.IO) { categoryAvailableCents(categories) }
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            result.fold(
                onSuccess = { changed ->
                    if (changed) {
                        dataVersion += 1
                        val after = withContext(Dispatchers.IO) { categoryAvailableCents(categories) }
                        transactionImpactCues = impactCues(before, after)
                        onChanged()
                    } else {
                        DiagnosticsLog.actionRejected(label)
                        errorMessage = navStrings.mutationIncompleteFormat.format(label)
                    }
                },
                onFailure = { error ->
                    DiagnosticsLog.actionFailed(label, error)
                    errorMessage = error.message?.takeIf(String::isNotBlank)
                        ?: navStrings.mutationFailedFormat.format(label)
                },
            )
        }
    }

    fun refreshTransactions() {
        if (transactionsRefreshing) return
        transactionsRefreshing = true
        coroutineScope.launch {
            try {
                val linkedAccount = accounts.firstOrNull {
                    it.name == transactionAccount && it.bankSyncSource != null
                }
                if (linkedAccount != null) {
                    val bankResult = withContext(Dispatchers.IO) { repository.syncBanks(linkedAccount.id) }
                    snackbarHostState.showSnackbar(bankResult.summary)
                }
                when (withContext(Dispatchers.IO) {
                    ActualSyncRunner.run(appContext, trigger = SYNC_TRIGGER_PULL_REFRESH)
                }) {
                    is SyncRunResult.Success -> Unit
                    SyncRunResult.NotConfigured -> dataVersion += 1
                    SyncRunResult.EncryptionKeyUnavailable -> {
                        errorMessage = navStrings.unlockBudgetBeforeSync
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DiagnosticsLog.actionFailed("Pull to refresh", error)
                errorMessage = error.message?.takeIf(String::isNotBlank)
                    ?: navStrings.syncFailed
            } finally {
                transactionsRefreshing = false
            }
        }
    }

    fun refreshAccounts() {
        if (accountsRefreshing) return
        accountsRefreshing = true
        coroutineScope.launch {
            try {
                if (accounts.any { it.bankSyncSource != null }) {
                    val bankResult = withContext(Dispatchers.IO) { repository.syncBanks() }
                    // One account with new rows can be opened from the message (#1066).
                    val viewAccount = bankResult.added.keys.singleOrNull()?.let { id -> accounts.firstOrNull { it.id == id } }
                    launch {
                        val action = snackbarHostState.showSnackbar(
                            bankResult.summary,
                            actionLabel = viewAccount?.let { navStrings.view },
                            duration = if (viewAccount != null) SnackbarDuration.Long else SnackbarDuration.Short,
                        )
                        if (action == SnackbarResult.ActionPerformed && viewAccount != null) {
                            transactionAccount = viewAccount.name; transactionCategory = null; transactionMonth = null
                            transactionSearch = ""
                            destination = MainDestination.Accounts; detail = DetailDestination.Transactions
                        }
                    }
                }
                when (withContext(Dispatchers.IO) {
                    ActualSyncRunner.run(appContext, trigger = SYNC_TRIGGER_PULL_REFRESH)
                }) {
                    is SyncRunResult.Success -> Unit
                    SyncRunResult.NotConfigured -> dataVersion += 1
                    SyncRunResult.EncryptionKeyUnavailable -> {
                        errorMessage = navStrings.unlockBudgetBeforeSync
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DiagnosticsLog.actionFailed("Pull to refresh", error)
                errorMessage = error.message?.takeIf(String::isNotBlank)
                    ?: navStrings.syncFailed
            } finally {
                accountsRefreshing = false
            }
        }
    }

    fun loadBankSyncProviderStatus() {
        coroutineScope.launch {
            simpleFinConfigured = withContext(Dispatchers.IO) {
                runCatching { repository.bankSyncProviderConfigured("simpleFin") }.getOrDefault(false)
            }
            goCardlessConfigured = withContext(Dispatchers.IO) {
                runCatching { repository.bankSyncProviderConfigured("goCardless") }.getOrDefault(false)
            }
            // Enable Banking is experimental: don't touch its server routes unless the user opted in.
            enableBankingConfigured = enableBankingExperiment && withContext(Dispatchers.IO) {
                runCatching { repository.bankSyncProviderConfigured("enableBanking") }.getOrDefault(false)
            }
        }
    }

    val enableBankingMessages = com.azimulkabir.actua.ui.banksync.EnableBankingMessages(
        starting = stringResource(R.string.fs_eb_starting),
        wait = stringResource(R.string.fs_eb_wait),
        loadFailed = stringResource(R.string.fs_eb_load_failed),
        authorizeFailed = stringResource(R.string.fs_eb_authorize_failed),
    )
    val enableBankingFlow = remember(repository, enableBankingMessages) {
        com.azimulkabir.actua.ui.banksync.EnableBankingFlow(
            scope = coroutineScope,
            io = Dispatchers.IO,
            loadBanks = repository::discoverEnableBankingBanks,
            startAuthorization = repository::startEnableBankingAuthorization,
            awaitAuthorization = repository::awaitEnableBankingAuthorization,
            openUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            onError = { message -> errorMessage = message },
            messages = enableBankingMessages,
        )
    }

    fun saveEnableBankingCredentials(applicationId: String, secretKey: String) {
        coroutineScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.setEnableBankingCredentials(applicationId, secretKey) } }
                .onSuccess {
                    loadBankSyncProviderStatus()
                    snackbarHostState.showSnackbar(navStrings.enableBankingSaved)
                }
                .onFailure {
                    errorMessage = it.message?.takeIf(String::isNotBlank)
                        ?: navStrings.enableBankingSaveFailed
                }
        }
    }

    fun saveSimpleFinToken(token: String) {
        coroutineScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.setSimpleFinToken(token) } }
                .onSuccess {
                    loadBankSyncProviderStatus()
                    snackbarHostState.showSnackbar(navStrings.simplefinSaved)
                }
                .onFailure {
                    errorMessage = it.message?.takeIf(String::isNotBlank)
                        ?: navStrings.simplefinSaveFailed
                }
        }
    }

    fun saveGoCardlessCredentials(secretId: String, secretKey: String) {
        coroutineScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.setGoCardlessCredentials(secretId, secretKey) } }
                .onSuccess {
                    loadBankSyncProviderStatus()
                    snackbarHostState.showSnackbar(navStrings.gocardlessSaved)
                }
                .onFailure {
                    errorMessage = it.message?.takeIf(String::isNotBlank)
                        ?: navStrings.gocardlessSaveFailed
                }
        }
    }

    fun discoverSimpleFinAccounts() {
        simpleFinDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Loading
        coroutineScope.launch {
            simpleFinDiscovery = runCatching { withContext(Dispatchers.IO) { repository.discoverSimpleFinAccounts() } }.fold(
                onSuccess = { result ->
                    when (result) {
                        is com.azimulkabir.actua.data.network.SimpleFinAccountsResult.Available -> com.azimulkabir.actua.ui.banksync.DiscoveryState.Available(
                            result.accounts.map {
                                com.azimulkabir.actua.ui.banksync.DiscoveredBankAccount(
                                    it.id, it.name, it.orgName, bankId = it.orgDomain ?: it.orgId, bankName = it.orgName,
                                )
                            },
                        )
                        is com.azimulkabir.actua.data.network.SimpleFinAccountsResult.Error ->
                            com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(result.reason)
                    }
                },
                onFailure = { error ->
                    DiagnosticsLog.actionFailed("Loading SimpleFIN accounts", error)
                    com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(
                        error.message ?: navStrings.simplefinAccountsLoadFailed,
                    )
                },
            )
        }
    }

    fun loadGoCardlessInstitutions(country: String) {
        goCardlessInstitutionsLoading = true
        coroutineScope.launch {
            goCardlessInstitutions = runCatching { withContext(Dispatchers.IO) { repository.discoverGoCardlessInstitutions(country) } }
                .getOrElse { error ->
                    errorMessage = error.message?.takeIf(String::isNotBlank)
                        ?: navStrings.banksLoadFailedFormat.format(country)
                    emptyList()
                }.map { com.azimulkabir.actua.ui.banksync.GoCardlessInstitutionUi(it.id, it.name) }
            goCardlessInstitutionsLoading = false
        }
    }

    fun startGoCardlessAuthorization(institutionId: String) {
        coroutineScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.startGoCardlessAuthorization(institutionId) } }
                .onSuccess { webToken ->
                    goCardlessRequisitionId = webToken.requisitionId
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webToken.link)))
                }
                .onFailure {
                    errorMessage = it.message?.takeIf(String::isNotBlank)
                        ?: navStrings.gocardlessAuthorizationStartFailed
                }
        }
    }

    fun checkGoCardlessAccounts() {
        val requisitionId = goCardlessRequisitionId
        if (requisitionId == null) {
            goCardlessDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(
                navStrings.chooseBankFirst,
            )
            return
        }
        goCardlessDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Loading
        coroutineScope.launch {
            goCardlessDiscovery = runCatching { withContext(Dispatchers.IO) { repository.goCardlessAccountsForRequisition(requisitionId) } }.fold(
                onSuccess = { result ->
                    when (result) {
                        is com.azimulkabir.actua.data.network.GoCardlessAccountsResult.Available -> com.azimulkabir.actua.ui.banksync.DiscoveryState.Available(
                            result.accounts.map {
                                com.azimulkabir.actua.ui.banksync.DiscoveredBankAccount(
                                    "$requisitionId::${it.id}", it.name ?: it.iban ?: it.id, it.iban,
                                    bankName = it.institutionName,
                                )
                            },
                        )
                        is com.azimulkabir.actua.data.network.GoCardlessAccountsResult.Pending ->
                            com.azimulkabir.actua.ui.banksync.DiscoveryState.Pending(
                                navStrings.gocardlessAuthorizationPendingFormat.format(result.status),
                            )
                        is com.azimulkabir.actua.data.network.GoCardlessAccountsResult.Error ->
                            com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(result.reason)
                    }
                },
                onFailure = { error ->
                    DiagnosticsLog.actionFailed("Checking GoCardless accounts", error)
                    com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(
                        error.message ?: navStrings.gocardlessAccountsCheckFailed,
                    )
                },
            )
        }
    }

    /** GoCardless ids are packed as "requisitionId::accountId" by [checkGoCardlessAccounts]; other providers use the bare id. */
    fun splitGoCardlessId(discoveredId: String): Pair<String?, String> {
        val parts = discoveredId.split("::", limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else null to discoveredId
    }

    fun linkBankAccount(discovered: com.azimulkabir.actua.ui.banksync.DiscoveredBankAccount, account: Account, source: String) {
        val (requisitionId, externalId) = when (source) {
            "goCardless" -> splitGoCardlessId(discovered.id)
            // Enable Banking keeps one `banks` row per account, keyed by the account id itself.
            "enableBanking" -> discovered.id to discovered.id
            // SimpleFIN's `banks` row is keyed on the org domain, or its id (#1004).
            "simpleFin" -> discovered.bankId to discovered.id
            else -> null to discovered.id
        }
        val bankName = if (source == "enableBanking") enableBankingFlow.institutionFor(discovered.id) else discovered.bankName
        mutate(
            R.string.navigation_linking_account,
            onChanged = {
                goCardlessDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                simpleFinDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                enableBankingFlow.reset()
            },
        ) { repository.linkBankAccount(account.id, externalId, source, requisitionId, bankName) }
    }

    fun createAndLinkBankAccount(
        discovered: com.azimulkabir.actua.ui.banksync.DiscoveredBankAccount, name: String, offBudget: Boolean, source: String,
    ) {
        val (requisitionId, externalId) = when (source) {
            "goCardless" -> splitGoCardlessId(discovered.id)
            "enableBanking" -> discovered.id to discovered.id
            "simpleFin" -> discovered.bankId to discovered.id
            else -> null to discovered.id
        }
        val bankName = if (source == "enableBanking") enableBankingFlow.institutionFor(discovered.id) else discovered.bankName
        mutate(
            R.string.navigation_creating_account,
            onChanged = {
                goCardlessDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                simpleFinDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                enableBankingFlow.reset()
            },
        ) { repository.createLinkedAccount(name, offBudget, externalId, source, requisitionId, bankName) }
    }

    fun unlinkBankAccount(account: Account) {
        mutate(R.string.navigation_unlinking_account) { repository.unlinkBankAccount(account.id) }
    }

    // Same contract as [mutate], but the (disk I/O) mutation runs off the main thread and
    // [onChanged] fires afterwards on the main thread once the local write has durably
    // completed. Use this for mutations on interaction-critical paths (e.g. dismissing an
    // editor) so local DB work never blocks the UI thread. labelRes is a runtime @StringRes
    // parameter (see [mutate]).
    @SuppressLint("LocalContextGetResourceValueCall")
    fun mutateAsync(@StringRes labelRes: Int, action: () -> Boolean, onChanged: () -> Unit) {
        val label = context.getString(labelRes)
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            result.fold(
                onSuccess = { changed ->
                    if (changed) {
                        dataVersion += 1
                        onChanged()
                    } else {
                        DiagnosticsLog.actionRejected(label)
                        errorMessage = navStrings.mutationIncompleteFormat.format(label)
                    }
                },
                onFailure = { error ->
                    DiagnosticsLog.actionFailed(label, error)
                    errorMessage = error.message?.takeIf(String::isNotBlank)
                        ?: navStrings.mutationFailedFormat.format(label)
                },
            )
        }
    }

    /** Like [mutateAsync], but also shows [transactionImpactCues] for every budget category [categories] touches. */
    // labelRes is a runtime @StringRes parameter (see [mutate]).
    @SuppressLint("LocalContextGetResourceValueCall")
    fun mutateAsyncWithImpactCue(@StringRes labelRes: Int, categories: Set<String>, action: () -> Boolean, onChanged: () -> Unit) {
        val label = context.getString(labelRes)
        coroutineScope.launch {
            val before = withContext(Dispatchers.IO) { categoryAvailableCents(categories) }
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            result.fold(
                onSuccess = { changed ->
                    if (changed) {
                        dataVersion += 1
                        val after = withContext(Dispatchers.IO) { categoryAvailableCents(categories) }
                        transactionImpactCues = impactCues(before, after)
                        onChanged()
                    } else {
                        DiagnosticsLog.actionRejected(label)
                        errorMessage = navStrings.mutationIncompleteFormat.format(label)
                    }
                },
                onFailure = { error ->
                    DiagnosticsLog.actionFailed(label, error)
                    errorMessage = error.message?.takeIf(String::isNotBlank)
                        ?: navStrings.mutationFailedFormat.format(label)
                },
            )
        }
    }

    fun returnFromScheduleRule() {
        scheduleRuleId = null
        detail = DetailDestination.EditSchedule
    }

    fun returnFromEditSchedule() {
        when {
            scheduleReturnsToTransactionsTab -> {
                destination = MainDestination.Transactions
                detail = DetailDestination.Main
            }
            scheduleReturnsToTransactions -> detail = DetailDestination.Transactions
            scheduleReturnsToBills -> detail = DetailDestination.BillsCalendar
            else -> detail = DetailDestination.Schedules
        }
        scheduleReturnsToBills = false
        scheduleReturnsToTransactions = false
        scheduleReturnsToTransactionsTab = false
    }

    fun openAddTransaction() {
        addOrigin = destination
        editingTransaction = null
        newTransactionType = com.azimulkabir.actua.model.Type.EXPENSE
        editorReturnsToTransactions = false
        editorReturnsToCategory = false
        detail = DetailDestination.EditTransaction
    }

    fun openAddTransactionForAccount() {
        editingTransaction = null
        newTransactionType = com.azimulkabir.actua.model.Type.EXPENSE
        editorReturnsToTransactions = true
        editorReturnsToCategory = false
        transactionFabExpanded = true
        detail = DetailDestination.EditTransaction
    }

    fun openAddTransactionForCategory() {
        transactionCategory = activeBudgetCategory
        editingTransaction = null
        newTransactionType = com.azimulkabir.actua.model.Type.EXPENSE
        editorReturnsToTransactions = false
        editorReturnsToCategory = true
        transactionFabExpanded = true
        detail = DetailDestination.EditTransaction
    }

    val fabScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f) transactionFabExpanded = false
                if (available.y > 2f) transactionFabExpanded = true
                tabBarScroll.onScroll(available.y)
                return Offset.Zero
            }
        }
    }

    // Every tab or screen change starts with the bar showing.
    LaunchedEffect(destination, detail) { tabBarScroll.show() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    LaunchedEffect(syncStatusGeneration) {
        syncStatus = syncStatusStore.read()
    }

    LaunchedEffect(syncDataGeneration) {
        if (syncDataGeneration > 0) dataVersion += 1
    }

    LaunchedEffect(budgetReplacementCompleted) {
        if (budgetReplacementCompleted) {
            // repositoryVersion has already rebuilt the repository and all screen projections.
            // Keep the blocker through the first frame that can display those new values.
            withFrameNanos { }
            budgetReplacementInProgress = false
            budgetReplacementCompleted = false
        }
    }

    LaunchedEffect(destination) {
        if (tabSwitchJob?.isActive != true) selectedTab = destination
    }

    LaunchedEffect(detail) {
        // Otherwise a later plain visit to Reports (bottom tab, Manage) would replay this stale
        // request and force the view back to whatever favorite was last opened from Home.
        if (detail != DetailDestination.Reports) {
            requestedReportPageId = null
            requestedReportPageRequest = 0
        }
    }

    LaunchedEffect(destination, detail, dataVersion, repository, homeMonthStart) {
        repository.reportMonthStart = homeMonthStart
        val needsReportSnapshot = detail == DetailDestination.Reports ||
            (detail == DetailDestination.Main && destination in setOf(MainDestination.Home, MainDestination.Reports))
        if (needsReportSnapshot && (reportSnapshotVersion != dataVersion || reportSnapshotMonthStart != homeMonthStart)) {
            try {
                val loaded = withContext(Dispatchers.IO) { repository.reports(dataVersion) }
                reportSnapshot = loaded
                reportSnapshotVersion = dataVersion
                reportSnapshotMonthStart = homeMonthStart
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DiagnosticsLog.actionFailed("Loading reports", e)
                errorMessage = navStrings.reportsLoadFailedFormat.format(e.localizedMessage)
            }
        }
    }

    LaunchedEffect(foregroundGeneration) {
        if (!shouldRequestForegroundSync(foregroundGeneration)) return@LaunchedEffect
        val result = try {
            withContext(Dispatchers.IO) {
                ActualSyncRunner.run(context, allowRecentSuccess = true, trigger = "App open")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // This automatic app-open sync is opportunistic: a cold launch can race the
            // network coming up (e.g. via a launcher shortcut straight into the editor),
            // and ActualSyncRunner.run already records the failure in SyncStatusStore for
            // the Connection screen. Surfacing every transient failure here as a Snackbar
            // would alarm the user over something that resolves on the next sync attempt.
            null
        }
        when (result) {
            is SyncRunResult.Success -> {
                // Reminder planning opens the selected budget and widget discovery crosses
                // Binder; the successful sync has already refreshed visible data, so defer
                // this non-critical maintenance work from the main thread.
                withContext(Dispatchers.IO) {
                    CreditCardDueNotificationScheduler.refresh(appContext)
                    WidgetUpdater.requestAll(appContext)
                }
            }
            SyncRunResult.NotConfigured -> Unit
            SyncRunResult.EncryptionKeyUnavailable -> {
                errorMessage = navStrings.unlockBeforeSyncing
            }
            null -> Unit
        }
    }

    LaunchedEffect(launchRequest?.nonce) {
        val request = launchRequest ?: return@LaunchedEffect
        if (!repository.isUsingActualBudget) {
            destination = MainDestination.Manage
            detail = DetailDestination.Connection
            onLaunchRequestConsumed()
            return@LaunchedEffect
        }
        when (request.action) {
            SHARED_IMPORT_ACTION -> {
                sharedImportText = request.target
                destination = MainDestination.Manage
                detail = DetailDestination.ImportTransactions
            }
            CapturedTransactionNotifications.ACTION_REVIEW -> {
                destination = MainDestination.Accounts
                openCapturedReview()
            }
            WidgetActions.BUDGET -> {
                destination = MainDestination.Budget
                detail = DetailDestination.Main
            }
            WidgetActions.CATEGORY -> {
                destination = MainDestination.Budget
                activeBudgetCategory = null
                reopenBudgetCategory = request.target
                detail = DetailDestination.Main
            }
            WidgetActions.ACCOUNTS -> {
                destination = MainDestination.Accounts
                transactionAccount = request.target
                transactionCategory = null
                transactionMonth = null
                transactionSearch = ""
                detail = if (request.target == null) DetailDestination.Main else DetailDestination.Transactions
            }
            CreditCardDueLaunch.ACTION -> {
                // A card deleted since the reminder was posted opens the Accounts list instead.
                val accountName = CreditCardDueLaunch.accountName(accounts, request.target)
                destination = MainDestination.Accounts
                transactionAccount = accountName
                transactionCategory = null
                transactionMonth = null
                transactionSearch = ""
                detail = if (accountName == null) DetailDestination.Main else DetailDestination.Transactions
            }
            ScheduleReminderLaunch.ACTION -> {
                // A schedule deleted (or in another budget) since the reminder opens the schedule list.
                val scheduleId = ScheduleReminderLaunch.scheduleId(schedules, request.target)
                destination = MainDestination.Manage
                if (scheduleId == null) {
                    detail = DetailDestination.Schedules
                } else {
                    editingScheduleId = scheduleId
                    scheduleReturnsToBills = false
                    scheduleReturnsToTransactions = false
                    scheduleReturnsToTransactionsTab = false
                    detail = DetailDestination.EditSchedule
                }
            }
            WidgetActions.SEARCH -> {
                destination = MainDestination.Transactions
                detail = DetailDestination.Search
            }
            WidgetActions.SCHEDULES -> {
                destination = MainDestination.Manage
                detail = DetailDestination.Schedules
            }
            WidgetActions.ADD_EXPENSE, WidgetActions.ADD_INCOME, WidgetActions.ADD_TRANSFER -> {
                destination = MainDestination.Transactions
                addOrigin = MainDestination.Transactions
                editingTransaction = null
                editorReturnsToTransactions = false
                editorReturnsToCategory = false
                newTransactionType = when (request.action) {
                    WidgetActions.ADD_INCOME -> com.azimulkabir.actua.model.Type.INCOME
                    WidgetActions.ADD_TRANSFER -> com.azimulkabir.actua.model.Type.TRANSFER
                    else -> com.azimulkabir.actua.model.Type.EXPENSE
                }
                detail = DetailDestination.EditTransaction
            }
        }
        onLaunchRequestConsumed()
    }

    // Tabs can be hidden/reordered, so the exit tab for the back gesture follows the
    // user's configured start page rather than being hardcoded to Home.
    val backGestureHomeDestination = visibleMainDestinations.firstOrNull { it.label == startPage }
        ?: visibleMainDestinations.firstOrNull()
        ?: MainDestination.Accounts

    BackHandler(enabled = detail != DetailDestination.Main || destination != backGestureHomeDestination) {
        when {
            detail == DetailDestination.EditTransaction && editorReturnsToCategory -> {
                reopenBudgetCategory = transactionCategory
                detail = DetailDestination.Main
                destination = MainDestination.Budget
                editingTransaction = null
                editorReturnsToCategory = false
            }
            detail == DetailDestination.Rules && scheduleRuleId != null -> returnFromScheduleRule()
            detail == DetailDestination.BillsCalendar && billsCalendarReturnsToSchedules -> {
                billsCalendarReturnsToSchedules = false
                detail = DetailDestination.Schedules
            }
            detail == DetailDestination.CreditCardStatementDetail -> {
                detail = DetailDestination.CreditCardStatements
            }
            detail == DetailDestination.CreditCardStatements -> {
                detail = if (statementsReturnToTransactions) DetailDestination.Transactions else DetailDestination.CreditCards
                statementsReturnToTransactions = false
            }
            detail == DetailDestination.EditTransaction && editorReturnsToReports -> {
                destination = MainDestination.Reports
                detail = editorReturnsToReportsDetail ?: DetailDestination.Main
                editingTransaction = null
                editorReturnsToReports = false
                editorReturnsToReportsDetail = null
            }
            detail == DetailDestination.EditTransaction && editorReturnsToTransactions -> {
                detail = DetailDestination.Transactions
                editingTransaction = null
            }
            detail == DetailDestination.ReorderGroups -> {
                detail = DetailDestination.ManageCategories
            }
            detail == DetailDestination.ReviewCaptured -> returnFromCapturedReview()
            detail == DetailDestination.CustomizeHome -> {
                detail = DetailDestination.Main
            }
            detail == DetailDestination.CustomizeTabBar -> {
                detail = DetailDestination.Main
            }
            detail == DetailDestination.BudgetAutomation -> {
                reopenBudgetCategory = editingAutomationCategory
                editingAutomationCategory = null
                detail = DetailDestination.Main
            }
            detail == DetailDestination.Search && searchReturnsToReports -> {
                searchReturnsToReports = false
                detail = DetailDestination.Reports
            }
            detail != DetailDestination.Main -> {
                if (detail == DetailDestination.Transactions && transactionsReturnCategory != null) {
                    reopenBudgetCategory = transactionsReturnCategory
                    transactionsReturnCategory = null
                    destination = MainDestination.Budget
                }
                detail = DetailDestination.Main
                editingTransaction = null
            }
            else -> destination = backGestureHomeDestination
        }
    }


    val onMainTab = detail == DetailDestination.Main && destination in setOf(
        MainDestination.Home,
        MainDestination.Budget,
        MainDestination.Accounts,
        MainDestination.Transactions,
        MainDestination.Reports,
    )
    val inAccount = detail == DetailDestination.Transactions && transactionAccount != null
    val inBudgetCategory = detail == DetailDestination.Main &&
        destination == MainDestination.Budget && activeBudgetCategory != null
    // When the configured tab bar includes the "+ Add" pseudo-tab, it replaces the floating
    // Transaction button entirely (see Slice 3, #481): no FAB on any screen, and the bottom
    // scroll padding each screen otherwise reserves to clear the FAB collapses to zero.
    val showAddTab = TabItem.ADD in tabBarLayout.visibleTabs
    val hasFab = repository.isUsingActualBudget && !reconcileOpen && (onMainTab || inAccount) && !showAddTab

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { ActuaSnackbarHost(snackbarHostState) },
        topBar = {
            if (shouldShowSessionExpiredBanner(sessionExpired, repository.isServerBacked, detail)) {
                SessionExpiredBanner(onSignIn = { detail = DetailDestination.Connection })
            } else if (shouldShowSyncBanner(syncStatus) && repository.isUsingActualBudget) {
                SyncStatusBanner()
            }
        },
        floatingActionButton = {
            if (hasFab) {
                ExtendedFloatingActionButton(
                    onClick = when {
                        inBudgetCategory -> ::openAddTransactionForCategory
                        inAccount -> ::openAddTransactionForAccount
                        else -> ::openAddTransaction
                    },
                    expanded = transactionFabExpanded,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.navigation_transaction)) },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = tabBarScroll.visible && detail != DetailDestination.EditTransaction,
                enter = slideInVertically { it } + expandVertically(),
                exit = slideOutVertically { it } + shrinkVertically(),
            ) { FloatingNavigationBar(
                showLabels = showBottomNavigationLabels,
            ) {
                tabBarLayout.visibleTabs.forEach { tabItem ->
                    if (tabItem == TabItem.ADD) {
                        FloatingNavigationBarItem(
                            selected = false,
                            onClick = {
                                if (inAccount) openAddTransactionForAccount() else openAddTransaction()
                                transactionFabExpanded = true
                            },
                            icon = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    Icon(
                                        Icons.Outlined.Add,
                                        contentDescription = stringResource(tabItem.labelRes()),
                                        modifier = Modifier.padding(10.dp).size(28.dp),
                                    )
                                }
                            },
                            // Just the big +, with no text even when tab labels are on; its content
                            // description above stays the accessibility name.
                            label = null,
                        )
                        return@forEach
                    }
                    val item = tabItem.toMainDestination() ?: return@forEach
                    FloatingNavigationBarItem(
                        selected = selectedTab == item,
                        onClick = {
                            if (item != MainDestination.Manage && !repository.isUsingActualBudget) {
                                tabSwitchJob?.cancel()
                                selectedTab = MainDestination.Manage
                                destination = MainDestination.Manage
                                detail = DetailDestination.Connection
                                return@FloatingNavigationBarItem
                            }
                            if (item == destination) {
                                tabSwitchJob?.cancel()
                                selectedTab = destination
                                if (detail != DetailDestination.Main) {
                                    searchReturnsToReports = false
                                    detail = DetailDestination.Main
                                    editingTransaction = null
                                    transactionAccount = null
                                    transactionCategory = null
                                    transactionMonth = null
                                    transactionSearch = ""
                                    transactionsReturnCategory = null
                                } else {
                                    rootRequests[item] = (rootRequests[item] ?: 0) + 1
                                }
                            } else {
                                tabSwitchJob?.cancel()
                                tabSnapshots[destination] = TabSnapshot(
                                    detail = detail.takeUnless {
                                        it == DetailDestination.Search || it == DetailDestination.EditTransaction
                                    } ?: DetailDestination.Main,
                                    transactionAccount = transactionAccount,
                                    transactionCategory = transactionCategory,
                                    transactionMonth = transactionMonth,
                                    transactionSearch = transactionSearch,
                                    activeBudgetCategory = activeBudgetCategory,
                                    transactionsReturnCategory = transactionsReturnCategory,
                                )
                                val restored = tabSnapshots[item] ?: TabSnapshot()
                                selectedTab = item
                                tabSwitchJob = coroutineScope.launch {
                                    // Let the navigation indicator render before composing the
                                    // target. No artificial delay is introduced: this resumes on
                                    // the very next frame.
                                    withFrameNanos { }
                                    destination = item
                                    detail = restored.detail
                                    transactionAccount = restored.transactionAccount
                                    transactionCategory = restored.transactionCategory
                                    transactionMonth = restored.transactionMonth
                                    transactionSearch = restored.transactionSearch
                                    activeBudgetCategory = restored.activeBudgetCategory
                                    reopenBudgetCategory = restored.activeBudgetCategory
                                    transactionsReturnCategory = restored.transactionsReturnCategory
                                }
                            }
                            transactionFabExpanded = true
                        },
                        icon = {
                            // Waiting captures show on the Accounts tab from any tab (#1065).
                            val captures = if (item == MainDestination.Accounts) pendingCaptureCount else 0
                            if (captures > 0) {
                                BadgedBox(badge = { Badge(Modifier.clearAndSetSemantics {}) { Text(pendingCaptureBadge(captures)) } }) {
                                    Icon(
                                        item.icon,
                                        contentDescription = pluralStringResource(
                                            R.plurals.navigation_tab_captures_waiting,
                                            captures,
                                            stringResource(item.labelRes),
                                            captures,
                                        ),
                                    )
                                }
                            } else {
                                Icon(item.icon, contentDescription = stringResource(item.labelRes))
                            }
                        },
                        label = if (showBottomNavigationLabels) {
                            {
                                Text(
                                    text = stringResource(item.labelRes),
                                    maxLines = 1,
                                    softWrap = false,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 8.sp,
                                        maxFontSize = 12.sp,
                                    ),
                                )
                            }
                        } else null,
                    )
                }
            } }
        },
    ) { innerPadding ->
        // Root list screens run behind the transparent area around the floating pill and clear it
        // with their own bottom padding; every other screen still ends above the bar.
        val drawsBehindTabBar = (detail == DetailDestination.Main && destination != MainDestination.Manage) ||
            detail == DetailDestination.Transactions || detail == DetailDestination.Reports
        val contentModifier = (if (drawsBehindTabBar) Modifier.padding(top = innerPadding.calculateTopPadding()) else Modifier.padding(innerPadding))
            .nestedScroll(fabScrollConnection)
        val loadReportWidgets: suspend (ReportViewFilter) -> List<ReportWidget> = remember(repository, dataVersion, homeMonthStart) {
            { view -> withContext(Dispatchers.IO) { repository.savedReportWidgets(view, dataVersion) } }
        }
        val loadReportWidgetForRange: suspend (String, com.azimulkabir.actua.model.ReportRange) -> ReportWidget? = remember(repository, dataVersion, homeMonthStart) {
            { widgetId, range -> withContext(Dispatchers.IO) { repository.reportWidgetForRange(widgetId, range, dataVersion) } }
        }
        val loadReportTransactions: suspend (List<String>) -> List<Transaction> = remember(repository) {
            { ids -> withContext(Dispatchers.IO) { repository.reportTransactions(ids) } }
        }
        val loadDrillDownTransactions: suspend (DrillDownQuery) -> List<Transaction> = remember(repository) {
            { query -> withContext(Dispatchers.IO) { repository.drillDownTransactions(query) } }
        }
        val onEditReportTransaction: (Transaction) -> Unit = { tx ->
            editingTransaction = tx
            editorReturnsToReports = true
            editorReturnsToReportsDetail = detail
            detail = DetailDestination.EditTransaction
        }
        val onOpenReportInTransactions: (DrillDownQuery?, ReportCategory) -> Unit = { query, segment ->
            transactionAccount = null
            transactionCategory = query?.categoryName ?: segment.name.takeUnless { it in setOf("Income", "Expenses", "Remaining", "This month", "Comparison", "Total") || it.matches(Regex("\\d{4}-\\d{2}|\\d{4}-\\d{2}-\\d{2}")) }
            transactionMonth = query?.month ?: segment.name.takeIf { it.matches(Regex("\\d{4}-\\d{2}")) }
            transactionSearch = ""
            detail = DetailDestination.Transactions
        }
        AnimatedContent(
            targetState = detail to destination,
            transitionSpec = {
                val openingDetail = initialState.first == DetailDestination.Main &&
                    targetState.first != DetailDestination.Main
                val closingDetail = initialState.first != DetailDestination.Main &&
                    targetState.first == DetailDestination.Main
                when {
                    openingDetail -> (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 5 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 10 })
                    closingDetail -> (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { -it / 5 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 10 })
                    else -> (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.985f)) togetherWith
                        (fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 1.015f))
                // Detail destinations can differ radically in height. Animating their bounds
                // repeatedly measures both full-screen trees; keep the motion on layers instead.
                }.using(SizeTransform(sizeAnimationSpec = { _, _ -> snap() }, clip = false))
            },
            label = "Main navigation motion",
        ) { (shownDetail, shownDestination) ->
        tabStateHolder.SaveableStateProvider("${shownDestination.name}:${shownDetail.name}") {
        when (shownDetail) {
            DetailDestination.Reports -> NavigationReportsScreen(
                snapshot = reportSnapshot,
                snapshotVersion = reportSnapshotVersion,
                dataVersion = dataVersion,
                hideDecimalPlaces = hideDecimalPlaces,
                modifier = contentModifier,
                onSearch = {
                    searchReturnsToReports = true
                    detail = DetailDestination.Search
                },
                favoriteBudgetId = favoriteBudgetId,
                favoritePreferences = favoritePreferences,
                favoriteReportIds = favoriteReportIds,
                onFavoriteReportChange = { id, favorite ->
                    favoritePreferences.set(favoriteBudgetId, FavoritePreferences.Type.REPORT, id, favorite)
                    favoriteReportIds = favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.REPORT)
                },
                loadSavedReports = loadReportWidgets,
                loadWidgetForRange = loadReportWidgetForRange,
                loadTransactions = loadReportTransactions,
                loadDrillDownTransactions = loadDrillDownTransactions,
                onEditTransaction = onEditReportTransaction,
                onOpenInTransactions = onOpenReportInTransactions,
                activeDrillDown = activeDrillDown,
                onDrillDownChange = { activeDrillDown = it },
                showReportSummary = showReportSummary,
                onShowReportSummaryChange = {
                    displayPreferences.showReportSummary = it
                    showReportSummary = it
                },
                scrollToTopRequest = 0,
                initialPageId = requestedReportPageId,
                initialPageRequest = requestedReportPageRequest,
                hasFab = hasFab,
                repository = repository,
                onMutate = { labelRes, action -> mutate(labelRes, action = action) },
            )
            DetailDestination.Transactions -> TransactionsScreen(
                accountName = transactionAccount,
                newTransactionIds = openAccountId?.let { newBankTransactions[it] }.orEmpty(),
                categoryName = transactionCategory,
                month = transactionMonth,
                onBack = {
                    transactionsReturnCategory?.let {
                        reopenBudgetCategory = it
                        transactionsReturnCategory = null
                        destination = MainDestination.Budget
                    }
                    detail = DetailDestination.Main
                },
                onEdit = {
                    editingTransaction = it
                    editorReturnsToTransactions = true
                    detail = DetailDestination.EditTransaction
                },
                modifier = contentModifier,
                transactions = filteredTransactions,
                allTransactions = transactions,
                searchTransactions = searchTransactions,
                transactionStatusFilter = transactionStatusFilter,
                onTransactionStatusFilterChange = { transactionStatusFilter = it },
                hideDecimalPlaces = hideDecimalPlaces,
                conventionalAmountEntry = conventionalAmountEntry,
                groupTransactionsByDate = groupTransactionsByDate,
                onGroupTransactionsByDateChange = {
                    displayPreferences.groupTransactionsByDate = it
                    groupTransactionsByDate = it
                },
                hideReconciledTransactions = accounts.firstOrNull { it.name == transactionAccount }
                    ?.let { it.id in hideReconciledAccountIds } == true,
                onHideReconciledTransactionsChange = { hide ->
                    accounts.firstOrNull { it.name == transactionAccount }?.let { account ->
                        mutate(R.string.navigation_updating_account_view) { repository.setHideReconciled(account.id, hide) }
                    }
                },
                onSetCleared = { transaction, cleared ->
                    mutate(R.string.navigation_updating_transaction) { repository.setTransactionCleared(transaction.id, cleared) }
                },
                onUnlockTransaction = { transaction ->
                    mutate(R.string.navigation_unlocking_transaction) { repository.unlockTransaction(transaction.id) }
                },
                onReconcileAccount = { account, onReconciled ->
                    mutate(R.string.navigation_reconciling_account, onChanged = onReconciled) { repository.reconcileAccount(account.id) }
                },
                onCreateReconciliationAdjustment = { account, difference, onCreated ->
                    mutate(R.string.navigation_creating_reconciliation_adjustment, onChanged = onCreated) {
                        repository.createReconciliationAdjustment(account.id, difference)
                    }
                },
                onDelete = { transaction ->
                    mutateWithImpactCue(R.string.navigation_deleting_transaction, budgetCategoriesOf(transaction)) {
                        repository.deleteTransaction(transaction.id)
                    }
                },
                onDeleteMultiple = { transactionsToDelete ->
                    val categories = transactionsToDelete.flatMapTo(mutableSetOf()) { budgetCategoriesOf(it) }
                    mutateWithImpactCue(R.string.navigation_deleting_transactions, categories) {
                        repository.deleteTransactions(transactionsToDelete.map { it.id }) > 0
                    }
                },
                onDuplicate = { transaction ->
                    val duplicate = transaction.asDuplicate().asTransferDraft()
                    mutateWithImpactCue(R.string.navigation_duplicating_transaction, budgetCategoriesOf(duplicate)) {
                        repository.saveTransaction(duplicate); true
                    }
                },
                onDuplicateMultiple = { transactionsToDuplicate ->
                    val duplicates = transactionsToDuplicate.map { it.asDuplicate().asTransferDraft() }
                    val categories = duplicates.flatMapTo(mutableSetOf()) { budgetCategoriesOf(it) }
                    mutateWithImpactCue(R.string.navigation_duplicating_transactions, categories) {
                        duplicates.forEach { repository.saveTransaction(it) }
                        duplicates.isNotEmpty()
                    }
                },
                onLinkSchedule = { transactionsToLink, scheduleId ->
                    mutate(R.string.navigation_linking_schedule) {
                        repository.linkScheduleTransactions(scheduleId, transactionsToLink.map { it.id }) > 0
                    }
                },
                onUnlinkSchedule = { transactionsToUnlink ->
                    mutate(R.string.navigation_unlinking_schedule) {
                        repository.unlinkScheduleFromTransactions(transactionsToUnlink.map { it.id }) > 0
                    }
                },
                onViewSchedule = { scheduleId ->
                    editingScheduleId = scheduleId
                    scheduleReturnsToBills = false
                    scheduleReturnsToTransactions = true
                    scheduleReturnsToTransactionsTab = false
                    detail = DetailDestination.EditSchedule
                },
                categoryOptions = categoryChoices.labels,
                onCategorizeMultiple = { transactionsToCategorize, category ->
                    val picked = categoryChoices.choice(category)
                    val updated = transactionsToCategorize.map {
                        it.copy(
                            category = picked?.name ?: category,
                            categoryId = picked?.id,
                            categoryIsExplicit = true,
                        ).asTransferDraft()
                    }
                    val categories = (transactionsToCategorize + updated).flatMapTo(mutableSetOf()) { budgetCategoriesOf(it) }
                    mutateWithImpactCue(R.string.navigation_categorizing_transactions, categories) {
                        updated.forEach { repository.saveTransaction(it) }
                        updated.isNotEmpty()
                    }
                },
                accountOptions = accountChoices.labels,
                onMoveMultiple = { transactionsToMove, accountName ->
                    val picked = accountChoices.choice(accountName)
                    val updated = transactionsToMove.map {
                        it.copy(account = picked?.name ?: accountName, accountId = picked?.id).asTransferDraft()
                    }
                    val categories = transactionsToMove.flatMapTo(mutableSetOf()) { budgetCategoriesOf(it) }
                    mutateWithImpactCue(R.string.navigation_moving_transactions, categories) {
                        updated.forEach { repository.saveTransaction(it) }
                        updated.isNotEmpty()
                    }
                },
                onLabelMultiple = { transactionsToLabel, tag ->
                    val updated = transactionsToLabel.mapNotNull { withTagAppended(it, tag)?.asTransferDraft() }
                    mutate(R.string.navigation_labeling_transactions) {
                        updated.forEach { repository.saveTransaction(it) }
                        true
                    }
                },
                onMergeTransactions = { first, second ->
                    val categories = budgetCategoriesOf(first) + budgetCategoriesOf(second)
                    mutateWithImpactCue(R.string.navigation_merging_transactions, categories) {
                        repository.mergeTransactions(first.id, second.id)
                    }
                },
                onMakeTransfer = { first, second ->
                    val categories = budgetCategoriesOf(first) + budgetCategoriesOf(second)
                    mutateWithImpactCue(R.string.navigation_making_transfer, categories) {
                        repository.linkAsTransfer(first.id, second.id)
                    }
                },
                linkableSchedules = linkableSchedules,
                account = accounts.firstOrNull { it.name == transactionAccount },
                creditCard = creditCards.firstOrNull { card ->
                    card.accountId == accounts.firstOrNull { it.name == transactionAccount }?.id
                },
                loan = loans.firstOrNull { loan -> loan.accountId == transactionLoanAccount?.id },
                loanTarget = rememberLoanTarget(repository, loans, transactionLoanAccount, dataVersion),
                onSnoozeLoanTarget = transactionLoanActions::snooze,
                loanFundingAccounts = accounts,
                onRecordLoanPayment = transactionLoanActions::record,
                onSaveAccountNote = { note ->
                    accounts.firstOrNull { it.name == transactionAccount }?.let { account ->
                        mutate(R.string.navigation_saving_account_note) { repository.setAccountNote(account.id, note) }
                    }
                },
                onPayStatement = { remainingDueCents ->
                    accounts.firstOrNull { it.name == transactionAccount }?.let { account ->
                        com.azimulkabir.actua.ui.transactions.payStatementPrefill(
                            accountChoices.labelOf(account.id, account.name), remainingDueCents,
                        )?.let { prefill ->
                            transactionPrefill = prefill
                            editingTransaction = null
                            newTransactionType = com.azimulkabir.actua.model.Type.EXPENSE
                            editorReturnsToTransactions = true
                            editorReturnsToCategory = false
                            detail = DetailDestination.EditTransaction
                        }
                    }
                },
                onViewStatements = {
                    accounts.firstOrNull { it.name == transactionAccount }?.let { account ->
                        statementsAccountId = account.id
                        statementsReturnToTransactions = true
                        detail = DetailDestination.CreditCardStatements
                    }
                },
                initialSearch = transactionSearch,
                showCurrentBalanceSummary = showCurrentBalanceSummary,
                onShowCurrentBalanceSummaryChange = {
                    displayPreferences.showCurrentBalanceSummary = it
                    showCurrentBalanceSummary = it
                },
                showRunningBalance = showRunningBalance,
                onShowRunningBalanceChange = {
                    displayPreferences.showRunningBalance = it
                    showRunningBalance = it
                },
                showNotes = showNotes,
                onReconcileVisibilityChange = { reconcileOpen = it },
                isRefreshing = transactionsRefreshing,
                onRefresh = ::refreshTransactions,
                upcomingTransactions = upcomingTransactions,
                showUpcomingTransactions = showUpcomingTransactions,
                onShowUpcomingTransactionsChange = {
                    displayPreferences.showUpcomingTransactions = it
                    showUpcomingTransactions = it
                },
                recurringScheduleIds = upcomingRecurringScheduleIds,
                onUpcomingAction = ::onUpcomingAction,
                hasFab = hasFab,
            )
            DetailDestination.EditTransaction -> {
            // Otherwise these are rebuilt from the whole account/payee lists on every
            // recomposition of this (very broad) composable while the editor is open, e.g. on
            // every keystroke/amount-entry change, instead of only when the underlying data or
            // display toggles actually change.
            val nonClosedAccounts = remember(accounts) { accounts.filterNot { it.closed } }
            // Options, off-budget flags and balances are keyed by picker label, not name (#747).
            val accountOptions = accountChoices.labels
            val offBudgetAccountOptions = remember(nonClosedAccounts, accountChoices) {
                nonClosedAccounts.filter { it.offBudget }.mapTo(mutableSetOf()) { accountChoices.labelOf(it.id, it.name) }
            }
            val accountBalanceLabels = remember(nonClosedAccounts, accountChoices, hideBalances, hideDecimalPlaces) {
                if (hideBalances) emptyMap() else nonClosedAccounts
                    .associate { accountChoices.labelOf(it.id, it.name) to formatMoneyCents(it.balanceCents, hideDecimalPlaces) }
            }
            val categoryBalanceLabels = remember(budgetGroups, categoryChoices, hideBalances, hideDecimalPlaces) {
                if (hideBalances) emptyMap() else budgetGroups
                    .asSequence()
                    .flatMap { it.categories.asSequence() }
                    .associate { categoryChoices.labelOf(it.id, it.name) to formatMoneyCents(it.balanceCents, hideDecimalPlaces) }
            }
            // Localization for the editor's non-composable callbacks below, resolved at composition
            // by a single helper call so this branch stays lean inside the content switch.
            val editorStrings = transactionEditorStrings()
            val payeeOptions = remember(payeeNames, accountOptions) {
                (payeeNames + accountOptions.map { editorStrings.transferPayeeFormat.format(it) }).distinct()
            }
            AddTransactionScreen(
                editing = editingTransaction,
                prefill = transactionPrefill,
                defaultType = newTransactionType,
                onBack = {
                    transactionPrefill = null
                    if (editorReturnsToCategory) {
                        reopenBudgetCategory = transactionCategory
                        destination = MainDestination.Budget
                    }
                    if (editorReturnsToReports) {
                        destination = MainDestination.Reports
                    }
                    detail = when {
                        editorReturnsToStatementDetail -> DetailDestination.CreditCardStatementDetail
                        editorReturnsToTransactions -> DetailDestination.Transactions
                        editorReturnsToReports -> editorReturnsToReportsDetail ?: DetailDestination.Main
                        else -> DetailDestination.Main
                    }
                    editingTransaction = null
                    if (!editorReturnsToTransactions && !editorReturnsToCategory && !editorReturnsToStatementDetail && !editorReturnsToReports) destination = addOrigin
                    editorReturnsToCategory = false
                    editorReturnsToStatementDetail = false
                    editorReturnsToReports = false
                    editorReturnsToReportsDetail = null
                },
                onSave = { savedTransaction ->
                    val wasEditing = editingTransaction != null
                    val impactCategories = budgetCategoriesOf(savedTransaction) +
                        (editingTransaction?.let { budgetCategoriesOf(it) } ?: emptySet())
                    coroutineScope.launch {
                        val categoryBalancesBefore = withContext(Dispatchers.IO) { categoryAvailableCents(impactCategories) }
                        // The local CRDT write is disk I/O; keep it off the main thread so the
                        // editor dismisses as soon as the transaction is durably saved locally,
                        // without waiting on anything network-related (sync is scheduled
                        // separately and runs fully asynchronously).
                        val result = withContext(Dispatchers.IO) {
                            runCatching { repository.saveTransaction(savedTransaction) }
                        }
                        result.onFailure { error ->
                            errorMessage = error.message?.takeIf(String::isNotBlank)
                            ?: editorStrings.savingTransactionFailed
                        }
                        if (result.isSuccess) {
                            dataVersion += 1
                            if (impactCategories.isNotEmpty()) {
                                val categoryBalancesAfter = withContext(Dispatchers.IO) { categoryAvailableCents(impactCategories) }
                                transactionImpactCues = impactCues(categoryBalancesBefore, categoryBalancesAfter)
                            }
                            WidgetUpdater.requestAll(context)
                            if (!wasEditing &&
                                savedTransaction.type != com.azimulkabir.actua.model.Type.TRANSFER &&
                                savedTransaction.payee.isNotBlank() &&
                                locationPreferences.recordPayeeLocations &&
                                repository.payeeLocationWritesSupported()
                            ) {
                                val location = AndroidLocationProvider(context).currentCoordinates()
                                if (location is CurrentLocationResult.Success) {
                                    withContext(Dispatchers.IO) {
                                        repository.recordPayeeLocation(savedTransaction.payee, location.coordinates)
                                    }
                                }
                            }
                            editingTransaction = null
                            transactionPrefill = null
                            if (editorReturnsToCategory) {
                                reopenBudgetCategory = transactionCategory
                                destination = MainDestination.Budget
                                detail = DetailDestination.Main
                                editorReturnsToCategory = false
                            } else if (editorReturnsToStatementDetail) {
                                detail = DetailDestination.CreditCardStatementDetail
                                editorReturnsToStatementDetail = false
                            } else if (editorReturnsToReports) {
                                destination = MainDestination.Reports
                                detail = editorReturnsToReportsDetail ?: DetailDestination.Main
                                editorReturnsToReports = false
                                editorReturnsToReportsDetail = null
                            } else if (editorReturnsToTransactions) {
                                detail = DetailDestination.Transactions
                            } else if (wasEditing) {
                                detail = DetailDestination.Main
                            } else {
                                destination = MainDestination.Transactions
                                transactionAccount = null
                                transactionCategory = null
                                transactionMonth = null
                                transactionSearch = ""
                                detail = DetailDestination.Main
                            }
                        }
                    }
                },
                onDelete = { transaction ->
                    mutateAsyncWithImpactCue(
                        R.string.navigation_deleting_transaction,
                        budgetCategoriesOf(transaction),
                        { repository.deleteTransaction(transaction.id) },
                    ) {
                        editingTransaction = null
                        detail = when {
                            editorReturnsToStatementDetail -> {
                                editorReturnsToStatementDetail = false
                                DetailDestination.CreditCardStatementDetail
                            }
                            editorReturnsToTransactions -> DetailDestination.Transactions
                            editorReturnsToReports -> {
                                destination = MainDestination.Reports
                                val target = editorReturnsToReportsDetail ?: DetailDestination.Main
                                editorReturnsToReports = false
                                editorReturnsToReportsDetail = null
                                target
                            }
                            else -> {
                                destination = addOrigin
                                DetailDestination.Main
                            }
                        }
                    }
                },
                modifier = contentModifier,
                    accountOptions = accountOptions,
                    offBudgetAccountOptions = offBudgetAccountOptions,
                    accountBalanceLabels = accountBalanceLabels,
                    categoryBalanceLabels = categoryBalanceLabels,
                    categoryOptions = categoryChoices.labels,
                    accountChoices = accountChoices,
                    categoryChoices = categoryChoices,
                    payeeOptions = payeeOptions,
                    suggestedPayees = suggestedPayees,
                    defaultAccount = if (editingTransaction == null && editorReturnsToTransactions) {
                        transactionAccount ?: defaultAccount
                    } else {
                        defaultAccount
                    },
                    defaultCategory = if (editingTransaction == null && editorReturnsToCategory) {
                        transactionCategory
                    } else {
                        null
                    },
                    hideDecimalPlaces = hideDecimalPlaces,
                    conventionalAmountEntry = conventionalAmountEntry,
                    onPreviewRules = repository::previewRules,
                    onFindNearbyPayees = {
                        when (val location = AndroidLocationProvider(context).currentCoordinates()) {
                            is CurrentLocationResult.Success -> {
                                val nearby = withContext(Dispatchers.IO) {
                                    repository.nearbyPayees(location.coordinates)
                                }
                                NearbyPayeeSearchResult(
                                    options = nearby.map {
                                        NearbyPayeeOption(
                                            payee = it.payeeName,
                                            distance = LocationUtils.formatDistance(it.distanceMeters),
                                            locationId = it.locationId,
                                        )
                                    },
                                    message = if (nearby.isEmpty()) {
                                        editorStrings.noNearbyPayeeLocations
                                    } else {
                                        null
                                    },
                                )
                            }
                            CurrentLocationResult.PermissionDenied -> NearbyPayeeSearchResult(
                                message = editorStrings.locationPermissionSearch,
                            )
                            CurrentLocationResult.ServicesDisabled -> NearbyPayeeSearchResult(
                                message = editorStrings.locationDisabledSearch,
                            )
                            CurrentLocationResult.Timeout -> NearbyPayeeSearchResult(
                                message = editorStrings.locationTimeoutSearch,
                            )
                            CurrentLocationResult.Unavailable -> NearbyPayeeSearchResult(
                                message = editorStrings.locationUnavailableSearch,
                            )
                            is CurrentLocationResult.Inaccurate -> NearbyPayeeSearchResult(
                                message = editorStrings.locationInaccurateSearch,
                            )
                        }
                    },
                    onSavePayeeLocation = if (repository.payeeLocationWritesSupported()) {
                        { payeeName ->
                            when (val location = AndroidLocationProvider(context).currentCoordinates()) {
                                is CurrentLocationResult.Success -> {
                                    val recorded = withContext(Dispatchers.IO) {
                                        repository.recordPayeeLocation(payeeName, location.coordinates)
                                    }
                                    if (recorded) dataVersion += 1
                                    PayeeLocationSaveResult(
                                        nowNearby = true,
                                        message = if (recorded) {
                                            editorStrings.locationSavedFormat.format(payeeName)
                                        } else {
                                            editorStrings.locationAlreadySavedFormat.format(payeeName)
                                        },
                                    )
                                }
                                CurrentLocationResult.PermissionDenied -> PayeeLocationSaveResult(
                                    false, editorStrings.locationPermissionSave,
                                )
                                CurrentLocationResult.ServicesDisabled -> PayeeLocationSaveResult(
                                    false, editorStrings.locationDisabledSave,
                                )
                                CurrentLocationResult.Timeout -> PayeeLocationSaveResult(
                                    false, editorStrings.locationTimeoutSave,
                                )
                                CurrentLocationResult.Unavailable -> PayeeLocationSaveResult(
                                    false, editorStrings.locationUnavailableSave,
                                )
                                is CurrentLocationResult.Inaccurate -> PayeeLocationSaveResult(
                                    false, editorStrings.locationInaccurateSave,
                                )
                            }
                        }
                    } else {
                        null
                    },
                    onForgetPayeeLocation = if (repository.payeeLocationWritesSupported()) {
                        { locationId ->
                            withContext(Dispatchers.IO) {
                                repository.deletePayeeLocation(locationId)
                            }.also { deleted ->
                                if (deleted) dataVersion += 1
                            }
                        }
                    } else {
                        null
                    },
            )
            }
            DetailDestination.Search -> GlobalSearchScreen(
                transactions = filteredTransactions,
                searchTransactions = remember(repository, transactionStatusFilter) {
                    { query, limit, offset ->
                        withContext(Dispatchers.IO) {
                            repository.transactions(query, limit, offset,
                                statusFilter = transactionStatusFilter)
                        }
                    }
                },
                accounts = accounts,
                payees = payeeNames,
                categories = categoryNames,
                hideDecimalPlaces = hideDecimalPlaces,
                onBack = {
                    detail = if (searchReturnsToReports) DetailDestination.Reports else DetailDestination.Main
                    searchReturnsToReports = false
                },
                onTransactionEdit = {
                    addOrigin = destination
                    editingTransaction = it
                    editorReturnsToTransactions = false
                    detail = DetailDestination.EditTransaction
                },
                onTransactionDelete = { transaction ->
                    mutateWithImpactCue(R.string.navigation_deleting_transaction, budgetCategoriesOf(transaction)) {
                        repository.deleteTransaction(transaction.id)
                    }
                },
                onTransactionClearedChange = { transaction, cleared ->
                    mutate(R.string.navigation_updating_transaction) { repository.setTransactionCleared(transaction.id, cleared) }
                },
                onTransactionUnlock = { transaction ->
                    mutate(R.string.navigation_unlocking_transaction) { repository.unlockTransaction(transaction.id) }
                },
                onAccountClick = {
                    transactionAccount = it; transactionCategory = null; transactionMonth = null; transactionSearch = ""
                    destination = MainDestination.Accounts; detail = DetailDestination.Transactions
                },
                onCategoryClick = {
                    transactionAccount = null; transactionCategory = it; transactionMonth = null; transactionSearch = ""
                    destination = MainDestination.Budget; detail = DetailDestination.Transactions
                },
                onPayeeClick = {
                    transactionAccount = null; transactionCategory = null; transactionMonth = null; transactionSearch = it
                    destination = MainDestination.Accounts; detail = DetailDestination.Transactions
                },
                modifier = contentModifier,
            )
            DetailDestination.Connection -> ConnectionScreen(
                onBack = { detail = DetailDestination.Main },
                onBeforeBudgetReplacement = {
                    if (!budgetReplacementInProgress) {
                        budgetReplacementInProgress = true
                        budgetReplacementCompleted = false
                        repository.close()
                    }
                },
                onBudgetInstalled = {
                    repositoryVersion += 1
                    dataVersion += 1
                    if (budgetReplacementInProgress) budgetReplacementCompleted = true
                    CreditCardDueNotificationScheduler.refreshAsync(context)
                },
                modifier = contentModifier,
            )
            DetailDestination.Loans -> LoansScreen(
                loans = loans,
                accounts = accounts,
                categoryChoices = categoryChoices,
                hideDecimalPlaces = hideDecimalPlaces,
                onBack = { detail = DetailDestination.Main },
                onSave = { accountId, config ->
                    mutate(R.string.navigation_saving_loan) { repository.setLoan(accountId, config) }
                },
                onRemove = { accountId ->
                    mutate(R.string.navigation_removing_loan) { repository.setLoan(accountId, null) }
                },
                modifier = contentModifier,
            )
            DetailDestination.CreditCards -> CreditCardsScreen(
                cards = creditCards,
                accounts = accounts,
                hideDecimalPlaces = hideDecimalPlaces,
                onBack = {
                    detail = if (creditCardsReturnToBills) DetailDestination.BillsCalendar else DetailDestination.Main
                    creditCardsReturnToBills = false
                },
                onSave = { accountId, day, paymentDue, limit ->
                    mutate(
                        R.string.navigation_saving_credit_card,
                        onChanged = { CreditCardDueNotificationScheduler.refreshAsync(context) },
                    ) { repository.setCreditCard(accountId, day, paymentDue, limit) }
                },
                onRemove = { accountId ->
                    mutate(
                        R.string.navigation_removing_credit_card,
                        onChanged = { CreditCardDueNotificationScheduler.refreshAsync(context) },
                    ) { repository.setCreditCard(accountId, null) }
                },
                notificationsEnabled = creditCardNotificationsEnabled,
                onNotificationsEnabledChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        creditCardNotificationSettings.isEnabled = enabled
                        creditCardNotificationsEnabled = enabled
                        CreditCardDueNotificationScheduler.refreshAsync(context)
                    }
                },
                onViewStatements = { card ->
                    statementsAccountId = card.accountId
                    detail = DetailDestination.CreditCardStatements
                },
                modifier = contentModifier,
            )
            DetailDestination.CreditCardStatements -> {
                val accountId = statementsAccountId
                val card = creditCards.firstOrNull { it.accountId == accountId }
                val statements = remember(accountId, dataVersion) {
                    accountId?.let { repository.fetchRecentStatements(it) } ?: emptyList()
                }
                com.azimulkabir.actua.ui.settings.CreditCardStatementsScreen(
                    accountName = card?.accountName ?: "",
                    statements = statements,
                    hideDecimalPlaces = hideDecimalPlaces,
                    onBack = {
                        detail = if (statementsReturnToTransactions) DetailDestination.Transactions else DetailDestination.CreditCards
                        statementsReturnToTransactions = false
                    },
                    onSelectStatement = { statement ->
                        selectedStatement = statement
                        detail = DetailDestination.CreditCardStatementDetail
                    },
                    modifier = contentModifier,
                )
            }
            DetailDestination.CreditCardStatementDetail -> {
                val statement = selectedStatement
                val accountId = statementsAccountId
                if (statement == null || accountId == null) {
                    detail = DetailDestination.CreditCardStatements
                } else {
                    var statementTransactions by remember(statement) { mutableStateOf<List<Transaction>?>(null) }
                    LaunchedEffect(statement, accountId, dataVersion) {
                        statementTransactions = repository.fetchStatementTransactions(
                            accountId, statement.startDate.yyyymmdd, statement.endDate.yyyymmdd,
                        )
                    }
                    com.azimulkabir.actua.ui.settings.CreditCardStatementDetailScreen(
                        statement = statement,
                        transactions = statementTransactions ?: emptyList(),
                        isLoading = statementTransactions == null,
                        hideDecimalPlaces = hideDecimalPlaces,
                        onBack = { detail = DetailDestination.CreditCardStatements },
                        onSelectTransaction = { tx ->
                            editingTransaction = tx
                            editorReturnsToStatementDetail = true
                            detail = DetailDestination.EditTransaction
                        },
                        modifier = contentModifier,
                    )
                }
            }
            DetailDestination.BankSync -> com.azimulkabir.actua.ui.banksync.BankSyncScreen(
                modifier = contentModifier,
                onBack = { detail = DetailDestination.Main },
                simpleFinConfigured = simpleFinConfigured,
                goCardlessConfigured = goCardlessConfigured,
                linkedAccounts = accounts.filter { it.bankSyncSource != null },
                linkableAccounts = accounts.filter { it.bankSyncSource == null && !it.closed },
                onSaveSimpleFinToken = ::saveSimpleFinToken,
                simpleFinDiscovery = simpleFinDiscovery,
                onDiscoverSimpleFin = ::discoverSimpleFinAccounts,
                onSaveGoCardlessCredentials = ::saveGoCardlessCredentials,
                goCardlessInstitutions = goCardlessInstitutions,
                goCardlessInstitutionsLoading = goCardlessInstitutionsLoading,
                onLoadGoCardlessInstitutions = ::loadGoCardlessInstitutions,
                onStartGoCardlessAuthorization = ::startGoCardlessAuthorization,
                goCardlessDiscovery = goCardlessDiscovery,
                onCheckGoCardlessAccounts = ::checkGoCardlessAccounts,
                enableBanking = com.azimulkabir.actua.ui.banksync.EnableBankingUiState(
                    visible = enableBankingExperiment,
                    configured = enableBankingConfigured,
                    banks = enableBankingFlow.banks.map { it.name },
                    banksLoading = enableBankingFlow.banksLoading,
                    discovery = enableBankingFlow.discovery,
                ),
                onSaveEnableBankingCredentials = ::saveEnableBankingCredentials,
                onLoadEnableBankingBanks = enableBankingFlow::loadBanks,
                onAuthorizeEnableBanking = { name ->
                    enableBankingFlow.banks.firstOrNull { it.name == name }?.let(enableBankingFlow::authorize)
                },
                onLinkExisting = { discovered, account, source -> linkBankAccount(discovered, account, source) },
                onCreateAndLink = { discovered, name, offBudget, source -> createAndLinkBankAccount(discovered, name, offBudget, source) },
                onUnlink = ::unlinkBankAccount,
            )
            DetailDestination.Rules -> RulesScreen(
                rules = remember(dataVersion) { repository.rules() },
                supported = remember(dataVersion) { repository.rulesSupported() },
                scheduleOwnedRuleIds = remember(dataVersion) { repository.scheduleOwnedRuleIds() },
                editorData = remember(dataVersion) { repository.ruleEditorData() },
                onBack = {
                    if (scheduleRuleId != null) returnFromScheduleRule() else detail = DetailDestination.Main
                },
                onSave = { rule, onSaved -> mutate(R.string.navigation_saving_rule, onChanged = onSaved) { repository.saveRule(rule) } },
                onDelete = { ruleId, onDeleted -> mutate(R.string.navigation_deleting_rule, onChanged = onDeleted) { repository.deleteRule(ruleId) } },
                onApplyRule = { rule, onApplied ->
                    mutate(R.string.navigation_applying_rule) {
                        val count = repository.applyRuleNow(rule)
                        onApplied(count)
                        count > 0
                    }
                },
                onPreviewRuleMatches = { rule -> repository.matchingTransactions(rule) },
                modifier = contentModifier,
                initialRuleId = scheduleRuleId,
                onInitialRuleClosed = ::returnFromScheduleRule,
            )
            DetailDestination.ManageCategories -> NavigationManageCategoriesScreen(
                repository = repository,
                dataVersion = dataVersion,
                contentModifier = contentModifier,
                onBack = { detail = DetailDestination.Main },
                onReorderGroupsClick = { detail = DetailDestination.ReorderGroups },
                onMutate = { labelRes, action -> mutate(labelRes, action = action) },
                onMutateSync = { labelRes, action -> mutateSync(labelRes, action) },
            )
            DetailDestination.ReorderGroups -> NavigationReorderGroupsScreen(
                repository = repository,
                dataVersion = dataVersion,
                contentModifier = contentModifier,
                onBack = { detail = DetailDestination.ManageCategories },
                onMutateSync = { labelRes, action -> mutateSync(labelRes, action) },
            )
            DetailDestination.ReorderAccounts -> NavigationReorderAccountsScreen(
                repository = repository,
                dataVersion = dataVersion,
                contentModifier = contentModifier,
                onBack = { detail = DetailDestination.Main },
                onMutate = { labelRes, action -> mutate(labelRes, action = action) },
                onMutateSync = { labelRes, action -> mutateSync(labelRes, action) },
            )
            DetailDestination.CustomizeHome -> CustomizeHomeScreen(
                layout = homeLayout,
                onBack = { detail = DetailDestination.Main },
                onLayoutChange = { updated ->
                    homePreferences.save(updated)
                    homeLayout = updated
                },
                modifier = contentModifier,
            )
            DetailDestination.CustomizeTabBar -> CustomizeTabBarScreen(
                layout = tabBarLayout,
                onBack = { detail = DetailDestination.Main },
                onLayoutChange = { updated ->
                    tabBarPreferences.save(updated)
                    tabBarLayout = updated
                },
                modifier = contentModifier,
            )
            DetailDestination.BudgetAutomation -> budgetGroups.firstNotNullOfOrNull { g ->
                g.categories.firstOrNull { it.name == editingAutomationCategory }?.let { g to it }
            }?.let { (group, category) ->
                BudgetAutomationScreen(
                    group = group,
                    category = category,
                    month = budgetMonth,
                    hideDecimalPlaces = hideDecimalPlaces,
                    scheduleFunding = budgetScheduleFunding,
                    incomeCategories = budgetGroups.filter { it.isIncome }.flatMap { it.categories }
                        .map { it.name }.filter { it.isNotBlank() }.distinct(),
                    conventionalAmountEntry = conventionalAmountEntry,
                    onBack = {
                        reopenBudgetCategory = editingAutomationCategory
                        editingAutomationCategory = null
                        detail = DetailDestination.Main
                    },
                    onSave = { automations ->
                        mutate(R.string.navigation_saving_automations) { repository.setCategoryAutomations(category.id.orEmpty(), automations) }
                    },
                    modifier = contentModifier,
                )
            } ?: run { detail = DetailDestination.Main }
            DetailDestination.Schedules,
            DetailDestination.PayeeLocations,
            DetailDestination.ManagePayees,
            DetailDestination.ImportTransactions,
            DetailDestination.ReviewCaptured,
            DetailDestination.BillsCalendar,
            DetailDestination.FindSchedules,
            DetailDestination.NewSchedule,
            DetailDestination.EditSchedule,
            -> NavigationScheduleScreens(
                repository = repository,
                dataVersion = dataVersion,
                schedules = schedules,
                nonWorkingDays = homeMonthStart.nonWorkingDays,
                accounts = accounts,
                payeeNames = payeeNames,
                hideDecimalPlaces = hideDecimalPlaces,
                conventionalAmountEntry = conventionalAmountEntry,
                context = context,
                scheduleReminderSettings = scheduleReminderSettings,
                scheduleReminderPermissionLauncher = scheduleReminderPermissionLauncher,
                contentModifier = contentModifier,
                onMutate = { labelRes, onChanged, action -> mutate(labelRes, onChanged = onChanged, action = action) },
                detail = detail,
                onDetailChange = { detail = it },
                editingScheduleId = editingScheduleId,
                onEditingScheduleIdChange = { editingScheduleId = it },
                scheduleReturnsToBills = scheduleReturnsToBills,
                onScheduleReturnsToBillsChange = { scheduleReturnsToBills = it },
                scheduleReturnsToTransactions = scheduleReturnsToTransactions,
                onScheduleReturnsToTransactionsChange = { scheduleReturnsToTransactions = it },
                scheduleReturnsToTransactionsTab = scheduleReturnsToTransactionsTab,
                onScheduleReturnsToTransactionsTabChange = { scheduleReturnsToTransactionsTab = it },
                billsCalendarReturnsToSchedules = billsCalendarReturnsToSchedules,
                onBillsCalendarReturnsToSchedulesChange = { billsCalendarReturnsToSchedules = it },
                creditCardsReturnToBills = creditCardsReturnToBills,
                onCreditCardsReturnToBillsChange = { creditCardsReturnToBills = it },
                scheduleRuleId = scheduleRuleId,
                onScheduleRuleIdChange = { scheduleRuleId = it },
                sharedImportText = sharedImportText,
                onSharedTextConsumed = { sharedImportText = null },
                capturedReviewRequest = capturedReviewRequest,
                onReturnFromCapturedReview = ::returnFromCapturedReview,
                onOpenCapturedReview = ::openCapturedReview,
                onReturnFromEditSchedule = ::returnFromEditSchedule,
                scheduleRemindersEnabled = scheduleRemindersEnabled,
                onScheduleRemindersEnabledChange = { scheduleRemindersEnabled = it },
                scheduleReminderLeadDays = scheduleReminderLeadDays,
                onScheduleReminderLeadDaysChange = { scheduleReminderLeadDays = it },
            )
            DetailDestination.Main -> if (!repository.isUsingActualBudget && destination != MainDestination.Manage) {
                NoBudgetScreen(
                    onConnect = {
                        destination = MainDestination.Manage
                        detail = DetailDestination.Connection
                    },
                    onBudgetInstalled = { repositoryVersion += 1 },
                    modifier = contentModifier,
                )
            } else when (shownDestination) {
                MainDestination.Home -> HomeScreen(
                    modifier = contentModifier,
                    projection = remember(
                        dataVersion,
                        favoriteCategoryIds,
                        favoriteAccountIds,
                        favoriteReportIds,
                        reportSnapshot,
                        budgetMonth,
                        homeMonthStart,
                    ) {
                        HomeDashboardProjection.from(
                            budgetOverview = budgetOverview,
                            budgetGroups = budgetGroups,
                            accounts = accounts,
                            reportDashboards = reportSnapshot?.dashboards.orEmpty(),
                            schedules = schedules,
                            transactions = transactions,
                            favoriteCategoryIds = favoriteCategoryIds,
                            favoriteAccountIds = favoriteAccountIds,
                            favoriteReportIds = favoriteReportIds,
                            month = budgetMonth,
                            monthStart = homeMonthStart,
                        )
                    },
                    sections = homeLayout.visibleSections,
                    hideDecimalPlaces = hideDecimalPlaces,
                    onBudgetClick = { destination = MainDestination.Budget },
                    onCategoryClick = { category ->
                        reopenBudgetCategory = category
                        destination = MainDestination.Budget
                    },
                    onAccountsClick = { destination = MainDestination.Accounts },
                    onAccountClick = { account ->
                        transactionAccount = account; transactionCategory = null; transactionMonth = null
                        transactionSearch = ""
                        destination = MainDestination.Accounts; detail = DetailDestination.Transactions
                    },
                    onSchedulesClick = {
                        destination = MainDestination.Manage
                        detail = DetailDestination.Schedules
                    },
                    onTransactionsClick = { destination = MainDestination.Transactions },
                    onReportsClick = { detail = DetailDestination.Reports },
                    onReportClick = { id ->
                        requestedReportPageId = id
                        requestedReportPageRequest += 1
                        detail = DetailDestination.Reports
                    },
                    onCustomizeClick = { detail = DetailDestination.CustomizeHome },
                    returnToRootRequest = rootRequests[MainDestination.Home] ?: 0,
                    hasFab = hasFab,
                )
                MainDestination.Budget -> BudgetScreen(
                    contentModifier,
                    groups = budgetGroups,
                    overview = budgetOverview,
                    month = budgetMonth,
                    monthRange = budgetMonthRange,
                    onMonthChange = { budgetMonth = it },
                    hideDecimalPlaces = hideDecimalPlaces,
                    showHidden = showHiddenCategories,
                    onShowHiddenChange = {
                        displayPreferences.showHiddenCategories = it
                        showHiddenCategories = it
                    },
                    showSpent = showSpentColumn,
                    onShowSpentChange = {
                        displayPreferences.showSpentColumn = it
                        showSpentColumn = it
                    },
                    showProgressBars = showBudgetProgressBars,
                    onShowProgressBarsChange = {
                        displayPreferences.showBudgetProgressBars = it
                        showBudgetProgressBars = it
                    },
                    budgetView = budgetView,
                    onBudgetViewChange = {
                        displayPreferences.budgetView = it
                        budgetView = it
                    },
                    showOverview = showBudgetOverview,
                    onShowOverviewChange = {
                        displayPreferences.showBudgetOverview = it
                        showBudgetOverview = it
                    },
                    showBudgetStatusBanners = showBudgetStatusBanners,
                    onShowBudgetStatusBannersChange = {
                        displayPreferences.showBudgetStatusBanners = it
                        showBudgetStatusBanners = it
                    },
                    showGroupTotals = showGroupTotals,
                    onShowGroupTotalsChange = {
                        displayPreferences.showGroupTotals = it
                        showGroupTotals = it
                    },
                    showBalancePills = showBalancePills,
                    onShowBalancePillsChange = {
                        displayPreferences.showBalancePills = it
                        showBalancePills = it
                    },
                    hideFullySpent = hideFullySpentCategories,
                    onHideFullySpentChange = {
                        displayPreferences.hideFullySpentCategories = it
                        hideFullySpentCategories = it
                    },
                    categoryView = budgetCategoryView,
                    onCategoryViewChange = {
                        displayPreferences.budgetCategoryView = it
                        budgetCategoryView = it
                    },
                    showCategoryFilters = showCategoryFilters,
                    onShowCategoryFiltersChange = {
                        displayPreferences.showCategoryFilters = it
                        showCategoryFilters = it
                    },
                    favoritesOnly = favoritesOnly,
                    onFavoritesOnlyChange = {
                        displayPreferences.favoritesOnly = it
                        favoritesOnly = it
                    },
                    favoriteCategoryIds = favoriteCategoryIds,
                    onFavoriteCategoryChange = { id, favorite ->
                        favoritePreferences.set(favoriteBudgetId, FavoritePreferences.Type.CATEGORY, id, favorite)
                        favoriteCategoryIds = favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.CATEGORY)
                        WidgetUpdater.requestAll(context)
                    },
                    onSetCategoryHidden = { group, category, hidden, onChanged ->
                        mutate(if (hidden) R.string.navigation_hiding_category else R.string.navigation_showing_category, onChanged = onChanged) {
                            repository.setCategoryHidden(group, category, hidden)
                        }
                    },
                    onSetGroupHidden = { group, hidden, onChanged ->
                        mutate(if (hidden) R.string.navigation_hiding_group else R.string.navigation_showing_group, onChanged = onChanged) {
                            repository.setCategoryGroupHidden(group, hidden)
                        }
                    },
                    onRenameCategory = { group, category, name ->
                        mutate(R.string.navigation_renaming_category) { repository.renameCategory(group, category, name) }
                    },
                    onRenameGroup = { group, name ->
                        mutate(R.string.navigation_renaming_group) { repository.renameCategoryGroup(group, name) }
                    },
                    onShowCategoryTransactions = { category, thisMonth, returnToDetails ->
                        activeBudgetCategory = null
                        transactionsReturnCategory = category.takeIf { returnToDetails }
                        transactionAccount = null
                        transactionCategory = category
                        transactionMonth = if (thisMonth) budgetMonth else null
                        transactionSearch = ""
                        detail = DetailDestination.Transactions
                    },
                    onTransferBudget = { fromGroup, fromCategory, toGroup, toCategory, amount ->
                        mutate(R.string.navigation_moving_budget) {
                            repository.transferBudget(fromGroup, fromCategory, toGroup, toCategory, amount, budgetMonth)
                        }
                    },
                    onSummaryMove = { group, category, amount, covering ->
                        mutate(R.string.navigation_moving_budget) {
                            if (covering) {
                                repository.transferBudget(group, category, null, null, amount, budgetMonth,
                                    ActuaRepository.BudgetMoveKind.COVER_OVERBUDGETED)
                            } else {
                                repository.transferBudget(null, null, group, category, amount, budgetMonth,
                                    ActuaRepository.BudgetMoveKind.FROM_TO_BUDGET)
                            }
                        }
                    },
                    onSetBudgetAmount = { group, category, amount ->
                        mutate(R.string.navigation_updating_budget) { repository.setBudgetAmount(group, category, amount, budgetMonth) }
                    },
                    onSetCategoryNote = { categoryId, note ->
                        mutate(R.string.navigation_saving_category_note) { repository.setCategoryNote(categoryId, note) }
                    },
                    onSetCategoryCarryover = { categoryId, enabled ->
                        mutate(R.string.navigation_updating_rollover) { repository.setCategoryCarryover(categoryId, enabled, budgetMonth) }
                    },
                    onHoldForNextMonth = { amount ->
                        mutate(R.string.navigation_holding_for_next_month) { repository.setBufferedAmount(budgetMonth, amount) }
                    },
                    onResetNextMonthBuffer = {
                        mutate(R.string.navigation_resetting_next_month_buffer) { repository.resetNextMonthBuffer(budgetMonth) }
                    },
                    onCopyPreviousMonth = {
                        mutate(R.string.navigation_copying_previous_month_budget) { repository.copyPreviousMonthBudget(budgetMonth) }
                    },
                    onCategoryBudgetAction = { categoryId, action ->
                        mutate(R.string.navigation_updating_budget) {
                            when (action) {
                                CategoryBudgetAction.COPY_LAST_MONTH -> repository.copyCategoryFromPreviousMonth(categoryId, budgetMonth)
                                CategoryBudgetAction.AVERAGE_3 -> repository.setCategoryAverage(categoryId, budgetMonth, 3)
                                CategoryBudgetAction.AVERAGE_6 -> repository.setCategoryAverage(categoryId, budgetMonth, 6)
                                CategoryBudgetAction.AVERAGE_12 -> repository.setCategoryAverage(categoryId, budgetMonth, 12)
                                CategoryBudgetAction.COPY_TO_YEAR_END -> repository.copyCategoryToYearEnd(categoryId, budgetMonth)
                            }
                        }
                    },
                    onPreviewAverageBudget = { months -> repository.averageBudgetPreview(budgetMonth, months) },
                    onResetIncomeHold = {
                        mutate(R.string.navigation_updating_automatic_holds) { repository.resetIncomeHold(budgetMonth) }
                    },
                    onEditAutomations = { _, category ->
                        editingAutomationCategory = category.name
                        detail = DetailDestination.BudgetAutomation
                    },
                    onApplyBudgetTemplate = { preview ->
                        mutate(R.string.navigation_applying_budget_template) { repository.applyBudgetTemplate(preview) }
                    },
                    scheduleFunding = budgetScheduleFunding,
                    templateHideFraction = budgetHidesFraction,
                    loanSnoozedMonths = loanSnoozedMonths,
                    onPreviewCleanup = { repository.previewCleanup(budgetMonth) },
                    onRefreshNoteTemplates = {
                        mutate(R.string.navigation_reading_template_notes) { repository.refreshNoteTemplates() }
                    },
                    onApplyCleanup = { preview ->
                        mutate(R.string.navigation_applying_month_end_cleanup) { repository.applyCleanup(preview) }
                    },
                    onSearch = { detail = DetailDestination.Search },
                    onManageCategories = { detail = DetailDestination.ManageCategories },
                    transactions = filteredTransactions,
                    allTransactions = transactions,
                    onShowUncategorizedTransactions = {
                        transactionAccount = null
                        transactionCategory = null
                        transactionMonth = budgetMonth
                        transactionSearch = ""
                        transactionStatusFilter = TransactionStatusFilter.UNCATEGORIZED
                        detail = DetailDestination.Transactions
                    },
                    onDeleteCategory = { group, category, transferToId, onChanged ->
                        mutate(R.string.navigation_deleting_category, onChanged = onChanged) {
                            repository.deleteCategory(group, category, transferToId)
                        }
                    },
                    loadCategoryDeletePlan = { group, category ->
                        withContext(Dispatchers.IO) { runCatching { repository.categoryDeletePlan(group, category) }.getOrNull() }
                    },
                    onEditTransaction = { transaction ->
                        activeBudgetCategory = null
                        editingTransaction = transaction
                        editorReturnsToTransactions = true
                        transactionAccount = null
                        transactionCategory = transaction.category
                        transactionMonth = null
                        transactionSearch = ""
                        detail = DetailDestination.EditTransaction
                    },
                    onDeleteTransaction = { transaction ->
                        mutateWithImpactCue(R.string.navigation_deleting_transaction, budgetCategoriesOf(transaction)) {
                            repository.deleteTransaction(transaction.id)
                        }
                    },
                    requestedCategoryDetails = reopenBudgetCategory,
                    onCategoryDetailsChange = { category ->
                        activeBudgetCategory = category
                        if (category == reopenBudgetCategory) reopenBudgetCategory = null
                    },
                    returnToRootRequest = rootRequests[MainDestination.Budget] ?: 0,
                    showNotes = showNotes,
                    hideIncomeGroup = hideIncomeGroupInBudget,
                    hasFab = hasFab,
                )
                MainDestination.Accounts -> NavigationAccountsScreen(
                    repository = repository,
                    dataVersion = dataVersion,
                    contentModifier = contentModifier,
                    accounts = accounts,
                    transactions = transactions,
                    hideDecimalPlaces = hideDecimalPlaces,
                    showMonthlySummary = showAccountsMonthlySummary,
                    onShowMonthlySummaryChange = {
                        displayPreferences.showAccountsMonthlySummary = it
                        showAccountsMonthlySummary = it
                    },
                    creditCards = creditCards,
                    onAccountClick = {
                        transactionAccount = it
                        transactionCategory = null; transactionMonth = null
                        transactionSearch = ""
                        detail = DetailDestination.Transactions
                    },
                    onAllAccountsClick = {
                        transactionAccount = null
                        transactionCategory = null; transactionMonth = null
                        transactionSearch = ""
                        detail = DetailDestination.Transactions
                    },
                    onMutate = { labelRes, action -> mutate(labelRes, action = action) },
                    onMutateSync = { labelRes, action -> mutateSync(labelRes, action) },
                    pendingCaptureCount = pendingCaptureCount,
                    onReviewCaptures = { openCapturedReview() },
                    newTransactionCounts = newBankTransactionCounts,
                    onSearch = { detail = DetailDestination.Search },
                    onSetUpBankSync = { loadBankSyncProviderStatus(); detail = DetailDestination.BankSync },
                    isRefreshing = accountsRefreshing,
                    onRefresh = ::refreshAccounts,
                    favoriteAccountIds = favoriteAccountIds,
                    onFavoriteAccountChange = { id, favorite ->
                        favoritePreferences.set(favoriteBudgetId, FavoritePreferences.Type.ACCOUNT, id, favorite)
                        favoriteAccountIds = favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.ACCOUNT)
                    },
                    scrollToTopRequest = rootRequests[MainDestination.Accounts] ?: 0,
                    hasFab = hasFab,
                    monthStart = homeMonthStart,
                )
                MainDestination.Transactions -> TransactionsScreen(
                    accountName = null,
                    categoryName = null,
                    month = null,
                    onBack = {},
                    onEdit = {
                        addOrigin = MainDestination.Transactions
                        editingTransaction = it
                        editorReturnsToTransactions = false
                        detail = DetailDestination.EditTransaction
                    },
                    modifier = contentModifier,
                    transactions = filteredTransactions,
                    transactionStatusFilter = transactionStatusFilter,
                    onTransactionStatusFilterChange = { transactionStatusFilter = it },
                    hideDecimalPlaces = hideDecimalPlaces,
                    conventionalAmountEntry = conventionalAmountEntry,
                    groupTransactionsByDate = groupTransactionsByDate,
                    onGroupTransactionsByDateChange = {
                        displayPreferences.groupTransactionsByDate = it
                        groupTransactionsByDate = it
                    },
                    onSetCleared = { transaction, cleared ->
                        mutate(R.string.navigation_updating_transaction) { repository.setTransactionCleared(transaction.id, cleared) }
                    },
                    onUnlockTransaction = { transaction ->
                        mutate(R.string.navigation_unlocking_transaction) { repository.unlockTransaction(transaction.id) }
                    },
                    onReconcileAccount = { account, onReconciled ->
                        mutate(R.string.navigation_reconciling_account, onChanged = onReconciled) { repository.reconcileAccount(account.id) }
                    },
                    onCreateReconciliationAdjustment = { account, difference, onCreated ->
                        mutate(R.string.navigation_creating_reconciliation_adjustment, onChanged = onCreated) {
                            repository.createReconciliationAdjustment(account.id, difference)
                        }
                    },
                    onDelete = { transaction ->
                        mutateWithImpactCue(R.string.navigation_deleting_transaction, budgetCategoriesOf(transaction)) {
                            repository.deleteTransaction(transaction.id)
                        }
                    },
                    onDeleteMultiple = { transactionsToDelete ->
                        val categories = transactionsToDelete.flatMapTo(mutableSetOf()) { budgetCategoriesOf(it) }
                        mutateWithImpactCue(R.string.navigation_deleting_transactions, categories) {
                            repository.deleteTransactions(transactionsToDelete.map { it.id }) > 0
                        }
                    },
                    onDuplicate = { transaction ->
                        val duplicate = transaction.asDuplicate().asTransferDraft()
                        mutateWithImpactCue(R.string.navigation_duplicating_transaction, budgetCategoriesOf(duplicate)) {
                            repository.saveTransaction(duplicate); true
                        }
                    },
                    onDuplicateMultiple = { transactionsToDuplicate ->
                        val duplicates = transactionsToDuplicate.map { it.asDuplicate().asTransferDraft() }
                        val categories = duplicates.flatMapTo(mutableSetOf()) { budgetCategoriesOf(it) }
                        mutateWithImpactCue(R.string.navigation_duplicating_transactions, categories) {
                            duplicates.forEach { repository.saveTransaction(it) }
                            duplicates.isNotEmpty()
                        }
                    },
                    onLinkSchedule = { transactionsToLink, scheduleId ->
                        mutate(R.string.navigation_linking_schedule) {
                            repository.linkScheduleTransactions(scheduleId, transactionsToLink.map { it.id }) > 0
                        }
                    },
                    onUnlinkSchedule = { transactionsToUnlink ->
                        mutate(R.string.navigation_unlinking_schedule) {
                            repository.unlinkScheduleFromTransactions(transactionsToUnlink.map { it.id }) > 0
                        }
                    },
                    onViewSchedule = { scheduleId ->
                        editingScheduleId = scheduleId
                        scheduleReturnsToBills = false
                        scheduleReturnsToTransactions = false
                        scheduleReturnsToTransactionsTab = true
                        detail = DetailDestination.EditSchedule
                    },
                    categoryOptions = categoryChoices.labels,
                    onCategorizeMultiple = { transactionsToCategorize, category ->
                        val picked = categoryChoices.choice(category)
                        val updated = transactionsToCategorize.map {
                            it.copy(
                                category = picked?.name ?: category,
                                categoryId = picked?.id,
                                categoryIsExplicit = true,
                            ).asTransferDraft()
                        }
                        val categories = (transactionsToCategorize + updated).flatMapTo(mutableSetOf()) { budgetCategoriesOf(it) }
                        mutateWithImpactCue(R.string.navigation_categorizing_transactions, categories) {
                            updated.forEach { repository.saveTransaction(it) }
                            updated.isNotEmpty()
                        }
                    },
                    accountOptions = accountChoices.labels,
                    onMoveMultiple = { transactionsToMove, accountName ->
                        val picked = accountChoices.choice(accountName)
                        val updated = transactionsToMove.map {
                            it.copy(account = picked?.name ?: accountName, accountId = picked?.id).asTransferDraft()
                        }
                        val categories = transactionsToMove.flatMapTo(mutableSetOf()) { budgetCategoriesOf(it) }
                        mutateWithImpactCue(R.string.navigation_moving_transactions, categories) {
                            updated.forEach { repository.saveTransaction(it) }
                            updated.isNotEmpty()
                        }
                    },
                    onLabelMultiple = { transactionsToLabel, tag ->
                        val updated = transactionsToLabel.mapNotNull { withTagAppended(it, tag)?.asTransferDraft() }
                        mutate(R.string.navigation_labeling_transactions) {
                            updated.forEach { repository.saveTransaction(it) }
                            true
                        }
                    },
                    onMergeTransactions = { first, second ->
                        val categories = budgetCategoriesOf(first) + budgetCategoriesOf(second)
                        mutateWithImpactCue(R.string.navigation_merging_transactions, categories) {
                            repository.mergeTransactions(first.id, second.id)
                        }
                    },
                    onMakeTransfer = { first, second ->
                        val categories = budgetCategoriesOf(first) + budgetCategoriesOf(second)
                        mutateWithImpactCue(R.string.navigation_making_transfer, categories) {
                            repository.linkAsTransfer(first.id, second.id)
                        }
                    },
                    linkableSchedules = linkableSchedules,
                    showBackButton = false,
                    returnToRootRequest = rootRequests[MainDestination.Transactions] ?: 0,
                    isRefreshing = transactionsRefreshing,
                    onRefresh = ::refreshTransactions,
                    upcomingTransactions = upcomingTransactions,
                    showUpcomingTransactions = showUpcomingTransactions,
                    onShowUpcomingTransactionsChange = {
                        displayPreferences.showUpcomingTransactions = it
                        showUpcomingTransactions = it
                    },
                    recurringScheduleIds = upcomingRecurringScheduleIds,
                    onUpcomingAction = ::onUpcomingAction,
                    hasFab = hasFab,
                )
                MainDestination.Reports -> NavigationReportsScreen(
                    snapshot = reportSnapshot,
                    snapshotVersion = reportSnapshotVersion,
                    dataVersion = dataVersion,
                    hideDecimalPlaces = hideDecimalPlaces,
                    modifier = contentModifier,
                    onSearch = { detail = DetailDestination.Search },
                    favoriteBudgetId = favoriteBudgetId,
                    favoritePreferences = favoritePreferences,
                    favoriteReportIds = favoriteReportIds,
                    onFavoriteReportChange = { id, favorite ->
                        favoritePreferences.set(favoriteBudgetId, FavoritePreferences.Type.REPORT, id, favorite)
                        favoriteReportIds = favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.REPORT)
                    },
                    loadSavedReports = loadReportWidgets,
                    loadWidgetForRange = loadReportWidgetForRange,
                    loadTransactions = loadReportTransactions,
                    loadDrillDownTransactions = loadDrillDownTransactions,
                    onEditTransaction = onEditReportTransaction,
                    onOpenInTransactions = onOpenReportInTransactions,
                    activeDrillDown = activeDrillDown,
                    onDrillDownChange = { activeDrillDown = it },
                    showReportSummary = showReportSummary,
                    onShowReportSummaryChange = {
                        displayPreferences.showReportSummary = it
                        showReportSummary = it
                    },
                    scrollToTopRequest = rootRequests[MainDestination.Reports] ?: 0,
                    initialPageId = null,
                    initialPageRequest = 0,
                    hasFab = hasFab,
                    repository = repository,
                    onMutate = { labelRes, action -> mutate(labelRes, action = action) },
                )
                MainDestination.Manage -> SettingsScreen(
                    modifier = contentModifier,
                    monthStart = homeMonthStart,
                    onMonthStartChange = { rule ->
                        val clamped = rule.copy(day = rule.day.coerceIn(1, 31))
                        homePreferences.setSummaryStartDay(favoriteBudgetId, clamped.day)
                        homePreferences.setStartDayAdjustment(favoriteBudgetId, clamped.adjustment)
                        homePreferences.setWeekendDays(favoriteBudgetId, clamped.weekendDays)
                        homeMonthStart = clamped
                    },
                    holidays = homeHolidays,
                    onHolidaysChange = { holidays ->
                        val normalized = HolidayCodec.normalize(holidays)
                        homePreferences.setHolidays(favoriteBudgetId, normalized)
                        homeHolidays = normalized
                        homeMonthStart = homeMonthStart.copy(holidays = normalized.mapTo(mutableSetOf()) { it.date })
                    },
                    onConnectionClick = { detail = DetailDestination.Connection },
                    decimalPlacesMode = decimalPlacesMode,
                    onDecimalPlacesModeChange = {
                        displayPreferences.decimalPlacesMode = it
                        decimalPlacesMode = it
                        WidgetUpdater.requestAll(context)
                    },
                    showNotes = showNotes,
                    onShowNotesChange = {
                        displayPreferences.showNotes = it
                        showNotes = it
                    },
                    hideIncomeGroupInBudget = hideIncomeGroupInBudget,
                    onHideIncomeGroupInBudgetChange = {
                        displayPreferences.hideIncomeGroupInBudget = it
                        hideIncomeGroupInBudget = it
                    },
                    budgetType = budgetType,
                    onBudgetTypeChange = { newType ->
                        mutate(R.string.navigation_change_budget_type) {
                            repository.setBudgetType(newType)
                        }
                    },
                    currencyCode = currencyCode,
                    onCurrencyCodeChange = {
                        displayPreferences.currencyCode = it
                        currencyCode = it
                        WidgetUpdater.requestAll(context)
                    },
                    currencySymbolOnly = currencySymbolOnly,
                    onCurrencySymbolOnlyChange = {
                        displayPreferences.currencySymbolOnly = it
                        currencySymbolOnly = it
                        WidgetUpdater.requestAll(context)
                    },
                    currencySymbolPosition = currencySymbolPosition,
                    onCurrencySymbolPositionChange = {
                        displayPreferences.currencySymbolPosition = it
                        currencySymbolPosition = it
                        WidgetUpdater.requestAll(context)
                    },
                    currencySymbolSpacing = currencySymbolSpacing,
                    onCurrencySymbolSpacingChange = {
                        displayPreferences.currencySymbolSpacing = it
                        currencySymbolSpacing = it
                        WidgetUpdater.requestAll(context)
                    },
                    languageTag = languageTag,
                    onLanguageChange = { tag ->
                        val locales = if (tag.isBlank()) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(tag)
                        }
                        AppCompatDelegate.setApplicationLocales(locales)
                        WidgetUpdater.requestAll(context)
                        CreditCardDueNotificationScheduler.refreshAsync(context)
                    },
                    dateFormat = dateFormat,
                    onDateFormatChange = {
                        displayPreferences.dateFormat = it
                        dateFormat = it
                    },
                    numberFormat = numberFormat,
                    onNumberFormatChange = {
                        displayPreferences.numberFormat = it
                        numberFormat = it
                        WidgetUpdater.requestAll(context)
                    },
                    privacyMode = privacyMode,
                    onPrivacyModeChange = {
                        displayPreferences.privacyMode = it
                        privacyMode = it
                        WidgetUpdater.requestAll(context)
                    },
                    budgetDisplayFormats = budgetDisplayFormats,
                    appearance = appearance,
                    onAppearanceChange = {
                        displayPreferences.appearance = it
                        appearance = it
                        onAppearanceChange(it)
                    },
                    useDynamicColor = useDynamicColor,
                    onUseDynamicColorChange = {
                        displayPreferences.useDynamicColor = it
                        useDynamicColor = it
                        onUseDynamicColorChange(it)
                    },
                    startPage = startPage,
                    onStartPageChange = {
                        displayPreferences.startPage = it
                        startPage = it
                    },
                    startPageOptions = visibleMainDestinations.map { it.label },
                    accountOptions = accounts.filterNot { it.closed }.map { it.name },
                    defaultAccount = defaultAccount,
                    onDefaultAccountChange = {
                        displayPreferences.defaultAccount = it
                        defaultAccount = it
                    },
                    groupTransactionsByDate = groupTransactionsByDate,
                    onGroupTransactionsByDateChange = {
                        displayPreferences.groupTransactionsByDate = it
                        groupTransactionsByDate = it
                    },
                    showAccountsMonthlySummary = showAccountsMonthlySummary,
                    onShowAccountsMonthlySummaryChange = {
                        displayPreferences.showAccountsMonthlySummary = it
                        showAccountsMonthlySummary = it
                    },
                    onBillsCalendarClick = {
                        billsCalendarReturnsToSchedules = false
                        detail = DetailDestination.BillsCalendar
                    },
                    onCreditCardsClick = {
                        creditCardsReturnToBills = false
                        detail = DetailDestination.CreditCards
                    },
                    onLoansClick = { detail = DetailDestination.Loans },
                    onBankSyncClick = {
                        loadBankSyncProviderStatus()
                        detail = DetailDestination.BankSync
                    },
                    enableBankingExperiment = enableBankingExperiment,
                    onEnableBankingExperimentChange = {
                        experimentalPreferences.enableBanking = it
                        enableBankingExperiment = it
                        if (!it) enableBankingFlow.reset()
                    },
                    onRulesClick = {
                        scheduleRuleId = null
                        detail = DetailDestination.Rules
                    },
                    onSchedulesClick = { detail = DetailDestination.Schedules },
                    onImportTransactionsClick = { detail = DetailDestination.ImportTransactions },
                    onPayeeLocationsClick = { detail = DetailDestination.PayeeLocations },
                    onManagePayeesClick = { detail = DetailDestination.ManagePayees },
                    onReportsClick = { detail = DetailDestination.Reports },
                    showReportsShortcut = TabItem.REPORTS !in tabBarLayout.visibleTabs,
                    showHomeShortcut = TabItem.HOME !in tabBarLayout.visibleTabs,
                    onHomeClick = {
                        detail = DetailDestination.Main
                        destination = MainDestination.Home
                    },
                    onCustomizeHomeClick = { detail = DetailDestination.CustomizeHome },
                    onCustomizeTabBarClick = { detail = DetailDestination.CustomizeTabBar },
                    conventionalAmountEntry = conventionalAmountEntry,
                    onConventionalAmountEntryChange = {
                        displayPreferences.conventionalAmountEntry = it
                        conventionalAmountEntry = it
                    },
                    tabBarDisplay = tabBarScroll,
                    showCurrentBalanceSummary = showCurrentBalanceSummary,
                    onShowCurrentBalanceSummaryChange = {
                        displayPreferences.showCurrentBalanceSummary = it
                        showCurrentBalanceSummary = it
                    },
                    budgetOptions = availableBudgetOptions,
                    activeBudgetId = activeBudgetId,
                    onBudgetChange = { budgetId ->
                        if (budgetId != activeBudgetId &&
                            availableBudgetOptions.any { it.id == budgetId } &&
                            !budgetReplacementInProgress
                        ) {
                            budgetReplacementInProgress = true
                            budgetReplacementCompleted = false
                            repository.close()
                            ActiveBudgetStore(context).budgetId = budgetId
                            // Account, category and month filters hold ids from the previous budget.
                            transactionAccount = null
                            transactionCategory = null
                            transactionMonth = null
                            transactionSearch = ""
                            repositoryVersion += 1
                            dataVersion += 1
                            budgetReplacementCompleted = true
                            CreditCardDueNotificationScheduler.refreshAsync(context)
                            WidgetUpdater.requestAll(context)
                            // Catch up with the server and send any edits made before switching away.
                            ActualSyncScheduler.scheduleMutation(context)
                        }
                    },
                    returnToRootRequest = rootRequests[MainDestination.Manage] ?: 0,
                )
            }
        }
        }
        }
    }
    if (budgetReplacementInProgress) BudgetSwitchDialog()
    TransactionImpactPopup(
        cues = transactionImpactCues,
        hideDecimalPlaces = hideDecimalPlaces,
        onDismiss = { transactionImpactCues = emptyList() },
        modifier = Modifier.fillMaxSize(),
    )
    }
}

@Composable
private fun NavigationReportsScreen(
    snapshot: ReportSnapshot?,
    snapshotVersion: Int,
    dataVersion: Int,
    hideDecimalPlaces: Boolean,
    modifier: Modifier,
    onSearch: () -> Unit,
    favoriteBudgetId: String,
    favoritePreferences: FavoritePreferences,
    favoriteReportIds: Set<String>,
    onFavoriteReportChange: (String, Boolean) -> Unit,
    loadSavedReports: suspend (ReportViewFilter) -> List<ReportWidget>,
    loadWidgetForRange: suspend (String, com.azimulkabir.actua.model.ReportRange) -> ReportWidget?,
    loadTransactions: suspend (List<String>) -> List<Transaction>,
    loadDrillDownTransactions: suspend (DrillDownQuery) -> List<Transaction>,
    onEditTransaction: (Transaction) -> Unit,
    onOpenInTransactions: (DrillDownQuery?, ReportCategory) -> Unit,
    activeDrillDown: Pair<ReportCategory, String>?,
    onDrillDownChange: (Pair<ReportCategory, String>?) -> Unit,
    showReportSummary: Boolean,
    onShowReportSummaryChange: (Boolean) -> Unit,
    scrollToTopRequest: Int,
    initialPageId: String?,
    initialPageRequest: Int,
    hasFab: Boolean,
    repository: ActuaRepository,
    onMutate: (labelRes: Int, action: () -> Boolean) -> Unit,
) {
    ReportsScreen(
        snapshot = snapshot ?: ReportSnapshot(emptyList(), emptyList(), 0),
        hideDecimalPlaces = hideDecimalPlaces,
        modifier = modifier,
        isLoading = snapshot == null || snapshotVersion != dataVersion,
        onSearch = onSearch,
        favoriteReportIds = favoriteReportIds,
        onFavoriteReportChange = onFavoriteReportChange,
        loadSavedReports = loadSavedReports,
        loadWidgetForRange = loadWidgetForRange,
        loadTransactions = loadTransactions,
        loadDrillDownTransactions = loadDrillDownTransactions,
        onEditTransaction = onEditTransaction,
        onOpenInTransactions = onOpenInTransactions,
        activeDrillDown = activeDrillDown,
        onDrillDownChange = onDrillDownChange,
        showReportSummary = showReportSummary,
        onShowReportSummaryChange = onShowReportSummaryChange,
        scrollToTopRequest = scrollToTopRequest,
        initialPageId = initialPageId,
        initialPageRequest = initialPageRequest,
        hasFab = hasFab,
        savedReports = remember(dataVersion) { repository.savedReports() },
        ruleEditorData = remember(dataVersion) { repository.ruleEditorData() },
        onComputePreview = { draft -> repository.computeCustomReportPreview(draft) },
        onSaveCustomReport = { draft ->
            onMutate(if (draft.id.isBlank()) R.string.navigation_creating_custom_report else R.string.navigation_updating_custom_report) {
                repository.saveCustomReport(draft)
                true
            }
        },
        onDeleteCustomReport = { id ->
            onMutate(R.string.navigation_deleting_custom_report) {
                repository.deleteCustomReport(id)
            }
        },
        onAddWidgetToDashboard = { pageId, type, metaJson ->
            onMutate(R.string.navigation_adding_dashboard_card) {
                repository.addDashboardWidget(pageId, type, metaJson)
                true
            }
        },
        onDeleteWidgetFromDashboard = { widgetId ->
            onMutate(R.string.navigation_removing_dashboard_card) {
                repository.deleteDashboardWidget(widgetId)
            }
        },
        onReorderDashboardWidgets = { pageId, widgetIds ->
            onMutate(R.string.navigation_reordering_cards) {
                repository.reorderDashboardWidgets(pageId, widgetIds)
            }
        },
        onCreateDashboardPage = { name ->
            onMutate(R.string.navigation_creating_dashboard_page) {
                repository.createDashboardPage(name)
                true
            }
        },
        onRenameDashboardPage = { pageId, name ->
            onMutate(R.string.navigation_renaming_dashboard_page) {
                repository.renameDashboardPage(pageId, name)
            }
        },
        onDeleteDashboardPage = { pageId ->
            onMutate(R.string.navigation_deleting_dashboard_page) {
                repository.deleteDashboardPage(pageId)
            }
        },
    )
}

/**
 * Strings used by [AppNavigation]'s mutation/refresh helper functions and launch effects,
 * resolved once at composition. Bundled in one class so the (size-critical) AnimatedContent
 * content lambda captures a single object rather than one parameter per string.
 */
private data class NavigationStrings(
    val mutationIncompleteFormat: String,
    val mutationFailedFormat: String,
    val unlockBudgetBeforeSync: String,
    val syncFailed: String,
    val view: String,
    val enableBankingSaved: String,
    val enableBankingSaveFailed: String,
    val simplefinSaved: String,
    val simplefinSaveFailed: String,
    val gocardlessSaved: String,
    val gocardlessSaveFailed: String,
    val simplefinAccountsLoadFailed: String,
    val banksLoadFailedFormat: String,
    val gocardlessAuthorizationStartFailed: String,
    val chooseBankFirst: String,
    val gocardlessAuthorizationPendingFormat: String,
    val gocardlessAccountsCheckFailed: String,
    val reportsLoadFailedFormat: String,
    val unlockBeforeSyncing: String,
)

@Composable
private fun navigationStrings(): NavigationStrings = NavigationStrings(
    mutationIncompleteFormat = stringResource(R.string.navigation_mutation_incomplete),
    mutationFailedFormat = stringResource(R.string.navigation_mutation_failed),
    unlockBudgetBeforeSync = stringResource(R.string.navigation_unlock_budget_before_sync),
    syncFailed = stringResource(R.string.navigation_sync_failed),
    view = stringResource(R.string.navigation_view),
    enableBankingSaved = stringResource(R.string.navigation_enable_banking_credentials_saved),
    enableBankingSaveFailed = stringResource(R.string.navigation_enable_banking_credentials_save_failed),
    simplefinSaved = stringResource(R.string.navigation_simplefin_token_saved),
    simplefinSaveFailed = stringResource(R.string.navigation_simplefin_token_save_failed),
    gocardlessSaved = stringResource(R.string.navigation_gocardless_credentials_saved),
    gocardlessSaveFailed = stringResource(R.string.navigation_gocardless_credentials_save_failed),
    simplefinAccountsLoadFailed = stringResource(R.string.navigation_simplefin_accounts_load_failed),
    banksLoadFailedFormat = stringResource(R.string.navigation_banks_load_failed),
    gocardlessAuthorizationStartFailed = stringResource(R.string.navigation_gocardless_authorization_start_failed),
    chooseBankFirst = stringResource(R.string.navigation_choose_bank_first),
    gocardlessAuthorizationPendingFormat = stringResource(R.string.navigation_gocardless_authorization_pending),
    gocardlessAccountsCheckFailed = stringResource(R.string.navigation_gocardless_accounts_check_failed),
    reportsLoadFailedFormat = stringResource(R.string.navigation_reports_load_failed_with_reason),
    unlockBeforeSyncing = stringResource(R.string.navigation_unlock_before_syncing),
)

/**
 * Callback strings for the transaction editor, resolved once at composition so the
 * non-composable [AddTransactionScreen] callbacks stay configuration-aware without
 * bloating [AppNavigation]'s content switch (a single composable call instead of fifteen).
 */
private data class TransactionEditorStrings(
    val transferPayeeFormat: String,
    val savingTransactionFailed: String,
    val noNearbyPayeeLocations: String,
    val locationPermissionSearch: String,
    val locationDisabledSearch: String,
    val locationTimeoutSearch: String,
    val locationUnavailableSearch: String,
    val locationInaccurateSearch: String,
    val locationSavedFormat: String,
    val locationAlreadySavedFormat: String,
    val locationPermissionSave: String,
    val locationDisabledSave: String,
    val locationTimeoutSave: String,
    val locationUnavailableSave: String,
    val locationInaccurateSave: String,
)

@Composable
private fun transactionEditorStrings(): TransactionEditorStrings = TransactionEditorStrings(
    transferPayeeFormat = stringResource(R.string.navigation_transfer_payee),
    savingTransactionFailed = stringResource(R.string.navigation_saving_transaction_failed),
    noNearbyPayeeLocations = stringResource(R.string.navigation_no_nearby_payee_locations),
    locationPermissionSearch = stringResource(R.string.navigation_location_permission_search),
    locationDisabledSearch = stringResource(R.string.navigation_location_disabled_search),
    locationTimeoutSearch = stringResource(R.string.navigation_location_timeout_search),
    locationUnavailableSearch = stringResource(R.string.navigation_location_unavailable_search),
    locationInaccurateSearch = stringResource(R.string.navigation_location_inaccurate_search),
    locationSavedFormat = stringResource(R.string.navigation_location_saved),
    locationAlreadySavedFormat = stringResource(R.string.navigation_location_already_saved),
    locationPermissionSave = stringResource(R.string.navigation_location_permission_save),
    locationDisabledSave = stringResource(R.string.navigation_location_disabled_save),
    locationTimeoutSave = stringResource(R.string.navigation_location_timeout_save),
    locationUnavailableSave = stringResource(R.string.navigation_location_unavailable_save),
    locationInaccurateSave = stringResource(R.string.navigation_location_inaccurate_save),
)

/**
 * The schedule/payee/import cluster of detail screens. Extracted from [AppNavigation]'s
 * content switch so the (already huge) AnimatedContent content lambda stays under the JVM
 * method-size limit while strings localize — mirroring [NavigationAccountsScreen] and
 * [NavigationManageCategoriesScreen].
 */
@Composable
private fun NavigationScheduleScreens(
    repository: ActuaRepository,
    dataVersion: Int,
    schedules: List<ScheduleListItem>,
    nonWorkingDays: com.azimulkabir.actua.data.home.NonWorkingDays,
    accounts: List<Account>,
    payeeNames: List<String>,
    hideDecimalPlaces: Boolean,
    conventionalAmountEntry: Boolean,
    context: Context,
    scheduleReminderSettings: ScheduleReminderSettings,
    scheduleReminderPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    contentModifier: Modifier,
    onMutate: (labelRes: Int, onChanged: () -> Unit, action: () -> Boolean) -> Unit,
    detail: DetailDestination,
    onDetailChange: (DetailDestination) -> Unit,
    editingScheduleId: String?,
    onEditingScheduleIdChange: (String?) -> Unit,
    scheduleReturnsToBills: Boolean,
    onScheduleReturnsToBillsChange: (Boolean) -> Unit,
    scheduleReturnsToTransactions: Boolean,
    onScheduleReturnsToTransactionsChange: (Boolean) -> Unit,
    scheduleReturnsToTransactionsTab: Boolean,
    onScheduleReturnsToTransactionsTabChange: (Boolean) -> Unit,
    billsCalendarReturnsToSchedules: Boolean,
    onBillsCalendarReturnsToSchedulesChange: (Boolean) -> Unit,
    creditCardsReturnToBills: Boolean,
    onCreditCardsReturnToBillsChange: (Boolean) -> Unit,
    scheduleRuleId: String?,
    onScheduleRuleIdChange: (String?) -> Unit,
    sharedImportText: String?,
    onSharedTextConsumed: () -> Unit,
    capturedReviewRequest: Int,
    onReturnFromCapturedReview: () -> Unit,
    onOpenCapturedReview: (Boolean) -> Unit,
    onReturnFromEditSchedule: () -> Unit,
    scheduleRemindersEnabled: Boolean,
    onScheduleRemindersEnabledChange: (Boolean) -> Unit,
    scheduleReminderLeadDays: Int,
    onScheduleReminderLeadDaysChange: (Int) -> Unit,
) {
    when (detail) {
        DetailDestination.Schedules -> SchedulesScreen(
            schedules = schedules,
            hideDecimalPlaces = hideDecimalPlaces,
            canAdd = accounts.any { !it.closed },
            onBack = { onDetailChange(DetailDestination.Main) },
            onAdd = {
                onScheduleReturnsToBillsChange(false)
                onDetailChange(DetailDestination.NewSchedule)
            },
            onFind = { onDetailChange(DetailDestination.FindSchedules) },
            onCalendar = {
                onBillsCalendarReturnsToSchedulesChange(true)
                onDetailChange(DetailDestination.BillsCalendar)
            },
            onEdit = { id ->
                onEditingScheduleIdChange(id)
                onScheduleReturnsToBillsChange(false)
                onScheduleReturnsToTransactionsChange(false)
                onScheduleReturnsToTransactionsTabChange(false)
                onDetailChange(DetailDestination.EditSchedule)
            },
            onPost = { id, today ->
                onMutate(if (today) R.string.navigation_posting_schedule_today else R.string.navigation_posting_schedule, {}) {
                    repository.postScheduleTransaction(id, today, nonWorkingDays)
                }
            },
            onSkip = { id ->
                onMutate(R.string.navigation_skipping_next_date, {}) { repository.skipScheduleNextDate(id) }
            },
            onSetCompleted = { id, completed ->
                onMutate(if (completed) R.string.navigation_completing_schedule else R.string.navigation_restarting_schedule, {}) {
                    repository.setScheduleCompleted(id, completed)
                }
            },
            onDelete = { id ->
                onMutate(R.string.navigation_deleting_schedule, {}) { repository.deleteSchedule(id) }
            },
            remindersEnabled = scheduleRemindersEnabled,
            onRemindersEnabledChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                    scheduleReminderPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scheduleReminderSettings.isEnabled = enabled
                    onScheduleRemindersEnabledChange(enabled)
                }
            },
            reminderLeadDays = scheduleReminderLeadDays,
            onReminderLeadDaysChange = { days ->
                scheduleReminderSettings.leadDays = days
                onScheduleReminderLeadDaysChange(days)
            },
            modifier = contentModifier,
        )
        DetailDestination.PayeeLocations -> PayeeLocationsScreen(
            locations = remember(dataVersion) { repository.payeeLocations() },
            writesSupported = repository.payeeLocationWritesSupported(),
            onBack = { onDetailChange(DetailDestination.Main) },
            onDelete = { id ->
                onMutate(R.string.navigation_deleting_payee_location, {}) { repository.deletePayeeLocation(id) }
            },
            onClearPayee = { payeeId ->
                onMutate(R.string.navigation_clearing_payee_locations, {}) { repository.clearPayeeLocations(payeeId) > 0 }
            },
            modifier = contentModifier,
        )
        DetailDestination.ManagePayees -> ManagePayeesScreen(
            payees = remember(dataVersion) { repository.managedPayees() },
            learnCategoriesEnabled = remember(dataVersion) { repository.learnCategoriesEnabled() },
            onBack = { onDetailChange(DetailDestination.Main) },
            onSetLearnCategoriesEnabled = { enabled ->
                onMutate(R.string.navigation_changing_category_learning, {}) { repository.setLearnCategoriesEnabled(enabled) }
            },
            onRename = { payee, name -> onMutate(R.string.navigation_renaming_payee, {}) { repository.renamePayee(payee.id, name) } },
            onSetFavorite = { payee, favorite ->
                onMutate(R.string.navigation_updating_favorite, {}) { repository.setPayeeFavorite(payee.id, favorite) }
            },
            onSetLearnCategories = { payee, learn ->
                onMutate(R.string.navigation_changing_category_learning, {}) { repository.setPayeeLearnCategories(payee.id, learn) }
            },
            onDelete = { targets -> onMutate(R.string.navigation_deleting_payees, {}) { repository.deletePayees(targets.map { it.id }) } },
            onMerge = { target, merged ->
                onMutate(R.string.navigation_merging_payees, {}) { repository.mergePayees(target.id, merged.map { it.id }) }
            },
            modifier = contentModifier,
        )
        DetailDestination.ImportTransactions -> ImportTransactionsScreen(
            accounts = accounts.filterNot { it.closed },
            findMatches = repository::importMatches,
            onImport = { groups, cleared, onImported ->
                onMutate(R.string.navigation_importing_transactions, onImported) {
                    repository.importTransactions(groups, cleared) == groups.sumOf { it.candidates.size }
                }
            },
            onBack = { onDetailChange(DetailDestination.Main) },
            modifier = contentModifier,
            initialSharedText = sharedImportText,
            onSharedTextConsumed = onSharedTextConsumed,
            onOpenCapturedReview = { onOpenCapturedReview(true) },
        )
        DetailDestination.ReviewCaptured -> key(capturedReviewRequest) {
            ImportTransactionsScreen(
                accounts = accounts.filterNot { it.closed },
                findMatches = repository::importMatches,
                onImport = { groups, cleared, onImported ->
                    onMutate(R.string.navigation_importing_transactions, onImported) {
                        repository.importTransactions(groups, cleared) == groups.sumOf { it.candidates.size }
                    }
                },
                onBack = onReturnFromCapturedReview,
                modifier = contentModifier,
                capturedReviewOnly = true,
            )
        }
        DetailDestination.BillsCalendar -> BillsCalendarScreen(
            loadItems = { year, month, cardBills ->
                repository.billCalendarItems(year, month, cardBills, nonWorkingDays)
            },
            refreshKey = dataVersion,
            hideDecimalPlaces = hideDecimalPlaces,
            onBack = {
                onDetailChange(
                    if (billsCalendarReturnsToSchedules) {
                        onBillsCalendarReturnsToSchedulesChange(false)
                        DetailDestination.Schedules
                    } else {
                        DetailDestination.Main
                    },
                )
            },
            onAddSchedule = {
                onScheduleReturnsToBillsChange(true)
                onDetailChange(DetailDestination.NewSchedule)
            },
            onConfigureCards = {
                onCreditCardsReturnToBillsChange(true)
                onDetailChange(DetailDestination.CreditCards)
            },
            onEditSchedule = { id ->
                onEditingScheduleIdChange(id)
                onScheduleReturnsToBillsChange(true)
                onScheduleReturnsToTransactionsChange(false)
                onScheduleReturnsToTransactionsTabChange(false)
                onDetailChange(DetailDestination.EditSchedule)
            },
            onPost = { id, today ->
                onMutate(if (today) R.string.navigation_posting_schedule_today else R.string.navigation_posting_schedule, {}) {
                    repository.postScheduleTransaction(id, today, nonWorkingDays)
                }
            },
            onSkip = { id -> onMutate(R.string.navigation_skipping_occurrence, {}) { repository.skipScheduleNextDate(id) } },
            onDelete = { id -> onMutate(R.string.navigation_deleting_schedule, {}) { repository.deleteSchedule(id) } },
            modifier = contentModifier,
        )
        DetailDestination.FindSchedules -> {
            val proposals by produceState<List<com.azimulkabir.actua.data.schedules.ScheduleDiscovery.DisplayProposal>?>(
                initialValue = null,
                key1 = dataVersion,
            ) {
                value = withContext(Dispatchers.IO) { repository.discoverSchedules() }
            }
            FindSchedulesScreen(
                proposals = proposals,
                hideDecimalPlaces = hideDecimalPlaces,
                onBack = { onDetailChange(DetailDestination.Schedules) },
                onCreate = { selected ->
                    onMutate(
                        R.string.navigation_creating_schedules,
                        { onDetailChange(DetailDestination.Schedules) },
                    ) { repository.createDiscoveredSchedules(selected) }
                },
                modifier = contentModifier,
            )
        }
        DetailDestination.NewSchedule -> com.azimulkabir.actua.ui.settings.EditScheduleScreen(
            item = null,
            accounts = accounts,
            payeeOptions = payeeNames,
            hideDecimalPlaces = hideDecimalPlaces,
            conventionalAmountEntry = conventionalAmountEntry,
            onBack = {
                onDetailChange(if (scheduleReturnsToBills) DetailDestination.BillsCalendar else DetailDestination.Schedules)
                onScheduleReturnsToBillsChange(false)
            },
            onSave = { fields, payeeName ->
                onMutate(
                    R.string.navigation_creating_schedule,
                    {
                        onDetailChange(if (scheduleReturnsToBills) DetailDestination.BillsCalendar else DetailDestination.Schedules)
                        onScheduleReturnsToBillsChange(false)
                    },
                ) { repository.createSchedule(fields, payeeName) }
            },
            modifier = contentModifier,
        )
        DetailDestination.EditSchedule -> schedules.firstOrNull {
            it.schedule.id == editingScheduleId
        }?.let { item ->
            com.azimulkabir.actua.ui.settings.EditScheduleScreen(
                item = item,
                accounts = accounts,
                payeeOptions = payeeNames,
                hideDecimalPlaces = hideDecimalPlaces,
                conventionalAmountEntry = conventionalAmountEntry,
                linkedTransactions = remember(dataVersion, item.schedule.id) {
                    repository.scheduleTransactions(item.schedule.id)
                },
                onBack = onReturnFromEditSchedule,
                onSave = { fields, payeeName ->
                    onMutate(
                        R.string.navigation_saving_schedule,
                        onReturnFromEditSchedule,
                    ) { repository.updateSchedule(item.schedule.id, fields, payeeName) }
                },
                onDelete = {
                    onMutate(
                        R.string.navigation_deleting_schedule,
                        onReturnFromEditSchedule,
                    ) { repository.deleteSchedule(item.schedule.id) }
                },
                onUnlinkTransaction = { transactionId ->
                    onMutate(R.string.navigation_unlinking_transaction, {}) {
                        repository.unlinkScheduleTransaction(item.schedule.id, transactionId)
                    }
                },
                onEditAsRule = { ruleId ->
                    onScheduleRuleIdChange(ruleId)
                    onDetailChange(DetailDestination.Rules)
                },
                modifier = contentModifier,
            )
        } ?: run { onDetailChange(DetailDestination.Schedules) }
        else -> Unit
    }
}

@Composable
private fun NavigationManageCategoriesScreen(
    repository: ActuaRepository,
    dataVersion: Int,
    contentModifier: Modifier,
    onBack: () -> Unit,
    onReorderGroupsClick: () -> Unit,
    onMutate: (labelRes: Int, action: () -> Boolean) -> Unit,
    onMutateSync: (labelRes: Int, action: () -> Boolean) -> Boolean,
) {
    ManageCategoriesScreen(
        groups = remember(dataVersion) { repository.categoryGroupsForReorder() },
        onBack = onBack,
        onReorderGroupsClick = onReorderGroupsClick,
        onCreateGroup = { name -> onMutate(R.string.navigation_creating_group) { repository.createCategoryGroup(name) } },
        onRenameGroup = { group, name -> onMutate(R.string.navigation_renaming_group) { repository.renameCategoryGroup(group, name) } },
        onSetGroupHidden = { group, hidden ->
            onMutate(if (hidden) R.string.navigation_hiding_group else R.string.navigation_showing_group) { repository.setCategoryGroupHidden(group, hidden) }
        },
        onCreateCategory = { group, name -> onMutate(R.string.navigation_creating_category) { repository.createCategory(group, name) } },
        onRenameCategory = { group, category, name ->
            onMutate(R.string.navigation_renaming_category) { repository.renameCategory(group, category, name) }
        },
        onSetCategoryHidden = { group, category, hidden ->
            onMutate(if (hidden) R.string.navigation_hiding_category else R.string.navigation_showing_category) {
                repository.setCategoryHidden(group, category, hidden)
            }
        },
        onDeleteCategory = { group, category, transferToId ->
            onMutate(R.string.navigation_deleting_category) { repository.deleteCategory(group, category, transferToId) }
        },
        loadCategoryDeletePlan = { group, category ->
            withContext(Dispatchers.IO) { runCatching { repository.categoryDeletePlan(group, category) }.getOrNull() }
        },
        onDeleteGroup = { group, targetGroupId ->
            onMutate(R.string.navigation_deleting_group) { repository.deleteCategoryGroup(group.id, targetGroupId) }
        },
        onMoveCategory = { move -> onMutateSync(R.string.navigation_reordering_category) { repository.moveCategory(move) } },
        modifier = contentModifier,
    )
}

@Composable
private fun NavigationReorderGroupsScreen(
    repository: ActuaRepository,
    dataVersion: Int,
    contentModifier: Modifier,
    onBack: () -> Unit,
    onMutateSync: (labelRes: Int, action: () -> Boolean) -> Boolean,
) {
    ReorderGroupsScreen(
        groups = remember(dataVersion) { repository.categoryGroupsForReorder() },
        onBack = onBack,
        onMoveGroup = { move -> onMutateSync(R.string.navigation_reordering_category_group) { repository.moveCategoryGroup(move) } },
        modifier = contentModifier,
    )
}

@Composable
private fun NavigationReorderAccountsScreen(
    repository: ActuaRepository,
    dataVersion: Int,
    contentModifier: Modifier,
    onBack: () -> Unit,
    onMutate: (labelRes: Int, action: () -> Boolean) -> Unit,
    onMutateSync: (labelRes: Int, action: () -> Boolean) -> Boolean,
) {
    ReorderAccountsScreen(
        accounts = remember(dataVersion) { repository.accountsForReorder() },
        groups = remember(dataVersion) { repository.accountGroupsForReorder() },
        onBack = onBack,
        onMoveAccount = { move -> onMutateSync(R.string.navigation_reordering_account) { repository.moveAccount(move) } },
        onMoveGroup = { move -> onMutateSync(R.string.navigation_reordering_account_group) { repository.moveAccountGroup(move) } },
        onCreateGroup = { name -> onMutate(R.string.navigation_creating_account_group) { repository.createAccountGroup(name) } },
        onRenameGroup = { id, name -> onMutate(R.string.navigation_renaming_account_group) { repository.renameAccountGroup(id, name) } },
        onDeleteGroup = { id -> onMutate(R.string.navigation_deleting_account_group) { repository.deleteAccountGroup(id) } },
        modifier = contentModifier,
    )
}

@Composable
private fun NavigationAccountsScreen(
    repository: ActuaRepository,
    dataVersion: Int,
    contentModifier: Modifier,
    accounts: List<Account>,
    transactions: List<Transaction>,
    hideDecimalPlaces: Boolean,
    showMonthlySummary: Boolean,
    onShowMonthlySummaryChange: (Boolean) -> Unit,
    creditCards: List<CreditCardStatus>,
    onAccountClick: (String) -> Unit,
    onAllAccountsClick: () -> Unit,
    onMutate: (labelRes: Int, action: () -> Boolean) -> Unit,
    onMutateSync: (labelRes: Int, action: () -> Boolean) -> Boolean,
    pendingCaptureCount: Int,
    onReviewCaptures: () -> Unit,
    newTransactionCounts: Map<String, Int>,
    onSearch: () -> Unit,
    onSetUpBankSync: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    favoriteAccountIds: Set<String>,
    onFavoriteAccountChange: (String, Boolean) -> Unit,
    scrollToTopRequest: Int,
    hasFab: Boolean,
    monthStart: MonthStartRule,
) {
    AccountsScreen(
        modifier = contentModifier,
        accounts = accounts,
        transactions = transactions,
        hideDecimalPlaces = hideDecimalPlaces,
        showMonthlySummary = showMonthlySummary,
        onShowMonthlySummaryChange = onShowMonthlySummaryChange,
        creditCards = creditCards,
        onAccountClick = onAccountClick,
        onAllAccountsClick = onAllAccountsClick,
        loadCloseOptions = { account ->
            withContext(Dispatchers.IO) {
                CloseAccountOptions(
                    hasTransactions = repository.accountHasTransactions(account.id),
                    categoryGroups = repository.categoryGroupsForReorder().map { group ->
                        CloseCategoryGroup(group.name, group.categories.map { it.id to it.name })
                    },
                )
            }
        },
        onCloseAccount = { account, transferAccountId, categoryId, forced ->
            onMutate(if (forced) R.string.navigation_force_closing_account else R.string.navigation_closing_account) {
                repository.closeAccount(account.id, transferAccountId, categoryId, forced)
            }
        },
        onReopenAccount = { account ->
            onMutate(R.string.navigation_reopening_account) { repository.reopenAccount(account.id) }
        },
        onRenameAccount = { account, name ->
            onMutate(R.string.navigation_renaming_account) { repository.renameAccount(account.id, name) }
        },
        onChangeAccountType = { account, type ->
            onMutate(R.string.navigation_changing_account_type) { repository.setAccountType(account.id, type) }
        },
        onCreateAccount = { name, offBudget, balance, type, groupId ->
            onMutate(R.string.navigation_creating_account) { repository.createAccount(name, offBudget, balance, type, groupId) }
        },
        accountGroups = remember(dataVersion) { repository.accountGroupsForReorder() },
        onSetAccountGroup = { account, groupId ->
            onMutate(R.string.navigation_updating_account_group) { repository.setAccountGroup(account.id, groupId) }
        },
        accountsForReorder = remember(dataVersion) { repository.accountsForReorder() },
        onMoveAccount = { move -> onMutateSync(R.string.navigation_reordering_account) { repository.moveAccount(move) } },
        onMoveGroup = { move -> onMutateSync(R.string.navigation_reordering_account_group) { repository.moveAccountGroup(move) } },
        onCreateGroup = { name -> onMutate(R.string.navigation_creating_account_group) { repository.createAccountGroup(name) } },
        onRenameGroup = { id, name -> onMutate(R.string.navigation_renaming_account_group) { repository.renameAccountGroup(id, name) } },
        onDeleteGroup = { id -> onMutate(R.string.navigation_deleting_account_group) { repository.deleteAccountGroup(id) } },
        pendingCaptureCount = pendingCaptureCount,
        onReviewCaptures = onReviewCaptures,
        newTransactionCounts = newTransactionCounts,
        onSearch = onSearch,
        onSetUpBankSync = onSetUpBankSync,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        favoriteAccountIds = favoriteAccountIds,
        onFavoriteAccountChange = onFavoriteAccountChange,
        scrollToTopRequest = scrollToTopRequest,
        hasFab = hasFab,
        monthStart = monthStart,
    )
}


