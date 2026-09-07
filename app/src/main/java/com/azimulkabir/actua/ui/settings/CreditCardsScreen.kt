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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardCycle
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.ui.components.formatMoneyCents
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun CreditCardsScreen(
    cards: List<CreditCardStatus>,
    accounts: List<Account>,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onSave: (String, Int, Int, Long?) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<CreditCardStatus?>(null) }
    var adding by remember { mutableStateOf(false) }
    val configured = cards.mapTo(mutableSetOf()) { it.accountId }
    val availableAccounts = accounts.filter { !it.closed && it.id !in configured }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Credit Cards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { adding = true }, enabled = availableAccounts.isNotEmpty()) {
                Icon(Icons.Outlined.Add, "Add credit card")
            }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cards.isEmpty()) item {
                Text("Mark an account as a credit card to track its billing cycle, cycle spend, payment due date and available credit.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp))
            }
            items(cards, key = { it.accountId }) { card ->
                CreditCardRow(card, hideDecimalPlaces, Modifier.padding(horizontal = 16.dp).clickable { editing = card })
            }
        }
    }

    if (adding) CardEditorDialog(null, availableAccounts, onDismiss = { adding = false }, onSave = { id, day, offset, limit ->
        onSave(id, day, offset, limit); adding = false
    })
    editing?.let { card -> CardEditorDialog(card, accounts, onDismiss = { editing = null }, onSave = { id, day, offset, limit ->
        onSave(id, day, offset, limit); editing = null
    }, onRemove = { onRemove(card.accountId); editing = null }) }
}

@Composable
private fun CreditCardRow(card: CreditCardStatus, hideDecimals: Boolean, modifier: Modifier = Modifier) {
    val days = card.cycle.daysUntilDue()
    val urgency = when { days <= 3 -> MaterialTheme.colorScheme.error; days <= 7 -> Color(0xFFF57C00); else -> Color(0xFFF9A825) }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row {
            Box(Modifier.padding(vertical = 0.dp).align(Alignment.CenterVertically)) {
                Surface(color = urgency, modifier = Modifier.padding(0.dp)) { Box(Modifier.padding(horizontal = 2.dp, vertical = 34.dp)) }
            }
            Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(card.accountName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(formatMoneyCents(card.balanceCents, hideDecimals), fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Spend ${formatMoneyCents(card.cycleSpendCents, hideDecimals)} · ${card.cycle.daysRemainingInCycle()}d left",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    Text(card.cycle.dueShortSummary(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp))
                }
                card.availableCreditCents?.let {
                    Text("Available credit ${formatMoneyCents(it, hideDecimals)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CardEditorDialog(
    card: CreditCardStatus?, accounts: List<Account>, onDismiss: () -> Unit,
    onSave: (String, Int, Int, Long?) -> Unit, onRemove: (() -> Unit)? = null,
) {
    var accountId by remember { mutableStateOf(card?.accountId ?: accounts.firstOrNull()?.id.orEmpty()) }
    var day by remember { mutableStateOf((card?.config?.statementDay ?: 15).toString()) }
    var offset by remember { mutableStateOf((card?.config?.dueOffsetDays ?: CreditCardCycle.DEFAULT_DUE_OFFSET_DAYS).toString()) }
    var limit by remember { mutableStateOf(card?.config?.limitCents?.let { BigDecimal(it).movePointLeft(2).toPlainString() }.orEmpty()) }
    var accountsExpanded by remember { mutableStateOf(false) }
    val account = accounts.firstOrNull { it.id == accountId }
    val validDay = day.toIntOrNull()?.takeIf { it in 1..31 }
    val validOffset = offset.toIntOrNull()?.takeIf { it in 1..CreditCardCycle.MAX_DUE_OFFSET_DAYS }
    val limitCents = runCatching { limit.takeIf(String::isNotBlank)?.let {
        BigDecimal(it).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact().takeIf { cents -> cents > 0 }
    } }.getOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (card == null) "Add Credit Card" else "Edit Card") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (card == null) Box {
                TextButton(onClick = { accountsExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(account?.name ?: "Select account")
                }
                DropdownMenu(accountsExpanded, { accountsExpanded = false }) { accounts.forEach { option ->
                    DropdownMenuItem(text = { Text(option.name) }, onClick = { accountId = option.id; accountsExpanded = false })
                } }
            } else Text("Account  ${card.accountName}")
            TextField(day, { day = it.filter(Char::isDigit).take(2) }, label = { Text("Statement closing day (1–31)") }, singleLine = true)
            TextField(offset, { offset = it.filter(Char::isDigit).take(2) }, label = { Text("Payment due after (1–60 days)") }, singleLine = true)
            TextField(limit, { value -> limit = value.filter { it.isDigit() || it == '.' } }, label = { Text("Credit limit (optional)") }, singleLine = true)
            Text("The due date is the statement closing date plus the issuer’s payment period.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove Credit Card Tracking", color = MaterialTheme.colorScheme.error) }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = {
        Button(enabled = accountId.isNotBlank() && validDay != null && validOffset != null && (limit.isBlank() || limitCents != null),
            onClick = { onSave(accountId, validDay!!, validOffset!!, limitCents) }) { Text("Save") }
    })
}
