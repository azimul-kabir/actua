package com.azimulkabir.actua.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.azimulkabir.actua.ui.accounts.AccountsScreen
import com.azimulkabir.actua.ui.budget.BudgetScreen
import com.azimulkabir.actua.ui.settings.SettingsScreen
import com.azimulkabir.actua.ui.settings.ConnectionScreen
import com.azimulkabir.actua.ui.settings.CreditCardsScreen
import com.azimulkabir.actua.ui.settings.RulesScreen
import com.azimulkabir.actua.ui.settings.SchedulesScreen
import com.azimulkabir.actua.ui.settings.FindSchedulesScreen
import com.azimulkabir.actua.ui.settings.BillsCalendarScreen
import com.azimulkabir.actua.ui.transactions.AddTransactionScreen
import com.azimulkabir.actua.ui.transactions.TransactionsScreen
import com.azimulkabir.actua.ui.reports.ReportsScreen
import com.azimulkabir.actua.ui.search.GlobalSearchScreen
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import com.azimulkabir.actua.data.sync.SyncRunResult
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.notifications.CreditCardDueNotificationScheduler
import com.azimulkabir.actua.data.notifications.CreditCardNotificationSettings
import com.azimulkabir.actua.ui.components.BalanceVisibility
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.AppLaunchRequest
import com.azimulkabir.actua.widget.WidgetActions
import com.azimulkabir.actua.widget.WidgetUpdater

private enum class MainDestination(
    val label: String,
    val icon: ImageVector,
) {
    Budget("Budget", Icons.Outlined.PieChartOutline),
    Accounts("Accounts", Icons.Outlined.AccountBalanceWallet),
    Transactions("Transactions", Icons.Outlined.ReceiptLong),
    Reports("Reports", Icons.Outlined.BarChart),
    More("More", Icons.Outlined.MoreHoriz),
}

private enum class DetailDestination { Main, Transactions, EditTransaction, Search, Connection, CreditCards, Rules, Schedules, BillsCalendar, FindSchedules, NewSchedule, EditSchedule }

