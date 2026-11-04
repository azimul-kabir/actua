package com.azimulkabir.actua.data.budget.gdrive

import com.azimulkabir.actua.data.budget.backup.BackupTransport
import com.azimulkabir.actua.data.budget.backup.BackupUpload
import com.azimulkabir.actua.data.budget.backup.RemoteBackupItem
import java.io.InputStream
import java.io.OutputStream

/**
 * Google Drive as a [BackupTransport]: one root folder ("Actua Backups") holding one sub-folder per budget,
 * which is the layout Drive backups have always used. Folder ids are remembered for the life of this object so
 * one operation resolves each folder once.
 */
class GoogleDriveBackupTransport(
    private val restClient: GoogleDriveRestClient,
) : BackupTransport {
    private var rootFolderId: String? = null
    private val budgetFolderIds = mutableMapOf<String, String>()

    private fun rootFolder(): String = rootFolderId
        ?: restClient.findOrCreateFolder(GoogleDriveConfig.DEFAULT_ROOT_FOLDER_NAME).also { rootFolderId = it }

    private fun budgetFolder(budgetId: String): String = budgetFolderIds.getOrPut(budgetId) {
        restClient.findOrCreateFolder(budgetId, rootFolder())
    }

    override fun upload(request: BackupUpload, content: InputStream): RemoteBackupItem {
        val budgetFolderId = budgetFolder(request.budgetId)
        val appProperties = mapOf(
            "budgetId" to request.budgetId,
            "budgetName" to request.budgetName,
            "archiveName" to request.archiveName,
            "format" to "ACTUABKP",
            "version" to "1",
            "encrypted" to "true",
            "createdAt" to request.createdAt,
        )
        val uploaded = restClient.uploadMultipart(
            name = request.name,
            content = content,
            mimeType = GoogleDriveConfig.MIME_TYPE_ENCRYPTED_BACKUP,
            parentFolderId = budgetFolderId,
            appProperties = appProperties,
        )
        return uploaded.toItem(budgetId = uploaded.appProperties["budgetId"] ?: request.budgetId,
            budgetName = uploaded.appProperties["budgetName"])
    }

    override fun list(budgetId: String): List<RemoteBackupItem> {
        val budgetFolderId = runCatching { budgetFolder(budgetId) }.getOrNull() ?: return emptyList()
        return restClient.listBackups(budgetFolderId)
            .filter { it.mimeType != GoogleDriveConfig.MIME_TYPE_FOLDER }
            .map { file ->
                file.toItem(budgetId = file.appProperties["budgetId"] ?: budgetId, budgetName = file.appProperties["budgetName"])
            }
    }

    override fun listAll(): List<RemoteBackupItem> {
        val rootFolderId = runCatching { rootFolder() }.getOrNull() ?: return emptyList()

        // Each sub-folder of the root is a budget; loose files directly under the root are listed as they are.
        val result = mutableListOf<RemoteBackupItem>()
        for (item in restClient.listBackups(rootFolderId)) {
            if (item.mimeType == GoogleDriveConfig.MIME_TYPE_FOLDER) {
                val subFiles = runCatching { restClient.listBackups(item.id) }.getOrDefault(emptyList())
                for (file in subFiles) {
                    if (file.mimeType != GoogleDriveConfig.MIME_TYPE_FOLDER) {
                        result += file.toItem(
                            budgetId = file.appProperties["budgetId"] ?: item.name,
                            budgetName = file.appProperties["budgetName"] ?: item.name,
                        )
                    }
                }
            } else {
                result += item.toItem(
                    budgetId = item.appProperties["budgetId"],
                    budgetName = item.appProperties["budgetName"],
                )
            }
        }
        return result
    }

    override fun download(backupId: String, output: OutputStream) {
        restClient.downloadFile(backupId, output)
    }

    override fun delete(backupId: String) {
        restClient.deleteFile(backupId)
    }

    private fun GoogleDriveFileMetadata.toItem(budgetId: String?, budgetName: String?) = RemoteBackupItem(
        id = id,
        name = name,
        budgetId = budgetId,
        budgetName = budgetName,
        sizeBytes = sizeBytes,
        modifiedTime = modifiedTime,
        isEncrypted = name.endsWith(".actuabkp") || appProperties["encrypted"] == "true",
    )
}
