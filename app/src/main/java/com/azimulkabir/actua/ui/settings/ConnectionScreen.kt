package com.azimulkabir.actua.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.ActualServerException
import com.azimulkabir.actua.data.network.LocalNetworkAccess
import com.azimulkabir.actua.data.network.OidcCallbackPageText
import com.azimulkabir.actua.data.network.OidcCallbackServer
import com.azimulkabir.actua.data.network.RemoteBudgetFile
import com.azimulkabir.actua.data.network.ServerCertificateInfo
import com.azimulkabir.actua.data.network.TrustedCertificateStore
import com.azimulkabir.actua.data.network.UrlConnectionTransport
import com.azimulkabir.actua.data.network.inspectServerCertificate
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetDownloadException
import com.azimulkabir.actua.data.budget.BudgetDownloadService
import com.azimulkabir.actua.data.budget.BudgetResetRecovery
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.BackupItem
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetMetadata
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveAuthManager
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveBackupItem
import com.azimulkabir.actua.data.budget.gdrive.GoogleDriveBackupManager
import com.azimulkabir.actua.data.budget.gdrive.InvalidPassphraseException
import com.azimulkabir.actua.data.budget.gdrive.PassphraseRequiredException
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import com.azimulkabir.actua.data.security.CredentialStore
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import com.azimulkabir.actua.data.sync.ActualSyncScheduler
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_MANUAL
import com.azimulkabir.actua.data.sync.SyncErrorCode
import com.azimulkabir.actua.data.sync.SyncRunResult
import com.azimulkabir.actua.data.sync.EncryptionKeyException
import com.azimulkabir.actua.data.sync.SYNC_REASON_FILE_HAS_NEW_KEY
import com.azimulkabir.actua.data.sync.SYNC_REASON_FILE_HAS_RESET
import com.azimulkabir.actua.data.sync.SyncStatusStore
import com.azimulkabir.actua.data.sync.SyncedLogMarkers
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_AFTER_CHANGE
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_APP_OPEN
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_BACKGROUND
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_PULL_REFRESH
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaFormTextField
import com.azimulkabir.actua.ui.theme.Sizes
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private data class PendingOpenIdLogin(
    val primaryUrl: String,
    val fallbackUrl: String,
    val activeUrl: String,
    val authorizationUrl: String,
    val callbackServer: OidcCallbackServer,
)

private enum class CertificateRetryAction { PASSWORD, OPEN_ID }

private data class PendingCertificateTrust(
    val info: ServerCertificateInfo,
    val action: CertificateRetryAction,
    val replacingExistingTrust: Boolean,
)

private data class CompletedOpenIdLogin(
    val primaryUrl: String,
    val fallbackUrl: String,
    val activeUrl: String,
    val token: String,
    val budgets: List<RemoteBudgetFile>,
)

internal fun formatSyncDuration(durationMillis: Long, locale: java.util.Locale = java.util.Locale.getDefault()): String = when {
    durationMillis < 1_000L -> "$durationMillis ms"
    else -> String.format(locale, "%.1f s", durationMillis / 1_000.0)
}

/** Persisted trigger codes (and legacy English labels) → the label shown while syncing. */
@Composable
internal fun syncTriggerDisplayName(trigger: String): String = stringResource(when (trigger) {
    SYNC_TRIGGER_MANUAL, "Manual", "Sync" -> R.string.sync_trigger_manual
    SYNC_TRIGGER_AFTER_CHANGE, "After change" -> R.string.sync_trigger_after_change
    SYNC_TRIGGER_APP_OPEN, "App open" -> R.string.sync_trigger_app_open
    SYNC_TRIGGER_BACKGROUND, "Background" -> R.string.sync_trigger_background
    SYNC_TRIGGER_PULL_REFRESH, "Pull to refresh" -> R.string.sync_trigger_pull_refresh
    else -> R.string.sync_trigger_background
})

/** Persisted error codes (and legacy English messages) → localized display text. */
@Composable
internal fun syncErrorDisplayMessage(error: String): String = when (error) {
    SyncErrorCode.ENCRYPTION_KEY_UNAVAILABLE.persistedValue,
    "Unlock this encrypted budget before syncing" -> stringResource(R.string.unlock_budget_before_sync)
    SyncErrorCode.UNAUTHORIZED.persistedValue, "Unauthorized" -> stringResource(R.string.sync_error_unauthorized)
    SyncErrorCode.CERTIFICATE.persistedValue -> stringResource(R.string.sync_error_certificate)
    SyncErrorCode.NETWORK.persistedValue -> stringResource(R.string.sync_error_network)
    SyncErrorCode.OUT_OF_SYNC.persistedValue, "Unable to converge with the server" ->
        stringResource(R.string.sync_error_out_of_sync)
    SyncErrorCode.CLOCK.persistedValue -> stringResource(R.string.sync_error_clock)
    SyncErrorCode.INVALID_SERVER_RESPONSE.persistedValue,
    "The server returned an invalid response" -> stringResource(R.string.sync_error_invalid_response)
    SyncErrorCode.SERVER_REQUEST.persistedValue -> stringResource(R.string.sync_error_server_request)
    SyncErrorCode.UNKNOWN.persistedValue -> stringResource(R.string.sync_error_unknown)
    // Anything else is a specific message persisted as-is (Actual sync rejection reasons).
    else -> error
}

/**
 * The raw exception message for an untrusted server certificate is a cryptic Java stack-trace
 * line (e.g. "Trust anchor for certification path not found"), which just repeats the error
 * shown before this fix without telling the user what to actually do about it. Two distinct
 * setups produce this same exception:
 *  - A self-signed/private-CA cert: the CA needs installing as a user CA certificate, scoped to
 *    "VPN and apps" — the install flow also offers a "Wi-Fi" only scope that leaves apps still
 *    untrusting it, reproducing this exact error even after "installing" the certificate.
 *  - A publicly-issued cert (e.g. Let's Encrypt via Tailscale) whose server sends only the leaf
 *    certificate instead of the full chain: unlike browsers, Android's TLS stack doesn't fetch
 *    missing intermediates on the fly, so the trust path fails even though the root is already
 *    trusted (see https://discuss.grapheneos.org/d/13339-is-there-no-lets-encrypt-ca-integrated).
 *    That side is a server/reverse-proxy misconfiguration no client-side change can fix.
 */
internal fun isCertificateTrustFailure(error: Throwable): Boolean {
    val causes = generateSequence(error) { it.cause }.toList()
    return causes.any { it is java.security.cert.CertPathValidatorException } ||
        causes.any {
            it is java.security.cert.CertificateException &&
                (it.message?.contains("trust anchor", ignoreCase = true) == true ||
                    it.message?.contains("no longer matches the certificate trusted in Actua", ignoreCase = true) == true)
        }
}

internal fun connectionErrorMessage(error: Throwable, fallback: String, certificateTrustMessage: String = fallback): String {
    return if (isCertificateTrustFailure(error)) {
        certificateTrustMessage
    } else {
        error.message ?: fallback
    }
}

@Composable
internal fun ManualSyncButtonContent(
    syncing: Boolean,
    demoActive: Boolean,
    standaloneActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.testTag("manualSyncButtonContent"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("manualSyncButtonIndicator"),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                when {
                    demoActive -> stringResource(R.string.demo_local_only)
                    syncing -> stringResource(R.string.syncing)
                    standaloneActive -> stringResource(R.string.process_schedules_backup)
                    else -> stringResource(R.string.sync_now)
                }
            )
        }
    }
}

