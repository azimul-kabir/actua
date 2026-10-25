package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.azimulkabir.actua.R

/**
 * Asks before the budget type changes. The type is a synced Actual preference, so it changes the
 * budget on every device; the Budget menu and Settings both go through this dialog.
 * [newType] is `"envelope"` or `"tracking"`.
 */
@Composable
fun BudgetTypeConfirmDialog(newType: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val tracking = newType == "tracking"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (tracking) R.string.budget_type_confirm_title_tracking
                    else R.string.budget_type_confirm_title_envelope,
                ),
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.budget_type_confirm_all_devices))
                Text(
                    stringResource(
                        if (tracking) R.string.budget_type_confirm_tracking else R.string.budget_type_confirm_envelope,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.budget_type_confirm_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
