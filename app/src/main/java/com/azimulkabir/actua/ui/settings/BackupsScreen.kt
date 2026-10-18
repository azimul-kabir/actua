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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.BackupDestinationManager
import com.azimulkabir.actua.data.budget.BackupItem
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveAuthManager
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveBackupItem
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveBackupManager
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

@Composable
fun BackupsScreen(
    budgetId: String,
    onBack: () -> Unit,
    onBeforeRestore: () -> Unit,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
                    message = "Backup created."
                    if (driveManager.isConnected() && driveManager.hasEncryptionPassphrase()) {
                        message = "Backup created and queued for Google Drive upload."
                    }
                }
                .onFailure { message = it.message ?: "Could not create backup." }
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
                message = "Uploaded to Google Drive."
                refreshRemote()
            }.onFailure {
                message = it.message ?: "Google Drive upload failed."
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
                message = "Backup restored."
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
                    restorePassphraseError = "Incorrect password. Could not decrypt backup."
                    restorePassphrasePrompt = true
                } else if (error is com.azimulkabir.actua.data.budget.gdrive.PassphraseRequiredException) {
                    restorePassphraseError = "Password required to decrypt this backup."
                    restorePassphrasePrompt = true
                } else {
                    message = error.message ?: "Could not restore backup."
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
            }.onSuccess { message = "Existing backups mirrored." }
                .onFailure { message = it.message ?: "Could not use this folder." }
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
            }.onSuccess { message = "Backup exported." }
                .onFailure { message = it.message ?: "Could not export backup." }
            busy = false
        }
    }

    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { service.importArchive(budgetId, it) }
                        ?: error("Could not read the selected file")
                }
            }.onSuccess { message = "Backup imported. Tap it below when you are ready to restore." }
                .onFailure { message = it.message ?: "Could not import this backup." }
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
            title = { Text("Backup encryption password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your backups will be encrypted on this device with AES-256-GCM before upload. Save this password or recovery key — Actual and Google cannot recover your backups without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text("Password or recovery key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                Icon(
                                    imageVector = if (passphraseVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (passphraseVisible) "Hide password" else "Show password",
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
                        Text("Generate secure recovery key")
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
                        message = "Backup encryption password saved."
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPassphraseDialog = false; passphraseInput = "" }) { Text("Cancel") }
            },
        )
    }

    // Disconnect Confirmation Dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Google Drive?") },
            text = {
                Text("Google Drive authorization will be revoked and automatic cloud backups will stop. Existing backups in your Google Drive will remain.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        driveManager.disconnect()
                        refreshRemote()
                        message = "Disconnected from Google Drive."
                    },
                ) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Remote Restore Passphrase Prompt Dialog
    if (restorePassphrasePrompt && pendingRestoreRemote != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) { restorePassphrasePrompt = false; pendingRestoreRemote = null } },
            title = { Text("Enter backup password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This backup is encrypted with client-side AES-256-GCM. Enter your backup password or recovery key to decrypt and restore it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = restorePassphraseInput,
                        onValueChange = { restorePassphraseInput = it; restorePassphraseError = null },
                        label = { Text("Password") },
                        singleLine = true,
                        isError = restorePassphraseError != null,
                        supportingText = restorePassphraseError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (restorePassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { restorePassphraseVisible = !restorePassphraseVisible }) {
                                Icon(
                                    imageVector = if (restorePassphraseVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (restorePassphraseVisible) "Hide password" else "Show password",
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
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { restorePassphrasePrompt = false; pendingRestoreRemote = null },
                ) { Text("Cancel") }
            },
        )
    }

    // Remote Restore Confirmation Dialog
    pendingRestoreRemote?.takeIf { !restorePassphrasePrompt }?.let { remoteItem ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestoreRemote = null },
            title = { Text("Restore Google Drive backup?") },
            text = {
                Text("Your current budget will be preserved for one-tap revert. Restoring disconnects this copy from server sync.")
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
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingRestoreRemote = null }) { Text("Cancel") }
            },
        )
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestore = null },
            title = { Text(if (backup is BackupItem.Latest) "Revert budget?" else "Restore backup?") },
            text = {
                Text(if (backup is BackupItem.Latest) {
                    "Replace this restored budget with the version that was active immediately before the restore?"
                } else {
                    "Your current budget will be preserved for one-tap revert. Restoring disconnects this copy from server sync."
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
                        }.onSuccess { message = "Backup restored." }
                            .onFailure { message = it.message ?: "Could not restore backup." }
                        pendingRestore = null
                        busy = false
                        refresh()
                        onRestored()
                    }
                }) { Text(if (backup is BackupItem.Latest) "Revert" else "Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }

    if (confirmBackup) AlertDialog(
        onDismissRequest = { confirmBackup = false },
        title = { Text("Create a new backup?") },
        text = { Text("This replaces the one-tap pre-restore version. The restored budget will remain available as a normal backup.") },
        confirmButton = { TextButton(onClick = { confirmBackup = false; makeBackup() }) { Text("Back up") } },
        dismissButton = { TextButton(onClick = { confirmBackup = false }) { Text("Cancel") } },
    )

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Backups", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ActuaGroupLabel("Destination")
            ActuaFormCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Backup location", fontWeight = FontWeight.Medium)
                            Text(destination.name ?: "Private app storage", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (destination.lastMirroredMillis > 0) Text(
                                "Mirrored ${relativeTime(destination.lastMirroredMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(enabled = !busy, onClick = { chooseFolder.launch(null) }) { Text("Change") }
                    }
                    if (destination.uri != null) {
                        HorizontalDivider()
                        TextButton(enabled = !busy, onClick = {
                            destinations.reset()
                            destination = destinations.read()
                            message = "Using private app storage only."
                        }) { Text("Reset to default", color = MaterialTheme.colorScheme.error) }
                    }
                    destination.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            Text(
                "Backups stay in private app storage and are automatically mirrored under Actua/$budgetId/ when a folder is selected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Google Drive Backup Section
            ActuaGroupLabel("Google Drive backup")
            ActuaFormCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Google Drive", fontWeight = FontWeight.Medium)
                            Text(
                                if (driveManager.isConnected()) {
                                    driveManager.getAccountEmail() ?: "Connected"
                                } else "Disconnected",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (driveManager.isConnected() && driveManager.preferences.lastSuccessfulBackupMillis > 0) {
                                Text(
                                    "Last uploaded ${relativeTime(driveManager.preferences.lastSuccessfulBackupMillis)}",
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
                                Text("Connect")
                            }
                        } else {
                            TextButton(enabled = !busy, onClick = { showDisconnectDialog = true }) {
                                Text("Disconnect", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (driveManager.isConnected()) {
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Encryption password", fontWeight = FontWeight.Medium)
                                Text(
                                    if (driveManager.hasEncryptionPassphrase()) "Protected (AES-256-GCM)" else "Password required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (driveManager.hasEncryptionPassphrase()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }
                            TextButton(enabled = !busy, onClick = { showPassphraseDialog = true }) {
                                Text(if (driveManager.hasEncryptionPassphrase()) "Change" else "Set password")
                            }
                        }
                        HorizontalDivider()
                        OutlinedButton(
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { uploadToDriveNow() },
                        ) {
                            Text("Upload to Drive now")
                        }
                    }

                    val driveError = driveManager.preferences.lastBackupError ?: authState.error
                    driveError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                "Drive backups are encrypted on-device before transmission (drive.file scope only). Google cannot read your budget contents.",
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
                Text("Back up now")
            }
            ActuaSecondaryButton(text = "Import backup", enabled = !busy, onClick = {
                importBackup.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            })
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            // Local Backups List
            ActuaGroupLabel("Available backups")
            if (backups.isNotEmpty()) ActuaFormCard {
                backups.forEachIndexed { index, backup ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { pendingRestore = backup }
                            .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(when (backup) {
                                BackupItem.Latest -> "Pre-restore version"
                                is BackupItem.Archive -> DateFormat.getDateTimeInstance().format(Date.from(backup.modifiedAt))
                            })
                            Text("Tap to ${if (backup is BackupItem.Latest) "revert" else "restore"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (backup is BackupItem.Archive) OutlinedButton(enabled = !busy, onClick = {
                            pendingExport = backup
                            export.launch(backup.id)
                        }) { Text("Export") }
                    }
                    if (index != backups.lastIndex) ActuaCardDivider(inset = Spacing.lg)
                }
            }
            if (backups.isEmpty()) Text("No local backups yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Google Drive Backups List
            if (driveManager.isConnected() && remoteBackups.isNotEmpty()) {
                ActuaGroupLabel("Google Drive backups")
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
                                    "${formatFileSize(backup.sizeBytes)}${if (backup.isEncrypted) " • Encrypted" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(enabled = !busy, onClick = { pendingRestoreRemote = backup }) {
                                Text("Restore")
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

internal fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - timestamp).coerceAtLeast(0) / 1000)
    return when {
        seconds < 10 -> "just now"
        seconds < 60 -> "$seconds sec ago"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86400 -> "${seconds / 3600} hr ago"
        else -> "${seconds / 86400} days ago"
    }
}
