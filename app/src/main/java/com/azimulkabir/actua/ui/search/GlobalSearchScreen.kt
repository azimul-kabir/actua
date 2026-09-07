package com.azimulkabir.actua.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.transactions.TransactionDetailsSheet
import com.azimulkabir.actua.ui.transactions.TransactionRow

private enum class SearchFilter(val label: String) { ALL("All"), TRANSACTIONS("Transactions"), ACCOUNTS("Accounts"), PAYEES("Payees"), CATEGORIES("Categories") }

@Composable
fun GlobalSearchScreen(
    transactions: List<Transaction>,
    accounts: List<Account>,
    payees: List<String>,
    categories: List<String>,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onTransactionEdit: (Transaction) -> Unit,
    onTransactionDelete: (Transaction) -> Unit,
    onTransactionClearedChange: (Transaction, Boolean) -> Unit,
    onAccountClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onPayeeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SearchFilter.ALL) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    val term = query.trim()
    val matchingTransactions = remember(term, transactions) {
        if (term.isBlank()) emptyList() else transactions.filter {
            listOf(it.payee, it.category, it.account, it.transferAccount.orEmpty(), it.notes)
                .any { value -> value.contains(term, ignoreCase = true) }
        }.take(50)
    }
    val matchingAccounts = remember(term, accounts) { if (term.isBlank()) emptyList() else accounts.filter { it.name.contains(term, true) } }
    val matchingPayees = remember(term, payees) { if (term.isBlank()) emptyList() else payees.filter { it.contains(term, true) }.take(20) }
    val matchingCategories = remember(term, categories) { if (term.isBlank()) emptyList() else categories.filter { it.contains(term, true) }.take(20) }

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search Actua") },
            leadingIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            trailingIcon = { Icon(Icons.Outlined.Search, "Search") },
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            items(SearchFilter.entries) { item ->
                FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) })
            }
        }
        if (term.isBlank()) {
            Text("Search transactions, accounts, payees and categories",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (filter == SearchFilter.ALL || filter == SearchFilter.TRANSACTIONS) {
                    searchSection("Transactions", matchingTransactions) { transaction ->
                        TransactionRow(
                            transaction = transaction,
                            hideDecimalPlaces = hideDecimalPlaces,
                            showDate = true,
                            showAccount = true,
                            onClick = { selectedTransaction = transaction },
                            onLongClick = { selectedTransaction = transaction },
                            onClearedClick = { onTransactionClearedChange(transaction, !transaction.cleared) },
                        )
                    }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.ACCOUNTS) {
                    searchSection("Accounts", matchingAccounts) { account ->
                        SearchRow(account.name, account.type, onClick = { onAccountClick(account.name) })
                    }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.PAYEES) {
                    searchSection("Payees", matchingPayees) { payee -> SearchRow(payee, onClick = { onPayeeClick(payee) }) }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.CATEGORIES) {
                    searchSection("Categories", matchingCategories) { category -> SearchRow(category, onClick = { onCategoryClick(category) }) }
                }
            }
        }
    }
    selectedTransaction?.let { transaction ->
        TransactionDetailsSheet(
            transaction = transaction,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { selectedTransaction = null },
            onEdit = { selectedTransaction = null; onTransactionEdit(transaction) },
            onDelete = { selectedTransaction = null; onTransactionDelete(transaction) },
        )
    }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.searchSection(
    title: String,
    values: List<T>,
    content: @Composable (T) -> Unit,
) {
    if (values.isEmpty()) return
    item("header-$title") {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
    items(values) { value -> content(value) }
}

@Composable
private fun SearchRow(title: String, subtitle: String = "", trailing: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let { Text(it, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp)) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}
