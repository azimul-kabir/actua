package com.azimulkabir.actua.ui.transactions

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.azimulkabir.actua.R
import com.azimulkabir.actua.model.Transaction

internal enum class ReconciledAction { EDIT, BULK_EDIT, DELETE }

/**
 * Actual's `confirm-transaction-edit` reason for changing [rows], or null when none is reconciled
 * and no other transfer leg is (#746). Texts from `ConfirmTransactionEditModal.tsx` at 59fe126f,
 * returned as resource ids so callers resolve them where composition is available.
 */
@StringRes
internal fun reconciledWarningRes(rows: List<Transaction>, action: ReconciledAction): Int? = when {
    rows.any { it.reconciled } -> when (action) {
        ReconciledAction.EDIT -> R.string.reconciled_warning_edit
        ReconciledAction.BULK_EDIT -> R.string.reconciled_warning_bulk_edit
        ReconciledAction.DELETE -> R.string.reconciled_warning_delete
    }
    rows.any { it.transferReconciled } ->
        if (action == ReconciledAction.DELETE) R.string.reconciled_warning_transfer_delete
        else R.string.reconciled_warning_transfer_edit
    else -> null
}

/** Asks before a change that [reconciledWarningRes] flags; nothing is written until Confirm. */
@Composable
internal fun ReconciledConfirmDialog(@StringRes messageRes: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reconciled_warning_title)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.reconciled_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Actual's `unlockReconciled` confirmation text (`ConfirmTransactionEditModal.tsx` at 59fe126f). */
@StringRes
internal val UNLOCK_RECONCILED_WARNING_RES: Int = R.string.reconciled_unlock_warning

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
            messageRes = UNLOCK_RECONCILED_WARNING_RES,
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

/**
 * The rows a bulk "Mark cleared"/"Mark uncleared" changes: like Actual's batch `cleared` edit, it
 * skips reconciled rows instead of failing on them (#992).
 */
internal fun bulkClearedTargets(rows: List<Transaction>, cleared: Boolean): List<Transaction> =
    rows.filter { !it.reconciled && it.cleared != cleared }
