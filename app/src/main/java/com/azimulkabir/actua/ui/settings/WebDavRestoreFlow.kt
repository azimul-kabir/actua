package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.azimulkabir.actua.data.budget.BudgetMetadata
import com.azimulkabir.actua.data.budget.backup.EncryptedBackupPipeline
import com.azimulkabir.actua.data.budget.backup.InvalidPassphraseException
import com.azimulkabir.actua.data.budget.backup.PassphraseRequiredException
import com.azimulkabir.actua.data.budget.backup.RemoteBackupItem
import com.azimulkabir.actua.data.budget.webdav.WebDavBackupManager
import com.azimulkabir.actua.data.budget.webdav.WebDavConfig
import com.azimulkabir.actua.data.network.TrustedCertificateStore
import com.azimulkabir.actua.util.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * "Restore from WebDAV" for a device with no budget yet (and for Connection and Data): [start] begins the flow
 * and [dialogs] draws whichever step is open. Call [dialogs] once, anywhere in the screen's composition.
 */
@Stable
class WebDavRestore internal constructor(
    val start: () -> Unit,
    val dialogs: @Composable () -> Unit,
)

/**
 * Connects to a WebDAV server if needed, lists the backups of every budget on it, asks for the backup password
 * and restores the chosen one as a new standalone budget; [onRestored] then receives that budget. The backup
 * password that was typed is kept as the WebDAV backup password so backups can carry on without asking again.
 * Failures are reported through [onError] and leave the device unchanged.
 */