@Composable
fun ConnectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onBeforeBudgetReplacement: () -> Unit = {},
    onBudgetInstalled: () -> Unit = {},
) {
    val context = LocalContext.current
    // Configuration-aware resource access; re-captured on every recomposition (#424 pattern).
    val resources = LocalResources.current
    val credentials = remember { CredentialStore(context) }
    val certificateStore = remember { TrustedCertificateStore(context) }
    val client = remember {
        ActualServerClient(UrlConnectionTransport(certificateStore)).apply {
            customHeaders = credentials.customHeaders
        }
    }
    val files = remember { BudgetFileManager(context) }
    val activeBudget = remember { ActiveBudgetStore(context) }
    val downloader = remember { BudgetDownloadService(client, files, BudgetEncryptionKeyStore(context)) }
    val backupService = remember { BackupService(context, files) }
    val resetRecovery = remember { BudgetResetRecovery(files, backupService, downloader, SyncedLogMarkers(context)) }
    var resetRecoveryPrompt by remember { mutableStateOf<ResetRecoveryPrompt?>(null) }
    var recoveringFromReset by remember { mutableStateOf(false) }
    var newKeyPassword by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(credentials.serverUrl) }
    var fallbackServerUrl by remember { mutableStateOf(credentials.fallbackServerUrl) }
    var activeServerUrl by remember { mutableStateOf(credentials.serverUrl) }
    var headerEntries by remember { mutableStateOf(credentials.customHeaders.toList()) }
    var showAddHeader by remember { mutableStateOf(false) }
    var headerNameInput by remember { mutableStateOf("") }
    var headerValueInput by remember { mutableStateOf("") }
    var editingConnection by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(credentials.token() != null) }
    var sessionExpired by remember { mutableStateOf(credentials.sessionExpired) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var remoteBudgets by remember { mutableStateOf<List<RemoteBudgetFile>>(emptyList()) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var encryptionPassword by remember { mutableStateOf("") }
    val syncStatusStore = remember { SyncStatusStore(context) }
    var syncStatus by remember { mutableStateOf(syncStatusStore.read()) }
    var syncing by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
    var backupBusy by remember { mutableStateOf(false) }
    var showBackups by remember { mutableStateOf(false) }
    var budgetsExpanded by remember { mutableStateOf(true) }
    var showCreateBudget by remember { mutableStateOf(false) }
    var newBudgetName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<RemoteBudgetFile?>(null) }
    var deleteConfirmation by remember { mutableStateOf("") }
    var confirmQuickBackup by remember { mutableStateOf(false) }
    var pendingCertificateTrust by remember { mutableStateOf<PendingCertificateTrust?>(null) }
    var activeOidcServer by remember { mutableStateOf<OidcCallbackServer?>(null) }
    var localBudgetsGeneration by remember { mutableStateOf(0) }
    var showCreateLocalBudget by remember { mutableStateOf(false) }
    var newLocalBudgetName by remember { mutableStateOf("") }
    var pendingDeleteLocal by remember { mutableStateOf<BudgetMetadata?>(null) }
    var pendingUploadBudget by remember { mutableStateOf<BudgetMetadata?>(null) }
    var showConnectDriveDialog by remember { mutableStateOf(false) }
    var showBrowseDriveBackupsDialog by remember { mutableStateOf(false) }
    var driveBackups by remember { mutableStateOf<List<GoogleDriveBackupItem>>(emptyList()) }
    var selectedDriveBackup by remember { mutableStateOf<GoogleDriveBackupItem?>(null) }
    var driveLoading by remember { mutableStateOf(false) }
    var showDrivePassphraseDialog by remember { mutableStateOf(false) }
    var drivePassphraseInput by remember { mutableStateOf("") }
    var drivePassphraseVisible by remember { mutableStateOf(false) }
    var drivePassphraseError by remember { mutableStateOf<String?>(null) }
    val activeMetadata = remember(activeBudget.budgetId, localBudgetsGeneration) {
        activeBudget.budgetId?.let(files::metadata)
    }
    val demoActive = activeMetadata?.isDemo == true || DemoBudgetManager.isDemoBudget(activeBudget.budgetId)
    val standaloneActive = activeMetadata?.isUserLocalOnly == true

    fun refreshBackups() {
        val budgetId = activeBudget.budgetId
        backups = if (budgetId == null) emptyList() else runCatching {
            backupService.availableBackups(budgetId)
        }.getOrDefault(emptyList())
    }

    /** Shows [error]; a rejected session switches to re-sign-in, keeping every downloaded budget. */
    fun showServerError(error: Throwable, fallbackRes: Int) {
        if (error is ActualServerException.SessionExpired) {
            credentials.expireSession()
            connected = false
            sessionExpired = true
            remoteBudgets = emptyList()
        }
        message = connectionErrorMessage(
            error,
            resources.getString(fallbackRes),
            resources.getString(R.string.server_certificate_not_trusted_error),
        )
    }

    /** Clears the expired-session state and sends changes made while the session was invalid. */
    fun onSignedIn() {
        sessionExpired = false
        ActualSyncScheduler.scheduleMutation(context)
    }

    fun disconnect() {
        listOf(serverUrl, fallbackServerUrl).filter(String::isNotBlank).forEach { url ->
            runCatching { java.net.URI(client.normalizeServerUrl(url)).host }
                .getOrNull()?.let(certificateStore::forget)
        }
        credentials.clear()
        connected = false
        remoteBudgets = emptyList()
        message = resources.getString(R.string.disconnected)
    }

    fun loadBudgets() {
        val token = credentials.token() ?: return
        loading = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                runCatching { serverUrl to client.listFiles(serverUrl, token) }.getOrElse { primary ->
                    if (primary is ActualServerException.SessionExpired) throw primary
                    val fallback = fallbackServerUrl.takeIf { it.isNotBlank() && it != serverUrl } ?: throw primary
                    fallback to client.listFiles(fallback, token)
                }
            } }.onSuccess { (usedUrl, budgets) ->
                activeServerUrl = usedUrl; remoteBudgets = budgets
                message = if (budgets.isEmpty()) resources.getString(R.string.no_budgets_found) else null
            }
                .onFailure { showServerError(it, R.string.could_not_load_budgets) }
            loading = false
        }
    }

    fun requestCertificateTrust(error: Throwable, action: CertificateRetryAction, failedUrl: String) {
        if (!isCertificateTrustFailure(error)) {
            message = connectionErrorMessage(
                error,
                resources.getString(R.string.could_not_connect_server),
                resources.getString(R.string.server_certificate_not_trusted_error),
            )
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { inspectServerCertificate(failedUrl) }
            }.onSuccess { info ->
                pendingCertificateTrust = PendingCertificateTrust(
                    info = info,
                    action = action,
                    replacingExistingTrust = certificateStore.hasTrust(info.host),
                )
                message = null
            }.onFailure {
                message = resources.getString(R.string.could_not_inspect_certificate)
            }
        }
    }

    fun connectWithPassword() {
        loading = true
        message = null
        scope.launch {
            // Tracks whichever URL the most recent login attempt targeted, so a certificate
            // trust prompt triggered by the fallback's failure inspects the fallback's
            // certificate rather than always re-probing the primary URL.
            var attemptedUrl = serverUrl
            runCatching {
                withContext(Dispatchers.IO) {
                    val normalized = client.normalizeServerUrl(serverUrl)
                    attemptedUrl = normalized
                    val fallback = fallbackServerUrl.trim().takeIf(String::isNotEmpty)?.let(client::normalizeServerUrl).orEmpty()
                    runCatching { Triple(normalized, fallback, client.login(normalized, password)) }.getOrElse { primary ->
                        // The server answered; the fallback is the same server and would count the
                        // rejected password against its sign-in rate limit again.
                        if (primary is ActualServerException.LoginRejected) throw primary
                        if (fallback.isEmpty() || fallback == normalized) throw primary
                        attemptedUrl = fallback
                        Triple(normalized, fallback, client.login(fallback, password))
                    }
                }
            }.onSuccess { (url, fallback, token) ->
                credentials.saveConnection(url, token, fallback)
                onSignedIn()
                credentials.customHeaders = headerEntries.toMap()
                serverUrl = url
                fallbackServerUrl = fallback
                activeServerUrl = url
                password = ""
                connected = true
                message = resources.getString(R.string.connected)
                loadBudgets()
            }.onFailure { error ->
                if (isCertificateTrustFailure(error)) {
                    requestCertificateTrust(error, CertificateRetryAction.PASSWORD, attemptedUrl)
                } else if (error is ActualServerException.LoginRejected && error.reason == "invalid-password") {
                    message = resources.getString(R.string.incorrect_server_password)
                } else {
                    message = connectionErrorMessage(
                        error,
                        resources.getString(R.string.could_not_connect_server),
                        resources.getString(R.string.server_certificate_not_trusted_error),
                    )
                }
            }
            loading = false
        }
    }

    fun connectWithOpenId() {
        loading = true
        message = resources.getString(R.string.preparing_openid)
        scope.launch {
            // Tracks whichever URL the most recent OpenID step targeted, so a certificate trust
            // prompt inspects the certificate that actually failed rather than always the primary.
            var attemptedUrl = serverUrl
            runCatching {
                val pending = withContext(Dispatchers.IO) {
                    val primary = client.normalizeServerUrl(serverUrl)
                    attemptedUrl = primary
                    val fallback = fallbackServerUrl.trim().takeIf(String::isNotEmpty)?.let(client::normalizeServerUrl).orEmpty()
                    val candidates = listOf(primary, fallback)
                        .filter(String::isNotBlank)
                        .distinct()
                    var lastError: Throwable? = null
                    var activeUrl: String? = null
                    for (candidate in candidates) {
                        attemptedUrl = candidate
                        val methods = runCatching { client.loginMethods(candidate) }
                            .onFailure { lastError = it }
                            .getOrNull() ?: continue
                        if (methods.any { it.method.equals("openid", ignoreCase = true) }) {
                            activeUrl = candidate
                            break
                        }
                        lastError = IllegalStateException(resources.getString(R.string.openid_not_offered))
                    }
                    val selectedUrl = activeUrl ?: throw (lastError
                        ?: IllegalStateException(resources.getString(R.string.could_not_find_openid_server)))
                    attemptedUrl = selectedUrl
                    val callback = OidcCallbackServer(
                        OidcCallbackPageText(
                            languageTag = java.util.Locale.getDefault().toLanguageTag(),
                            completionTitle = resources.getString(R.string.openid_completion_title),
                            completionMessage = resources.getString(R.string.openid_completion_message),
                            returnToApp = resources.getString(R.string.openid_return_to_app),
                            callbackErrorTitle = resources.getString(R.string.openid_callback_error_title),
                            callbackErrorMessage = resources.getString(R.string.openid_callback_error_message),
                        ),
                    )
                    activeOidcServer = callback
                    try {
                        val authorizationUrl = client.startOpenIdLogin(selectedUrl, callback.returnUrl, password)
                        PendingOpenIdLogin(primary, fallback, selectedUrl, authorizationUrl, callback)
                    } catch (error: Throwable) {
                        callback.close()
                        activeOidcServer = null
                        throw error
                    }
                }

                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(pending.authorizationUrl))
                context.startActivity(browserIntent)
                message = resources.getString(R.string.complete_sign_in_browser)

                val token = withContext(Dispatchers.IO) { pending.callbackServer.awaitToken() }
                val budgets = withContext(Dispatchers.IO) { client.listFiles(pending.activeUrl, token) }
                CompletedOpenIdLogin(
                    pending.primaryUrl,
                    pending.fallbackUrl,
                    pending.activeUrl,
                    token,
                    budgets,
                )
            }.onSuccess { result ->
                credentials.saveConnection(result.primaryUrl, result.token, result.fallbackUrl)
                onSignedIn()
                credentials.customHeaders = headerEntries.toMap()
                serverUrl = result.primaryUrl
                fallbackServerUrl = result.fallbackUrl
                activeServerUrl = result.activeUrl
                remoteBudgets = result.budgets
                password = ""
                connected = true
                message = resources.getString(
                    if (result.budgets.isEmpty()) R.string.connected_openid_no_budgets else R.string.connected_openid
                )
            }.onFailure { error ->
                message = when ((error as? ActualServerException.LoginRejected)?.reason) {
                    "invalid-password" -> resources.getString(R.string.openid_current_password_required)
                    else -> {
                        if (isCertificateTrustFailure(error)) {
                            requestCertificateTrust(error, CertificateRetryAction.OPEN_ID, attemptedUrl)
                            null
                        } else {
                            connectionErrorMessage(
                                error,
                                resources.getString(R.string.could_not_complete_openid),
                                resources.getString(R.string.server_certificate_not_trusted_error),
                            )
                        }
                    }
                }
            }
            activeOidcServer?.close()
            activeOidcServer = null
            loading = false
        }
    }

    fun openDemoBudget() {
        val previousBudgetId = activeBudget.budgetId
        loading = true
        message = null
        onBeforeBudgetReplacement()
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { DemoBudgetManager.recreate(files) } }
                .onSuccess { metadata ->
                    activeBudget.budgetId = metadata.id
                    message = if (previousBudgetId == metadata.id) {
                        resources.getString(R.string.demo_budget_reset)
                    } else {
                        resources.getString(R.string.demo_budget_opened)
                    }
                    onBudgetInstalled()
                    refreshBackups()
                }
                .onFailure { error ->
                    if (previousBudgetId == DemoBudgetManager.BUDGET_ID && !files.databaseFile(DemoBudgetManager.BUDGET_ID).isFile) {
                        activeBudget.budgetId = files.listLocalBudgets().firstOrNull { it.id != DemoBudgetManager.BUDGET_ID }?.id
                    }
                    message = error.message ?: resources.getString(R.string.could_not_create_demo_budget)
                    onBudgetInstalled()
                }
            loading = false
        }
    }

    val localNetworkPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) connectWithPassword()
        else message = resources.getString(R.string.local_network_required)
    }
    val localNetworkOpenIdPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) connectWithOpenId()
        else message = resources.getString(R.string.local_network_required)
    }
    val importLocalBudgetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            loading = true
            message = null
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            files.importLocalBudget(stream)
                        } ?: error(resources.getString(R.string.could_not_open_selected_archive))
                    }
                }.onSuccess { imported ->
                    onBeforeBudgetReplacement()
                    activeBudget.budgetId = imported.id
                    message = resources.getString(R.string.budget_archive_imported, imported.budgetName ?: imported.id)
                    localBudgetsGeneration++
                    refreshBackups()
                    onBudgetInstalled()
                }.onFailure { error ->
                    message = error.message ?: resources.getString(R.string.could_not_import_budget_archive)
                }
                loading = false
            }
        }
    }

    // Android 17+ shows ACCESS_LOCAL_NETWORK as "Nearby devices"; only ask for it when the
    // primary or fallback server is actually on the local network.
    fun connectWithLocalNetworkAccess(launcher: ActivityResultLauncher<String>, connect: () -> Unit) {
        if (Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            connect()
            return
        }
        loading = true
        message = null
        scope.launch {
            val required = withContext(Dispatchers.IO) {
                LocalNetworkAccess.isRequired(listOf(serverUrl, fallbackServerUrl))
            }
            loading = false
            if (required) launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) else connect()
        }
    }

    LaunchedEffect(headerEntries) { client.customHeaders = headerEntries.toMap() }
    DisposableEffect(activeOidcServer) {
        val server = activeOidcServer
        onDispose { server?.close() }
    }
    LaunchedEffect(connected) {
        if (connected && remoteBudgets.isEmpty()) loadBudgets()
        refreshBackups()
    }
    LaunchedEffect(Unit) {
        while (true) {
            syncStatus = syncStatusStore.read()
            delay(1_000)
        }
    }

    if (showBackups && activeBudget.budgetId != null) {
        BackupsScreen(
            budgetId = activeBudget.budgetId!!,
            onBack = { showBackups = false; refreshBackups() },
            onBeforeRestore = onBeforeBudgetReplacement,
            onRestored = onBudgetInstalled,
            modifier = modifier,
        )
        return
    }

    pendingCertificateTrust?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingCertificateTrust = null },
            title = {
                Text(stringResource(
                    if (pending.replacingExistingTrust) R.string.server_certificate_changed
                    else R.string.server_certificate_untrusted
                ))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (pending.replacingExistingTrust) {
                            stringResource(R.string.server_certificate_changed_description)
                        } else {
                            stringResource(R.string.server_certificate_untrusted_description)
                        },
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
                TextButton(onClick = {
                    certificateStore.trust(pending.info.host, pending.info.sha256Fingerprint)
                    pendingCertificateTrust = null
                    when (pending.action) {
                        CertificateRetryAction.PASSWORD -> connectWithPassword()
                        CertificateRetryAction.OPEN_ID -> connectWithOpenId()
                    }
                }) {
                    Text(stringResource(
                        if (pending.replacingExistingTrust) R.string.trust_new_certificate else R.string.trust_certificate
                    ))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCertificateTrust = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    resetRecoveryPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { if (!recoveringFromReset) { resetRecoveryPrompt = null; newKeyPassword = "" } },
            title = {
                Text(stringResource(if (prompt.needsNewPassword) R.string.sync_reset_new_key_title else R.string.sync_reset_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val unsynced = prompt.unsyncedChanges
                    Text(
                        when {
                            unsynced == null -> stringResource(R.string.sync_reset_unsynced_unknown)
                            unsynced > 0 -> pluralStringResource(R.plurals.sync_reset_unsynced, unsynced, unsynced)
                            else -> stringResource(R.string.sync_reset_no_unsynced)
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(R.string.sync_reset_explanation))
                    if (prompt.needsNewPassword) {
                        Text(stringResource(R.string.sync_reset_new_key_explanation))
                        OutlinedTextField(
                            value = newKeyPassword,
                            onValueChange = { newKeyPassword = it },
                            enabled = !recoveringFromReset,
                            singleLine = true,
                            label = { Text(stringResource(R.string.sync_reset_new_password_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (recoveringFromReset) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !recoveringFromReset && (!prompt.needsNewPassword || newKeyPassword.isNotBlank()),
                    onClick = {
                        val budgetId = activeBudget.budgetId ?: return@TextButton
                        val token = credentials.token() ?: return@TextButton
                        recoveringFromReset = true
                        message = null
                        // Releases the app's handle on the database before its files are replaced.
                        onBeforeBudgetReplacement()
                        scope.launch {
                            var tryAgain = false
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    resetRecovery.recover(
                                        activeServerUrl, token, budgetId,
                                        newPassword = newKeyPassword.takeIf { prompt.needsNewPassword },
                                    )
                                }
                            }.onSuccess { result ->
                                syncStatusStore.rejectionResolved()
                                syncStatus = syncStatusStore.read()
                                message = resources.getString(
                                    R.string.sync_reset_done,
                                    result.metadata.budgetName ?: result.metadata.id,
                                )
                                localBudgetsGeneration++
                                refreshBackups()
                                onBudgetInstalled()
                                ActualSyncScheduler.scheduleMutation(context)
                            }.onFailure { error ->
                                // The local budget is untouched on any failure; reopen it as it was.
                                tryAgain = error == EncryptionKeyException.InvalidPassword
                                message = when (error) {
                                    EncryptionKeyException.InvalidPassword ->
                                        resources.getString(R.string.sync_reset_wrong_password)
                                    BudgetDownloadException.EncryptionPasswordRequired ->
                                        resources.getString(R.string.enter_budget_encryption_password)
                                    BudgetDownloadException.EncryptionKeyChanged ->
                                        resources.getString(R.string.budget_encryption_key_changed)
                                    BudgetDownloadException.InvalidEncryptionMetadata ->
                                        resources.getString(R.string.invalid_budget_encryption_metadata)
                                    else -> connectionErrorMessage(
                                        error,
                                        resources.getString(R.string.sync_reset_failed),
                                        resources.getString(R.string.server_certificate_not_trusted_error),
                                    )
                                }
                                if (error is ActualServerException.SessionExpired) showServerError(error, R.string.sync_reset_failed)
                                onBudgetInstalled()
                            }
                            recoveringFromReset = false
                            // A wrong password keeps the dialog open to retry; nothing was changed.
                            if (!tryAgain) { resetRecoveryPrompt = null; newKeyPassword = "" }
                        }
                    },
                ) { Text(stringResource(R.string.sync_reset_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !recoveringFromReset, onClick = { resetRecoveryPrompt = null; newKeyPassword = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showAddHeader) AlertDialog(
        onDismissRequest = { showAddHeader = false; headerNameInput = ""; headerValueInput = "" },
        title = { Text(stringResource(R.string.add_http_header)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = headerNameInput, onValueChange = { headerNameInput = it },
                    label = { Text(stringResource(R.string.header_name)) },
                    placeholder = { Text(stringResource(R.string.header_name_example)) }, singleLine = true,
                )
                OutlinedTextField(
                    value = headerValueInput, onValueChange = { headerValueInput = it },
                    label = { Text(stringResource(R.string.header_value)) }, singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(
            enabled = headerNameInput.isNotBlank() && headerValueInput.isNotBlank(),
            onClick = {
                val name = headerNameInput.trim()
                headerEntries = headerEntries.filterNot { it.first.equals(name, ignoreCase = true) } + (name to headerValueInput.trim())
                showAddHeader = false; headerNameInput = ""; headerValueInput = ""
            },
        ) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = { showAddHeader = false; headerNameInput = ""; headerValueInput = "" }) { Text(stringResource(R.string.cancel)) } },
    )

    if (showCreateBudget) AlertDialog(
        onDismissRequest = { if (!loading) showCreateBudget = false },
        title = { Text(stringResource(R.string.create_new_budget)) },
        text = { OutlinedTextField(
            value = newBudgetName,
            onValueChange = { if (it.length <= 100) newBudgetName = it },
            label = { Text(stringResource(R.string.budget_name)) },
            supportingText = { Text(stringResource(R.string.create_budget_description)) },
            singleLine = true,
        ) },
        confirmButton = { TextButton(
            enabled = !loading && newBudgetName.trim().isNotEmpty(),
            onClick = {
                val name = newBudgetName.trim()
                val existingNames = remoteBudgets.map { it.name } + files.listLocalBudgets().mapNotNull { it.budgetName }
                if (name in existingNames) { message = resources.getString(R.string.name_already_exists, name); return@TextButton }
                loading = true; message = null
                scope.launch {
                    var localId: String? = null
                    runCatching { withContext(Dispatchers.IO) {
                        val token = credentials.token() ?: throw ActualServerException.Unauthorized
                        val local = files.createBudget(name); localId = local.id
                        val cloudFileId = UUID.randomUUID().toString().lowercase()
                        val archive = files.uploadArchive(local.id)
                        val groupId = runCatching {
                            client.uploadFile(activeServerUrl, token, cloudFileId, name, archive)
                        }.getOrElse { uploadError ->
                            val remote = runCatching { client.listFiles(activeServerUrl, token) }.getOrNull()
                                ?.firstOrNull { it.fileId == cloudFileId }
                            remote?.groupId ?: throw uploadError
                        }
                        files.saveCloudRegistration(local.id, cloudFileId, groupId)
                        local.id
                    } }.onSuccess { id ->
                        onBeforeBudgetReplacement(); activeBudget.budgetId = id
                        showCreateBudget = false; newBudgetName = ""
                        message = resources.getString(R.string.budget_created_opened, name)
                        onBudgetInstalled(); loadBudgets(); refreshBackups()
                    }.onFailure { error ->
                        localId?.let { runCatching { files.deleteBudget(it) } }
                        showServerError(error, R.string.could_not_create_budget)
                    }
                    loading = false
                }
            },
        ) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(enabled = !loading, onClick = { showCreateBudget = false }) { Text(stringResource(R.string.cancel)) } },
    )

    pendingDelete?.let { remote ->
        AlertDialog(
            onDismissRequest = { if (!loading) pendingDelete = null },
            title = { Text(stringResource(R.string.delete_named_budget, remote.name)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.delete_budget_warning))
                OutlinedTextField(
                    value = deleteConfirmation,
                    onValueChange = { deleteConfirmation = it },
                    label = { Text(stringResource(R.string.type_name_to_confirm, remote.name)) },
                    singleLine = true,
                )
            } },
            confirmButton = { TextButton(
                enabled = !loading && deleteConfirmation == remote.name,
                onClick = {
                    loading = true; message = null; onBeforeBudgetReplacement()
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) {
                            val token = credentials.token() ?: throw ActualServerException.Unauthorized
                            val local = files.listLocalBudgets().firstOrNull { it.cloudFileId == remote.fileId }
                            if (local?.id == activeBudget.budgetId) runCatching { ActualSyncRunner.run(context) }
                            try { client.deleteFile(activeServerUrl, token, remote.fileId) }
                            catch (_: ActualServerException.FileNotFound) { }
                            BudgetEncryptionKeyStore(context).remove(remote.fileId)
                            local?.let { files.deleteBudget(it.id) }
                        } }.onSuccess {
                            val remaining = files.listLocalBudgets()
                            if (remaining.none { it.id == activeBudget.budgetId }) {
                                activeBudget.budgetId = remaining.firstOrNull()?.id
                            }
                            pendingDelete = null; deleteConfirmation = ""
                            message = resources.getString(R.string.budget_deleted, remote.name)
                            localBudgetsGeneration++; loadBudgets(); refreshBackups(); onBudgetInstalled()
                        }.onFailure { error ->
                            if (error is ActualServerException.FileAccessDenied) {
                                message = resources.getString(R.string.only_owner_can_delete)
                            } else {
                                showServerError(error, R.string.could_not_delete_budget)
                            }
                            onBudgetInstalled()
                        }
                        loading = false
                    }
                },
            ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(enabled = !loading, onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmQuickBackup) AlertDialog(
        onDismissRequest = { confirmQuickBackup = false },
        title = { Text(stringResource(R.string.create_new_backup_title)) },
        text = { Text(stringResource(R.string.quick_backup_description)) },
        confirmButton = { TextButton(onClick = {
            confirmQuickBackup = false
            val budgetId = activeBudget.budgetId ?: return@TextButton
            backupBusy = true
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { backupService.makeBackup(budgetId) } }
                    .onSuccess { message = resources.getString(R.string.backup_created) }
                    .onFailure { message = it.message ?: resources.getString(R.string.could_not_create_a_backup) }
                backupBusy = false; refreshBackups()
            }
        }) { Text(stringResource(R.string.back_up)) } },
        dismissButton = { TextButton(onClick = { confirmQuickBackup = false }) { Text(stringResource(R.string.cancel)) } },
    )

    if (showCreateLocalBudget) AlertDialog(
        onDismissRequest = { if (!loading) showCreateLocalBudget = false },
        title = { Text(stringResource(R.string.create_local_budget_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.create_local_budget_description))
                OutlinedTextField(
                    value = newLocalBudgetName,
                    onValueChange = { newLocalBudgetName = it },
                    label = { Text(stringResource(R.string.budget_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && newLocalBudgetName.isNotBlank(),
                onClick = {
                    val name = newLocalBudgetName.trim()
                    val existing = files.listLocalBudgets().mapNotNull { it.budgetName?.lowercase() }
                    if (name.lowercase() in existing) {
                        message = resources.getString(R.string.name_already_exists, name)
                        return@TextButton
                    }
                    loading = true
                    message = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                files.createBudget(name)
                            }
                        }.onSuccess { created ->
                            onBeforeBudgetReplacement()
                            activeBudget.budgetId = created.id
                            showCreateLocalBudget = false
                            newLocalBudgetName = ""
                            message = resources.getString(R.string.local_budget_created, name)
                            localBudgetsGeneration++
                            refreshBackups()
                            onBudgetInstalled()
                        }.onFailure { error ->
                            message = error.message ?: resources.getString(R.string.could_not_create_local_budget)
                        }
                        loading = false
                    }
                },
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = { showCreateLocalBudget = false }) { Text(stringResource(R.string.cancel)) }
        },
    )

    pendingDeleteLocal?.let { local ->
        val budgetName = local.budgetName ?: local.id
        AlertDialog(
            onDismissRequest = { if (!loading) pendingDeleteLocal = null },
            title = { Text(stringResource(R.string.delete_named_budget, budgetName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.delete_local_budget_warning))
                    OutlinedTextField(
                        value = deleteConfirmation,
                        onValueChange = { deleteConfirmation = it },
                        label = { Text(stringResource(R.string.type_name_to_confirm, budgetName)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !loading && deleteConfirmation == budgetName,
                    onClick = {
                        loading = true
                        message = null
                        onBeforeBudgetReplacement()
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    local.cloudFileId?.let { BudgetEncryptionKeyStore(context).remove(it) }
                                    files.deleteBudget(local.id)
                                }
                            }.onSuccess {
                                val remaining = files.listLocalBudgets()
                                if (remaining.none { it.id == activeBudget.budgetId }) {
                                    activeBudget.budgetId = remaining.firstOrNull()?.id
                                }
                                pendingDeleteLocal = null
                                deleteConfirmation = ""
                                message = resources.getString(R.string.local_budget_deleted, budgetName)
                                localBudgetsGeneration++
                                refreshBackups()
                                onBudgetInstalled()
                            }.onFailure { error ->
                                message = error.message ?: resources.getString(R.string.could_not_delete_budget)
                                onBudgetInstalled()
                            }
                            loading = false
                        }
                    },
                ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !loading, onClick = { pendingDeleteLocal = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingUploadBudget?.let { local ->
        val budgetName = local.budgetName ?: local.id
        AlertDialog(
            onDismissRequest = { if (!loading) pendingUploadBudget = null },
            title = { Text(stringResource(R.string.upload_to_server_title, budgetName)) },
            text = {
                Text(stringResource(R.string.upload_to_server_description, activeServerUrl))
            },
            confirmButton = {
                TextButton(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        message = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val token = credentials.token() ?: throw ActualServerException.Unauthorized
                                    val cloudFileId = UUID.randomUUID().toString().lowercase()
                                    val archive = files.uploadArchive(local.id)
                                    val groupId = runCatching {
                                        client.uploadFile(activeServerUrl, token, cloudFileId, budgetName, archive)
                                    }.getOrElse { uploadError ->
                                        val remote = runCatching { client.listFiles(activeServerUrl, token) }.getOrNull()
                                            ?.firstOrNull { it.fileId == cloudFileId }
                                        remote?.groupId ?: throw uploadError
                                    }
                                    files.saveCloudRegistration(local.id, cloudFileId, groupId)
                                    local.id
                                }
                            }.onSuccess {
                                pendingUploadBudget = null
                                message = resources.getString(R.string.budget_uploaded, budgetName)
                                localBudgetsGeneration++
                                loadBudgets()
                                onBudgetInstalled()
                            }.onFailure { error ->
                                pendingUploadBudget = null
                                showServerError(error, R.string.could_not_upload_budget)
                            }
                            loading = false
                        }
                    },
                ) { Text(stringResource(R.string.upload)) }
            },
            dismissButton = {
                TextButton(enabled = !loading, onClick = { pendingUploadBudget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    fun restoreDriveStandalone(item: GoogleDriveBackupItem, passphrase: String?) {
        driveLoading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val driveManager = GoogleDriveBackupManager(context)
                    driveManager.restoreRemoteBackup(
                        remoteFileId = item.id,
                        passphrase = passphrase?.toCharArray(),
                        targetBudgetId = null,
                    )
                }
            }.onSuccess { imported ->
                onBeforeBudgetReplacement()
                activeBudget.budgetId = imported.id
                message = resources.getString(R.string.drive_backup_restored, imported.budgetName ?: imported.id)
                localBudgetsGeneration++
                refreshBackups()
                showBrowseDriveBackupsDialog = false
                showDrivePassphraseDialog = false
                selectedDriveBackup = null
                drivePassphraseInput = ""
                driveLoading = false
                onBudgetInstalled()
            }.onFailure { error ->
                driveLoading = false
                if (error is InvalidPassphraseException || error is PassphraseRequiredException) {
                    drivePassphraseError = resources.getString(R.string.incorrect_password_or_recovery_key)
                    showDrivePassphraseDialog = true
                } else {
                    message = error.message ?: resources.getString(R.string.could_not_restore_drive_backup)
                    showBrowseDriveBackupsDialog = false
                    showDrivePassphraseDialog = false
                    selectedDriveBackup = null
                }
            }
        }
    }

    if (showConnectDriveDialog) AlertDialog(
        onDismissRequest = { showConnectDriveDialog = false },
        title = { Text(stringResource(R.string.connect_to_drive_title)) },
        text = {
            Text(stringResource(R.string.connect_to_drive_description))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showConnectDriveDialog = false
                    GoogleDriveAuthManager.startAuthorization(context)
                },
            ) { Text(stringResource(R.string.connect)) }
        },
        dismissButton = {
            TextButton(onClick = { showConnectDriveDialog = false }) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showBrowseDriveBackupsDialog) AlertDialog(
        onDismissRequest = { if (!driveLoading) showBrowseDriveBackupsDialog = false },
        title = { Text(stringResource(R.string.restore_from_drive)) },
        text = {
            if (driveLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.loading_backups))
                }
            } else if (driveBackups.isEmpty()) {
                Text(stringResource(R.string.no_drive_backups))
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val driveManager = remember { GoogleDriveBackupManager(context) }
                    driveBackups.forEach { backup ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !driveLoading) {
                                    selectedDriveBackup = backup
                                    if (backup.isEncrypted && !driveManager.hasEncryptionPassphrase()) {
                                        drivePassphraseInput = ""
                                        drivePassphraseError = null
                                        showDrivePassphraseDialog = true
                                    } else {
                                        restoreDriveStandalone(backup, null)
                                    }
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(backup.budgetName ?: backup.name, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.drive_backup_meta, backup.name, backup.sizeBytes / 1024) +
                                    if (backup.isEncrypted) stringResource(R.string.encrypted_suffix) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
            ) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showDrivePassphraseDialog && selectedDriveBackup != null) AlertDialog(
        onDismissRequest = { if (!driveLoading) { showDrivePassphraseDialog = false; selectedDriveBackup = null } },
        title = { Text(stringResource(R.string.enter_backup_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.backup_protected_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = drivePassphraseInput,
                    onValueChange = { drivePassphraseInput = it; drivePassphraseError = null },
                    label = { Text(stringResource(R.string.password_or_recovery_key)) },
                    singleLine = true,
                    isError = drivePassphraseError != null,
                    supportingText = drivePassphraseError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (drivePassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { drivePassphraseVisible = !drivePassphraseVisible }) {
                            Icon(
                                imageVector = if (drivePassphraseVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(
                                    if (drivePassphraseVisible) R.string.hide_password else R.string.show_password
                                ),
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !driveLoading && drivePassphraseInput.isNotBlank(),
                onClick = {
                    restoreDriveStandalone(selectedDriveBackup!!, drivePassphraseInput.trim())
                },
            ) {
                if (driveLoading) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.restore))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !driveLoading,
                onClick = { showDrivePassphraseDialog = false; selectedDriveBackup = null },
            ) { Text(stringResource(R.string.cancel)) }
        },
    )

    Column(modifier = modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.connection_data_title), onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ActuaGroupLabel(stringResource(R.string.try_actua))
            Text(
                if (demoActive) {
                    stringResource(R.string.demo_budget_active_description)
                } else {
                    stringResource(R.string.demo_budget_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConnectionAction(
                text = if (demoActive) stringResource(R.string.reset_demo_budget) else stringResource(R.string.try_demo_budget),
                onClick = ::openDemoBudget,
                enabled = !loading && downloadingId == null && !syncing,
                busy = loading,
            )
            Text(
                stringResource(R.string.demo_local_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ActuaGroupLabel(stringResource(R.string.on_this_device))
            Text(
                stringResource(R.string.local_budgets_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { showCreateLocalBudget = true; newLocalBudgetName = "" },
                    enabled = !loading && downloadingId == null && !syncing,
                    modifier = Modifier.weight(1f).height(Sizes.secondaryButtonHeight),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.create_local))
                }
                OutlinedButton(
                    onClick = {
                        importLocalBudgetLauncher.launch(
                            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                        )
                    },
                    enabled = !loading && downloadingId == null && !syncing,
                    modifier = Modifier.weight(1f).height(Sizes.secondaryButtonHeight),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.import_zip))
                }
            }
            OutlinedButton(
                onClick = {
                    val driveManager = GoogleDriveBackupManager(context)
                    if (!driveManager.isConnected()) {
                        showConnectDriveDialog = true
                    } else {
                        showBrowseDriveBackupsDialog = true
                        driveLoading = true
                        scope.launch {
                            val list = runCatching {
                                withContext(Dispatchers.IO) { driveManager.listRemoteBackups(null) }
                            }.getOrDefault(emptyList())
                            driveBackups = list
                            driveLoading = false
                        }
                    }
                },
                enabled = !loading && downloadingId == null && !syncing,
                modifier = Modifier.fillMaxWidth().height(Sizes.secondaryButtonHeight),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(stringResource(R.string.restore_from_drive))
            }
            val localBudgets = remember(localBudgetsGeneration, activeBudget.budgetId) {
                files.listLocalBudgets().filterNot { it.isDemo }
            }
            if (localBudgets.isNotEmpty()) {
                ActuaFormCard {
                    localBudgets.forEachIndexed { index, local ->
                        val isActive = activeBudget.budgetId == local.id
                        val budgetName = local.budgetName ?: local.id
                        if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(budgetName, fontWeight = FontWeight.Medium)
                                Text(
                                    when {
                                        isActive && local.isServerBacked -> stringResource(R.string.local_budget_active_server)
                                        isActive -> stringResource(R.string.local_budget_active_device)
                                        local.isServerBacked -> stringResource(R.string.local_budget_server_backed)
                                        else -> stringResource(R.string.local_budget_on_device)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!isActive) {
                                FilledTonalButton(
                                    enabled = !loading && downloadingId == null && !syncing,
                                    shape = MaterialTheme.shapes.large,
                                    onClick = {
                                        onBeforeBudgetReplacement()
                                        activeBudget.budgetId = local.id
                                        message = resources.getString(R.string.local_budget_now_active, budgetName)
                                        refreshBackups()
                                        onBudgetInstalled()
                                        if (local.isServerBacked && connected) {
                                            ActualSyncScheduler.scheduleMutation(context)
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.open))
                                }
                            }
                            if (local.isUserLocalOnly && connected) {
                                TextButton(
                                    enabled = !loading && downloadingId == null && !syncing,
                                    onClick = { pendingUploadBudget = local },
                                ) {
                                    Text(stringResource(R.string.upload))
                                }
                            }
                            TextButton(
                                enabled = !loading && downloadingId == null && !syncing,
                                onClick = { pendingDeleteLocal = local; deleteConfirmation = "" },
                            ) {
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.connection), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).padding(start = Spacing.xs, top = Spacing.sm))
                if (connected && !editingConnection) TextButton(onClick = { editingConnection = true }) { Text(stringResource(R.string.edit)) }
            }
            if (sessionExpired) {
                Text(
                    stringResource(R.string.session_expired_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (!connected) {
                val uriHandler = LocalUriHandler.current
                Text(
                    stringResource(R.string.server_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.server_intro_link),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { uriHandler.openUri("https://actualbudget.org") },
                )
            }
            // Downloaded budgets belong to this server, so re-sign-in can't switch servers.
            val urlsEditable = (!connected || editingConnection) && !sessionExpired && !loading
            ActuaFormCard {
                if (connected) {
                    ActuaFormRow(icon = Icons.Outlined.CloudDone, label = stringResource(R.string.status), value = stringResource(R.string.connected))
                    ActuaCardDivider()
                }
                ActuaFormTextField(
                    icon = Icons.Outlined.Link,
                    label = stringResource(R.string.server_url),
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    placeholder = stringResource(R.string.server_url_example),
                    enabled = urlsEditable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                ActuaCardDivider()
                ActuaFormTextField(
                    icon = Icons.Outlined.Dns,
                    label = stringResource(R.string.fallback_server_url),
                    value = fallbackServerUrl,
                    onValueChange = { fallbackServerUrl = it },
                    placeholder = stringResource(R.string.fallback_server_url_example),
                    enabled = urlsEditable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                if (!connected) {
                    ActuaCardDivider()
                    ActuaFormTextField(
                        icon = Icons.Outlined.Key,
                        label = stringResource(R.string.server_password),
                        value = password,
                        onValueChange = { password = it },
                        placeholder = stringResource(R.string.server_password_required),
                        enabled = !loading,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailing = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.hide_password else R.string.show_password
                                    ))
                            }
                        },
                    )
                }
            }
            if (!connected || editingConnection) {
                ActuaGroupLabel(stringResource(R.string.custom_http_headers))
                ActuaFormCard {
                    headerEntries.forEachIndexed { index, (name, value) ->
                        if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                        Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(value, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(
                                enabled = !loading,
                                onClick = { headerEntries = headerEntries.filterIndexed { i, _ -> i != index } },
                            ) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.remove_named_header, name)) }
                        }
                    }
                    if (headerEntries.isNotEmpty()) ActuaCardDivider(inset = Spacing.lg)
                    ActuaFormRow(
                        icon = Icons.Outlined.Add,
                        label = stringResource(R.string.add_header),
                        value = null,
                        enabled = !loading,
                        onClick = { showAddHeader = true },
                    )
                }
                Text(
                    stringResource(R.string.custom_headers_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!connected) {
                ConnectionAction(
                    text = when {
                        loading -> stringResource(R.string.connecting)
                        sessionExpired -> stringResource(R.string.sign_in_again_password)
                        else -> stringResource(R.string.connect_with_password)
                    },
                    primary = true,
                    busy = loading,
                    onClick = { connectWithLocalNetworkAccess(localNetworkPermission, ::connectWithPassword) },
                    enabled = serverUrl.isNotBlank() && password.isNotBlank() && !loading,
                )
                ConnectionAction(
                    text = stringResource(R.string.sign_in_openid),
                    onClick = { connectWithLocalNetworkAccess(localNetworkOpenIdPermission, ::connectWithOpenId) },
                    enabled = serverUrl.isNotBlank() && !loading,
                )
                if (Build.VERSION.SDK_INT >= 37) {
                    Text(
                        stringResource(R.string.local_network_permission_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.openid_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sessionExpired) {
                    ConnectionAction(text = stringResource(R.string.disconnect), onClick = ::disconnect, enabled = !loading)
                }
            } else {
                if (editingConnection) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ConnectionAction(text = stringResource(R.string.cancel), modifier = Modifier.weight(1f), onClick = {
                        serverUrl = credentials.serverUrl
                        fallbackServerUrl = credentials.fallbackServerUrl
                        headerEntries = credentials.customHeaders.toList()
                        editingConnection = false
                    })
                    ConnectionAction(text = stringResource(R.string.save), primary = true, busy = loading, modifier = Modifier.weight(1f), onClick = {
                        loading = true; message = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                val primary = client.normalizeServerUrl(serverUrl)
                                val fallback = fallbackServerUrl.trim().takeIf(String::isNotEmpty)?.let(client::normalizeServerUrl).orEmpty()
                                client.loginMethods(primary)
                                primary to fallback
                            } }.onSuccess { (primary, fallback) ->
                                credentials.updateServerUrls(primary, fallback)
                                credentials.customHeaders = headerEntries.toMap()
                                serverUrl = primary; fallbackServerUrl = fallback; activeServerUrl = primary
                                editingConnection = false
                                message = resources.getString(R.string.server_addresses_updated)
                            }.onFailure { message = it.message ?: resources.getString(R.string.could_not_reach_primary_server) }
                            loading = false
                        }
                    }, enabled = serverUrl.isNotBlank() && !loading)
                }
                ConnectionAction(text = stringResource(R.string.disconnect), onClick = ::disconnect)
            }
            message?.let {
                Text(it, color = if (connected || demoActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.token_security_description),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (connected) {
                ActuaGroupLabel(stringResource(R.string.sync))
                if (demoActive) {
                    Text(
                        stringResource(R.string.demo_sync_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (standaloneActive) {
                    Text(
                        stringResource(R.string.standalone_sync_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ActuaFormCard {
                    ActuaFormRow(
                        icon = Icons.Outlined.Sync,
                        label = stringResource(R.string.status),
                        value = null,
                        supportingValue = if (syncStatus.running || syncing) {
                            syncStatus.activeTrigger?.let { stringResource(R.string.syncing_trigger, syncTriggerDisplayName(it)) }
                                ?: stringResource(R.string.syncing_plain)
                        } else if (syncStatus.error != null) stringResource(R.string.error) else stringResource(R.string.idle),
                    )
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.History,
                        label = stringResource(R.string.last_sync),
                        value = null,
                        supportingValue = syncStatus.lastSuccessMillis.takeIf { it > 0 }?.let { relativeTime(it) }
                            ?: stringResource(R.string.never),
                    )
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.PhoneAndroid,
                        label = stringResource(R.string.last_app_open_refresh),
                        value = null,
                        supportingValue = syncStatus.lastForegroundRefreshMillis.takeIf { it > 0 }?.let { relativeTime(it) }
                            ?: stringResource(R.string.never),
                    )
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Schedule,
                        label = stringResource(R.string.last_background_attempt),
                        value = null,
                        supportingValue = syncStatus.lastBackgroundRefreshMillis.takeIf { it > 0 }?.let { relativeTime(it) }
                            ?: stringResource(R.string.never),
                    )
                    if (syncStatus.lastDurationMillis > 0) {
                        ActuaCardDivider()
                        ActuaFormRow(
                            icon = Icons.Outlined.Timer,
                            label = stringResource(R.string.last_sync_duration),
                            value = null,
                            supportingValue = formatSyncDuration(
                                syncStatus.lastDurationMillis,
                                LocalConfiguration.current.locales[0],
                            ),
                        )
                    }
                }
                Text(
                    stringResource(R.string.sync_timing_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                syncStatus.error?.let { Text(syncErrorDisplayMessage(it), color = MaterialTheme.colorScheme.error) }
                if ((syncStatus.rejectionReason == SYNC_REASON_FILE_HAS_RESET ||
                        syncStatus.rejectionReason == SYNC_REASON_FILE_HAS_NEW_KEY) && activeBudget.budgetId != null
                ) {
                    FilledTonalButton(
                        enabled = !syncing && !loading && downloadingId == null && !recoveringFromReset,
                        modifier = Modifier.fillMaxWidth().height(Sizes.secondaryButtonHeight),
                        shape = MaterialTheme.shapes.large,
                        onClick = {
                            val budgetId = activeBudget.budgetId ?: return@FilledTonalButton
                            message = null
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { resetRecovery.unsyncedChanges(budgetId) } }
                                    .onSuccess {
                                        newKeyPassword = ""
                                        resetRecoveryPrompt = ResetRecoveryPrompt(
                                            it, needsNewPassword = syncStatus.rejectionReason == SYNC_REASON_FILE_HAS_NEW_KEY,
                                        )
                                    }
                                    .onFailure { message = resources.getString(R.string.sync_reset_failed) }
                            }
                        },
                    ) { Text(stringResource(R.string.sync_reset_recover_action)) }
                }
                FilledTonalButton(enabled = !demoActive && !syncing && !loading && downloadingId == null,
                    modifier = Modifier.fillMaxWidth().height(Sizes.secondaryButtonHeight),
                    shape = MaterialTheme.shapes.large, onClick = {
                        syncing = true; message = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) {
                                ActualSyncRunner.run(context, trigger = SYNC_TRIGGER_MANUAL)
                            } }.onSuccess { result ->
                                when (result) {
                                    is SyncRunResult.Success -> {
                                        message = if (standaloneActive) {
                                            resources.getString(R.string.processed_schedules_backup)
                                        } else {
                                            resources.getString(
                                                R.string.sync_result,
                                                result.outcome.sentMessages, result.outcome.receivedMessages,
                                            )
                                        }
                                        onBudgetInstalled()
                                    }
                                    SyncRunResult.NotConfigured -> {
                                        message = resources.getString(R.string.download_select_budget_first)
                                    }
                                    SyncRunResult.EncryptionKeyUnavailable -> {
                                        message = resources.getString(R.string.unlock_budget_before_sync)
                                    }
                                }
                            }.onFailure { showServerError(it, R.string.sync_failed) }
                            syncStatus = syncStatusStore.read(); syncing = false
                        }
                    }) {
                    ManualSyncButtonContent(
                        syncing = syncing,
                        demoActive = demoActive,
                        standaloneActive = standaloneActive,
                    )
                }
                val budgetsChevronRotation by animateFloatAsState(
                    if (budgetsExpanded) 0f else -90f, tween(220), label = "budgets section",
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClick = { budgetsExpanded = !budgetsExpanded }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.budgets), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).padding(start = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm))
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (budgetsExpanded) R.string.collapse_budgets else R.string.expand_budgets
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.rotate(budgetsChevronRotation),
                    )
                }
                AnimatedVisibility(visible = budgetsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ConnectionAction(
                            text = stringResource(R.string.create_new_budget),
                            onClick = { showCreateBudget = true; newBudgetName = "" },
                            enabled = !loading && downloadingId == null,
                        )
                        if (remoteBudgets.any { it.encryptedKeyId != null }) ActuaFormCard {
                            ActuaFormTextField(
                                icon = Icons.Outlined.Key,
                                label = stringResource(R.string.budget_encryption_password),
                                value = encryptionPassword,
                                onValueChange = { encryptionPassword = it },
                                placeholder = stringResource(R.string.budget_encryption_password_hint),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                        }
                        if (remoteBudgets.isNotEmpty()) ActuaFormCard {
                            remoteBudgets.forEachIndexed { index, remote ->
                                val local = downloader.localCopy(remote, localBudgets)
                                val isActive = local != null && activeBudget.budgetId == local.id
                                if (index > 0) ActuaCardDivider(inset = Spacing.lg)
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(remote.name, fontWeight = FontWeight.Medium)
                                        Text(
                                            when {
                                                isActive -> stringResource(R.string.active)
                                                local != null -> stringResource(R.string.downloaded)
                                                remote.encryptedKeyId != null -> stringResource(R.string.encrypted)
                                                else -> stringResource(R.string.available)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    // The active budget has no button: its local copy is already open, and
                                    // replacing it with the server snapshot would discard unsynced edits.
                                    if (!isActive) FilledTonalButton(
                                        enabled = downloadingId == null,
                                        shape = MaterialTheme.shapes.large,
                                        onClick = {
                                            val token = credentials.token() ?: return@FilledTonalButton
                                            downloadingId = remote.fileId
                                            message = null
                                            scope.launch {
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        val needsKey = remote.encryptedKeyId != null &&
                                                            BudgetEncryptionKeyStore(context).load(remote.fileId) == null
                                                        if (needsKey && encryptionPassword.isNotBlank()) {
                                                            downloader.unlock(activeServerUrl, token, remote.fileId, encryptionPassword)
                                                        }
                                                        downloader.openOrDownload(activeServerUrl, token, remote)
                                                    }
                                                }.onSuccess { metadata ->
                                                    onBeforeBudgetReplacement()
                                                    activeBudget.budgetId = metadata.id
                                                    message = if (local == null) {
                                                        resources.getString(R.string.budget_downloaded_active, remote.name)
                                                    } else {
                                                        resources.getString(R.string.budget_now_active, remote.name)
                                                    }
                                                    localBudgetsGeneration++
                                                    onBudgetInstalled()
                                                    // Catch up with the server and send any edits made before switching away.
                                                    ActualSyncScheduler.scheduleMutation(context)
                                                }.onFailure { error ->
                                                    message = when (error) {
                                                        BudgetDownloadException.EncryptionPasswordRequired ->
                                                            resources.getString(R.string.enter_budget_encryption_password)
                                                        BudgetDownloadException.EncryptionKeyChanged ->
                                                            resources.getString(R.string.budget_encryption_key_changed)
                                                        BudgetDownloadException.InvalidEncryptionMetadata ->
                                                            resources.getString(R.string.invalid_budget_encryption_metadata)
                                                        else -> connectionErrorMessage(
                                                            error,
                                                            resources.getString(R.string.could_not_download_budget),
                                                            resources.getString(R.string.server_certificate_not_trusted_error),
                                                        )
                                                    }
                                                    if (error is ActualServerException.SessionExpired) showServerError(error, R.string.could_not_download_budget)
                                                }
                                                downloadingId = null
                                            }
                                        },
                                    ) {
                                        if (downloadingId == remote.fileId) {
                                            CircularProgressIndicator(Modifier.padding(end = Spacing.sm).size(18.dp), strokeWidth = 2.dp)
                                        }
                                        Text(stringResource(if (local == null) R.string.download else R.string.open))
                                    }
                                    TextButton(
                                        enabled = downloadingId == null && !loading,
                                        onClick = { pendingDelete = remote; deleteConfirmation = "" },
                                    ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                        ConnectionAction(text = stringResource(R.string.refresh_budget_list), onClick = { loadBudgets() },
                            enabled = !loading && downloadingId == null)
                    }
                }
            }

            if (!connected && standaloneActive) {
                ActuaGroupLabel(stringResource(R.string.schedules_backup))
                Text(
                    stringResource(R.string.standalone_schedules_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    enabled = !syncing && !loading && downloadingId == null,
                    modifier = Modifier.fillMaxWidth().height(Sizes.secondaryButtonHeight),
                    shape = MaterialTheme.shapes.large,
                    onClick = {
                        syncing = true
                        message = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ActualSyncRunner.run(context, trigger = SYNC_TRIGGER_MANUAL)
                                }
                            }.onSuccess {
                                message = resources.getString(R.string.processed_schedules_backup)
                                onBudgetInstalled()
                                refreshBackups()
                            }.onFailure { error ->
                                message = error.message ?: resources.getString(R.string.could_not_process_schedules)
                            }
                            syncing = false
                        }
                    },
                ) {
                    ManualSyncButtonContent(
                        syncing = syncing,
                        demoActive = false,
                        standaloneActive = true,
                    )
                }
            }

            activeBudget.budgetId?.let { budgetId ->
                ActuaGroupLabel(stringResource(R.string.backups_title))
                Text(
                    stringResource(R.string.private_backups_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActuaFormCard {
                    ActuaFormRow(
                        icon = Icons.Outlined.Inventory2,
                        label = stringResource(R.string.backups_title),
                        value = null,
                        supportingValue = backups.count { it is BackupItem.Archive }.toString(),
                        enabled = !backupBusy,
                        onClick = { showBackups = true },
                    )
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Backup,
                        label = stringResource(if (backupBusy) R.string.working else R.string.create_backup_now),
                        value = null,
                        enabled = !backupBusy && downloadingId == null && !syncing,
                        trailing = if (backupBusy) {
                            { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                        } else null,
                        onClick = {
                            if (backups.any { it is BackupItem.Latest }) {
                                confirmQuickBackup = true
                            } else {
                                backupBusy = true; message = null
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { backupService.makeBackup(budgetId) } }
                                        .onSuccess { message = resources.getString(R.string.backup_created) }
                                        .onFailure { message = it.message ?: resources.getString(R.string.could_not_create_a_backup) }
                                    backupBusy = false; refreshBackups()
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** A full-width action in the form style: the filled main action when [primary], otherwise tonal. */
@Composable
private fun ConnectionAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    busy: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (busy) CircularProgressIndicator(Modifier.padding(end = Spacing.sm).size(18.dp), strokeWidth = 2.dp)
        Text(text)
    }
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth().height(Sizes.primaryButtonHeight),
            shape = MaterialTheme.shapes.large,
            content = content,
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth().height(Sizes.secondaryButtonHeight),
            shape = MaterialTheme.shapes.large,
            content = content,
        )
    }
}

/** What the re-download confirmation shows; [unsyncedChanges] is null when this device can't tell. */
private class ResetRecoveryPrompt(val unsyncedChanges: Int?, val needsNewPassword: Boolean = false)
