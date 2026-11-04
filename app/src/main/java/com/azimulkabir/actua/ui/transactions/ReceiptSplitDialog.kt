package com.azimulkabir.actua.ui.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R

/**
 * Offers the tax and line items found on a scanned receipt as split lines. The tax is on by default; the
 * items, the least reliable part of a scan, are off. Nothing is changed until the user confirms, and the
 * split editor that opens afterwards lets every line be edited or removed.
 */
@Composable
fun ReceiptSplitDialog(
    taxText: String?,
    itemCount: Int,
    onConfirm: (includeTax: Boolean, includeItems: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeTax by remember { mutableStateOf(taxText != null) }
    var includeItems by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.receipt_split_title)) },
        text = {
            Column {
                Text(stringResource(R.string.receipt_split_message), style = MaterialTheme.typography.bodyMedium)
                if (taxText != null) {
                    ChoiceRow(
                        checked = includeTax,
                        onCheckedChange = { includeTax = it },
                        label = stringResource(R.string.receipt_split_tax, taxText),
                    )
                }
                if (itemCount >= 2) {
                    ChoiceRow(
                        checked = includeItems,
                        onCheckedChange = { includeItems = it },
                        label = pluralStringResource(R.plurals.receipt_split_items, itemCount, itemCount),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(includeTax, includeItems) },
                enabled = includeTax || includeItems,
            ) { Text(stringResource(R.string.receipt_split_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.receipt_split_not_now)) } },
    )
}

@Composable
private fun ChoiceRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}
