package com.azimulkabir.actua.data.budget.gdrive

import android.content.Context
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BackupService.Companion.DatedBackup
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.BudgetMetadata
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID

data class GoogleDriveBackupItem(
    val id: String,
    val name: String,
    val budgetId: String?,
    val budgetName: String?,
    val sizeBytes: Long,
    val modifiedTime: String?,
    val isEncrypted: Boolean,
)

class PassphraseRequiredException : Exception("Backup encryption password is required to restore this backup")
class InvalidPassphraseException(message: String = "Incorrect backup encryption password") : Exception(message)

/**
 * Coordinates Google Drive cloud backup, retention pruning, and restore operations.
 * Preserves local-first invariants: local backups always succeed independently of Drive or network status.
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

    fun uploadBackup(budgetId: String, archiveFile: File): GoogleDriveFileMetadata {
        require(archiveFile.isFile) { "Archive file does not exist: ${archiveFile.path}" }
        val passphrase = encryptionStore.loadPassphrase()
            ?: throw IllegalStateException("Backup encryption password not configured")

        val stagingFile = File(appContext.cacheDir, ".staging-${UUID.randomUUID()}.actuabkp")
        try {
            // 1. Client-side authenticated encryption (before cloud upload)
            archiveFile.inputStream().use { input ->
                FileOutputStream(stagingFile).use { output ->
                    EncryptedBackupArchive.encrypt(input, output, passphrase)
                }
            }

            // 2. Drive REST client
            val restClient = GoogleDriveRestClient(tokenProvider = { getValidAccessToken() })

            // 3. Ensure root folder and budget subfolder exist
            val rootFolderId = restClient.findOrCreateFolder(GoogleDriveConfig.DEFAULT_ROOT_FOLDER_NAME)
            val budgetFolderId = restClient.findOrCreateFolder(budgetId, rootFolderId)

            val metadata = files.listLocalBudgets().firstOrNull { it.id == budgetId }
            val appProperties = mapOf(
                "budgetId" to budgetId,
                "budgetName" to (metadata?.budgetName ?: budgetId),
                "archiveName" to archiveFile.name,
                "format" to "ACTUABKP",
                "version" to "1",
                "encrypted" to "true",
                "createdAt" to Instant.now().toString(),
            )

            // 4. Upload encrypted container
            val uploadedName = "${archiveFile.name.removeSuffix(".zip")}.actuabkp"
            val uploaded = stagingFile.inputStream().use { stream ->
                restClient.uploadMultipart(
                    name = uploadedName,
                    content = stream,
                    mimeType = GoogleDriveConfig.MIME_TYPE_ENCRYPTED_BACKUP,
                    parentFolderId = budgetFolderId,
                    appProperties = appProperties,
                )
            }

            // 5. Safe remote retention pruning: only runs after new upload is verified
            pruneRemoteBackups(restClient, budgetFolderId, uploaded.id)

            preferences.lastSuccessfulBackupMillis = System.currentTimeMillis()
            preferences.lastBackupError = null
            return uploaded
        } finally {
            stagingFile.delete()
        }
    }

    private fun pruneRemoteBackups(
        restClient: GoogleDriveRestClient,
        budgetFolderId: String,
        currentUploadedFileId: String,
    ) {
        val remoteFiles = runCatching { restClient.listBackups(budgetFolderId) }.getOrNull() ?: return
        if (remoteFiles.none { it.id == currentUploadedFileId }) {
            // New upload not yet visible in listing; preserve all remote backups
            return
        }

        val datedBackups = remoteFiles.mapNotNull { file ->
            val date = parseBackupDate(file.name) ?: file.modifiedTime?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            } ?: return@mapNotNull null
            BackupService.Companion.DatedBackup(file.id, date)
        }.sortedByDescending { it.date }

        val toRemove = BackupService.backupsToRemove(datedBackups, Instant.now())
        // Invariant: Never delete the backup we just verified and uploaded
        val safeToRemove = toRemove.filter { it != currentUploadedFileId }

        for (fileId in safeToRemove) {
            runCatching { restClient.deleteFile(fileId) }
        }
    }

    fun listRemoteBackups(budgetId: String? = null): List<GoogleDriveBackupItem> {
        val restClient = GoogleDriveRestClient(tokenProvider = { getValidAccessToken() })
        val rootFolderId = runCatching {
            restClient.findOrCreateFolder(GoogleDriveConfig.DEFAULT_ROOT_FOLDER_NAME)
        }.getOrNull() ?: return emptyList()

        if (budgetId != null) {
            val budgetFolderId = runCatching {
                restClient.findOrCreateFolder(budgetId, rootFolderId)
            }.getOrNull() ?: return emptyList()

            return restClient.listBackups(budgetFolderId)
                .filter { it.mimeType != GoogleDriveConfig.MIME_TYPE_FOLDER }
                .map { file ->
                    GoogleDriveBackupItem(
                        id = file.id,
                        name = file.name,
                        budgetId = file.appProperties["budgetId"] ?: budgetId,
                        budgetName = file.appProperties["budgetName"],
                        sizeBytes = file.sizeBytes,
                        modifiedTime = file.modifiedTime,
                        isEncrypted = file.name.endsWith(".actuabkp") || file.appProperties["encrypted"] == "true",
                    )
                }
                .sortedByDescending { it.modifiedTime ?: it.name }
        }

        // For fresh install / restore across all budgets:
        // List subfolders in Actua Backups (each subfolder corresponds to a budget)
        val children = restClient.listBackups(rootFolderId)
        val result = mutableListOf<GoogleDriveBackupItem>()

        for (item in children) {
            if (item.mimeType == GoogleDriveConfig.MIME_TYPE_FOLDER) {
                val subFiles = runCatching { restClient.listBackups(item.id) }.getOrDefault(emptyList())
                for (file in subFiles) {
                    if (file.mimeType != GoogleDriveConfig.MIME_TYPE_FOLDER) {
                        result.add(
                            GoogleDriveBackupItem(
                                id = file.id,
                                name = file.name,
                                budgetId = file.appProperties["budgetId"] ?: item.name,
                                budgetName = file.appProperties["budgetName"] ?: item.name,
                                sizeBytes = file.sizeBytes,
                                modifiedTime = file.modifiedTime,
                                isEncrypted = file.name.endsWith(".actuabkp") || file.appProperties["encrypted"] == "true",
                            )
                        )
                    }
                }
            } else {
                result.add(
                    GoogleDriveBackupItem(
                        id = item.id,
                        name = item.name,
                        budgetId = item.appProperties["budgetId"],
                        budgetName = item.appProperties["budgetName"],
                        sizeBytes = item.sizeBytes,
                        modifiedTime = item.modifiedTime,
                        isEncrypted = item.name.endsWith(".actuabkp") || item.appProperties["encrypted"] == "true",
                    )
                )
            }
        }

        return result.sortedByDescending { it.modifiedTime ?: it.name }
    }

    fun restoreRemoteBackup(
        remoteFileId: String,
        passphrase: CharArray? = null,
        targetBudgetId: String? = null,
    ): BudgetMetadata {
        val restClient = GoogleDriveRestClient(tokenProvider = { getValidAccessToken() })
        val tempDownloaded = File(appContext.cacheDir, ".gdrive-download-${UUID.randomUUID()}.tmp")
        val tempDecrypted = File(appContext.cacheDir, ".gdrive-decrypted-${UUID.randomUUID()}.zip")

        try {
            // 1. Download file from Google Drive
            FileOutputStream(tempDownloaded).use { out ->
                restClient.downloadFile(remoteFileId, out)
            }

            // 2. Detect container format (ACTUABKP v1 or plain ZIP)
            val isEncrypted = isActuaBackupArchive(tempDownloaded)
            val archiveToRestore = if (isEncrypted) {
                val effectivePassphrase = passphrase ?: encryptionStore.loadPassphrase()
                    ?: throw PassphraseRequiredException()

                // Fast canary validation before full decryption
                val valid = tempDownloaded.inputStream().use { inStream ->
                    EncryptedBackupArchive.validatePassphrase(inStream, effectivePassphrase)
                }
                if (!valid) throw InvalidPassphraseException()

                // Decrypt to standard ZIP archive
                tempDownloaded.inputStream().use { inStream ->
                    FileOutputStream(tempDecrypted).use { outStream ->
                        EncryptedBackupArchive.decrypt(inStream, outStream, effectivePassphrase)
                    }
                }
                tempDecrypted
            } else {
                tempDownloaded
            }

            // 3. Restore: either into an open budget or as a new standalone budget
            return if (targetBudgetId != null) {
                archiveToRestore.inputStream().use { stream ->
                    val imported = backupService.importArchive(targetBudgetId, stream)
                    backupService.restore(targetBudgetId, imported.name)
                }
                files.metadata(targetBudgetId) ?: error("Restored budget metadata not found")
            } else {
                archiveToRestore.inputStream().use { stream ->
                    files.importLocalBudget(stream)
                }
            }
        } finally {
            tempDownloaded.delete()
            tempDecrypted.delete()
        }
    }

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

    private fun isActuaBackupArchive(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val magic = ByteArray(8)
            val read = input.read(magic)
            read == 8 && String(magic, Charsets.US_ASCII) == "ACTUABKP"
        }
    }.getOrDefault(false)

    companion object {
        private val backupNameFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

        fun parseBackupDate(name: String): Instant? = runCatching {
            val base = name.removeSuffix(".actuabkp").removeSuffix(".zip")
            val date = backupNameFormat.parse(base) ?: return null
            date.toInstant()
        }.getOrNull()
    }
}
