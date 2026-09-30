package com.azimulkabir.actua.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaPrimaryActionBar
import com.azimulkabir.actua.ui.components.ActuaSheetTitle
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.Spacing

/** What the close sheet needs beyond the account row, loaded off the main thread. */
data class CloseAccountOptions(
    val hasTransactions: Boolean,
    val categoryGroups: List<CloseCategoryGroup>,
)

data class CloseCategoryGroup(val name: String, val categories: List<Pair<String, String>>)

/**
 * Android form of Actual's `CloseAccountModal`: an account without transactions is deleted; a
 * balance must be transferred to another open account (categorized when it leaves the budget);
 * an account with transactions can instead be force closed, deleting it and its transactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CloseAccountSheet(
    account: Account,
    accounts: List<Account>,
    hideDecimalPlaces: Boolean,
    loadOptions: suspend (Account) -> CloseAccountOptions,
    onDismiss: () -> Unit,
    onClose: (transferAccountId: String?, categoryId: String?) -> Unit,
    onForceClose: () -> Unit,
) {
    val options by produceState<CloseAccountOptions?>(null, account.id) { value = loadOptions(account) }
    val transferTargets = accounts.filter { !it.closed && it.id != account.id }
    var transferAccountId by rememberSaveable(account.id) { mutableStateOf<String?>(null) }
    var categoryId by rememberSaveable(account.id) { mutableStateOf<String?>(null) }
    var showErrors by rememberSaveable(account.id) { mutableStateOf(false) }
    var pickingAccount by rememberSaveable(account.id) { mutableStateOf(false) }
    var pickingCategory by rememberSaveable(account.id) { mutableStateOf(false) }
    var confirmingForce by rememberSaveable(account.id) { mutableStateOf(false) }
    val transferAccount = transferTargets.firstOrNull { it.id == transferAccountId }
    val hasBalance = account.balanceCents != 0L
    val needsCategory = hasBalance && !account.offBudget && transferAccount?.offBudget == true
    val categories = options?.categoryGroups.orEmpty().flatMap { it.categories }
    val categoryName = categories.firstOrNull { it.first == categoryId }?.second

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                ActuaSheetTitle("Close account")
                val loaded = options
                if (loaded == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.xl),
                    )
                    return@Column
                }
                Text(
                    "Are you sure you want to close ${account.name}? " + if (loaded.hasTransactions) {
                        "This account has transactions so it can't be permanently deleted."
                    } else {
                        "This account has no transactions so it will be permanently deleted."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (hasBalance) {
                    Text(
                        "This account has a balance of ${formatMoneyCents(account.balanceCents, hideDecimalPlaces)}. " +
                            "To close it, choose another account to transfer this balance to:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PickerField(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        label = "Transfer to",
                        value = transferAccount?.name,
                        placeholder = "Select account…",
                        error = if (showErrors && transferAccount == null) "Transfer is required" else null,
                        onClick = { pickingAccount = true },
                    )
                    if (needsCategory) {
                        Text(
                            "Since the balance moves from an on-budget account to an off-budget account, " +
                                "this transaction must be categorized:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        PickerField(
                            icon = Icons.Outlined.Category,
                            label = "Category",
                            value = categoryName,
                            placeholder = "Select category…",
                            error = if (showErrors && categoryName == null) "Category is required" else null,
                            onClick = { pickingCategory = true },
                        )
                    }
                }
                if (loaded.hasTransactions) {
                    Text(
                        "You can also force close the account, which permanently deletes it and all its " +
                            "transactions. Doing so may change your budget unexpectedly since money in it may vanish.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { confirmingForce = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Force close", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
            if (options != null) ActuaPrimaryActionBar(
                text = "Close account",
                onClick = {
                    showErrors = true
                    val missingTransfer = hasBalance && transferAccount == null
                    val missingCategory = needsCategory && categoryName == null
                    if (!missingTransfer && !missingCategory) {
                        onClose(transferAccount?.id, categoryId.takeIf { needsCategory })
                    }
                },
            )
        }
    }

    if (pickingAccount) OptionDialog(
        title = "Transfer balance to",
        sections = listOf(null to transferTargets.map { it.id to it.name }),
        selectedId = transferAccountId,
        onDismiss = { pickingAccount = false },
        onSelect = { transferAccountId = it; pickingAccount = false },
    )
    if (pickingCategory) OptionDialog(
        title = "Category",
        sections = options?.categoryGroups.orEmpty().map { it.name to it.categories },
        selectedId = categoryId,
        onDismiss = { pickingCategory = false },
        onSelect = { categoryId = it; pickingCategory = false },
    )
    if (confirmingForce) AlertDialog(
        onDismissRequest = { confirmingForce = false },
        title = { Text("Force close ${account.name}?") },
        text = { Text("This permanently deletes the account and all its transactions. Transfers into other accounts are kept there without a payee.") },
        dismissButton = { TextButton(onClick = { confirmingForce = false }) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = { confirmingForce = false; onForceClose() }) {
                Text("Force close", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/** A picker as a form-card row, with its validation [error] under the card. */
@Composable
private fun PickerField(
    icon: ImageVector,
    label: String,
    value: String?,
    placeholder: String,
    error: String?,
    onClick: () -> Unit,
) {
    Column {
        ActuaFormCard {
            ActuaFormRow(
                icon = icon,
                label = label,
                value = value ?: placeholder,
                valueIsPlaceholder = value == null,
                onClick = onClick,
            )
        }
        if (error != null) Text(
            error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xs),
        )
    }
}

@Composable
private fun OptionDialog(
    title: String,
    sections: List<Pair<String?, List<Pair<String, String>>>>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                sections.forEach { (header, rows) ->
                    if (header != null && rows.isNotEmpty()) item(key = "header-$header") {
                        Text(
                            header, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(rows, key = { it.first }) { (id, name) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.RadioButton) { onSelect(id) },
                        ) {
                            RadioButton(selected = id == selectedId, onClick = null)
                            Text(name, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
