package com.azimulkabir.actua.data.budget.gdrive

import android.content.Context
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.BudgetMetadata
import com.azimulkabir.actua.data.budget.backup.BackupRestoreSink
import com.azimulkabir.actua.data.budget.backup.EncryptedBackupPipeline
import com.azimulkabir.actua.data.budget.backup.RemoteBackupItem
import java.io.File
import java.io.InputStream
import java.time.Instant

/** The backup types now live with the transport-independent pipeline; these names keep Drive callers unchanged. */
typealias GoogleDriveBackupItem = RemoteBackupItem
typealias PassphraseRequiredException = com.azimulkabir.actua.data.budget.backup.PassphraseRequiredException
typealias InvalidPassphraseException = com.azimulkabir.actua.data.budget.backup.InvalidPassphraseException

/**
 * Coordinates Google Drive cloud backup, retention pruning, and restore operations.
 * Preserves local-first invariants: local backups always succeed independently of Drive or network status.
 *
 * Authentication, the Drive-specific preferences and the connect/disconnect lifecycle live here; encryption,
 * retention and restore are the shared [EncryptedBackupPipeline] running over [GoogleDriveBackupTransport].
 */
class GoogleDriveBackupManager(
    context: Context,
    private val tokenStore: GoogleDriveTokenStore = GoogleDriveTokenStore(context),
    private val encryptionStore: GoogleDriveEncryptionStore = GoogleDriveEncryptionStore(context),
    val preferences: GoogleDrivePreferences = GoogleDrivePreferences(context),
    private val authClient: GoogleDriveAuthClient = GoogleDriveAuthClient(),
    private val files: BudgetFileManager = BudgetFileManager(context),
    private val backupService: BackupService = BackupService(context, files),
) {
    private val appContext = context.applicationContext

    fun isConnected(): Boolean = tokenStore.loadTokens() != null

    fun getAccountEmail(): String? = tokenStore.loadTokens()?.accountEmail

    fun hasEncryptionPassphrase(): Boolean = encryptionStore.hasPassphrase()

    fun getEncryptionPassphrase(): CharArray? = encryptionStore.loadPassphrase()

    fun saveEncryptionPassphrase(passphrase: CharArray) = encryptionStore.savePassphrase(passphrase)

    fun recordError(error: String?) {
        preferences.lastBackupError = error
    }

    @Synchronized
    fun getValidAccessToken(): String {
        val tokens = tokenStore.loadTokens() ?: throw GoogleDriveException.Unauthorized
        if (tokens.isExpired) {
            val refreshToken = tokens.refreshToken ?: throw GoogleDriveException.Unauthorized
            val refreshed = authClient.refreshAccessToken(
                clientId = preferences.getEffectiveClientId(),
                refreshToken = refreshToken,
            )
            tokenStore.saveTokens(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken,
                expiresInSeconds = refreshed.expiresAtEpochSeconds - (System.currentTimeMillis() / 1000),
                accountEmail = tokens.accountEmail,
            )
            return refreshed.accessToken
        }
        return tokens.accessToken
    }

    private fun pipeline() = EncryptedBackupPipeline(
        transport = GoogleDriveBackupTransport(GoogleDriveRestClient(tokenProvider = { getValidAccessToken() })),
        passphraseProvider = { encryptionStore.loadPassphrase() },
        budgetNameOf = { budgetId -> files.listLocalBudgets().firstOrNull { it.id == budgetId }?.budgetName },
        restoreSink = object : BackupRestoreSink {
            override fun restoreInto(budgetId: String, archive: InputStream): BudgetMetadata {
                val imported = backupService.importArchive(budgetId, archive)
                backupService.restore(budgetId, imported.name)
                return files.metadata(budgetId) ?: error("Restored budget metadata not found")
            }

            override fun importAsNew(archive: InputStream): BudgetMetadata = files.importLocalBudget(archive)
        },
        workDir = appContext.cacheDir,
    )

    fun uploadBackup(budgetId: String, archiveFile: File): RemoteBackupItem {
        val uploaded = pipeline().upload(budgetId, archiveFile)
        preferences.lastSuccessfulBackupMillis = System.currentTimeMillis()
        preferences.lastBackupError = null
        return uploaded
    }

    fun listRemoteBackups(budgetId: String? = null): List<GoogleDriveBackupItem> =
        pipeline().listBackups(budgetId)

    fun restoreRemoteBackup(
        remoteFileId: String,
        passphrase: CharArray? = null,
        targetBudgetId: String? = null,
    ): BudgetMetadata = pipeline().restore(remoteFileId, passphrase, targetBudgetId)

    fun disconnect() {
        val tokens = tokenStore.loadTokens()
        if (tokens != null) {
            tokens.refreshToken?.let { runCatching { authClient.revokeToken(it) } }
            runCatching { authClient.revokeToken(tokens.accessToken) }
        }
        tokenStore.clear()
        encryptionStore.clear()
        preferences.clear()
        GoogleDriveBackupScheduler.cancelAll(appContext)
    }

    companion object {
        fun parseBackupDate(name: String): Instant? = EncryptedBackupPipeline.parseBackupDate(name)
    }
}
