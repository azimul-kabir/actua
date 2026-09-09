package com.azimulkabir.actua.ui.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.ui.components.RenameDialog
import com.azimulkabir.actua.ui.components.NewAccountDialog
import com.azimulkabir.actua.ui.components.formatMoneyCents
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue

private data class AccountSection(val title: String, val accounts: List<Account>)

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
    onCloseAccount: (Account) -> Unit = {},
    onRenameAccount: (Account, String) -> Unit = { _, _ -> },
    onCreateAccount: (String, Boolean, String) -> Unit = { _, _, _ -> },
    onSearch: () -> Unit = {},
    scrollToTopRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
    var collapsedSections by remember { mutableStateOf(setOf("Closed accounts")) }
    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var renamingAccount by remember { mutableStateOf<Account?>(null) }
    val accountSections = listOf(
        AccountSection("On budget", accounts.filter { !it.offBudget && !it.closed }),
        AccountSection("Off budget", accounts.filter { it.offBudget && !it.closed }),
        AccountSection("Closed accounts", accounts.filter { it.closed }),
    ).filter { it.accounts.isNotEmpty() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 10.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Accounts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(28.dp),
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
                                    collapsedSections = emptySet()
                                    accountMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Collapse all") },
                                onClick = {
                                    collapsedSections = accountSections.mapTo(mutableSetOf()) { it.title }
                                    accountMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
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
                            collapsedSections = if (collapsed) collapsedSections - section.title
                            else collapsedSections + section.title
                        },
                    )
                }
                itemsIndexed(section.accounts, key = { _, account -> "${section.title}-${account.name}" }) { index, account ->
                    AnimatedVisibility(
                        visible = !collapsed,
                        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 3 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 3 },
                    ) {
                        AccountRow(
                            account = account,
                            creditCard = creditCards.firstOrNull { it.accountId == account.id },
                            showTopDivider = index > 0,
                            onClick = { onAccountClick(account.name) },
                        onLongClick = { selectedAccount = account },
                            hideDecimalPlaces = hideDecimalPlaces,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    selectedAccount?.let { account ->
        AccountActionsSheet(
            account = account,
            onDismiss = { selectedAccount = null },
            onViewTransactions = { selectedAccount = null; onAccountClick(account.name) },
            onRename = { selectedAccount = null; renamingAccount = account },
            onClose = { selectedAccount = null; onCloseAccount(account) },
        )
    }
    if (showAddSheet) NewAccountDialog(onDismiss = { showAddSheet = false }) { name, offBudget, balance ->
        onCreateAccount(name, offBudget, balance); showAddSheet = false
    }
    renamingAccount?.let { account -> RenameDialog("Rename account", account.name,
        onDismiss = { renamingAccount = null }, onSave = { name -> onRenameAccount(account, name); renamingAccount = null }) }
}

@Composable
private fun AccountsSummary(
    accounts: List<Account>,
    transactions: List<Transaction>,
    onClick: () -> Unit,
    hideDecimalPlaces: Boolean,
    showMonthlySummary: Boolean,
) {
    val total = accounts.sumOf { it.balanceCents }
    val monthKey = java.text.SimpleDateFormat("yyyyMM", java.util.Locale.US).format(java.util.Date())
    val monthTransactions = transactions.filter { it.date.filter(Char::isDigit).startsWith(monthKey) }
    val income = monthTransactions.filter { it.amountCents > 0 }.sumOf { it.amountCents }
    val expenses = monthTransactions.filter { it.amountCents < 0 }.sumOf { it.amountCents }
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = {}),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("All accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                BalanceText(total, FontWeight.Bold, hideDecimalPlaces)
                Icon(Icons.Outlined.ChevronRight, contentDescription = "View all transactions")
            }
            if (showMonthlySummary) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                Text(java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date()), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryStat("Income", income, hideDecimalPlaces = hideDecimalPlaces)
                    SummaryStat("Expenses", expenses, Alignment.CenterHorizontally, hideDecimalPlaces)
                    SummaryStat("Net", income + expenses, Alignment.End, hideDecimalPlaces,
                        Modifier.padding(end = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, amount: Long, alignment: Alignment.Horizontal = Alignment.Start,
    hideDecimalPlaces: Boolean, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = alignment, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        BalanceText(amount, FontWeight.SemiBold, hideDecimalPlaces)
    }
}

@Composable
private fun AccountSectionHeader(section: AccountSection, collapsed: Boolean,
    hideDecimalPlaces: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, tween(220), label = "account section")
    val total = section.accounts.sumOf { it.balanceCents }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                role = Role.Button, onClick = onClick, onLongClick = {},
            ).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (collapsed) "Expand ${section.title}" else "Collapse ${section.title}",
                modifier = Modifier.rotate(rotation))
            Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            BalanceText(total, FontWeight.SemiBold, hideDecimalPlaces)
            // Match the space occupied by the account-row disclosure chevron.
            Spacer(Modifier.width(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(
    account: Account,
    creditCard: CreditCardStatus?,
    showTopDivider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    hideDecimalPlaces: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        if (showTopDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 24.dp, end = 12.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = 24.dp, top = 15.dp, end = 12.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.bodyMedium)
                Text(creditCard?.let { "${it.cycle.dueShortSummary()} · Spend ${formatMoneyCents(it.cycleSpendCents, hideDecimalPlaces)}" }
                    ?: account.type, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BalanceText(account.balanceCents, FontWeight.SemiBold, hideDecimalPlaces)
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Open ${account.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BalanceText(amount: Long, weight: FontWeight, hideDecimalPlaces: Boolean) {
    Text(formatMoneyCents(amount, hideDecimalPlaces), style = MaterialTheme.typography.bodyMedium, fontWeight = weight, color = when {
        amount > 0 -> MaterialTheme.colorScheme.primary
        amount < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountActionsSheet(
    account: Account,
    onDismiss: () -> Unit,
    onViewTransactions: () -> Unit,
    onRename: () -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(account.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            AccountSheetAction("View transactions", onViewTransactions)
            AccountSheetAction("Rename account", onRename)
            AccountSheetAction(if (account.closed) "Reopen account" else "Close account", onClose, destructive = !account.closed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Text("Add account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            AccountSheetAction("Bank account", onDismiss)
            AccountSheetAction("Cash account", onDismiss)
            AccountSheetAction("Credit card", onDismiss)
            AccountSheetAction("Savings account", onDismiss)
            AccountSheetAction("Off-budget account", onDismiss)
        }
    }
}

@Composable
private fun AccountSheetAction(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    DropdownMenuItem(
        text = { Text(label, color = if (destructive) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface) },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}
