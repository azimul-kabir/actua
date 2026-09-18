package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NewCategoryDialog(groups: List<String>, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var group by remember(groups) { mutableStateOf(groups.firstOrNull().orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New category") }, text = {
        Column {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(group.ifBlank { "Select group" }) }
            DropdownMenu(expanded, { expanded = false }) { groups.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { group = option; expanded = false })
            } }
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
        Column {
            groups.forEach { option ->
                val enabled = option != currentGroup
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { selected = option },
                ) {
                    RadioButton(selected = option == selected, onClick = { selected = option }, enabled = enabled)
                    Text(option, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
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
    var typeExpanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add account") }, text = {
        Column {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            OutlinedTextField(balance, { balance = it.filter { char -> char.isDigit() || char in ".-" } },
                label = { Text("Starting balance") }, singleLine = true)
            TextButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Type: $type") }
            DropdownMenu(typeExpanded, { typeExpanded = false }) { AccountTypeOptions.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { type = option; typeExpanded = false })
            } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Off budget", modifier = Modifier.weight(1f)); Switch(offBudget, { offBudget = it })
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
        Column {
            AccountTypeOptions.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { selected = option },
                ) {
                    RadioButton(selected = option == selected, onClick = { selected = option })
                    Text(option, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = {
        TextButton(enabled = selected != currentType, onClick = { onSave(selected) }) { Text("Save") }
    })
}
