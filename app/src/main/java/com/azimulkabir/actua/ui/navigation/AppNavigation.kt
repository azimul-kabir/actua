package com.azimulkabir.actua.ui.navigation

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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
import com.azimulkabir.actua.ui.transactions.AddTransactionScreen
import com.azimulkabir.actua.ui.transactions.TransactionsScreen
import com.azimulkabir.actua.ui.reports.ReportsScreen
import com.azimulkabir.actua.ui.search.GlobalSearchScreen
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import com.azimulkabir.actua.data.sync.SyncRunResult
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.ui.components.BalanceVisibility
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.formatMoneyCents

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

private enum class DetailDestination { Main, Transactions, EditTransaction, Search, Connection, CreditCards, Rules }

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    foregroundGeneration: Int = 0,
    onAppearanceChange: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val displayPreferences = remember { DisplayPreferences(context) }
    var repositoryVersion by remember { mutableStateOf(0) }
    val repository = remember(repositoryVersion) { ActuaRepository(context) }
    var dataVersion by remember { mutableStateOf(0) }
    var budgetMonth by rememberSaveable {
        mutableStateOf(java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date()))
    }
    val budgetGroups = remember(dataVersion, budgetMonth) { repository.budgetGroups(budgetMonth) }
    val budgetOverview = remember(dataVersion, budgetMonth) { repository.budgetOverview(budgetMonth) }
    val accounts = remember(dataVersion) { repository.accounts() }
    val transactions = remember(dataVersion) { repository.transactions() }
    val categoryNames = remember(dataVersion) { repository.categoryNames() }
    val payeeNames = remember(dataVersion) { repository.payeeNames() }
    val reportSnapshot = remember(dataVersion) { repository.reports() }
    val creditCards = remember(dataVersion) { repository.creditCards() }
    val rules = remember(dataVersion) { repository.rules() }
    val rulesSupported = remember(dataVersion) { repository.rulesSupported() }
    val scheduleOwnedRuleIds = remember(dataVersion) { repository.scheduleOwnedRuleIds() }
    val ruleEditorData = remember(dataVersion) { repository.ruleEditorData() }
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
    var editorReturnsToTransactions by rememberSaveable { mutableStateOf(false) }
    var addOrigin by rememberSaveable { mutableStateOf(MainDestination.Accounts) }
    var transactionFabExpanded by rememberSaveable { mutableStateOf(true) }
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
        editorReturnsToTransactions = false
        detail = DetailDestination.EditTransaction
    }

    fun openAddTransactionForAccount() {
        editingTransaction = null
        editorReturnsToTransactions = true
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
        if (result is SyncRunResult.Success) dataVersion += 1
    }

    BackHandler(enabled = detail != DetailDestination.Main || destination != MainDestination.Budget) {
        when {
            detail == DetailDestination.EditTransaction && editorReturnsToTransactions -> {
                detail = DetailDestination.Transactions
                editingTransaction = null
            }
            detail != DetailDestination.Main -> {
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
            if (repository.isUsingActualBudget && (onMainTab || inAccount)) {
                ExtendedFloatingActionButton(
                    onClick = if (inAccount) ::openAddTransactionForAccount else ::openAddTransaction,
                    expanded = transactionFabExpanded,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Transaction") },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item && detail == DetailDestination.Main,
                        onClick = {
                            if (item != MainDestination.More && !repository.isUsingActualBudget) {
                                destination = MainDestination.More
                                detail = DetailDestination.Connection
                                return@NavigationBarItem
                            }
                            destination = item
                            detail = DetailDestination.Main
                            transactionFabExpanded = true
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
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
        when (shownDetail) {
            DetailDestination.Transactions -> TransactionsScreen(
                accountName = transactionAccount,
                categoryName = transactionCategory,
                month = transactionMonth,
                onBack = { detail = DetailDestination.Main },
                onEdit = {
                    editingTransaction = it
                    editorReturnsToTransactions = true
                    detail = DetailDestination.EditTransaction
                },
                modifier = contentModifier,
                transactions = transactions,
                hideDecimalPlaces = hideDecimalPlaces,
                groupTransactionsByDate = groupTransactionsByDate,
                onGroupTransactionsByDateChange = {
                    displayPreferences.groupTransactionsByDate = it
                    groupTransactionsByDate = it
                },
                onSetCleared = { transaction, cleared ->
                    mutate("Updating transaction") { repository.setTransactionCleared(transaction.id, cleared) }
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
            )
            DetailDestination.EditTransaction -> AddTransactionScreen(
                editing = editingTransaction,
                onBack = {
                    detail = if (editorReturnsToTransactions) DetailDestination.Transactions else DetailDestination.Main
                    editingTransaction = null
                    if (!editorReturnsToTransactions) destination = addOrigin
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
                        editingTransaction = null
                        if (editorReturnsToTransactions) {
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
                    hideDecimalPlaces = hideDecimalPlaces,
                    conventionalAmountEntry = conventionalAmountEntry,
                    onResolveRuleCategory = repository::ruleCategoryFor,
            )
            DetailDestination.Search -> GlobalSearchScreen(
                transactions = transactions,
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
                },
                modifier = contentModifier,
            )
            DetailDestination.CreditCards -> CreditCardsScreen(
                cards = creditCards,
                accounts = accounts,
                hideDecimalPlaces = hideDecimalPlaces,
                onBack = { detail = DetailDestination.Main },
                onSave = { accountId, day, offset, limit ->
                    mutate("Saving credit card") { repository.setCreditCard(accountId, day, offset, limit) }
                },
                onRemove = { accountId ->
                    mutate("Removing credit card") { repository.setCreditCard(accountId, null) }
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
                    onShowCategoryTransactions = { category, thisMonth ->
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
                    onSearch = { detail = DetailDestination.Search },
                    transactions = transactions,
                    onDeleteCategory = { group, category ->
                        mutate("Deleting category") { repository.deleteCategory(group, category) }
                    },
                    onEditTransaction = { transaction ->
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
                )
                MainDestination.Accounts -> AccountsScreen(
                    modifier = contentModifier,
                    accounts = accounts,
                    transactions = transactions,
                    hideDecimalPlaces = hideDecimalPlaces,
                    showMonthlySummary = showAccountsMonthlySummary,
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
                    transactions = transactions,
                    hideDecimalPlaces = hideDecimalPlaces,
                    groupTransactionsByDate = groupTransactionsByDate,
                    onGroupTransactionsByDateChange = {
                        displayPreferences.groupTransactionsByDate = it
                        groupTransactionsByDate = it
                    },
                    onSetCleared = { transaction, cleared ->
                        mutate("Updating transaction") { repository.setTransactionCleared(transaction.id, cleared) }
                    },
                    onDelete = { transaction ->
                        mutate("Deleting transaction") { repository.deleteTransaction(transaction.id) }
                    },
                    showBackButton = false,
                )
                MainDestination.Reports -> ReportsScreen(reportSnapshot, hideDecimalPlaces, contentModifier,
                    onSearch = { detail = DetailDestination.Search })
                MainDestination.More -> SettingsScreen(
                    modifier = contentModifier,
                    onConnectionClick = { detail = DetailDestination.Connection },
                    hideDecimalPlaces = hideDecimalPlaces,
                    onHideDecimalPlacesChange = {
                        displayPreferences.hideDecimalPlaces = it
                        hideDecimalPlaces = it
                    },
                    currencyCode = currencyCode,
                    onCurrencyCodeChange = {
                        displayPreferences.currencyCode = it
                        currencyCode = it
                    },
                    currencySymbolOnly = currencySymbolOnly,
                    onCurrencySymbolOnlyChange = {
                        displayPreferences.currencySymbolOnly = it
                        currencySymbolOnly = it
                    },
                    hideBalances = hideBalances,
                    onHideBalancesChange = {
                        displayPreferences.hideBalances = it
                        hideBalances = it
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
                    onCreditCardsClick = { detail = DetailDestination.CreditCards },
                    onRulesClick = { detail = DetailDestination.Rules },
                    conventionalAmountEntry = conventionalAmountEntry,
                    onConventionalAmountEntryChange = {
                        displayPreferences.conventionalAmountEntry = it
                        conventionalAmountEntry = it
                    },
                )
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
