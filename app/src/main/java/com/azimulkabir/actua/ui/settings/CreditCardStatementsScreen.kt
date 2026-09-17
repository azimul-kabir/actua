package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.CreditCardCycle
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.transactions.TransactionRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Mirrors Actuali's "Recent Statements" list within AccountDetailView, adapted as its own
 * screen since Actua has no account-detail screen yet. */
@Composable
fun CreditCardStatementsScreen(
    accountName: String,
    statements: List<CreditCardCycle.StatementRecord>,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onSelectStatement: (CreditCardCycle.StatementRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = accountName, onBack = onBack)
        if (statements.isEmpty()) {
            Text("No closed statements yet.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(statements, key = { it.id }) { statement ->
                    StatementRow(statement, hideDecimalPlaces, onClick = { onSelectStatement(statement) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun StatementRow(statement: CreditCardCycle.StatementRecord, hideDecimalPlaces: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${statement.startDate.abbreviated()} – ${statement.endDate.abbreviated()}")
            if (statement.isPaid) {
                Text("Paid", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
            } else {
                Text("Due ${statement.dueDate.abbreviated()}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(formatMoneyCents(statement.statementBalance, hideDecimalPlaces), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 8.dp))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.outline)
    }
}

private fun com.azimulkabir.actua.data.schedules.DayDate.abbreviated(): String =
    LocalDate.of(year, month, day).format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH))

/** Mirrors Actuali's CreditCardStatementDetailView: statement summary plus its transactions. */
@Composable
fun CreditCardStatementDetailScreen(
    statement: CreditCardCycle.StatementRecord,
    transactions: List<Transaction>,
    isLoading: Boolean,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onSelectTransaction: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Statement Details", onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DetailRow("Statement Period", "${statement.startDate.abbreviated()} – ${statement.endDate.abbreviated()}")
            DetailRow("Statement Balance", formatMoneyCents(statement.statementBalance, hideDecimalPlaces))
            DetailRow("Payment Due", statement.dueDate.abbreviated())
            if (statement.isPaid) {
                DetailRow("Status", "Paid", highlightGreen = true)
            } else {
                DetailRow("Remaining Due", formatMoneyCents(statement.remainingDue, hideDecimalPlaces))
            }
            DetailRow("Cycle Spend", formatMoneyCents(statement.totalSpend, hideDecimalPlaces))
            if (statement.paymentsSince > 0) {
                DetailRow("Payments & Credits", formatMoneyCents(statement.paymentsSince, hideDecimalPlaces))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        when {
            isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp)) { CircularProgressIndicator(Modifier.padding(0.dp)) }
            transactions.isEmpty() -> Text("No transactions in this statement", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(transactions, key = { it.id }) { tx ->
                    TransactionRow(
                        transaction = tx, hideDecimalPlaces = hideDecimalPlaces,
                        showDate = true, showAccount = false,
                        onClick = { onSelectTransaction(tx) }, onLongClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(title: String, value: String, highlightGreen: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold,
            color = if (highlightGreen) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface)
    }
}
