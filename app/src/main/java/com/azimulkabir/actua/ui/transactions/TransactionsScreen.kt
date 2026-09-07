package com.azimulkabir.actua.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.components.formatStoredDate
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue

private val sampleTransactions = listOf(
    Transaction("1", "Today", "Agora Super Shop", "Groceries", "Everyday account", -2_450, true),
    Transaction("2", "Today", "Salary", "Income", "Everyday account", 72_000, true),
    Transaction("3", "Today", "Pathao", "Transport", "Credit card", -380, false),
    Transaction("4", "Yesterday", "DESCO", "Electricity", "Everyday account", -2_700, true),
    Transaction("5", "Yesterday", "Coffee World", "Dining", "Credit card", -620, false),
    Transaction("6", "1 Sep 2026", "Landlord", "Rent", "Everyday account", -35_000, true),
    Transaction("7", "1 Sep 2026", "ISP", "Internet", "Everyday account", -1_500, true),
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    accountName: String?,
    categoryName: String? = null,
    month: String? = null,
    onBack: () -> Unit,
    onEdit: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
    transactions: List<Transaction> = sampleTransactions,
    hideDecimalPlaces: Boolean = false,
    groupTransactionsByDate: Boolean = true,
    onGroupTransactionsByDateChange: (Boolean) -> Unit = {},
    onSetCleared: (Transaction, Boolean) -> Unit = { _, _ -> },
    onDelete: (Transaction) -> Unit = {},
    account: Account? = null,
    creditCard: CreditCardStatus? = null,
    onSaveAccountNote: (String) -> Unit = {},
    initialSearch: String = "",
    showBackButton: Boolean = true,
) {
    var search by remember(initialSearch) { mutableStateOf(initialSearch) }
    var showSearch by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var hideCleared by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Transaction?>(null) }
    var viewed by remember { mutableStateOf<Transaction?>(null) }
    var accountNote by remember(account) { mutableStateOf(account?.note.orEmpty()) }

    val visible = transactions.filter {
        (accountName == null || it.account == accountName) &&
            (categoryName == null || it.category == categoryName) &&
            (month == null || it.date.filter(Char::isDigit).startsWith(month.replace("-", ""))) &&
            (!hideCleared || !it.cleared) &&
            (search.isBlank() || listOf(it.payee, it.category, it.account).any { text ->
                text.contains(search, ignoreCase = true)
            })
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
            Text(categoryName ?: accountName ?: if (showBackButton) "All accounts" else "Transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { showSearch = !showSearch }) {
                Icon(Icons.Outlined.Search, contentDescription = "Search transactions")
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Transaction options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ToggleItem("Group by date", groupTransactionsByDate, onGroupTransactionsByDateChange)
                    ToggleItem("Hide cleared transactions", hideCleared) { hideCleared = it }
                }
            }
        }
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(180)) + expandVertically(tween(240)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(200)),
        ) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Search transactions") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            account?.let { selectedAccount ->
                item("account-details") {
                    AccountDetails(selectedAccount, creditCard, accountNote,
                        { savedNote -> accountNote = savedNote; onSaveAccountNote(savedNote) }, hideDecimalPlaces)
                }
            }
            item("transaction-total") {
                val total = visible.sumOf { it.amountCents }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text("${visible.size} transactions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Amount(total, FontWeight.Bold, hideDecimalPlaces)
                }
            }
            if (groupTransactionsByDate) {
                visible.groupBy { it.date }.forEach { (date, transactions) ->
                    stickyHeader(key = date) {
                        Text(formatTransactionDate(date), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    items(transactions, key = { it.id }) { transaction ->
                        TransactionRow(transaction, hideDecimalPlaces, showDate = false, onClick = { viewed = transaction },
                            showAccount = accountName == null, onLongClick = { selected = transaction },
                            onClearedClick = { onSetCleared(transaction, !transaction.cleared) })
                    }
                }
            } else {
                items(visible, key = { it.id }) { transaction ->
                    TransactionRow(transaction, hideDecimalPlaces, showDate = true, onClick = { viewed = transaction },
                        showAccount = accountName == null, onLongClick = { selected = transaction },
                        onClearedClick = { onSetCleared(transaction, !transaction.cleared) })
                }
            }
        }
    }
    viewed?.let { transaction ->
        TransactionDetailsSheet(
            transaction = transaction,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { viewed = null },
            onEdit = { viewed = null; onEdit(transaction) },
            onDelete = { viewed = null; onDelete(transaction) },
        )
    }
    selected?.let { transaction ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(transaction.payee, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                Action("Edit transaction") { selected = null; onEdit(transaction) }
                Action(if (transaction.cleared) "Mark uncleared" else "Mark cleared") {
                    selected = null
                    onSetCleared(transaction, !transaction.cleared)
                }
                Action("Delete transaction", destructive = true) {
                    selected = null
                    onDelete(transaction)
                }
            }
        }
    }
}

