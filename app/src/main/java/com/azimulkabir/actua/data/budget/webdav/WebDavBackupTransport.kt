package com.azimulkabir.actua.data.budget.webdav

import com.azimulkabir.actua.data.budget.backup.BackupTransport
import com.azimulkabir.actua.data.budget.backup.BackupUpload
import com.azimulkabir.actua.data.budget.backup.RemoteBackupItem
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.InputStream
import java.io.OutputStream

/**
 * A WebDAV server as a [BackupTransport]. Backups live in `<base>/<folder>/<budget id>/<name>.actuabkp`.
 *
 * Uploads go to `<name>.actuabkp.part` first and are renamed once complete, and the stored size is checked
 * against what was sent, so a dropped connection never leaves a file that is listed as a backup. Only
 * `.actuabkp` files inside the configured folder are ever listed, downloaded or deleted, whatever id is given.
 */
class WebDavBackupTransport(
    private val client: WebDavClient,
    private val baseUrl: HttpUrl,
    private val folderSegments: List<String>,
) : BackupTransport {
    private val folderUrl: HttpUrl = baseUrl.newBuilder().apply { folderSegments.forEach { addPathSegment(it) } }.build()

    override fun upload(request: BackupUpload, content: InputStream): RemoteBackupItem {
        val budgetFolder = client.ensureCollections(baseUrl, folderSegments + validBudgetId(request.budgetId))
        val finalUrl = budgetFolder.newBuilder().addPathSegment(request.name).build()
        val partUrl = budgetFolder.newBuilder().addPathSegment(request.name + PART_SUFFIX).build()

        try {
            client.put(partUrl, request.contentLength, content)
            client.move(partUrl, finalUrl)
        } catch (error: Exception) {
            runCatching { client.delete(partUrl) }
            throw error
        }

        // A server that reports a different size than was sent has stored something truncated.
        val stored = runCatching { client.propfind(finalUrl, 0).firstOrNull() }.getOrNull()
        if (stored != null && stored.sizeBytes >= 0 && stored.sizeBytes != request.contentLength) {
            runCatching { client.delete(finalUrl) }
            throw WebDavException.UploadIncomplete(request.contentLength, stored.sizeBytes)
        }

        return RemoteBackupItem(
            id = finalUrl.toString(),
            name = request.name,
            budgetId = request.budgetId,
            budgetName = request.budgetName,
            sizeBytes = request.contentLength,
            modifiedTime = request.createdAt,
            isEncrypted = true,
        )
    }

    override fun list(budgetId: String): List<RemoteBackupItem> {
        val collection = folderUrl.newBuilder().addPathSegment(validBudgetId(budgetId)).build()
        return backupFilesIn(collection, budgetId)
    }

    override fun listAll(): List<RemoteBackupItem> {
        val entries = try {
            client.propfind(folderUrl, 1)
        } catch (error: WebDavException.NotFound) {
            return emptyList()
        }
        val result = mutableListOf<RemoteBackupItem>()
        for (entry in entries) {
            if (!entry.isCollection || isSameLocation(entry.url, folderUrl)) continue
            val budgetId = entry.url.pathSegments.lastOrNull { it.isNotEmpty() }
                ?.takeIf { ID_PATTERN.matches(it) } ?: continue
            // A budget folder that cannot be read is skipped so the others are still offered.
            result += runCatching { backupFilesIn(entry.url, budgetId) }.getOrDefault(emptyList())
        }
        return result
    }

    override fun download(backupId: String, output: OutputStream) {
        client.get(managedBackup(backupId), output)
    }

    override fun delete(backupId: String) {
        client.delete(managedBackup(backupId))
    }

    private fun backupFilesIn(collection: HttpUrl, budgetId: String): List<RemoteBackupItem> {
        val entries = try {
            client.propfind(collection, 1)
        } catch (error: WebDavException.NotFound) {
            return emptyList()
        }
        return entries
            .filter { !it.isCollection && !isSameLocation(it.url, collection) && isBackupFile(it.url) }
            .map { entry ->
                val name = entry.url.pathSegments.last()
                RemoteBackupItem(
                    id = entry.url.toString(),
                    name = name,
                    budgetId = budgetId,
                    budgetName = null,
                    sizeBytes = entry.sizeBytes.coerceAtLeast(0),
                    modifiedTime = entry.modified,
                    isEncrypted = true,
                )
            }
    }

    /** [backupId] as a URL, only if it is a backup file inside the configured folder on the same server. */
    private fun managedBackup(backupId: String): HttpUrl {
        val url = backupId.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Not a WebDAV backup address")
        val insideFolder = url.scheme == folderUrl.scheme && url.host == folderUrl.host && url.port == folderUrl.port &&
            segments(url).size > segments(folderUrl).size &&
            segments(url).take(segments(folderUrl).size) == segments(folderUrl)
        require(insideFolder && isBackupFile(url)) { "Not a backup in the configured WebDAV folder" }
        return url
    }

    private fun isBackupFile(url: HttpUrl): Boolean = url.pathSegments.lastOrNull().orEmpty().endsWith(BACKUP_SUFFIX)

    private fun isSameLocation(a: HttpUrl, b: HttpUrl): Boolean = segments(a) == segments(b)

    private fun segments(url: HttpUrl): List<String> = url.pathSegments.filter { it.isNotEmpty() }

    private fun validBudgetId(budgetId: String): String {
        require(ID_PATTERN.matches(budgetId) && budgetId != "." && budgetId != "..") { "Unsupported budget id" }
        return budgetId
    }

    private companion object {
        const val BACKUP_SUFFIX = ".actuabkp"
        const val PART_SUFFIX = ".part"
        val ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
