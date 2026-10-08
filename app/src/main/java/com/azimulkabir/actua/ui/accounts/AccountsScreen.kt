package com.azimulkabir.actua.ui.accounts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaGroupedItem
import com.azimulkabir.actua.ui.components.ActuaHeroAmount
import com.azimulkabir.actua.ui.components.ActuaHeroSize
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSheetAction
import com.azimulkabir.actua.ui.components.ActuaSheetCard
import com.azimulkabir.actua.ui.components.ActuaSheetContent
import com.azimulkabir.actua.ui.components.ActuaSheetTitle
import com.azimulkabir.actua.ui.components.ChangeAccountTypeDialog
import com.azimulkabir.actua.ui.components.GroupPosition
import com.azimulkabir.actua.ui.components.MonetaryText
import com.azimulkabir.actua.ui.components.NewAccountDialog
import com.azimulkabir.actua.ui.components.RenameDialog
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.Sizes
import com.azimulkabir.actua.ui.theme.Spacing

private data class AccountSection(val title: String, val accounts: List<Account>)

/** One named sub-bucket of an [AccountSection], derived from Actual's experimental account groups. `groupName` is null for the ungrouped bucket. */
internal data class AccountGroupChunk(val groupName: String?, val accounts: List<Account>) {
    /** The group's balance as Actual's sidebar shows it, over the accounts listed in this chunk. */
    val totalCents: Long get() = accounts.sumOf { it.balanceCents }
}

/**
 * Splits a section's accounts into group chunks, preserving each account's existing sort order.
 * Returns a single ungrouped chunk untouched when none of the accounts have a group assigned,
 * so users who have not enabled Actual's account grouping see no behavior change.
 */
internal fun List<Account>.chunkedByGroup(): List<AccountGroupChunk> {
    if (none { it.groupId != null }) return listOf(AccountGroupChunk(null, this))
    val grouped = groupBy { it.groupId }
    val orderedGroupIds = grouped.keys.filterNotNull()
        .sortedWith(compareBy({ grouped[it]!!.first().groupSortOrder }, { it }))
    val chunks = orderedGroupIds.map { groupId ->
        val accountsInGroup = grouped.getValue(groupId)
        AccountGroupChunk(accountsInGroup.first().groupName, accountsInGroup)
    }
    val ungrouped = grouped[null].orEmpty()
    return if (ungrouped.isEmpty()) chunks else chunks + AccountGroupChunk(null, ungrouped)
}

