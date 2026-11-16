package com.azimulkabir.actua.data.budget.webdav

import android.content.Context
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.BudgetMetadata
import com.azimulkabir.actua.data.budget.backup.BackupRestoreSink
import com.azimulkabir.actua.data.receipts.ReceiptPhotoStore
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Adds receipt recovery to WebDAV only; the standard sink still owns budget validation and safety copies. */
internal class WebDavReceiptRestoreSink(
    context: Context,
    private val files: BudgetFileManager,
    private val delegate: BackupRestoreSink,
) : BackupRestoreSink {
    private val app = context.applicationContext
    private val photos = ReceiptPhotoStore(app)

    override fun restoreInto(budgetId: String, archive: InputStream): BudgetMetadata = staged(archive) { zip, receipts ->
        ActualSyncRunner.whileSyncPaused {
            photos.withRestoredPhotos(budgetId, receipts) {
                zip.inputStream().use { delegate.restoreInto(budgetId, it) }
            }
        }
    }

    override fun importAsNew(archive: InputStream): BudgetMetadata = staged(archive) { zip, receipts ->
        val metadata = zip.inputStream().use(delegate::importAsNew)
        try {
            photos.withRestoredPhotos(metadata.id, receipts) { metadata }
        } catch (error: Throwable) {
            files.deleteBudget(metadata.id)
            throw error
        }
    }

    private fun staged(archive: InputStream, restore: (File, File) -> BudgetMetadata): BudgetMetadata {
        val directory = File(app.cacheDir, "webdav-restore-${UUID.randomUUID()}")
        check(directory.mkdir())
        try {
            val zip = File(directory, "backup.zip")
            zip.outputStream().use { archive.copyTo(it) }
            val receipts = File(directory, "photos")
            WebDavReceiptArchive.extract(zip, receipts)
            // The local backup list must keep standard archives, so another destination cannot later
            // upload receipt files through an imported WebDAV backup.
            val budget = if (receipts.listFiles().orEmpty().isNotEmpty()) {
                File(directory, "budget.zip").also { WebDavReceiptArchive.write(zip, emptyMap(), it) }
            } else zip
            return restore(budget, receipts)
        } finally {
            directory.deleteRecursively()
        }
    }
}
