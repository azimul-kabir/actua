package com.azimulkabir.actua.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun RenameDialog(title: String, currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TextField(value = value, onValueChange = { value = it }, singleLine = true) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = value.trim().isNotEmpty(), onClick = { onSave(value.trim()) }) { Text("Save") } },
    )
}