@Composable
private fun AccountDetails(account: Account, card: CreditCardStatus?, note: String,
    onSaveNote: (String) -> Unit, hideDecimals: Boolean) {
    val context = LocalContext.current
    val detailPreferences = remember(context) {
        context.applicationContext.getSharedPreferences("account_detail_preferences", android.content.Context.MODE_PRIVATE)
    }
    var balanceExpanded by remember(account.id) {
        mutableStateOf(detailPreferences.getBoolean("balance_expanded_${account.id}", true))
    }
    var noteEditorOpen by remember(account.id) { mutableStateOf(false) }
    var noteDraft by remember(account.id, noteEditorOpen) { mutableStateOf(note) }
    val toggleBalance = {
        balanceExpanded = !balanceExpanded
        detailPreferences.edit().putBoolean("balance_expanded_${account.id}", balanceExpanded).apply()
    }
    val balanceArrowRotation by animateFloatAsState(
        targetValue = if (balanceExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "Balance disclosure",
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = toggleBalance,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = if (balanceExpanded) "Collapse balance details" else "Expand balance details",
                        modifier = Modifier.padding(end = 8.dp).size(20.dp).rotate(balanceArrowRotation),
                    )
                    Text(
                        "Working balance",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        formatMoneyCents(account.balanceCents, hideDecimals),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                AnimatedVisibility(
                    visible = balanceExpanded,
                    enter = fadeIn(tween(180)) + expandVertically(tween(260)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(220)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        card?.availableCreditCents?.let { DetailAmount("Available credit", it, hideDecimals) }
                        HorizontalDivider()
                        DetailAmount("Cleared", account.clearedCents, hideDecimals)
                        DetailAmount("Uncleared", account.unclearedCents, hideDecimals)
                        DetailAmount("Reconciled", account.reconciledCents, hideDecimals)
                        card?.config?.limitCents?.let { DetailAmount("Credit limit", it, hideDecimals) }
                    }
                }
            }
        }
        card?.let {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Billing cycle", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(it.cycle.dueSummary(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                    DetailAmount("Cycle spend", it.cycleSpendCents, hideDecimals)
                }
            }
        }
        Text("Note", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Surface(
            onClick = { noteEditorOpen = true },
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        ) {
            Text(
                text = note.ifBlank { "Add note" },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (note.isBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = if (note.isBlank()) 1 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (noteEditorOpen) {
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
                    placeholder = { Text("Account note") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveNote(noteDraft)
                    noteEditorOpen = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { noteEditorOpen = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailAmount(label: String, amount: Long, hideDecimals: Boolean, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
        Text(formatMoneyCents(amount, hideDecimals), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ToggleItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(text = { Text(label) }, trailingIcon = {
        Checkbox(checked = checked, onCheckedChange = null)
    }, onClick = { onChange(!checked) })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(transaction: Transaction, hideDecimalPlaces: Boolean,
    showDate: Boolean, showAccount: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
    onClearedClick: (() -> Unit)? = null) {
    val presentation = transactionRowPresentation(transaction, showAccount)
    Column(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(presentation.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Amount(transaction.amountCents, FontWeight.SemiBold, hideDecimalPlaces)
            ClearedIndicator(transaction.cleared, onClearedClick)
        }
        presentation.transferContext?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
        } ?: Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CategoryChip(presentation.categoryLabel, transaction.type == Type.TRANSFER)
            Spacer(Modifier.weight(1f))
            presentation.accountLabel?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 12.dp))
            }
        }
        if (transaction.notes.isNotBlank() || showDate) {
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                if (transaction.notes.isNotBlank()) {
                    Text(transaction.notes, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else Spacer(Modifier.weight(1f))
                if (showDate) Text(formatTransactionDate(transaction.date), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

internal data class TransactionRowPresentation(
    val title: String,
    val categoryLabel: String,
    val accountLabel: String?,
    val transferContext: String?,
)

internal fun transactionRowPresentation(transaction: Transaction, showAccount: Boolean): TransactionRowPresentation {
    if (transaction.type != Type.TRANSFER) {
        return TransactionRowPresentation(
            title = transaction.payee.ifBlank { "Unknown payee" },
            categoryLabel = transaction.category.ifBlank { "Uncategorized" },
            accountLabel = transaction.account.takeIf { showAccount && it.isNotBlank() },
            transferContext = null,
        )
    }
    val otherAccount = transaction.transferAccount?.ifBlank { null }
        ?: transaction.payee.ifBlank { "Unknown account" }
    val outgoing = transaction.amountCents < 0
    return TransactionRowPresentation(
        title = if (outgoing) "Transfer to $otherAccount" else "Transfer from $otherAccount",
        categoryLabel = "Transfer",
        accountLabel = null,
        transferContext = if (!showAccount) null else if (outgoing) "From ${transaction.account}" else "To ${transaction.account}",
    )
}

@Composable
private fun CategoryChip(label: String, transfer: Boolean) {
    Surface(
        color = if (transfer) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (transfer) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun ClearedIndicator(cleared: Boolean, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.padding(start = 7.dp).size(18.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = CircleShape,
        color = if (cleared) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = if (cleared) "Cleared" else "Uncleared",
            tint = if (cleared) Color.White else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(3.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsSheet(
    transaction: Transaction,
    hideDecimalPlaces: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember(transaction.id) { mutableStateOf(false) }
    val presentation = transactionRowPresentation(transaction, showAccount = true)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Transaction details", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Amount(transaction.amountCents, FontWeight.Bold, hideDecimalPlaces)
                ClearedIndicator(transaction.cleared)
            }
            HorizontalDivider()
            TransactionDetail("Payee", presentation.title)
            TransactionDetail("Date", formatTransactionDate(transaction.date))
            TransactionDetail("Category", presentation.categoryLabel)
            TransactionDetail("Account", transaction.account)
            transaction.transferAccount?.takeIf(String::isNotBlank)?.let {
                TransactionDetail("Transfer account", it)
            }
            TransactionDetail("Status", if (transaction.cleared) "Cleared" else "Uncleared")
            transaction.notes.takeIf(String::isNotBlank)?.let { TransactionDetail("Notes", it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = { Text("This transaction will be deleted.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TransactionDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
    }
}

internal fun formatTransactionDate(value: String): String {
    return formatStoredDate(value)
}

@Composable
private fun Amount(value: Long, weight: FontWeight, hideDecimalPlaces: Boolean) {
    Text(formatMoneyCents(value, hideDecimalPlaces, showPositiveSign = true), style = MaterialTheme.typography.bodyMedium, fontWeight = weight, textAlign = TextAlign.End,
        color = if (value >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun Action(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, color = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface) }, onClick = onClick, modifier = Modifier.fillMaxWidth())
}
