package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
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

@Composable
fun NewCategoryDialog(groups: List<String>, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var group by remember(groups) { mutableStateOf(groups.firstOrNull().orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New category") }, text = {
        Column {
            TextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
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
fun NewAccountDialog(onDismiss: () -> Unit, onSave: (String, Boolean, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var balance by remember { mutableStateOf("") }
    var offBudget by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add account") }, text = {
        Column {
            TextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            TextField(balance, { balance = it.filter { char -> char.isDigit() || char in ".-" } },
                label = { Text("Starting balance") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Off budget", modifier = Modifier.weight(1f)); Switch(offBudget, { offBudget = it })
            }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, confirmButton = {
        TextButton(enabled = name.trim().isNotEmpty(), onClick = { onSave(name.trim(), offBudget, balance) }) { Text("Add") }
    })
}
