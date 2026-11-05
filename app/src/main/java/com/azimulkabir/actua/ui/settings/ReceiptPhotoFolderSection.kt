package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.receipts.ReceiptPhotoFolder
import com.azimulkabir.actua.data.receipts.ReceiptPhotoFolderState
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.theme.Spacing

/**
 * Chooses where kept receipt photos go: Actua's private storage (the default) or a folder picked with the system
 * folder picker, for example on an SD card or in a folder a sync app mirrors. Before the picker opens the user is
 * told plainly what that means: the folder is outside the app lock and outside Actua's control.
 */
@Composable
fun ReceiptPhotoFolderSection(state: ReceiptPhotoFolderState, onChanged: () -> Unit) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ReceiptPhotoFolder(context).select(uri, null) }
            onChanged()
        }
    }
    val folderLabel = state.name ?: stringResource(R.string.selected_folder)
    ActuaFormRow(
        icon = Icons.Outlined.Folder,
        label = stringResource(R.string.receipt_folder_title),
        value = if (state.uri == null) stringResource(R.string.private_app_storage) else folderLabel,
        caption = when {
            state.uri == null -> stringResource(R.string.receipt_folder_private_caption)
            state.usable -> stringResource(R.string.receipt_folder_chosen_caption)
            else -> stringResource(R.string.receipt_folder_unavailable_caption)
        },
        onClick = { confirming = true },
    )
    if (state.uri != null) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {
                ReceiptPhotoFolder(context).clear()
                onChanged()
            }) { Text(stringResource(R.string.receipt_folder_use_private)) }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.receipt_folder_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.receipt_folder_dialog_message), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.receipt_folder_dialog_existing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { confirming = false; chooseFolder.launch(null) }) {
                    Text(stringResource(R.string.receipt_folder_choose))
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
