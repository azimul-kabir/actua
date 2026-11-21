package com.azimulkabir.actua.ui.transactions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.azimulkabir.actua.R

/**
 * Asks what a receipt scanned for an existing transaction may change. Attaching the photo leaves every field
 * as it is; replacing the details fills the amount, payee and date from the receipt (and offers split lines),
 * and nothing is saved until the form is saved.
 */
@Composable
fun ReceiptEditChoiceDialog(
    canAttachPhoto: Boolean,
    onAttachOnly: () -> Unit,
    onReplaceDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.receipt_edit_title)) },
        text = { Text(stringResource(R.string.receipt_edit_message)) },
        confirmButton = {
            if (canAttachPhoto) {
                TextButton(onClick = onAttachOnly) { Text(stringResource(R.string.receipt_edit_attach_only)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onReplaceDetails) { Text(stringResource(R.string.receipt_edit_replace)) }
        },
    )
}
