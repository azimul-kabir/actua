package com.azimulkabir.actua.data.budget.webdav

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.BudgetMetadata
import com.azimulkabir.actua.data.budget.backup.BudgetRestoreSink
import com.azimulkabir.actua.data.budget.backup.EncryptedBackupPipeline
import com.azimulkabir.actua.data.budget.backup.RemoteBackupItem
import com.azimulkabir.actua.data.network.PinnedCertificateTrustManager
import com.azimulkabir.actua.data.network.TrustedCertificateStore
import com.azimulkabir.actua.data.network.systemTrustManager
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

/**
 * WebDAV backup: the saved connection, the HTTP client (with the same pinned-certificate trust Actua gives
 * Actual servers) and the shared [EncryptedBackupPipeline] running over a [WebDavBackupTransport].
 * Nothing leaves the phone unencrypted, and every network call is made on the caller's thread.
 */
class WebDavBackupManager(
    context: Context,
    private val store: WebDavBackupStore = WebDavBackupStore(context),
    private val certificates: TrustedCertificateStore = TrustedCertificateStore(context),
    private val files: BudgetFileManager = BudgetFileManager(context),
    private val backupService: BackupService = BackupService(context, files),
) {
    private val appContext = context.applicationContext

    fun isConfigured(): Boolean = store.isConfigured()

    fun config(): WebDavConfig? = store.load()

    fun hasEncryptionPassphrase(): Boolean = store.hasPassphrase()

    fun saveEncryptionPassphrase(passphrase: CharArray) = store.savePassphrase(passphrase)

    val lastSuccessfulBackupMillis: Long get() = store.lastSuccessfulBackupMillis

    val lastBackupError: String? get() = store.lastBackupError

    fun recordError(error: String?) {
        store.lastBackupError = error
    }

    /**
     * The wording for [error]. A server that cannot be reached is explained as the missing Nearby devices
     * permission when that is the likely cause (Android 17 or later, server on the local network, permission not
     * granted). It resolves the server's host, so call it off the main thread.
     */
    fun describe(error: Throwable): String {
        val missingPermission = Build.VERSION.SDK_INT >= WebDavLocalNetwork.FIRST_RESTRICTED_SDK &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_LOCAL_NETWORK) !=
            PackageManager.PERMISSION_GRANTED
        val explainedByPermission = WebDavLocalNetwork.explainsFailure(
            error = error,
            serverUrl = store.load()?.serverUrl,
            sdkInt = Build.VERSION.SDK_INT,
            permissionGranted = !missingPermission,
        )
        return if (explainedByPermission) {
            appContext.getString(R.string.webdav_error_local_network_denied)
        } else {
            WebDavErrorMessages.describe(appContext.resources, error)
        }
    }

    /**
     * Checks the settings against the server and creates the backup folder, then saves them. Nothing is saved
     * when anything fails, so a wrong password or an untrusted certificate leaves the old connection as it was.
     */
    fun connect(config: WebDavConfig) {
        val base = config.baseUrl()
        val segments = config.folderSegments()
        val client = client(config)
        client.propfind(base, 0)
        client.ensureCollections(base, segments)
        store.save(config.copy(serverUrl = config.serverUrl.trim(), folder = segments.joinToString("/")))
        store.lastBackupError = null
    }

    fun disconnect() {
        WebDavBackupScheduler.cancelAll(appContext)
        store.clear()
    }

    fun uploadBackup(budgetId: String, archiveFile: File): RemoteBackupItem {
        val uploaded = pipeline().upload(budgetId, archiveFile)
        store.lastSuccessfulBackupMillis = System.currentTimeMillis()
        store.lastBackupError = null
        return uploaded
    }

    fun listRemoteBackups(budgetId: String? = null): List<RemoteBackupItem> = pipeline().listBackups(budgetId)

    fun restoreRemoteBackup(
        remoteBackupId: String,
        passphrase: CharArray? = null,
        targetBudgetId: String? = null,
    ): BudgetMetadata = pipeline().restore(remoteBackupId, passphrase, targetBudgetId)

    private fun pipeline(): EncryptedBackupPipeline {
        val config = store.load() ?: throw WebDavException.InvalidSettings(
            WebDavException.InvalidSettings.Reason.MISSING_CREDENTIALS,
        )
        return EncryptedBackupPipeline(
            transport = WebDavBackupTransport(client(config), config.baseUrl(), config.folderSegments()),
            passphraseProvider = { store.loadPassphrase() },
            budgetNameOf = { budgetId -> files.listLocalBudgets().firstOrNull { it.id == budgetId }?.budgetName },
            restoreSink = BudgetRestoreSink(files, backupService),
            workDir = appContext.cacheDir,
        )
    }

    private fun client(config: WebDavConfig): WebDavClient {
        val base = config.baseUrl()
        return WebDavClient(httpClient(base.host, certificates.fingerprint(base.host)), config.username, config.password)
    }

    companion object {
        /**
         * An OkHttp client for one WebDAV host. A certificate the user chose to trust is accepted by its pinned
         * fingerprint; hostname verification stays on, and redirects between http and https are never followed,
         * so credentials are not resent to another scheme.
         */
        internal fun httpClient(host: String, pinnedFingerprint: String?): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followSslRedirects(false)
            if (pinnedFingerprint != null) {
                val trustManager = PinnedCertificateTrustManager(systemTrustManager(), host, pinnedFingerprint)
                val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            }
            return builder.build()
        }
    }
}
