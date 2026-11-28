package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun RenameDialog(title: String, currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit, extraAction: (@Composable () -> Unit)? = null) {
    var value by rememberSaveable(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val nameField = @Composable {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (extraAction == null) nameField() else Column {
                nameField()
                extraAction()
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = value.trim().isNotEmpty(), onClick = { onSave(value.trim()) }) { Text("Save") } },
    )
}