private data class TabSnapshot(
    val detail: DetailDestination = DetailDestination.Main,
    val transactionAccount: String? = null,
    val transactionCategory: String? = null,
    val transactionMonth: String? = null,
    val transactionSearch: String = "",
    val activeBudgetCategory: String? = null,
    val transactionsReturnCategory: String? = null,
)

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    foregroundGeneration: Int = 0,
    launchRequest: AppLaunchRequest? = null,
    onLaunchRequestConsumed: () -> Unit = {},
    onAppearanceChange: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val displayPreferences = remember { DisplayPreferences(context) }
    val creditCardNotificationSettings = remember { CreditCardNotificationSettings(context) }
    var creditCardNotificationsEnabled by remember {
        mutableStateOf(creditCardNotificationSettings.isEnabled)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        creditCardNotificationSettings.isEnabled = granted
        creditCardNotificationsEnabled = granted
        CreditCardDueNotificationScheduler.refresh(context)
    }
    var repositoryVersion by remember { mutableStateOf(0) }
    val repository = remember(repositoryVersion) { ActuaRepository(context) }
    var dataVersion by remember { mutableStateOf(0) }
    var budgetMonth by rememberSaveable {
        mutableStateOf(java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date()))
    }
    val budgetGroups = remember(dataVersion, budgetMonth) { repository.budgetGroups(budgetMonth) }
    val budgetOverview = remember(dataVersion, budgetMonth) { repository.budgetOverview(budgetMonth) }
    val accounts = remember(dataVersion) { repository.accounts() }
    var hideReconciledTransactions by remember {
        mutableStateOf(displayPreferences.hideReconciledTransactions)
    }
    val transactions = remember(dataVersion) { repository.transactions() }
    val filteredTransactions = remember(dataVersion, hideReconciledTransactions) {
        if (hideReconciledTransactions) repository.transactions(hideReconciled = true) else transactions
    }
    val searchTransactions: suspend (String) -> List<Transaction> = remember(repository, hideReconciledTransactions) {
        { query -> withContext(Dispatchers.IO) {
            repository.transactions(query, hideReconciled = hideReconciledTransactions)
        } }
    }
    val categoryNames = remember(dataVersion) { repository.categoryNames() }
    val payeeNames = remember(dataVersion) { repository.payeeNames() }
    val reportSnapshot = remember(dataVersion) { repository.reports() }
    val creditCards = remember(dataVersion) { repository.creditCards() }
    val rules = remember(dataVersion) { repository.rules() }
    val rulesSupported = remember(dataVersion) { repository.rulesSupported() }
    val scheduleOwnedRuleIds = remember(dataVersion) { repository.scheduleOwnedRuleIds() }
    val ruleEditorData = remember(dataVersion) { repository.ruleEditorData() }
    val schedules = remember(dataVersion) { repository.schedules() }
    var editingScheduleId by rememberSaveable { mutableStateOf<String?>(null) }
    var destination by rememberSaveable {
        mutableStateOf(MainDestination.entries.firstOrNull { it.label == displayPreferences.startPage }
            ?: MainDestination.Accounts)
    }
    var detail by rememberSaveable { mutableStateOf(DetailDestination.Main) }
    var transactionAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionSearch by rememberSaveable { mutableStateOf("") }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var newTransactionType by remember { mutableStateOf(com.azimulkabir.actua.model.Type.EXPENSE) }
    var editorReturnsToTransactions by rememberSaveable { mutableStateOf(false) }
    var editorReturnsToCategory by rememberSaveable { mutableStateOf(false) }
    var activeBudgetCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var reopenBudgetCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var transactionsReturnCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val tabSnapshots = remember { mutableStateMapOf<MainDestination, TabSnapshot>() }
    val rootRequests = remember { mutableStateMapOf<MainDestination, Int>() }
    val tabStateHolder = rememberSaveableStateHolder()
    var addOrigin by rememberSaveable { mutableStateOf(MainDestination.Accounts) }
    var transactionFabExpanded by rememberSaveable { mutableStateOf(true) }
    var reconcileOpen by remember { mutableStateOf(false) }
    var scheduleReturnsToBills by rememberSaveable { mutableStateOf(false) }
    var creditCardsReturnToBills by rememberSaveable { mutableStateOf(false) }
    var hideDecimalPlaces by remember { mutableStateOf(displayPreferences.hideDecimalPlaces) }
    var currencyCode by remember { mutableStateOf(displayPreferences.currencyCode) }
    var currencySymbolOnly by remember { mutableStateOf(displayPreferences.currencySymbolOnly) }
    var showHiddenCategories by remember { mutableStateOf(displayPreferences.showHiddenCategories) }
    var showSpentColumn by remember { mutableStateOf(displayPreferences.showSpentColumn) }
    var showBudgetProgressBars by remember { mutableStateOf(displayPreferences.showBudgetProgressBars) }
    var budgetView by remember { mutableStateOf(displayPreferences.budgetView) }
    var showBudgetOverview by remember { mutableStateOf(displayPreferences.showBudgetOverview) }
    var showGroupTotals by remember { mutableStateOf(displayPreferences.showGroupTotals) }
    var hideFullySpentCategories by remember { mutableStateOf(displayPreferences.hideFullySpentCategories) }
    var hideBalances by remember { mutableStateOf(displayPreferences.hideBalances) }
    var appearance by remember { mutableStateOf(displayPreferences.appearance) }
    var startPage by remember { mutableStateOf(displayPreferences.startPage) }
    var defaultAccount by remember { mutableStateOf(displayPreferences.defaultAccount) }
    var groupTransactionsByDate by remember { mutableStateOf(displayPreferences.groupTransactionsByDate) }
    var showAccountsMonthlySummary by remember { mutableStateOf(displayPreferences.showAccountsMonthlySummary) }
    var conventionalAmountEntry by remember { mutableStateOf(displayPreferences.conventionalAmountEntry) }
    var showBottomNavigationLabels by remember { mutableStateOf(displayPreferences.showBottomNavigationLabels) }
    var showCurrentBalanceSummary by remember { mutableStateOf(displayPreferences.showCurrentBalanceSummary) }
    BalanceVisibility.hidden = hideBalances
    CurrencyDisplay.code = currencyCode
    CurrencyDisplay.symbolOnly = currencySymbolOnly
    val snackbarHostState = remember { SnackbarHostState() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun mutate(label: String, action: () -> Boolean): Boolean = runCatching(action).fold(
        onSuccess = { changed ->
            if (changed) dataVersion += 1 else errorMessage = "$label could not be completed."
            changed
        },
        onFailure = { error ->
            errorMessage = error.message?.takeIf(String::isNotBlank) ?: "$label failed."
            false
        },
    )

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
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    LaunchedEffect(foregroundGeneration) {
        val result = runCatching { withContext(Dispatchers.IO) { ActualSyncRunner.run(context) } }
            .onFailure { errorMessage = it.message ?: "Automatic sync failed." }
            .getOrNull()
        if (result is SyncRunResult.Success) {
            dataVersion += 1
            CreditCardDueNotificationScheduler.refresh(context)
            WidgetUpdater.requestAll(context)
        }
    }

    LaunchedEffect(launchRequest?.nonce) {
        val request = launchRequest ?: return@LaunchedEffect
        if (!repository.isUsingActualBudget) {
            destination = MainDestination.More
            detail = DetailDestination.Connection
            onLaunchRequestConsumed()
            return@LaunchedEffect
        }
        when (request.action) {
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

    BackHandler(enabled = detail != DetailDestination.Main || destination != MainDestination.Budget) {
        when {
            detail == DetailDestination.EditTransaction && editorReturnsToCategory -> {
                reopenBudgetCategory = transactionCategory
                detail = DetailDestination.Main
                destination = MainDestination.Budget
                editingTransaction = null
                editorReturnsToCategory = false
            }
            detail == DetailDestination.EditTransaction && editorReturnsToTransactions -> {
                detail = DetailDestination.Transactions
                editingTransaction = null
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
            else -> destination = MainDestination.Budget
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val onMainTab = detail == DetailDestination.Main && destination in setOf(
                MainDestination.Budget,
                MainDestination.Accounts,
                MainDestination.Transactions,
                MainDestination.Reports,
            )
            val inAccount = detail == DetailDestination.Transactions && transactionAccount != null
            val inBudgetCategory = detail == DetailDestination.Main &&
                destination == MainDestination.Budget && activeBudgetCategory != null
            if (repository.isUsingActualBudget && !reconcileOpen && (onMainTab || inAccount)) {
                ExtendedFloatingActionButton(
                    onClick = when {
                        inBudgetCategory -> ::openAddTransactionForCategory
                        inAccount -> ::openAddTransactionForAccount
                        else -> ::openAddTransaction
                    },
                    expanded = transactionFabExpanded,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Transaction") },
                )
            }
        },
        bottomBar = {
            if (detail != DetailDestination.EditTransaction) NavigationBar {
                MainDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = {
                            if (item != MainDestination.More && !repository.isUsingActualBudget) {
                                destination = MainDestination.More
                                detail = DetailDestination.Connection
                                return@NavigationBarItem
                            }
                            if (item == destination) {
                                if (detail != DetailDestination.Main) {
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
                            transactionFabExpanded = true
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = if (showBottomNavigationLabels) { { Text(item.label) } } else null,
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding).nestedScroll(fabScrollConnection)
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
                }.using(SizeTransform(clip = false))
            },
            label = "Main navigation motion",
        ) { (shownDetail, shownDestination) ->
        tabStateHolder.SaveableStateProvider("${shownDestination.name}:${shownDetail.name}") {
        when (shownDetail) {
            DetailDestination.Transactions -> TransactionsScreen(
                accountName = transactionAccount,
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
                searchTransactions = searchTransactions,
                hideDecimalPlaces = hideDecimalPlaces,
                conventionalAmountEntry = conventionalAmountEntry,
                groupTransactionsByDate = groupTransactionsByDate,
                onGroupTransactionsByDateChange = {
                    displayPreferences.groupTransactionsByDate = it
                    groupTransactionsByDate = it
                },
                hideReconciledTransactions = hideReconciledTransactions,
                onHideReconciledTransactionsChange = {
                    displayPreferences.hideReconciledTransactions = it
                    hideReconciledTransactions = it
                },
                onSetCleared = { transaction, cleared ->
                    mutate("Updating transaction") { repository.setTransactionCleared(transaction.id, cleared) }
                },
                onReconcileAccount = { account ->
                    mutate("Reconciling account") { repository.reconcileAccount(account.id) }
                },
                onCreateReconciliationAdjustment = { account, difference ->
                    mutate("Creating reconciliation adjustment") {
                        repository.createReconciliationAdjustment(account.id, difference)
                    }
                },
                onDelete = { transaction ->
                    mutate("Deleting transaction") { repository.deleteTransaction(transaction.id) }
                },
                account = accounts.firstOrNull { it.name == transactionAccount },
                creditCard = creditCards.firstOrNull { card ->
                    card.accountId == accounts.firstOrNull { it.name == transactionAccount }?.id
                },
                onSaveAccountNote = { note ->
                    accounts.firstOrNull { it.name == transactionAccount }?.let { account ->
                        mutate("Saving account note") { repository.setAccountNote(account.id, note) }
                    }
                },
                initialSearch = transactionSearch,
                showCurrentBalanceSummary = showCurrentBalanceSummary,
                onShowCurrentBalanceSummaryChange = {
                    displayPreferences.showCurrentBalanceSummary = it
                    showCurrentBalanceSummary = it
                },
                onReconcileVisibilityChange = { reconcileOpen = it },
            )
            DetailDestination.EditTransaction -> AddTransactionScreen(
                editing = editingTransaction,
                defaultType = newTransactionType,
                onBack = {
                    if (editorReturnsToCategory) {
                        reopenBudgetCategory = transactionCategory
                        destination = MainDestination.Budget
                    }
                    detail = if (editorReturnsToTransactions) DetailDestination.Transactions else DetailDestination.Main
                    editingTransaction = null
                    if (!editorReturnsToTransactions && !editorReturnsToCategory) destination = addOrigin
                    editorReturnsToCategory = false
                },
                onSave = {
                    val wasEditing = editingTransaction != null
                    if (runCatching { repository.saveTransaction(it) }.fold(
                            onSuccess = { true },
                            onFailure = { error ->
                                errorMessage = error.message?.takeIf(String::isNotBlank) ?: "Saving transaction failed."
                                false
                            },
                        )) {
                        dataVersion += 1
                        WidgetUpdater.requestAll(context)
                        editingTransaction = null
                        if (editorReturnsToCategory) {
                            reopenBudgetCategory = transactionCategory
                            destination = MainDestination.Budget
                            detail = DetailDestination.Main
                            editorReturnsToCategory = false
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
                },
                onDelete = { transaction ->
                    if (mutate("Deleting transaction") { repository.deleteTransaction(transaction.id) }) {
                        editingTransaction = null
                        detail = if (editorReturnsToTransactions) {
                            DetailDestination.Transactions
                        } else {
                            destination = addOrigin
                            DetailDestination.Main
                        }
                    }
                },
                modifier = contentModifier,
                    accountOptions = accounts.filter { !it.closed }.map { it.name },
                    offBudgetAccountOptions = accounts.filter { !it.closed && it.offBudget }
                        .mapTo(mutableSetOf()) { it.name },
                    accountBalanceLabels = if (hideBalances) emptyMap() else accounts
                        .filterNot { it.closed }
                        .associate { it.name to formatMoneyCents(it.balanceCents, hideDecimalPlaces) },
                    categoryOptions = categoryNames,
                    payeeOptions = (payeeNames + accounts.filterNot { it.closed }.map { "Transfer: ${it.name}" }).distinct(),
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
                    onResolveRuleCategory = repository::ruleCategoryFor,
            )
            DetailDestination.Search -> GlobalSearchScreen(
                transactions = filteredTransactions,
                searchTransactions = remember(repository, hideReconciledTransactions) {
                    { query, limit, offset ->
                        withContext(Dispatchers.IO) {
                            repository.transactions(query, limit, offset,
                                hideReconciled = hideReconciledTransactions)
                        }
                    }
                },
                accounts = accounts,
                payees = payeeNames,
                categories = categoryNames,
                hideDecimalPlaces = hideDecimalPlaces,
                onBack = { detail = DetailDestination.Main },
                onTransactionEdit = {
                    addOrigin = destination
                    editingTransaction = it
                    editorReturnsToTransactions = false
                    detail = DetailDestination.EditTransaction
                },
                onTransactionDelete = { transaction ->
                    mutate("Deleting transaction") { repository.deleteTransaction(transaction.id) }
                },
                onTransactionClearedChange = { transaction, cleared ->
                    mutate("Updating transaction") { repository.setTransactionCleared(transaction.id, cleared) }
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
                onBeforeBudgetReplacement = { repository.close() },
                onBudgetInstalled = {
                    repositoryVersion += 1
                    dataVersion += 1
                    CreditCardDueNotificationScheduler.refresh(context)
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
                    if (mutate("Saving credit card") { repository.setCreditCard(accountId, day, paymentDue, limit) }) {
                        CreditCardDueNotificationScheduler.refresh(context)
                    }
                },
                onRemove = { accountId ->
                    if (mutate("Removing credit card") { repository.setCreditCard(accountId, null) }) {
                        CreditCardDueNotificationScheduler.refresh(context)
                    }
                },
                notificationsEnabled = creditCardNotificationsEnabled,
                onNotificationsEnabledChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        creditCardNotificationSettings.isEnabled = enabled
                        creditCardNotificationsEnabled = enabled
                        CreditCardDueNotificationScheduler.refresh(context)
                    }
                },
                modifier = contentModifier,
            )
            DetailDestination.Rules -> RulesScreen(
                rules = rules,
                supported = rulesSupported,
                scheduleOwnedRuleIds = scheduleOwnedRuleIds,
                editorData = ruleEditorData,
                onBack = { detail = DetailDestination.Main },
                onSave = { rule -> mutate("Saving rule") { repository.saveRule(rule) } },
                onDelete = { ruleId -> mutate("Deleting rule") { repository.deleteRule(ruleId) } },
                modifier = contentModifier,
            )
            DetailDestination.Schedules -> SchedulesScreen(
                schedules = schedules,
                hideDecimalPlaces = hideDecimalPlaces,
                canAdd = accounts.any { !it.closed },
                onBack = { detail = DetailDestination.Main },
                onAdd = {
                    scheduleReturnsToBills = false
                    detail = DetailDestination.NewSchedule
                },
                onFind = { detail = DetailDestination.FindSchedules },
                onCalendar = { detail = DetailDestination.BillsCalendar },
                onEdit = { id ->
                    editingScheduleId = id
                    scheduleReturnsToBills = false
                    detail = DetailDestination.EditSchedule
                },
                onPost = { id, today ->
                    mutate(if (today) "Posting schedule today" else "Posting schedule") {
                        repository.postScheduleTransaction(id, today)
                    }
                },
                onSkip = { id ->
                    mutate("Skipping next date") { repository.skipScheduleNextDate(id) }
                },
                onSetCompleted = { id, completed ->
                    mutate(if (completed) "Completing schedule" else "Restarting schedule") {
                        repository.setScheduleCompleted(id, completed)
                    }
                },
                onDelete = { id ->
                    mutate("Deleting schedule") { repository.deleteSchedule(id) }
                },
                modifier = contentModifier,
            )
            DetailDestination.BillsCalendar -> BillsCalendarScreen(
                loadItems = { year, month, cardBills ->
                    repository.billCalendarItems(year, month, cardBills)
                },
                refreshKey = dataVersion,
                hideDecimalPlaces = hideDecimalPlaces,
                onBack = { detail = DetailDestination.Schedules },
                onAddSchedule = {
                    scheduleReturnsToBills = true
                    detail = DetailDestination.NewSchedule
                },
                onConfigureCards = {
                    creditCardsReturnToBills = true
                    detail = DetailDestination.CreditCards
                },
                onEditSchedule = { id ->
                    editingScheduleId = id
                    scheduleReturnsToBills = true
                    detail = DetailDestination.EditSchedule
                },
                onPost = { id, today ->
                    mutate(if (today) "Posting schedule today" else "Posting schedule") {
                        repository.postScheduleTransaction(id, today)
                    }
                },
                onSkip = { id -> mutate("Skipping occurrence") { repository.skipScheduleNextDate(id) } },
                onDelete = { id -> mutate("Deleting schedule") { repository.deleteSchedule(id) } },
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
                    onBack = { detail = DetailDestination.Schedules },
                    onCreate = { selected ->
                        if (mutate("Creating schedules") {
                            repository.createDiscoveredSchedules(selected)
                        }) {
                            detail = DetailDestination.Schedules
                        }
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
                    detail = if (scheduleReturnsToBills) DetailDestination.BillsCalendar else DetailDestination.Schedules
                    scheduleReturnsToBills = false
                },
                onSave = { fields, payeeName ->
                    if (mutate("Creating schedule") {
                        repository.createSchedule(fields, payeeName)
                    }) {
                        detail = if (scheduleReturnsToBills) DetailDestination.BillsCalendar else DetailDestination.Schedules
                        scheduleReturnsToBills = false
                    }
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
                    onBack = {
                        detail = if (scheduleReturnsToBills) DetailDestination.BillsCalendar else DetailDestination.Schedules
                        scheduleReturnsToBills = false
                    },
                    onSave = { fields, payeeName ->
                        if (mutate("Saving schedule") {
                            repository.updateSchedule(item.schedule.id, fields, payeeName)
                        }) {
                            detail = if (scheduleReturnsToBills) DetailDestination.BillsCalendar else DetailDestination.Schedules
                            scheduleReturnsToBills = false
                        }
                    },
                    onDelete = {
                        if (mutate("Deleting schedule") { repository.deleteSchedule(item.schedule.id) }) {
                            detail = if (scheduleReturnsToBills) DetailDestination.BillsCalendar else DetailDestination.Schedules
                            scheduleReturnsToBills = false
                        }
                    },
                    onUnlinkTransaction = { transactionId ->
                        mutate("Unlinking transaction") {
                            repository.unlinkScheduleTransaction(item.schedule.id, transactionId)
                        }
                    },
                    modifier = contentModifier,
                )
            } ?: run { detail = DetailDestination.Schedules }
            DetailDestination.Main -> if (!repository.isUsingActualBudget && destination != MainDestination.More) {
                NoBudgetScreen(contentModifier) {
                    destination = MainDestination.More
                    detail = DetailDestination.Connection
                }
            } else when (shownDestination) {
                MainDestination.Budget -> BudgetScreen(
                    contentModifier,
                    groups = budgetGroups,
                    overview = budgetOverview,
                    month = budgetMonth,
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
                    showGroupTotals = showGroupTotals,
                    onShowGroupTotalsChange = {
                        displayPreferences.showGroupTotals = it
                        showGroupTotals = it
                    },
                    hideFullySpent = hideFullySpentCategories,
                    onHideFullySpentChange = {
                        displayPreferences.hideFullySpentCategories = it
                        hideFullySpentCategories = it
                    },
                    onSetCategoryHidden = { group, category, hidden ->
                        mutate(if (hidden) "Hiding category" else "Showing category") {
                            repository.setCategoryHidden(group, category, hidden)
                        }
                    },
                    onSetGroupHidden = { group, hidden ->
                        mutate(if (hidden) "Hiding group" else "Showing group") {
                            repository.setCategoryGroupHidden(group, hidden)
                        }
                    },
                    onRenameCategory = { group, category, name ->
                        mutate("Renaming category") { repository.renameCategory(group, category, name) }
                    },
                    onRenameGroup = { group, name ->
                        mutate("Renaming group") { repository.renameCategoryGroup(group, name) }
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
                        mutate("Moving budget") {
                            repository.transferBudget(fromGroup, fromCategory, toGroup, toCategory, amount, budgetMonth)
                        }
                    },
                    onCreateCategory = { group, name ->
                        mutate("Creating category") { repository.createCategory(group, name) }
                    },
                    onCreateGroup = { name ->
                        mutate("Creating group") { repository.createCategoryGroup(name) }
                    },
                    onSetBudgetAmount = { group, category, amount ->
                        mutate("Updating budget") { repository.setBudgetAmount(group, category, amount, budgetMonth) }
                    },
                    onSetCategoryNote = { categoryId, note ->
                        mutate("Saving category note") { repository.setCategoryNote(categoryId, note) }
                    },
                    onSetCategoryCarryover = { categoryId, enabled ->
                        mutate("Updating rollover") { repository.setCategoryCarryover(categoryId, enabled, budgetMonth) }
                    },
                    onSetCategoryTarget = { categoryId, target ->
                        mutate(if (target == null) "Removing target" else "Saving target") {
                            repository.setCategoryTarget(categoryId, target)
                        }
                    },
                    onSearch = { detail = DetailDestination.Search },
                    transactions = filteredTransactions,
                    onDeleteCategory = { group, category ->
                        mutate("Deleting category") { repository.deleteCategory(group, category) }
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
                        mutate("Deleting transaction") { repository.deleteTransaction(transaction.id) }
                    },
                    requestedCategoryDetails = reopenBudgetCategory,
                    onCategoryDetailsChange = { category ->
                        activeBudgetCategory = category
                        if (category == reopenBudgetCategory) reopenBudgetCategory = null
                    },
                    returnToRootRequest = rootRequests[MainDestination.Budget] ?: 0,
                )
                MainDestination.Accounts -> AccountsScreen(
                    modifier = contentModifier,
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
                    onCloseAccount = { account ->
                        mutate(if (account.closed) "Reopening account" else "Closing account") {
                            repository.setAccountClosed(account.name, !account.closed)
                        }
                    },
                    onRenameAccount = { account, name ->
                        mutate("Renaming account") { repository.renameAccount(account.name, name) }
                    },
                    onCreateAccount = { name, offBudget, balance ->
                        mutate("Creating account") { repository.createAccount(name, offBudget, balance) }
                    },
                    onSearch = { detail = DetailDestination.Search },
                    scrollToTopRequest = rootRequests[MainDestination.Accounts] ?: 0,
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
                    hideDecimalPlaces = hideDecimalPlaces,
                    conventionalAmountEntry = conventionalAmountEntry,
                    groupTransactionsByDate = groupTransactionsByDate,
                    onGroupTransactionsByDateChange = {
                        displayPreferences.groupTransactionsByDate = it
                        groupTransactionsByDate = it
                    },
                    hideReconciledTransactions = hideReconciledTransactions,
                    onHideReconciledTransactionsChange = {
                        displayPreferences.hideReconciledTransactions = it
                        hideReconciledTransactions = it
                    },
                    onSetCleared = { transaction, cleared ->
                        mutate("Updating transaction") { repository.setTransactionCleared(transaction.id, cleared) }
                    },
                    onReconcileAccount = { account ->
                        mutate("Reconciling account") { repository.reconcileAccount(account.id) }
                    },
                    onCreateReconciliationAdjustment = { account, difference ->
                        mutate("Creating reconciliation adjustment") {
                            repository.createReconciliationAdjustment(account.id, difference)
                        }
                    },
                    onDelete = { transaction ->
                        mutate("Deleting transaction") { repository.deleteTransaction(transaction.id) }
                    },
                    showBackButton = false,
                    returnToRootRequest = rootRequests[MainDestination.Transactions] ?: 0,
                )
                MainDestination.Reports -> ReportsScreen(reportSnapshot, hideDecimalPlaces, contentModifier,
                    onSearch = { detail = DetailDestination.Search },
                    scrollToTopRequest = rootRequests[MainDestination.Reports] ?: 0)
                MainDestination.More -> SettingsScreen(
                    modifier = contentModifier,
                    onConnectionClick = { detail = DetailDestination.Connection },
                    hideDecimalPlaces = hideDecimalPlaces,
                    onHideDecimalPlacesChange = {
                        displayPreferences.hideDecimalPlaces = it
                        hideDecimalPlaces = it
                        WidgetUpdater.requestAll(context)
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
                    hideBalances = hideBalances,
                    onHideBalancesChange = {
                        displayPreferences.hideBalances = it
                        hideBalances = it
                        WidgetUpdater.requestAll(context)
                    },
                    appearance = appearance,
                    onAppearanceChange = {
                        displayPreferences.appearance = it
                        appearance = it
                        onAppearanceChange(it)
                    },
                    startPage = startPage,
                    onStartPageChange = {
                        displayPreferences.startPage = it
                        startPage = it
                    },
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
                    onCreditCardsClick = {
                        creditCardsReturnToBills = false
                        detail = DetailDestination.CreditCards
                    },
                    onRulesClick = { detail = DetailDestination.Rules },
                    onSchedulesClick = { detail = DetailDestination.Schedules },
                    conventionalAmountEntry = conventionalAmountEntry,
                    onConventionalAmountEntryChange = {
                        displayPreferences.conventionalAmountEntry = it
                        conventionalAmountEntry = it
                    },
                    showBottomNavigationLabels = showBottomNavigationLabels,
                    onShowBottomNavigationLabelsChange = {
                        displayPreferences.showBottomNavigationLabels = it
                        showBottomNavigationLabels = it
                    },
                    showCurrentBalanceSummary = showCurrentBalanceSummary,
                    onShowCurrentBalanceSummaryChange = {
                        displayPreferences.showCurrentBalanceSummary = it
                        showCurrentBalanceSummary = it
                    },
                    returnToRootRequest = rootRequests[MainDestination.More] ?: 0,
                )
            }
        }
        }
        }
    }
}

@Composable
private fun NoBudgetScreen(modifier: Modifier, onConnect: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No budget open", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text(
            "Connect to your Actual server and download a budget to begin.",
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onConnect) { Text("Connect to Actual") }
    }
}
