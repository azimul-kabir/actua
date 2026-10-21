package com.azimulkabir.actua.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.BackupDestinationError
import com.azimulkabir.actua.data.budget.BackupDestinationManager
import com.azimulkabir.actua.data.budget.BackupItem
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveAuthManager
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveBackupItem
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveBackupManager
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveConfig
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveEncryptionStore
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSecondaryButton
import com.azimulkabir.actua.ui.theme.Sizes
import com.azimulkabir.actua.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Thrown when the selected document cannot be opened at all, so its message localizes. */
private class SelectedBackupUnreadableException : Exception()

@Composable
fun BackupsScreen(
    budgetId: String,
    onBack: () -> Unit,
    onBeforeRestore: () -> Unit,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Configuration-aware resource access; re-captured on every recomposition (#424 pattern).
    val resources = LocalResources.current
    val service = remember { BackupService(context) }
    val destinations = remember { BackupDestinationManager(context) }
    val driveManager = remember { GoogleDriveBackupManager(context) }
    val authState by GoogleDriveAuthManager.authState.collectAsState()
    val scope = rememberCoroutineScope()

    var backups by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
    var remoteBackups by remember { mutableStateOf<List<GoogleDriveBackupItem>>(emptyList()) }
    var destination by remember { mutableStateOf(destinations.read()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupItem?>(null) }
    var pendingExport by remember { mutableStateOf<BackupItem.Archive?>(null) }
    var confirmBackup by remember { mutableStateOf(false) }

    // Google Drive Dialog States
    var showPassphraseDialog by remember { mutableStateOf(false) }
    var passphraseInput by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }

    var showDisconnectDialog by remember { mutableStateOf(false) }

    var showClientIdDialog by remember { mutableStateOf(false) }
    var clientIdInput by remember { mutableStateOf("") }

    var pendingRestoreRemote by remember { mutableStateOf<GoogleDriveBackupItem?>(null) }
    var restorePassphrasePrompt by remember { mutableStateOf(false) }
    var restorePassphraseInput by remember { mutableStateOf("") }
    var restorePassphraseVisible by remember { mutableStateOf(false) }
    var restorePassphraseError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        backups = runCatching { service.availableBackups(budgetId) }.getOrDefault(emptyList())
    }

    fun refreshRemote() {
        GoogleDriveAuthManager.updateFromStorage(context)
        if (driveManager.isConnected()) {
            scope.launch {
                val list = runCatching {
                    withContext(Dispatchers.IO) { driveManager.listRemoteBackups(budgetId) }
                }.getOrDefault(emptyList())
                remoteBackups = list
            }
        } else {
            remoteBackups = emptyList()
        }
    }

    fun makeBackup() {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { service.makeBackup(budgetId) } }
                .onSuccess {
                    message = resources.getString(R.string.backup_created)
                    if (driveManager.isConnected() && driveManager.hasEncryptionPassphrase()) {
                        message = resources.getString(R.string.backup_created_and_queued_drive)
                    }
                }
                .onFailure { message = it.message ?: resources.getString(R.string.could_not_create_backup) }
            destination = destinations.read()
            busy = false
            refresh()
            refreshRemote()
        }
    }

    fun uploadToDriveNow() {
        if (!driveManager.hasEncryptionPassphrase()) {
            showPassphraseDialog = true
            return
        }
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val archive = service.makeBackup(budgetId)
                    driveManager.uploadBackup(budgetId, archive)
                }
            }.onSuccess {
                message = resources.getString(R.string.uploaded_to_drive)
                refreshRemote()
            }.onFailure {
                message = it.message ?: resources.getString(R.string.drive_upload_failed)
            }
            busy = false
            refresh()
        }
    }

    fun performRemoteRestore(item: GoogleDriveBackupItem, passphrase: String?) {
        busy = true
        onBeforeRestore()
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    driveManager.restoreRemoteBackup(
                        remoteFileId = item.id,
                        passphrase = passphrase?.toCharArray(),
                        targetBudgetId = budgetId,
                    )
                }
            }.onSuccess {
                message = resources.getString(R.string.backup_restored)
                pendingRestoreRemote = null
                restorePassphrasePrompt = false
                restorePassphraseInput = ""
                restorePassphraseError = null
                busy = false
                refresh()
                refreshRemote()
                onRestored()
            }.onFailure { error ->
                busy = false
                if (error is com.azimulkabir.actua.data.budget.gdrive.InvalidPassphraseException) {
                    restorePassphraseError = resources.getString(R.string.incorrect_password_decrypt)
                    restorePassphrasePrompt = true
                } else if (error is com.azimulkabir.actua.data.budget.gdrive.PassphraseRequiredException) {
                    restorePassphraseError = resources.getString(R.string.password_required_decrypt)
                    restorePassphrasePrompt = true
                } else {
                    message = error.message ?: resources.getString(R.string.could_not_restore_backup)
                    pendingRestoreRemote = null
                    restorePassphrasePrompt = false
                }
            }
        }
    }

    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    destinations.select(uri, null)
                    service.mirrorExisting(budgetId)
                }
            }.onSuccess { message = resources.getString(R.string.existing_backups_mirrored) }
                .onFailure { message = it.message ?: resources.getString(R.string.could_not_use_folder) }
            destination = destinations.read()
            busy = false
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val backup = pendingExport
        pendingExport = null
        if (uri != null && backup != null) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                        service.archiveFile(budgetId, backup.id).inputStream().use { it.copyTo(output) }
                    }
                }
            }.onSuccess { message = resources.getString(R.string.backup_exported) }
                .onFailure { message = it.message ?: resources.getString(R.string.could_not_export_backup) }
            busy = false
        }
    }

    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { service.importArchive(budgetId, it) }
                        ?: throw SelectedBackupUnreadableException()
                }
            }.onSuccess { message = resources.getString(R.string.backup_imported) }
                .onFailure {
                    message = if (it is SelectedBackupUnreadableException) {
                        resources.getString(R.string.could_not_read_selected_file)
                    } else {
                        it.message ?: resources.getString(R.string.could_not_import_backup)
                    }
                }
            busy = false
            refresh()
        }
    }

    LaunchedEffect(budgetId) {
        refresh()
        refreshRemote()
    }

    LaunchedEffect(authState.isConnected) {
        refreshRemote()
    }

    // Passphrase Configuration Dialog
    if (showPassphraseDialog) {
        AlertDialog(
            onDismissRequest = { showPassphraseDialog = false },
            title = { Text(stringResource(R.string.passphrase_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.passphrase_dialog_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text(stringResource(R.string.password_or_recovery_key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                Icon(
                                    imageVector = if (passphraseVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = stringResource(
                                        if (passphraseVisible) R.string.hide_password else R.string.show_password
                                    ),
                                )
                            }
                        },
                    )
                    TextButton(
                        onClick = {
                            passphraseInput = GoogleDriveEncryptionStore.generateRandomPassphrase()
                            passphraseVisible = true
                        },
                    ) {
                        Text(stringResource(R.string.generate_recovery_key))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passphraseInput.isNotBlank(),
                    onClick = {
                        driveManager.saveEncryptionPassphrase(passphraseInput.trim().toCharArray())
                        showPassphraseDialog = false
                        passphraseInput = ""
                        message = resources.getString(R.string.backup_encryption_password_saved)
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPassphraseDialog = false; passphraseInput = "" }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Disconnect Confirmation Dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text(stringResource(R.string.disconnect_drive_title)) },
            text = {
                Text(stringResource(R.string.disconnect_drive_description))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        driveManager.disconnect()
                        refreshRemote()
                        message = resources.getString(R.string.disconnected_from_drive)
                    },
                ) { Text(stringResource(R.string.disconnect), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Google OAuth Client ID Dialog
    if (showClientIdDialog) {
        AlertDialog(
            onDismissRequest = { showClientIdDialog = false },
            title = { Text(stringResource(R.string.oauth_client_id_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.oauth_client_id_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = clientIdInput,
                        onValueChange = { clientIdInput = it },
                        label = { Text(stringResource(R.string.client_id_label)) },
                        placeholder = { Text(GoogleDriveConfig.DEFAULT_CLIENT_ID) },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (driveManager.preferences.customClientId != null) {
                        TextButton(
                            onClick = {
                                driveManager.preferences.customClientId = null
                                showClientIdDialog = false
                                message = resources.getString(R.string.reset_to_default_client_id)
                            },
                        ) {
                            Text(stringResource(R.string.reset_to_default), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = clientIdInput.trim()
                        driveManager.preferences.customClientId = trimmed.ifBlank { null }
                        showClientIdDialog = false
                        message = if (trimmed.isBlank()) {
                            resources.getString(R.string.using_default_client_id)
                        } else {
                            resources.getString(R.string.custom_client_id_saved)
                        }
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showClientIdDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Remote Restore Passphrase Prompt Dialog
    if (restorePassphrasePrompt && pendingRestoreRemote != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) { restorePassphrasePrompt = false; pendingRestoreRemote = null } },
            title = { Text(stringResource(R.string.enter_backup_password)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.backup_decrypt_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = restorePassphraseInput,
                        onValueChange = { restorePassphraseInput = it; restorePassphraseError = null },
                        label = { Text(stringResource(R.string.password_label)) },
                        singleLine = true,
                        isError = restorePassphraseError != null,
                        supportingText = restorePassphraseError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (restorePassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { restorePassphraseVisible = !restorePassphraseVisible }) {
                                Icon(
                                    imageVector = if (restorePassphraseVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = stringResource(
                                        if (restorePassphraseVisible) R.string.hide_password else R.string.show_password
                                    ),
                                )
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && restorePassphraseInput.isNotBlank(),
                    onClick = {
                        performRemoteRestore(pendingRestoreRemote!!, restorePassphraseInput.trim())
                    },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { restorePassphrasePrompt = false; pendingRestoreRemote = null },
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Remote Restore Confirmation Dialog
    pendingRestoreRemote?.takeIf { !restorePassphrasePrompt }?.let { remoteItem ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestoreRemote = null },
            title = { Text(stringResource(R.string.restore_drive_backup_title)) },
            text = {
                Text(stringResource(R.string.restore_backup_description))
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (remoteItem.isEncrypted && !driveManager.hasEncryptionPassphrase()) {
                            restorePassphrasePrompt = true
                        } else {
                            performRemoteRestore(remoteItem, null)
                        }
                    },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingRestoreRemote = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestore = null },
            title = { Text(stringResource(if (backup is BackupItem.Latest) R.string.revert_budget_title else R.string.restore_backup_title)) },
            text = {
                Text(if (backup is BackupItem.Latest) {
                    stringResource(R.string.revert_budget_description)
                } else {
                    stringResource(R.string.restore_backup_description)
                })
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    onBeforeRestore()
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                service.restore(budgetId, when (backup) {
                                    BackupItem.Latest -> BackupService.LATEST_ID
                                    is BackupItem.Archive -> backup.id
                                })
                            }
                        }.onSuccess { message = resources.getString(R.string.backup_restored) }
                            .onFailure { message = it.message ?: resources.getString(R.string.could_not_restore_backup) }
                        pendingRestore = null
                        busy = false
                        refresh()
                        onRestored()
                    }
                }) { Text(stringResource(if (backup is BackupItem.Latest) R.string.revert else R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmBackup) AlertDialog(
        onDismissRequest = { confirmBackup = false },
        title = { Text(stringResource(R.string.create_new_backup_title)) },
        text = { Text(stringResource(R.string.create_new_backup_description)) },
        confirmButton = { TextButton(onClick = { confirmBackup = false; makeBackup() }) { Text(stringResource(R.string.back_up)) } },
        dismissButton = { TextButton(onClick = { confirmBackup = false }) { Text(stringResource(R.string.cancel)) } },
    )

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.backups_title), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ActuaGroupLabel(stringResource(R.string.destination))
            ActuaFormCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.backup_location), fontWeight = FontWeight.Medium)
                            Text(
                                destination.name ?: stringResource(
                                    if (destination.uri == null) R.string.private_app_storage else R.string.selected_folder
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (destination.lastMirroredMillis > 0) Text(
                                stringResource(R.string.mirrored_time, relativeTime(destination.lastMirroredMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(enabled = !busy, onClick = { chooseFolder.launch(null) }) { Text(stringResource(R.string.change)) }
                    }
                    if (destination.uri != null) {
                        HorizontalDivider()
                        TextButton(enabled = !busy, onClick = {
                            destinations.reset()
                            destination = destinations.read()
                            message = resources.getString(R.string.using_private_app_storage)
                        }) { Text(stringResource(R.string.reset_to_default), color = MaterialTheme.colorScheme.error) }
                    }
                    destination.error?.let { Text(backupDestinationErrorMessage(it), color = MaterialTheme.colorScheme.error) }
                }
            }
            Text(
                stringResource(R.string.backups_storage_description, budgetId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Google Drive Backup Section
            ActuaGroupLabel(stringResource(R.string.drive_backup_section))
            ActuaFormCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.google_drive), fontWeight = FontWeight.Medium)
                            Text(
                                if (driveManager.isConnected()) {
                                    driveManager.getAccountEmail() ?: stringResource(R.string.connected)
                                } else stringResource(R.string.disconnected),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (driveManager.isConnected() && driveManager.preferences.lastSuccessfulBackupMillis > 0) {
                                Text(
                                    stringResource(
                                        R.string.last_uploaded,
                                        relativeTime(driveManager.preferences.lastSuccessfulBackupMillis),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (!driveManager.isConnected()) {
                            TextButton(
                                enabled = !busy && !authState.isAuthenticating,
                                onClick = { GoogleDriveAuthManager.startAuthorization(context) },
                            ) {
                                if (authState.isAuthenticating) CircularProgressIndicator(Modifier.padding(end = 4.dp))
                                Text(stringResource(R.string.connect))
                            }
                        } else {
                            TextButton(enabled = !busy, onClick = { showDisconnectDialog = true }) {
                                Text(stringResource(R.string.disconnect), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.oauth_client_id), fontWeight = FontWeight.Medium)
                            Text(
                                if (driveManager.preferences.customClientId != null) {
                                    stringResource(R.string.custom_client_id)
                                } else stringResource(R.string.default_client_id),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            enabled = !busy && !authState.isAuthenticating,
                            onClick = {
                                clientIdInput = driveManager.preferences.customClientId ?: ""
                                showClientIdDialog = true
                            },
                        ) {
                            Text(stringResource(R.string.configure))
                        }
                    }

                    if (driveManager.isConnected()) {
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.encryption_password_label), fontWeight = FontWeight.Medium)
                                Text(
                                    if (driveManager.hasEncryptionPassphrase()) {
                                        stringResource(R.string.encryption_protected)
                                    } else stringResource(R.string.encryption_password_required),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (driveManager.hasEncryptionPassphrase()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }
                            TextButton(enabled = !busy, onClick = { showPassphraseDialog = true }) {
                                Text(stringResource(
                                    if (driveManager.hasEncryptionPassphrase()) R.string.change else R.string.set_password
                                ))
                            }
                        }
                        HorizontalDivider()
                        OutlinedButton(
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { uploadToDriveNow() },
                        ) {
                            Text(stringResource(R.string.upload_to_drive_now))
                        }
                    }

                    val driveError = driveManager.preferences.lastBackupError ?: authState.error
                    driveError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                stringResource(R.string.drive_encryption_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(Sizes.primaryButtonHeight),
                shape = MaterialTheme.shapes.large,
                onClick = { if (backups.any { it is BackupItem.Latest }) confirmBackup = true else makeBackup() },
            ) {
                if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.back_up_now))
            }
            ActuaSecondaryButton(text = stringResource(R.string.import_backup), enabled = !busy, onClick = {
                importBackup.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            })
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            // Local Backups List
            ActuaGroupLabel(stringResource(R.string.available_backups))
            if (backups.isNotEmpty()) ActuaFormCard {
                backups.forEachIndexed { index, backup ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { pendingRestore = backup }
                            .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(when (backup) {
                                BackupItem.Latest -> stringResource(R.string.pre_restore_version)
                                is BackupItem.Archive -> DateFormat.getDateTimeInstance().format(Date.from(backup.modifiedAt))
                            })
                            Text(
                                stringResource(
                                    if (backup is BackupItem.Latest) R.string.tap_to_revert else R.string.tap_to_restore
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (backup is BackupItem.Archive) OutlinedButton(enabled = !busy, onClick = {
                            pendingExport = backup
                            export.launch(backup.id)
                        }) { Text(stringResource(R.string.export)) }
                    }
                    if (index != backups.lastIndex) ActuaCardDivider(inset = Spacing.lg)
                }
            }
            if (backups.isEmpty()) Text(stringResource(R.string.no_local_backups_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Google Drive Backups List
            if (driveManager.isConnected() && remoteBackups.isNotEmpty()) {
                ActuaGroupLabel(stringResource(R.string.drive_backups_label))
                ActuaFormCard {
                    remoteBackups.forEachIndexed { index, backup ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) { pendingRestoreRemote = backup }
                                .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(backup.name)
                                Text(
                                    formatFileSize(backup.sizeBytes) + if (backup.isEncrypted) {
                                        stringResource(R.string.encrypted_suffix)
                                    } else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(enabled = !busy, onClick = { pendingRestoreRemote = backup }) {
                                Text(stringResource(R.string.restore))
                            }
                        }
                        if (index != remoteBackups.lastIndex) ActuaCardDivider(inset = Spacing.lg)
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024L * 1024L))
}

@Composable
internal fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - timestamp).coerceAtLeast(0) / 1000)
    return when {
        seconds < 10 -> stringResource(R.string.just_now)
        seconds < 60 -> pluralStringResource(R.plurals.seconds_ago, seconds.toInt(), seconds)
        seconds < 3600 -> pluralStringResource(R.plurals.minutes_ago, (seconds / 60).toInt(), seconds / 60)
        seconds < 86400 -> pluralStringResource(R.plurals.hours_ago, (seconds / 3600).toInt(), seconds / 3600)
        else -> pluralStringResource(R.plurals.days_ago, (seconds / 86400).toInt(), seconds / 86400)
    }
}

/** Mirrors the persisted mirror-failure codes (and legacy English messages) to display text. */
@Composable
internal fun backupDestinationErrorMessage(error: String): String = stringResource(when (error) {
    BackupDestinationError.CANNOT_CREATE_DIRECTORY.persistedValue,
    "The selected folder does not allow creating directories" -> R.string.backup_folder_cannot_create_directory
    BackupDestinationError.CANNOT_CREATE_BACKUP_FILE.persistedValue,
    "The selected folder did not create the backup file" -> R.string.backup_folder_cannot_create_file
    else -> R.string.backup_folder_cannot_write
})
