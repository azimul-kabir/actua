package com.azimulkabir.actua.data.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Receipt photos kept with a transaction, on this device only.
 *
 * Photos live in `files/ReceiptPhotos/<budget id>/<transaction id>.jpg`, outside the budget directory, so
 * no budget copy Actua makes (server sync, local and Google Drive backups, WebDAV backups, Export archive)
 * can include them: those only ever contain `db.sqlite` and `metadata.json`. The link between a transaction
 * and its photo is just that file name. It is not written to the budget database or the message log, adds
 * no synced field, and isolates budgets by directory. A missing file simply means no photo. The folder is
 * excluded from Android backup and device transfer in `backup_rules.xml` and `data_extraction_rules.xml`.
 */
class ReceiptPhotoStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, DIRECTORY)

    /** The kept photo of [transactionId] in [budgetId], or null when there is none or an id is not a safe name. */
    fun photoFile(budgetId: String, transactionId: String): File? {
        val file = fileFor(budgetId, transactionId) ?: return null
        return file.takeIf(File::isFile)
    }

    /** Moves the prepared [pending] photo into place as the photo of [transactionId]; false when it could not. */
    fun keep(budgetId: String, transactionId: String, pending: File): Boolean {
        val target = fileFor(budgetId, transactionId) ?: return false
        if (!pending.isFile) return false
        return try {
            check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
            if (!pending.renameTo(target)) {
                pending.copyTo(target, overwrite = true)
                pending.delete()
            }
            true
        } catch (_: Exception) {
            target.delete()
            false
        }
    }

    /** Deletes the photo of [transactionId]; a no-op when there is none. */
    fun delete(budgetId: String, transactionId: String) {
        fileFor(budgetId, transactionId)?.delete()
    }

    fun delete(budgetId: String, transactionIds: Collection<String>) {
        transactionIds.forEach { delete(budgetId, it) }
    }

    /** Deletes every photo of [budgetId], for when the budget is removed from this device. */
    fun deleteAll(budgetId: String) {
        val directory = directoryFor(budgetId) ?: return
        directory.deleteRecursively()
    }

    private fun directoryFor(budgetId: String): File? =
        if (isSafeName(budgetId)) File(root, budgetId) else null

    private fun fileFor(budgetId: String, transactionId: String): File? {
        val directory = directoryFor(budgetId) ?: return null
        if (!isSafeName(transactionId)) return null
        return File(directory, "$transactionId.jpg")
    }

    companion object {
        const val DIRECTORY = "ReceiptPhotos"
        private val SAFE_NAME = Regex("""[A-Za-z0-9._-]{1,128}""")
        private const val PENDING_DIRECTORY = "receipt-pending"
        private const val STALE_AFTER_MS = 24L * 60 * 60 * 1000

        /** Ids are file names, so only plain characters are accepted and "." and ".." never are. */
        internal fun isSafeName(name: String): Boolean = SAFE_NAME.matches(name) && name != "." && name != ".."

        /**
         * Decodes [source] (a camera photo or a picked image), turns it upright, scales it down to
         * [ReceiptPhotoSizing.MAX_EDGE] and writes it as a JPEG in the cache. This is the compressed copy that
         * may be kept; the original is untouched. Returns null when the image cannot be read. Blocking: call
         * it off the main thread. Leftover files from earlier scans are cleaned up here.
         */
        fun createPending(context: Context, source: Uri): File? {
            val directory = File(context.applicationContext.cacheDir, PENDING_DIRECTORY)
            directory.mkdirs()
            val now = System.currentTimeMillis()
            directory.listFiles()?.filter { now - it.lastModified() > STALE_AFTER_MS }?.forEach(File::delete)
            val target = File(directory, "${UUID.randomUUID()}.jpg")
            return try {
                compress(context, source, target)
                target
            } catch (_: Exception) {
                target.delete()
                null
            } catch (_: OutOfMemoryError) {
                target.delete()
                null
            }
        }

        private fun compress(context: Context, source: Uri, target: File) {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: throw IOException("Cannot open the image")
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")
            val options = BitmapFactory.Options().apply {
                inSampleSize = ReceiptPhotoSizing.sampleSize(bounds.outWidth, bounds.outHeight)
            }
            val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IOException("Cannot decode the image")
            val rotation = resolver.openInputStream(source)?.use { exifRotation(it) } ?: 0
            var bitmap = decoded
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (bitmap !== decoded) decoded.recycle()
            }
            val (width, height) = ReceiptPhotoSizing.fitWithin(bitmap.width, bitmap.height)
            if (width != bitmap.width || height != bitmap.height) {
                val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
            try {
                FileOutputStream(target).use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, ReceiptPhotoSizing.JPEG_QUALITY, it)) }
            } finally {
                bitmap.recycle()
            }
        }

        private fun exifRotation(input: java.io.InputStream): Int = try {
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }
}