@Composable
fun rememberWebDavRestore(
    onRestored: (BudgetMetadata) -> Unit,
    onError: (String) -> Unit,
): WebDavRestore {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val manager = remember { WebDavBackupManager(context) }
    val certificates = remember { TrustedCertificateStore(context) }
    val localNetwork = rememberLocalNetworkGate()
    val currentOnRestored by rememberUpdatedState(onRestored)
    val currentOnError by rememberUpdatedState(onError)

    var showSetup by remember { mutableStateOf(false) }
    var setupInitial by remember { mutableStateOf<WebDavConfig?>(null) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var pendingTrust by remember { mutableStateOf<PendingWebDavTrust?>(null) }

    var showBrowse by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<RemoteBackupItem>>(emptyList()) }
    var listError by remember { mutableStateOf<String?>(null) }

    var selected by remember { mutableStateOf<RemoteBackupItem?>(null) }
    var showPassphrase by remember { mutableStateOf(false) }
    var passphraseInput by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    var passphraseError by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }

    fun browse() {
        showBrowse = true
        loading = true
        listError = null
        scope.launch {
            suspendRunCatching { withContext(Dispatchers.IO) { manager.listRemoteBackups(null) } }
                .onSuccess { backups = it }
                .onFailure { error ->
                    backups = emptyList()
                    listError = withContext(Dispatchers.IO) { manager.describe(error) }
                }
            loading = false
        }
    }

    fun connectNow(config: WebDavConfig) {
        connecting = true
        scope.launch {
            when (val result = connectWebDav(manager, certificates, resources, config)) {
                is WebDavConnectResult.Connected -> {
                    showSetup = false
                    browse()
                }
                is WebDavConnectResult.NeedsTrust -> pendingTrust = result.pending
                is WebDavConnectResult.Failed -> setupError = result.message
            }
            connecting = false
        }
    }

    fun connect(config: WebDavConfig) {
        setupError = null
        // On Android 17 a server on the home network needs the Nearby devices permission first.
        localNetwork.check(
            config.serverUrl.trim(),
            { connectNow(config) },
            { setupError = resources.getString(R.string.webdav_error_local_network_denied) },
        )
    }

    fun restore(item: RemoteBackupItem, passphrase: String?) {
        restoring = true
        scope.launch {
            suspendRunCatching {
                withContext(Dispatchers.IO) {
                    val imported = manager.restoreRemoteBackup(
                        remoteBackupId = item.id,
                        passphrase = passphrase?.toCharArray(),
                        targetBudgetId = null,
                    )
                    // The password just proved itself against this backup: keep it for the next one.
                    if (passphrase != null && !manager.hasEncryptionPassphrase()) {
                        manager.saveEncryptionPassphrase(passphrase.toCharArray())
                    }
                    imported
                }
            }.onSuccess { imported ->
                showBrowse = false
                showPassphrase = false
                selected = null
                passphraseInput = ""
                restoring = false
                currentOnRestored(imported)
            }.onFailure { error ->
                restoring = false
                if (error is InvalidPassphraseException || error is PassphraseRequiredException) {
                    passphraseError = resources.getString(
                        if (error is InvalidPassphraseException) R.string.incorrect_password_decrypt
                        else R.string.password_required_decrypt,
                    )
                    showPassphrase = true
                } else {
                    showBrowse = false
                    showPassphrase = false
                    selected = null
                    currentOnError(withContext(Dispatchers.IO) { manager.describe(error) })
                }
            }
        }
    }

    fun start() {
        setupError = null
        if (manager.isConfigured()) {
            localNetwork.check(
                manager.config()?.serverUrl.orEmpty(),
                { browse() },
                { currentOnError(resources.getString(R.string.webdav_error_local_network_denied)) },
            )
        } else {
            setupInitial = null
            showSetup = true
        }
    }

    return WebDavRestore(start = ::start) {
        if (showSetup) {
            WebDavSetupDialog(
                initial = setupInitial,
                busy = connecting,
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

        if (showBrowse) {
            val encryptedSuffix = stringResource(R.string.encrypted_suffix)
            AlertDialog(
                onDismissRequest = { if (!loading && !restoring) showBrowse = false },
                title = { Text(stringResource(R.string.webdav_restore_from)) },
                text = {
                    when {
                        loading || restoring -> Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                            Text(stringResource(if (restoring) R.string.webdav_restore_restoring else R.string.webdav_restore_loading))
                        }
                        listError != null -> Text(listError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        backups.isEmpty() -> Text(stringResource(R.string.webdav_restore_no_backups))
                        else -> Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            backups.forEach { backup ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selected = backup
                                            if (backup.isEncrypted && !manager.hasEncryptionPassphrase()) {
                                                passphraseInput = ""
                                                passphraseError = null
                                                showPassphrase = true
                                            } else {
                                                restore(backup, null)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                ) {
                                    Text(backup.budgetId ?: backup.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        webDavRestoreSubtitle(backup) + if (backup.isEncrypted) encryptedSuffix else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (listError != null && !loading) {
                        TextButton(onClick = {
                            setupInitial = manager.config()
                            setupError = null
                            showBrowse = false
                            showSetup = true
                        }) { Text(stringResource(R.string.webdav_restore_change_connection)) }
                    }
                },
                dismissButton = {
                    TextButton(enabled = !loading && !restoring, onClick = { showBrowse = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        val chosen = selected
        if (showPassphrase && chosen != null) {
            AlertDialog(
                onDismissRequest = { if (!restoring) { showPassphrase = false; selected = null } },
                title = { Text(stringResource(R.string.enter_backup_password)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.backup_decrypt_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = passphraseInput,
                            onValueChange = { passphraseInput = it; passphraseError = null },
                            label = { Text(stringResource(R.string.password_label)) },
                            singleLine = true,
                            isError = passphraseError != null,
                            supportingText = passphraseError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
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
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !restoring && passphraseInput.isNotBlank(),
                        onClick = { restore(chosen, passphraseInput.trim()) },
                    ) {
                        if (restoring) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.restore))
                    }
                },
                dismissButton = {
                    TextButton(enabled = !restoring, onClick = { showPassphrase = false; selected = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

/** The backup's date (from its file name) and size, or the file name when it has no date. */
private fun webDavRestoreSubtitle(backup: RemoteBackupItem): String {
    val date = EncryptedBackupPipeline.parseBackupDate(backup.name)
        ?.let { DateFormat.getDateTimeInstance().format(Date.from(it)) }
    return (date ?: backup.name) + " • " + webDavRestoreSize(backup.sizeBytes)
}

private fun webDavRestoreSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024L * 1024L))
}
