package com.azimulkabir.actua.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveAuthManager
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveBackupItem
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveBackupManager
import com.azimulkabir.actua.data.budget.gdrive.InvalidPassphraseException
import com.azimulkabir.actua.data.budget.gdrive.PassphraseRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun NoBudgetScreen(
    onConnect: () -> Unit,
    onBudgetInstalled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val driveManager = remember { GoogleDriveBackupManager(context) }
    val authState by GoogleDriveAuthManager.authState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newBudgetName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Google Drive restore states
    var showConnectDriveDialog by remember { mutableStateOf(false) }
    var showBrowseDriveBackupsDialog by remember { mutableStateOf(false) }
    var remoteBackups by remember { mutableStateOf<List<GoogleDriveBackupItem>>(emptyList()) }
    var selectedRemoteBackup by remember { mutableStateOf<GoogleDriveBackupItem?>(null) }
    var driveLoading by remember { mutableStateOf(false) }

    var showPassphraseDialog by remember { mutableStateOf(false) }
    var passphraseInput by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    var passphraseError by remember { mutableStateOf<String?>(null) }

    fun restoreSelectedBackup(item: GoogleDriveBackupItem, passphrase: String?) {
        driveLoading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    driveManager.restoreRemoteBackup(
                        remoteFileId = item.id,
                        passphrase = passphrase?.toCharArray(),
                        targetBudgetId = null,
                    )
                }
            }.onSuccess { imported ->
                ActiveBudgetStore(context).budgetId = imported.id
                showBrowseDriveBackupsDialog = false
                showPassphraseDialog = false
                selectedRemoteBackup = null
                passphraseInput = ""
                driveLoading = false
                onBudgetInstalled()
            }.onFailure { error ->
                driveLoading = false
                if (error is InvalidPassphraseException || error is PassphraseRequiredException) {
                    passphraseError = context.getString(R.string.navigation_incorrect_password_or_recovery_key)
                    showPassphraseDialog = true
                } else {
                    errorMessage = error.message ?: context.getString(R.string.navigation_gdrive_restore_failed)
                    showBrowseDriveBackupsDialog = false
                    showPassphraseDialog = false
                    selectedRemoteBackup = null
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            errorMessage = null
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BudgetFileManager(context).importLocalBudget(stream)
                        } ?: error(context.getString(R.string.navigation_could_not_open_archive))
                    }
                }.onSuccess { imported ->
                    ActiveBudgetStore(context).budgetId = imported.id
                    onBudgetInstalled()
                }.onFailure { error ->
                    errorMessage = error.message ?: context.getString(R.string.navigation_import_archive_failed)
                }
            }
        }
    }

    // Connect to Google Drive Dialog
    if (showConnectDriveDialog) {
        AlertDialog(
            onDismissRequest = { showConnectDriveDialog = false },
            title = { Text(stringResource(R.string.navigation_connect_to_google_drive)) },
            text = {
                Text(stringResource(R.string.navigation_connect_to_google_drive_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConnectDriveDialog = false
                        GoogleDriveAuthManager.startAuthorization(context)
                    },
                ) { Text(stringResource(R.string.navigation_connect)) }
            },
            dismissButton = {
                TextButton(onClick = { showConnectDriveDialog = false }) { Text(stringResource(R.string.navigation_cancel)) }
            },
        )
    }

    // Browse and Select Remote Backup Dialog
    if (showBrowseDriveBackupsDialog) {
        val encryptedSuffix = stringResource(R.string.navigation_encrypted_suffix)
        AlertDialog(
            onDismissRequest = { if (!driveLoading) showBrowseDriveBackupsDialog = false },
            title = { Text(stringResource(R.string.navigation_restore_from_google_drive)) },
            text = {
                if (driveLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                        Text(stringResource(R.string.navigation_loading_backups))
                    }
                } else if (remoteBackups.isEmpty()) {
                    Text(stringResource(R.string.navigation_no_gdrive_backups))
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        remoteBackups.forEach { backup ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !driveLoading) {
                                        selectedRemoteBackup = backup
                                        if (backup.isEncrypted && !driveManager.hasEncryptionPassphrase()) {
                                            passphraseInput = ""
                                            passphraseError = null
                                            showPassphraseDialog = true
                                        } else {
                                            restoreSelectedBackup(backup, null)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(backup.budgetName ?: backup.name, fontWeight = FontWeight.Medium)
                                Text(
                                    formatFileSize(backup.sizeBytes) + if (backup.isEncrypted) encryptedSuffix else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    enabled = !driveLoading,
                    onClick = { showBrowseDriveBackupsDialog = false },
                ) { Text(stringResource(R.string.navigation_cancel)) }
            },
        )
    }

    // Remote Restore Passphrase Prompt
    if (showPassphraseDialog && selectedRemoteBackup != null) {
        AlertDialog(
            onDismissRequest = { if (!driveLoading) { showPassphraseDialog = false; selectedRemoteBackup = null } },
            title = { Text(stringResource(R.string.navigation_enter_backup_password)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.navigation_backup_password_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it; passphraseError = null },
                        label = { Text(stringResource(R.string.navigation_password_or_recovery_key)) },
                        singleLine = true,
                        isError = passphraseError != null,
                        supportingText = passphraseError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                Icon(
                                    imageVector = if (passphraseVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (passphraseVisible) {
                                        stringResource(R.string.navigation_hide_password)
                                    } else {
                                        stringResource(R.string.navigation_show_password)
                                    },
                                )
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !driveLoading && passphraseInput.isNotBlank(),
                    onClick = {
                        restoreSelectedBackup(selectedRemoteBackup!!, passphraseInput.trim())
                    },
                ) {
                    if (driveLoading) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.navigation_restore))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !driveLoading,
                    onClick = { showPassphraseDialog = false; selectedRemoteBackup = null },
                ) { Text(stringResource(R.string.navigation_cancel)) }
            },
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.navigation_create_local_budget)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.navigation_local_budget_message))
                    OutlinedTextField(
                        value = newBudgetName,
                        onValueChange = { newBudgetName = it },
                        label = { Text(stringResource(R.string.navigation_budget_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newBudgetName.isNotBlank(),
                    onClick = {
                        val name = newBudgetName.trim()
                        val files = BudgetFileManager(context)
                        val existing = files.listLocalBudgets().mapNotNull { it.budgetName?.lowercase() }
                        if (name.lowercase() in existing) {
                            errorMessage = context.getString(R.string.navigation_budget_name_exists, name)
                            return@TextButton
                        }
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    files.createBudget(name)
                                }
                            }.onSuccess { created ->
                                showCreateDialog = false
                                newBudgetName = ""
                                ActiveBudgetStore(context).budgetId = created.id
                                onBudgetInstalled()
                            }.onFailure { error ->
                                errorMessage = error.message ?: context.getString(R.string.navigation_create_local_budget_failed)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.navigation_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.navigation_cancel)) }
            },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.navigation_no_budget_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.navigation_no_budget_standalone_message),
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) { Text(stringResource(R.string.navigation_connect_to_actual)) }
        FilledTonalButton(
            onClick = {
                errorMessage = null
                newBudgetName = ""
                showCreateDialog = true
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) { Text(stringResource(R.string.navigation_create_local_budget)) }
        OutlinedButton(
            onClick = {
                errorMessage = null
                importLauncher.launch(
                    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")
                )
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) { Text(stringResource(R.string.navigation_import_budget_archive)) }
        OutlinedButton(
            onClick = {
                errorMessage = null
                if (!driveManager.isConnected()) {
                    showConnectDriveDialog = true
                } else {
                    showBrowseDriveBackupsDialog = true
                    driveLoading = true
                    scope.launch {
                        val list = runCatching {
                            withContext(Dispatchers.IO) { driveManager.listRemoteBackups(null) }
                        }.getOrDefault(emptyList())
                        remoteBackups = list
                        driveLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.navigation_restore_from_google_drive)) }

        errorMessage?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024L * 1024L))
}
