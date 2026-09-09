package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.schedules.ScheduleFormFields
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun EditScheduleScreen(
    item: ScheduleListItem,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onSave: (ScheduleFormFields) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val schedule = item.schedule
    var name by remember(schedule.id) { mutableStateOf(schedule.name.orEmpty()) }
    var amountText by remember(schedule.id) {
        mutableStateOf(BigDecimal(schedule.postAmount).movePointLeft(2).stripTrailingZeros().toPlainString())
    }
    var income by remember(schedule.id) { mutableStateOf(schedule.postAmount > 0) }
    var automaticallyAdd by remember(schedule.id) { mutableStateOf(schedule.postsTransaction) }
    var showDelete by remember { mutableStateOf(false) }
    val originalAmountText = remember(schedule.id) {
        BigDecimal(schedule.postAmount).movePointLeft(2).stripTrailingZeros().toPlainString()
    }
    val amountCents = amountText.toBigDecimalOrNull()?.movePointRight(2)
        ?.setScale(0, RoundingMode.HALF_UP)?.longValueExactOrNull()
    val canSave = amountCents != null && schedule.accountId != null && schedule.dateCondition != null

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Edit Schedule", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(enabled = canSave, onClick = {
                val cents = requireNotNull(amountCents)
                onSave(ScheduleFormFields(
                    name = name,
                    payeeId = schedule.payeeId,
                    accountId = schedule.accountId,
                    amount = if (amountText == originalAmountText && income == (schedule.postAmount > 0)) {
                        schedule.amount
                    } else ScheduledAmount.Fixed(if (income) kotlin.math.abs(cents) else -kotlin.math.abs(cents)),
                    amountOp = schedule.amountOp,
                    date = schedule.dateCondition,
                    postsTransaction = automaticallyAdd,
                    customUpcomingLength = schedule.customUpcomingLength,
                ))
            }) { Text("Save") }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Schedule name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    ReadOnlyValue("Payee", item.payeeName ?: "None")
                    ReadOnlyValue("Account", item.accountName ?: "None")
                }
            }

            Text("Amount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { income = false }, modifier = Modifier.weight(1f)) {
                            Text(if (!income) "✓ Expense" else "Expense")
                        }
                        TextButton(onClick = { income = true }, modifier = Modifier.weight(1f)) {
                            Text(if (income) "✓ Income" else "Income")
                        }
                    }
                    OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount") },
                        singleLine = true, isError = amountText.isNotBlank() && amountCents == null,
                        modifier = Modifier.fillMaxWidth())
                    ReadOnlyValue("Matches", schedule.amountOp.name.lowercase().replaceFirstChar(Char::uppercase))
                }
            }

            Text("Date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadOnlyValue(if (schedule.isRecurring) "Repeats" else "Date", describeScheduleDate(item))
                    ReadOnlyValue("Next", schedule.nextDate?.iso ?: "No next date")
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatically Add Transaction")
                        Text("Created when the app syncs on or after the scheduled date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(automaticallyAdd, { automaticallyAdd = it })
                }
            }

            TextButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete Schedule", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("Delete this schedule?") },
        text = { Text("Transactions already created by this schedule will be kept.") },
        confirmButton = { TextButton(onClick = { showDelete = false; onDelete() }) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ReadOnlyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun describeScheduleDate(item: ScheduleListItem): String {
    val recurring = item.schedule.dateCondition as? com.azimulkabir.actua.data.schedules.ScheduleDateCondition.Recurring
        ?: return item.schedule.nextDate?.iso ?: "Not set"
    val config = recurring.config
    return buildString {
        append("Every ")
        if (config.interval > 1) append("${config.interval} ")
        append(config.frequency.name.lowercase().removeSuffix("ly"))
        if (config.interval > 1) append("s")
    }
}

private fun BigDecimal.longValueExactOrNull(): Long? = runCatching { longValueExact() }.getOrNull()
