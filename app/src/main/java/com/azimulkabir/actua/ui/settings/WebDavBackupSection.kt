package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.backup.InvalidPassphraseException
import com.azimulkabir.actua.data.budget.backup.PassphraseRequiredException
import com.azimulkabir.actua.data.budget.backup.RemoteBackupItem
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveEncryptionStore
import com.azimulkabir.actua.data.budget.webdav.WebDavBackupManager
import com.azimulkabir.actua.data.budget.webdav.WebDavConfig
import com.azimulkabir.actua.data.network.TrustedCertificateStore
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.util.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The WebDAV part of the Backups screen: set up a server, back up now, list what is there and restore.
 * It shares [busy] and the screen's message line with the rest of the screen, and keeps its own dialogs.
 */
@Composable
fun WebDavBackupSection(
    budgetId: String,
    service: BackupService,
    busy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onBeforeRestore: () -> Unit,
    onRestored: () -> Unit,
    onLocalBackupsChanged: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val manager = remember { WebDavBackupManager(context) }
    val certificates = remember { TrustedCertificateStore(context) }
    val localNetwork = rememberLocalNetworkGate()

    var config by remember { mutableStateOf(manager.config()) }
    var hasPassphrase by remember { mutableStateOf(manager.hasEncryptionPassphrase()) }
    var remoteBackups by remember { mutableStateOf<List<RemoteBackupItem>>(emptyList()) }
    var statusRevision by remember { mutableStateOf(0) }

    var showSetup by remember { mutableStateOf(false) }
    var setupInitial by remember { mutableStateOf<WebDavConfig?>(null) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var pendingTrust by remember { mutableStateOf<PendingWebDavTrust?>(null) }

    var showPassphraseDialog by remember { mutableStateOf(false) }
    var passphraseInput by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }

    var showDisconnect by remember { mutableStateOf(false) }

    var pendingRestore by remember { mutableStateOf<RemoteBackupItem?>(null) }
    var restorePassphrasePrompt by remember { mutableStateOf(false) }
    var restorePassphraseInput by remember { mutableStateOf("") }
    var restorePassphraseVisible by remember { mutableStateOf(false) }
    var restorePassphraseError by remember { mutableStateOf<String?>(null) }

    fun refreshRemote() {
        if (!manager.isConfigured()) {
            remoteBackups = emptyList()
            return
        }
        scope.launch {
            remoteBackups = suspendRunCatching {
                withContext(Dispatchers.IO) { manager.listRemoteBackups(budgetId) }
            }.getOrDefault(emptyList())
        }
    }

    fun connectNow(newConfig: WebDavConfig) {
        onBusyChange(true)
        scope.launch {
            when (val result = connectWebDav(manager, certificates, resources, newConfig)) {
                is WebDavConnectResult.Connected -> {
                    config = manager.config()
                    showSetup = false
                    onMessage(resources.getString(R.string.webdav_connected))
                    refreshRemote()
                }
                is WebDavConnectResult.NeedsTrust -> pendingTrust = result.pending
                is WebDavConnectResult.Failed -> setupError = result.message
            }
            onBusyChange(false)
            statusRevision++
        }
    }

    fun connect(newConfig: WebDavConfig) {
        setupError = null
        // On Android 17 a server on the home network needs the Nearby devices permission first.
        localNetwork.check(
            newConfig.serverUrl.trim(),
            { connectNow(newConfig) },
            { setupError = resources.getString(R.string.webdav_error_local_network_denied) },
        )
    }

    fun uploadNowChecked() {
        onBusyChange(true)
        scope.launch {
            suspendRunCatching {
                withContext(Dispatchers.IO) {
                    val archive = service.makeBackup(budgetId)
                    manager.uploadBackup(budgetId, archive)
                }
            }.onSuccess {
                onMessage(resources.getString(R.string.webdav_uploaded))
                refreshRemote()
            }.onFailure { error ->
                val text = withContext(Dispatchers.IO) { manager.describe(error) }
                manager.recordError(text)
                onMessage(text)
            }
            onBusyChange(false)
            onLocalBackupsChanged()
            statusRevision++
        }
    }

    fun uploadNow() {
        if (!manager.hasEncryptionPassphrase()) {
            showPassphraseDialog = true
            return
        }
        localNetwork.check(
            manager.config()?.serverUrl.orEmpty(),
            { uploadNowChecked() },
            { onMessage(resources.getString(R.string.webdav_error_local_network_denied)) },
        )
    }

    fun performRestore(item: RemoteBackupItem, passphrase: String?) {
        onBusyChange(true)
        onBeforeRestore()
        scope.launch {
            suspendRunCatching {
                withContext(Dispatchers.IO) {
                    manager.restoreRemoteBackup(
                        remoteBackupId = item.id,
                        passphrase = passphrase?.toCharArray(),
                        targetBudgetId = budgetId,
                    )
                }
            }.onSuccess {
                onMessage(resources.getString(R.string.backup_restored))
                pendingRestore = null
                restorePassphrasePrompt = false
                restorePassphraseInput = ""
                restorePassphraseError = null
                onBusyChange(false)
                onLocalBackupsChanged()
                refreshRemote()
                onRestored()
            }.onFailure { error ->
                onBusyChange(false)
                when (error) {
                    is InvalidPassphraseException -> {
                        restorePassphraseError = resources.getString(R.string.incorrect_password_decrypt)
                        restorePassphrasePrompt = true
                    }
                    is PassphraseRequiredException -> {
                        restorePassphraseError = resources.getString(R.string.password_required_decrypt)
                        restorePassphrasePrompt = true
                    }
                    else -> {
                        onMessage(withContext(Dispatchers.IO) { manager.describe(error) })
                        pendingRestore = null
                        restorePassphrasePrompt = false
                    }
                }
            }
        }
    }

    LaunchedEffect(budgetId, config) { refreshRemote() }

    // --- Dialogs -----------------------------------------------------------------------------------------

    if (showSetup) {
        WebDavSetupDialog(
            initial = setupInitial,
            busy = busy,
            error = setupError,
            onEdited = { setupError = null },
            onConnect = ::connect,
            onDismiss = { showSetup = false },
        )
    }

    pendingTrust?.let { pending ->
        WebDavCertificateDialog(
            pending = pending,
            onTrust = {
                certificates.trust(pending.info.host, pending.info.sha256Fingerprint)
                pendingTrust = null
                connect(pending.config)
            },
            onDismiss = { pendingTrust = null },
        )
    }

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
                                        if (passphraseVisible) R.string.hide_password else R.string.show_password,
                                    ),
                                )
                            }
                        },
                    )
                    TextButton(onClick = {
                        passphraseInput = GoogleDriveEncryptionStore.generateRandomPassphrase()
                        passphraseVisible = true
                    }) { Text(stringResource(R.string.generate_recovery_key)) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passphraseInput.isNotBlank(),
                    onClick = {
                        manager.saveEncryptionPassphrase(passphraseInput.trim().toCharArray())
                        hasPassphrase = true
                        showPassphraseDialog = false
                        passphraseInput = ""
                        onMessage(resources.getString(R.string.backup_encryption_password_saved))
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPassphraseDialog = false; passphraseInput = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDisconnect) {
        AlertDialog(
            onDismissRequest = { showDisconnect = false },
            title = { Text(stringResource(R.string.webdav_disconnect_title)) },
            text = { Text(stringResource(R.string.webdav_disconnect_description)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnect = false
                    manager.disconnect()
                    config = null
                    hasPassphrase = false
                    remoteBackups = emptyList()
                    onMessage(resources.getString(R.string.webdav_disconnected))
                }) { Text(stringResource(R.string.disconnect), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnect = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (restorePassphrasePrompt && pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) { restorePassphrasePrompt = false; pendingRestore = null } },
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
                                        if (restorePassphraseVisible) R.string.hide_password else R.string.show_password,
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
                    onClick = { performRestore(pendingRestore!!, restorePassphraseInput.trim()) },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { restorePassphrasePrompt = false; pendingRestore = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingRestore?.takeIf { !restorePassphrasePrompt }?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestore = null },
            title = { Text(stringResource(R.string.webdav_restore_title)) },
            text = { Text(stringResource(R.string.restore_backup_description)) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (item.isEncrypted && !manager.hasEncryptionPassphrase()) {
                            restorePassphrasePrompt = true
                        } else {
                            performRestore(item, null)
                        }
                    },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // --- Content -----------------------------------------------------------------------------------------

    ActuaGroupLabel(stringResource(R.string.webdav_backup_section))
    ActuaFormCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.webdav_server_label), fontWeight = FontWeight.Medium)
                    Text(
                        config?.let { "${it.username} · ${it.serverUrl}" } ?: stringResource(R.string.webdav_not_set_up),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val lastSuccess = manager.lastSuccessfulBackupMillis
                    if (config != null && lastSuccess > 0) {
                        Text(
                            stringResource(R.string.last_uploaded, relativeTime(lastSuccess)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (config == null) {
                    TextButton(enabled = !busy, onClick = {
                        setupInitial = null; setupError = null
                        showSetup = true
                    }) { Text(stringResource(R.string.connect)) }
                } else {
                    TextButton(enabled = !busy, onClick = {
                        setupInitial = config; setupError = null
                        showSetup = true
                    }) { Text(stringResource(R.string.change)) }
                    TextButton(enabled = !busy, onClick = { showDisconnect = true }) {
                        Text(stringResource(R.string.disconnect), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (config != null) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.encryption_password_label), fontWeight = FontWeight.Medium)
                        Text(
                            if (hasPassphrase) stringResource(R.string.encryption_protected)
                            else stringResource(R.string.encryption_password_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasPassphrase) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(enabled = !busy, onClick = { showPassphraseDialog = true }) {
                        Text(stringResource(if (hasPassphrase) R.string.change else R.string.set_password))
                    }
                }
                HorizontalDivider()
                OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { uploadNow() }) {
                    Text(stringResource(R.string.webdav_back_up_now))
                }
                manager.lastBackupError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Text(
        stringResource(R.string.webdav_encryption_note) + " " + stringResource(R.string.webdav_schedule_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (config != null && remoteBackups.isNotEmpty()) {
        ActuaGroupLabel(stringResource(R.string.webdav_backups_label))
        ActuaFormCard {
            remoteBackups.forEachIndexed { index, backup ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) { pendingRestore = backup }
                        .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(backup.name)
                        Text(
                            webDavFileSize(backup.sizeBytes) + stringResource(R.string.encrypted_suffix),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(enabled = !busy, onClick = { pendingRestore = backup }) {
                        Text(stringResource(R.string.restore))
                    }
                }
                if (index != remoteBackups.lastIndex) ActuaCardDivider(inset = Spacing.lg)
            }
        }
    }

    // Reads the revision so the card above refreshes the last-upload time and error after an operation.
    @Suppress("UNUSED_EXPRESSION") statusRevision
}

private fun webDavFileSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024L * 1024L))
}
