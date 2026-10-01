package com.azimulkabir.actua.ui.transactions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.azimulkabir.actua.model.Transaction

internal enum class ReconciledAction { EDIT, BULK_EDIT, DELETE }

/**
 * Actual's `confirm-transaction-edit` reason for changing [rows], or null when none is reconciled
 * and no other transfer leg is (#746). Texts from `ConfirmTransactionEditModal.tsx` at 59fe126f.
 */
internal fun reconciledWarning(rows: List<Transaction>, action: ReconciledAction): String? = when {
    rows.any { it.reconciled } -> when (action) {
        ReconciledAction.EDIT ->
            "Saving your changes to this reconciled transaction may bring your reconciliation out of balance."
        ReconciledAction.BULK_EDIT ->
            "Editing reconciled transactions may bring your reconciliation out of balance."
        ReconciledAction.DELETE ->
            "Deleting reconciled transactions may bring your reconciliation out of balance."
    }
    rows.any { it.transferReconciled } -> "This transfer has a linked transaction in another account that " +
        "is reconciled. ${if (action == ReconciledAction.DELETE) "Deleting" else "Editing"} it may bring " +
        "that account's reconciliation out of balance."
    else -> null
}

/** Asks before a change that [reconciledWarning] flags; nothing is written until Confirm. */
@Composable
internal fun ReconciledConfirmDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reconciled transaction") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
