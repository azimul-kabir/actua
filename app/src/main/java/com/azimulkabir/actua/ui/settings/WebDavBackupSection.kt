package com.azimulkabir.actua.ui.settings

import android.content.res.Resources
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.backup.InvalidPassphraseException
import com.azimulkabir.actua.data.budget.backup.PassphraseRequiredException
import com.azimulkabir.actua.data.budget.backup.RemoteBackupItem
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveEncryptionStore
import com.azimulkabir.actua.data.budget.webdav.WebDavBackupManager
import com.azimulkabir.actua.data.budget.webdav.WebDavConfig
import com.azimulkabir.actua.data.budget.webdav.WebDavException
import com.azimulkabir.actua.data.network.ServerCertificateInfo
import com.azimulkabir.actua.data.network.TrustedCertificateStore
import com.azimulkabir.actua.data.network.inspectServerCertificate
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** A certificate the user has not trusted yet, and the connection that should be retried once they do. */
private class PendingWebDavTrust(
    val info: ServerCertificateInfo,
    val replacingExistingTrust: Boolean,
    val config: WebDavConfig,
)

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

    var config by remember { mutableStateOf(manager.config()) }
    var hasPassphrase by remember { mutableStateOf(manager.hasEncryptionPassphrase()) }
    var remoteBackups by remember { mutableStateOf<List<RemoteBackupItem>>(emptyList()) }
    var statusRevision by remember { mutableStateOf(0) }

    var showSetup by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var userInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var folderInput by remember { mutableStateOf(WebDavConfig.DEFAULT_FOLDER) }
    var allowHttpInput by remember { mutableStateOf(false) }
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
            remoteBackups = runCatching {
                withContext(Dispatchers.IO) { manager.listRemoteBackups(budgetId) }
            }.getOrDefault(emptyList())
        }
    }

    fun connect(newConfig: WebDavConfig) {
        onBusyChange(true)
        setupError = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { manager.connect(newConfig) } }
                .onSuccess {
                    config = manager.config()
                    showSetup = false
                    passwordInput = ""
                    onMessage(resources.getString(R.string.webdav_connected))
                    refreshRemote()
                }
                .onFailure { error ->
                    if (error is WebDavException.Tls) {
                        // Offer to trust the certificate the server presents, like Actual servers do.
                        val info = runCatching {
                            withContext(Dispatchers.IO) { inspectServerCertificate(newConfig.serverUrl.trim()) }
                        }.getOrNull()
                        if (info != null) {
                            pendingTrust = PendingWebDavTrust(info, certificates.hasTrust(info.host), newConfig)
                        } else {
                            setupError = resources.getString(R.string.could_not_inspect_certificate)
                        }
                    } else {
                        setupError = webDavErrorMessage(resources, error)
                    }
                }
            onBusyChange(false)
            statusRevision++
        }
    }

    fun uploadNow() {
        if (!manager.hasEncryptionPassphrase()) {
            showPassphraseDialog = true
            return
        }
        onBusyChange(true)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val archive = service.makeBackup(budgetId)
                    manager.uploadBackup(budgetId, archive)
                }
            }.onSuccess {
                onMessage(resources.getString(R.string.webdav_uploaded))
                refreshRemote()
            }.onFailure { error ->
                val text = webDavErrorMessage(resources, error)
                manager.recordError(text)
                onMessage(text)
            }
            onBusyChange(false)
            onLocalBackupsChanged()
            statusRevision++
        }
    }

    fun performRestore(item: RemoteBackupItem, passphrase: String?) {
        onBusyChange(true)
        onBeforeRestore()
        scope.launch {
            runCatching {
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
                        onMessage(webDavErrorMessage(resources, error))
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
        AlertDialog(
            onDismissRequest = { if (!busy) showSetup = false },
            title = { Text(stringResource(R.string.webdav_setup_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it; setupError = null },
                        label = { Text(stringResource(R.string.webdav_server_url)) },
                        placeholder = { Text(stringResource(R.string.webdav_server_url_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it; setupError = null },
                        label = { Text(stringResource(R.string.webdav_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; setupError = null },
                        label = { Text(stringResource(R.string.webdav_app_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.hide_password else R.string.show_password,
                                    ),
                                )
                            }
                        },
                    )
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it; setupError = null },
                        label = { Text(stringResource(R.string.webdav_folder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable { allowHttpInput = !allowHttpInput },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = allowHttpInput, onCheckedChange = { allowHttpInput = it })
                        Text(stringResource(R.string.webdav_allow_http), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (allowHttpInput) {
                        Text(
                            stringResource(R.string.webdav_http_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    setupError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && urlInput.isNotBlank() && userInput.isNotBlank() && passwordInput.isNotEmpty(),
                    onClick = {
                        connect(
                            WebDavConfig(
                                serverUrl = urlInput,
                                username = userInput,
                                password = passwordInput,
                                folder = folderInput,
                                allowInsecureHttp = allowHttpInput,
                            ),
                        )
                    },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.connect))
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showSetup = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingTrust?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingTrust = null },
            title = { Text(stringResource(R.string.server_certificate_untrusted)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.server_certificate_untrusted_description))
                    Text(stringResource(R.string.certificate_host, pending.info.host), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.certificate_issuer, pending.info.issuer), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.certificate_valid_from, pending.info.validFrom), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.certificate_valid_until, pending.info.validUntil), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.sha256_fingerprint), fontWeight = FontWeight.SemiBold)
                    Text(pending.info.sha256Fingerprint, style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.certificate_trust_scope, pending.info.host),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    certificates.trust(pending.info.host, pending.info.sha256Fingerprint)
                    pendingTrust = null
                    connect(pending.config)
                }) { Text(stringResource(R.string.trust_certificate)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingTrust = null }) { Text(stringResource(R.string.cancel)) }
            },
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
                        urlInput = ""; userInput = ""; passwordInput = ""; passwordVisible = false
                        folderInput = WebDavConfig.DEFAULT_FOLDER; allowHttpInput = false; setupError = null
                        showSetup = true
                    }) { Text(stringResource(R.string.connect)) }
                } else {
                    TextButton(enabled = !busy, onClick = {
                        val current = config
                        urlInput = current?.serverUrl.orEmpty(); userInput = current?.username.orEmpty()
                        passwordInput = current?.password.orEmpty(); passwordVisible = false
                        folderInput = current?.folder ?: WebDavConfig.DEFAULT_FOLDER
                        allowHttpInput = current?.allowInsecureHttp ?: false; setupError = null
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
        stringResource(R.string.webdav_encryption_note),
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

/** The Backups screen's wording for each way a WebDAV operation can fail. */
internal fun webDavErrorMessage(resources: Resources, error: Throwable): String = when (error) {
    is WebDavException.InvalidSettings -> resources.getString(
        when (error.reason) {
            WebDavException.InvalidSettings.Reason.MISSING_CREDENTIALS -> R.string.webdav_error_missing_credentials
            WebDavException.InvalidSettings.Reason.INVALID_URL -> R.string.webdav_error_invalid_url
            WebDavException.InvalidSettings.Reason.INSECURE_HTTP -> R.string.webdav_error_insecure_http
            WebDavException.InvalidSettings.Reason.INVALID_FOLDER -> R.string.webdav_error_invalid_folder
        },
    )
    is WebDavException.Unauthorized -> resources.getString(R.string.webdav_error_unauthorized)
    is WebDavException.NotFound -> resources.getString(R.string.webdav_error_not_found)
    is WebDavException.InsufficientStorage -> resources.getString(R.string.webdav_error_storage_full)
    is WebDavException.Tls -> resources.getString(R.string.webdav_error_tls)
    is WebDavException.Redirect -> resources.getString(R.string.webdav_error_redirect)
    is WebDavException.MoveUnsupported -> resources.getString(R.string.webdav_error_move_unsupported)
    is WebDavException.UploadIncomplete -> resources.getString(R.string.webdav_error_incomplete)
    is WebDavException.Server -> resources.getString(R.string.webdav_error_server, error.status)
    is WebDavException.Network -> resources.getString(R.string.webdav_error_network)
    else -> error.message ?: resources.getString(R.string.webdav_error_generic)
}
