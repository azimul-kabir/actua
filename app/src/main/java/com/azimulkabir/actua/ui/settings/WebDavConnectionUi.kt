package com.azimulkabir.actua.ui.settings

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.webdav.WebDavBackupManager
import com.azimulkabir.actua.data.budget.webdav.WebDavConfig
import com.azimulkabir.actua.data.budget.webdav.WebDavException
import com.azimulkabir.actua.data.network.ServerCertificateInfo
import com.azimulkabir.actua.data.network.TrustedCertificateStore
import com.azimulkabir.actua.data.network.inspectServerCertificate
import com.azimulkabir.actua.util.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A certificate the user has not trusted yet, and the connection to retry once they do. */
internal class PendingWebDavTrust(
    val info: ServerCertificateInfo,
    val replacingExistingTrust: Boolean,
    val config: WebDavConfig,
)

/** The result of trying to connect to a WebDAV server with [connectWebDav]. */
internal sealed interface WebDavConnectResult {
    data object Connected : WebDavConnectResult

    /** The server's certificate is not trusted: show it and let the user decide. */
    class NeedsTrust(val pending: PendingWebDavTrust) : WebDavConnectResult

    class Failed(val message: String) : WebDavConnectResult
}

/**
 * Checks [config] against the server, creates the backup folder and saves the connection. An untrusted
 * certificate is reported as [WebDavConnectResult.NeedsTrust] so the caller can ask the user, as it does for
 * Actual servers; nothing is saved unless this returns [WebDavConnectResult.Connected].
 */
internal suspend fun connectWebDav(
    manager: WebDavBackupManager,
    certificates: TrustedCertificateStore,
    resources: Resources,
    config: WebDavConfig,
): WebDavConnectResult = try {
    withContext(Dispatchers.IO) { manager.connect(config) }
    WebDavConnectResult.Connected
} catch (error: WebDavException.Tls) {
    val info = suspendRunCatching {
        withContext(Dispatchers.IO) { inspectServerCertificate(config.serverUrl.trim()) }
    }.getOrNull()
    if (info != null) {
        WebDavConnectResult.NeedsTrust(PendingWebDavTrust(info, certificates.hasTrust(info.host), config))
    } else {
        WebDavConnectResult.Failed(resources.getString(R.string.could_not_inspect_certificate))
    }
} catch (error: kotlinx.coroutines.CancellationException) {
    throw error
} catch (error: Exception) {
    // Explaining the error may resolve the server's host, so it is done off the main thread.
    WebDavConnectResult.Failed(withContext(Dispatchers.IO) { manager.describe(error) })
}

/**
 * The form for a WebDAV connection. [initial] prefills it (null for a new connection); [onConnect] receives the
 * entered settings and the caller runs [connectWebDav]. [error] is shown under the form and cleared by
 * [onEdited] as soon as the user changes a field.
 */
@Composable
internal fun WebDavSetupDialog(
    initial: WebDavConfig?,
    busy: Boolean,
    error: String?,
    onEdited: () -> Unit,
    onConnect: (WebDavConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initial?.serverUrl.orEmpty()) }
    var user by remember { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var passwordVisible by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf(initial?.folder ?: WebDavConfig.DEFAULT_FOLDER) }
    var allowHttp by remember { mutableStateOf(initial?.allowInsecureHttp ?: false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.webdav_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; onEdited() },
                    label = { Text(stringResource(R.string.webdav_server_url)) },
                    placeholder = { Text(stringResource(R.string.webdav_server_url_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it; onEdited() },
                    label = { Text(stringResource(R.string.webdav_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; onEdited() },
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
                    value = folder,
                    onValueChange = { folder = it; onEdited() },
                    label = { Text(stringResource(R.string.webdav_folder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().clickable { allowHttp = !allowHttp },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = allowHttp, onCheckedChange = { allowHttp = it })
                    Text(stringResource(R.string.webdav_allow_http), style = MaterialTheme.typography.bodyMedium)
                }
                if (allowHttp) {
                    Text(
                        stringResource(R.string.webdav_http_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && url.isNotBlank() && user.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    onConnect(
                        WebDavConfig(
                            serverUrl = url,
                            username = user,
                            password = password,
                            folder = folder,
                            allowInsecureHttp = allowHttp,
                        ),
                    )
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.connect))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Shows the certificate a WebDAV server presented and lets the user trust it for that server only. */
@Composable
internal fun WebDavCertificateDialog(
    pending: PendingWebDavTrust,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (pending.replacingExistingTrust) R.string.server_certificate_changed
                    else R.string.server_certificate_untrusted,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        if (pending.replacingExistingTrust) R.string.server_certificate_changed_description
                        else R.string.server_certificate_untrusted_description,
                    ),
                )
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
            TextButton(onClick = onTrust) {
                Text(
                    stringResource(
                        if (pending.replacingExistingTrust) R.string.trust_new_certificate else R.string.trust_certificate,
                    ),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
