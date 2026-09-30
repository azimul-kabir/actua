package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.ui.theme.Spacing

@Composable
fun NewCategoryDialog(groups: List<String>, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var group by remember(groups) { mutableStateOf(groups.firstOrNull().orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New category") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ActuaFormCard {
                ActuaMenuRow(
                    icon = Icons.Outlined.Category,
                    label = "Group",
                    value = group.ifBlank { "Select group" },
                    valueIsPlaceholder = group.isBlank(),
                    choices = groups.map { it to it },
                ) { group = it }
            }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = {
        TextButton(enabled = name.trim().isNotEmpty() && group.isNotEmpty(), onClick = { onSave(group, name.trim()) }) { Text("Add") }
    })
}

@Composable
fun MoveCategoryDialog(
    categoryName: String,
    groups: List<String>,
    currentGroup: String,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
) {
    var selected by remember(groups) { mutableStateOf(groups.firstOrNull { it != currentGroup }.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Move \"$categoryName\"") }, text = {
        RadioOptions(groups, selected, disabled = setOf(currentGroup)) { selected = it }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = {
        TextButton(enabled = selected.isNotEmpty() && selected != currentGroup, onClick = { onMove(selected) }) { Text("Move") }
    })
}

val AccountTypeOptions = listOf("Checking", "Savings", "Credit", "Investment", "Mortgage", "Debt", "Other")

@Composable
fun NewAccountDialog(onDismiss: () -> Unit, onSave: (String, Boolean, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var balance by remember { mutableStateOf("") }
    var offBudget by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(AccountTypeOptions.first()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add account") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(balance, { balance = it.filter { char -> char.isDigit() || char in ".-" } },
                label = { Text("Starting balance") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            ActuaFormCard {
                ActuaMenuRow(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    label = "Type",
                    value = type,
                    choices = AccountTypeOptions.map { it to it },
                ) { type = it }
                ActuaCardDivider()
                ActuaFormRow(
                    icon = Icons.Outlined.VisibilityOff,
                    label = "Off budget",
                    value = null,
                    checked = offBudget,
                    onClick = { offBudget = !offBudget },
                )
            }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = {
        TextButton(enabled = name.trim().isNotEmpty(), onClick = { onSave(name.trim(), offBudget, balance, type) }) { Text("Add") }
    })
}

@Composable
fun ChangeAccountTypeDialog(
    accountName: String,
    currentType: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var selected by remember(currentType) { mutableStateOf(currentType) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Change type of \"$accountName\"") }, text = {
        RadioOptions(AccountTypeOptions, selected) { selected = it }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = {
        TextButton(enabled = selected != currentType, onClick = { onSave(selected) }) { Text("Save") }
    })
}

/** A single-choice list of [options] whose whole rows are radio buttons; [disabled] ones can't be picked. */
@Composable
private fun RadioOptions(
    options: List<String>,
    selected: String,
    disabled: Set<String> = emptySet(),
    onSelect: (String) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            val enabled = option !in disabled
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                    selected = option == selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onSelect(option) },
                ),
            ) {
                RadioButton(selected = option == selected, onClick = null, enabled = enabled)
                Text(option, modifier = Modifier.padding(start = Spacing.md))
            }
        }
    }
}