private val sampleAccountSections = listOf(
    AccountSection("On budget", listOf(
        Account("Everyday account", 48_250, "Bank"),
        Account("Cash", 3_400, "Cash"),
        Account("Savings", 86_500, "Savings"),
        Account("Credit card", -12_780, "Credit"),
    )),
    AccountSection("Off budget", listOf(
        Account("Investment account", 125_000, "Investment"),
        Account("Motorbike loan", -65_000, "Loan"),
    )),
    AccountSection("Closed accounts", listOf(
        Account("Old bank account", 0, "Bank"),
    )),
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    accounts: List<Account> = sampleAccountSections.flatMap { it.accounts },
    transactions: List<Transaction> = emptyList(),
    hideDecimalPlaces: Boolean = false,
    showMonthlySummary: Boolean = true,
    onShowMonthlySummaryChange: (Boolean) -> Unit = {},
    creditCards: List<CreditCardStatus> = emptyList(),
    onAccountClick: (String) -> Unit = {},
    onAllAccountsClick: () -> Unit = {},
    loadCloseOptions: suspend (Account) -> CloseAccountOptions = { CloseAccountOptions(true, emptyList()) },
    onCloseAccount: (Account, transferAccountId: String?, categoryId: String?, forced: Boolean) -> Unit = { _, _, _, _ -> },
    onReopenAccount: (Account) -> Unit = {},
    onRenameAccount: (Account, String) -> Unit = { _, _ -> },
    onChangeAccountType: (Account, String) -> Unit = { _, _ -> },
    onCreateAccount: (String, Boolean, String, String) -> Unit = { _, _, _, _ -> },
    onReorderAccounts: () -> Unit = {},
    onSearch: () -> Unit = {},
    onSetUpBankSync: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    favoriteAccountIds: Set<String> = emptySet(),
    onFavoriteAccountChange: (String, Boolean) -> Unit = { _, _ -> },
    scrollToTopRequest: Int = 0,
    hasFab: Boolean = true,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
    val context = LocalContext.current
    val accountsUiPreferences = remember(context) {
        context.applicationContext.getSharedPreferences("budget_ui_preferences", android.content.Context.MODE_PRIVATE)
    }
    val activeBudgetId = remember(context) { ActiveBudgetStore(context).budgetId ?: "no-budget" }
    val collapsedSectionsKey = "collapsed_account_sections_$activeBudgetId"
    var collapsedSections by remember(activeBudgetId) {
        mutableStateOf(
            accountsUiPreferences.getStringSet(collapsedSectionsKey, setOf("Closed accounts")).orEmpty().toSet(),
        )
    }
    fun saveCollapsedSections(value: Set<String>) {
        collapsedSections = value
        accountsUiPreferences.edit().putStringSet(collapsedSectionsKey, value).apply()
    }
    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showNewAccountDialog by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var renamingAccount by remember { mutableStateOf<Account?>(null) }
    var closingAccount by remember { mutableStateOf<Account?>(null) }
    var changingTypeAccount by remember { mutableStateOf<Account?>(null) }
    // Otherwise this re-filters the whole account list on every recomposition of this screen
    // (e.g. opening the overflow menu or selecting an account), not just when `accounts` changes.
    val accountSections = remember(accounts, favoriteAccountIds) {
        listOf(
            AccountSection("Favorites", accounts.filter { it.id in favoriteAccountIds && !it.closed }),
            AccountSection("On budget", accounts.filter { !it.offBudget && !it.closed }),
            AccountSection("Off budget", accounts.filter { it.offBudget && !it.closed }),
            AccountSection("Closed accounts", accounts.filter { it.closed }),
        ).filter { it.accounts.isNotEmpty() }
    }
    val sectionGroupChunks = remember(accountSections) {
        accountSections.associate { it.title to it.accounts.chunkedByGroup() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Accounts") {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search Actua")
                    }
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add account")
                    }
                    IconButton(onClick = onReorderAccounts) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = "Reorder accounts")
                    }
                    Box {
                        IconButton(onClick = { accountMenuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Account display options")
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Monthly summary") },
                                trailingIcon = {
                                    Switch(
                                        checked = showMonthlySummary,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = {
                                    onShowMonthlySummaryChange(!showMonthlySummary)
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Expand all") },
                                onClick = {
                                    saveCollapsedSections(emptySet())
                                    accountMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Collapse all") },
                                onClick = {
                                    saveCollapsedSections(accountSections.mapTo(mutableSetOf()) { it.title })
                                    accountMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // Otherwise this is a linear scan repeated per visible row, per recomposition
        // (O(accounts x creditCards) overall), instead of a single O(creditCards) pass.
        val creditCardByAccountId = remember(creditCards) { creditCards.associateBy { it.accountId } }

        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = if (hasFab) 96.dp else 0.dp),
            ) {
                item { AccountsSummary(accounts, transactions, onAllAccountsClick, hideDecimalPlaces, showMonthlySummary) }
                accountSections.forEach { section ->
                    val collapsed = section.title in collapsedSections
                    stickyHeader(key = "account-header-${section.title}") {
                        AccountSectionHeader(
                            section = section,
                            collapsed = collapsed,
                            hideDecimalPlaces = hideDecimalPlaces,
                            onClick = {
                                saveCollapsedSections(
                                    if (collapsed) collapsedSections - section.title
                                    else collapsedSections + section.title,
                                )
                            },
                        )
                    }
                    val groupChunks = sectionGroupChunks[section.title].orEmpty()
                    groupChunks.forEach { chunk ->
                        if (chunk.groupName != null) {
                            item(key = "account-group-${section.title}-${chunk.groupName}") {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !collapsed,
                                    enter = fadeIn(tween(180)),
                                    exit = fadeOut(tween(120)),
                                ) {
                                    AccountGroupHeader(chunk.groupName, chunk.totalCents, hideDecimalPlaces)
                                }
                            }
                        }
                        itemsIndexed(chunk.accounts, key = { _, account -> "${section.title}-${account.id}" }) { index, account ->
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !collapsed,
                                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
                                exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
                            ) {
                                AccountRow(
                                    account = account,
                                    creditCard = creditCardByAccountId[account.id],
                                    position = GroupPosition.of(index, chunk.accounts.size),
                                    onClick = { onAccountClick(account.name) },
                                onLongClick = { selectedAccount = account },
                                    hideDecimalPlaces = hideDecimalPlaces,
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    selectedAccount?.let { account ->
        AccountActionsSheet(
            account = account,
            onDismiss = { selectedAccount = null },
            onViewTransactions = { selectedAccount = null; onAccountClick(account.name) },
            onRename = { selectedAccount = null; renamingAccount = account },
            onChangeType = { selectedAccount = null; changingTypeAccount = account },
            onClose = {
                selectedAccount = null
                if (account.closed) onReopenAccount(account) else closingAccount = account
            },
            favorite = account.id in favoriteAccountIds,
            onFavoriteChange = { onFavoriteAccountChange(account.id, it) },
        )
    }
    closingAccount?.let { account ->
        CloseAccountSheet(
            account = account,
            accounts = accounts,
            hideDecimalPlaces = hideDecimalPlaces,
            loadOptions = loadCloseOptions,
            onDismiss = { closingAccount = null },
            onClose = { transferAccountId, categoryId ->
                closingAccount = null; onCloseAccount(account, transferAccountId, categoryId, false)
            },
            onForceClose = { closingAccount = null; onCloseAccount(account, null, null, true) },
        )
    }
    if (showAddSheet) AddAccountSheet(
        onDismiss = { showAddSheet = false },
        onCreateLocal = { showAddSheet = false; showNewAccountDialog = true },
        onSetUpBankSync = { showAddSheet = false; onSetUpBankSync() },
    )
    if (showNewAccountDialog) NewAccountDialog(onDismiss = { showNewAccountDialog = false }) { name, offBudget, balance, type ->
        onCreateAccount(name, offBudget, balance, type); showNewAccountDialog = false
    }
    renamingAccount?.let { account -> RenameDialog("Rename account", account.name,
        onDismiss = { renamingAccount = null }, onSave = { name -> onRenameAccount(account, name); renamingAccount = null }) }
    changingTypeAccount?.let { account -> ChangeAccountTypeDialog(account.name, account.type,
        onDismiss = { changingTypeAccount = null },
        onSave = { type -> onChangeAccountType(account, type); changingTypeAccount = null }) }
}

@Composable
private fun AccountsSummary(
    accounts: List<Account>,
    transactions: List<Transaction>,
    onClick: () -> Unit,
    hideDecimalPlaces: Boolean,
    showMonthlySummary: Boolean,
) {
    val locale = LocalConfiguration.current.locales[0]
    val total = accounts.sumOf { it.balanceCents }
    // Skip the full-transaction-list scan and calculator entirely when the summary isn't shown,
    // and otherwise only recompute it when `transactions` actually changes, not on every
    // recomposition of this row (e.g. opening the overflow menu elsewhere on the screen).
    val summary = if (showMonthlySummary) {
        remember(transactions, accounts) {
            val monthKey = java.text.SimpleDateFormat("yyyyMM", java.util.Locale.US).format(java.util.Date())
            val monthTransactions = transactions.filter { it.date.filter(Char::isDigit).startsWith(monthKey) }
            val offBudgetAccountNames = accounts.filter { it.offBudget }.map { it.name }.toSet()
            AccountMonthlySummaryCalculator.calculate(monthTransactions, offBudgetAccountNames)
        }
    } else null
    val totalText = formatMoneyCents(total, hideDecimalPlaces)
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        ActuaHeroAmount(
            amount = totalText,
            caption = "All accounts",
            captionColor = MaterialTheme.colorScheme.onSurfaceVariant,
            amountColor = amountColor(total),
            size = ActuaHeroSize.Medium,
            contentDescription = "All accounts, $totalText",
            onClickLabel = "View all transactions",
            onClick = onClick,
        )
        if (summary != null) {
            ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                    Text(
                        java.text.SimpleDateFormat("MMMM yyyy", locale).format(java.util.Date()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SummaryStat("Income", summary.incomeCents, hideDecimalPlaces = hideDecimalPlaces)
                        SummaryStat("Expenses", summary.expenseCents, Alignment.CenterHorizontally, hideDecimalPlaces)
                        SummaryStat("Net", summary.netCents, Alignment.End, hideDecimalPlaces)
                    }
                }
            }
        }
    }
}

@Composable
private fun amountColor(cents: Long) = when {
    cents > 0 -> MaterialTheme.colorScheme.primary
    cents < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun SummaryStat(label: String, amount: Long, alignment: Alignment.Horizontal = Alignment.Start,
    hideDecimalPlaces: Boolean, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = alignment, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        MonetaryText(amount, hideDecimalPlaces)
    }
}

/** Sticky section label with its total; it keeps the page background so rows don't show through. */
@Composable
private fun AccountSectionHeader(section: AccountSection, collapsed: Boolean,
    hideDecimalPlaces: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, tween(220), label = "account section")
    val total = section.accounts.sumOf { it.balanceCents }
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                role = Role.Button, onClick = onClick, onLongClick = {},
            ).padding(
                start = Spacing.screenHorizontal + Spacing.xs,
                end = Spacing.screenHorizontal,
                top = Spacing.lg,
                bottom = Spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(section.title, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            MonetaryText(total, hideDecimalPlaces)
            Icon(Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (collapsed) "Expand ${section.title}" else "Collapse ${section.title}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.xs).rotate(rotation))
        }
    }
}

/** A lightweight, non-collapsible label and total for an Actual account group nested inside an on/off-budget section. */
@Composable
private fun AccountGroupHeader(groupName: String, totalCents: Long, hideDecimalPlaces: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = Spacing.screenHorizontal + Spacing.xs,
            end = Spacing.screenHorizontal,
            top = Spacing.md,
            bottom = Spacing.xs,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            groupName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MonetaryText(
            totalCents,
            hideDecimalPlaces,
            modifier = Modifier.padding(start = Spacing.sm),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Display-only icon for an account row, from its credit-card status or free-text Actual account type. */
internal fun accountTypeIcon(type: String, isCreditCard: Boolean): ImageVector {
    val normalized = type.lowercase()
    return when {
        isCreditCard || "credit" in normalized -> Icons.Outlined.CreditCard
        "saving" in normalized -> Icons.Outlined.Savings
        "invest" in normalized -> Icons.AutoMirrored.Outlined.TrendingUp
        "mortgage" in normalized -> Icons.Outlined.Home
        "debt" in normalized || "loan" in normalized -> Icons.AutoMirrored.Outlined.ReceiptLong
        "cash" in normalized -> Icons.Outlined.Payments
        else -> Icons.Outlined.AccountBalance
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(
    account: Account,
    creditCard: CreditCardStatus?,
    position: GroupPosition,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    hideDecimalPlaces: Boolean,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ActuaGroupedItem(position = position) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = Sizes.compactRowMinHeight)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(accountTypeIcon(account.type, creditCard != null), contentDescription = null, tint = muted)
            Spacer(Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                Text(creditCard?.let { "${it.cycle.dueShortSummary(dueDate = it.pendingStatementDue()?.dueDate)} · Spend ${formatMoneyCents(it.cycleSpendCents, hideDecimalPlaces)}" }
                    ?: account.type, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            MonetaryText(account.balanceCents, hideDecimalPlaces, modifier = Modifier.padding(start = Spacing.sm))
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Open ${account.name}", tint = muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountActionsSheet(
    account: Account,
    onDismiss: () -> Unit,
    onViewTransactions: () -> Unit,
    onRename: () -> Unit,
    onChangeType: () -> Unit,
    onClose: () -> Unit,
    favorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle(account.name)
            ActuaSheetCard {
                AccountSheetAction(
                    if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    if (favorite) "Remove from favorites" else "Add to favorites",
                    onClick = { onFavoriteChange(!favorite) },
                )
                ActuaCardDivider()
                AccountSheetAction(Icons.AutoMirrored.Outlined.ReceiptLong, "View transactions", onViewTransactions)
                ActuaCardDivider()
                AccountSheetAction(Icons.Outlined.Edit, "Rename account", onRename)
                ActuaCardDivider()
                AccountSheetAction(Icons.Outlined.Category, "Change account type", onChangeType)
            }
            ActuaSheetCard {
                AccountSheetAction(
                    if (account.closed) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    if (account.closed) "Reopen account" else "Close account",
                    onClose,
                    destructive = !account.closed,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(onDismiss: () -> Unit, onCreateLocal: () -> Unit, onSetUpBankSync: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle("Add account")
            ActuaSheetCard {
                AccountSheetAction(Icons.Outlined.AccountBalanceWallet, "Create a local account", onCreateLocal)
                ActuaCardDivider()
                AccountSheetAction(Icons.Outlined.Sync, "Set up bank sync", onSetUpBankSync)
            }
        }
    }
}

/** An icon action row in a sheet card; [destructive] actions use the error color. */
@Composable
private fun AccountSheetAction(icon: ImageVector, label: String, onClick: () -> Unit, destructive: Boolean = false) {
    ActuaSheetAction(label, onClick = onClick, icon = icon, destructive = destructive)
}
