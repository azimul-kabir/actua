package com.azimulkabir.actua.ui.transactions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Actual's `unlockReconciled` confirmation text (`ConfirmTransactionEditModal.tsx` at 59fe126f). */
internal const val UNLOCK_RECONCILED_WARNING = "Unlocking this transaction means you won't be warned about " +
    "changes that can impact your reconciled balance. (Changes to amount, account, payee, etc)."

/**
 * The rows a bulk "Mark cleared"/"Mark uncleared" changes: like Actual's batch `cleared` edit, it
 * skips reconciled rows instead of failing on them (#992).
 */
internal fun bulkClearedTargets(rows: List<Transaction>, cleared: Boolean): List<Transaction> =
    rows.filter { !it.reconciled && it.cleared != cleared }

/**
 * A row's cleared-indicator tap: toggles `cleared`, or for a reconciled row asks with Actual's
 * `unlockReconciled` text and unlocks it on Confirm (#992). Nothing is written on Cancel.
 */
@Composable
internal fun rememberClearedToggle(
    onSetCleared: (Transaction, Boolean) -> Unit,
    onUnlock: (Transaction) -> Unit,
): (Transaction) -> Unit {
    var pendingUnlock by remember { mutableStateOf<Transaction?>(null) }
    pendingUnlock?.let { transaction ->
        ReconciledConfirmDialog(
            message = UNLOCK_RECONCILED_WARNING,
            onConfirm = {
                pendingUnlock = null
                onUnlock(transaction)
            },
            onDismiss = { pendingUnlock = null },
        )
    }
    return { transaction ->
        if (transaction.reconciled) pendingUnlock = transaction
        else onSetCleared(transaction, !transaction.cleared)
    }
}
