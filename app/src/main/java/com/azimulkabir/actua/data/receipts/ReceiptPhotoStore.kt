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
import java.io.InputStream
import java.util.UUID

/** A kept receipt photo, in Actua's private storage or in the folder the user chose. */
class ReceiptPhoto private constructor(private val file: File?, private val document: Uri?) {
    /** True for a photo kept in the user's own folder, which the app lock does not protect. */
    val inFolder: Boolean get() = document != null

    /** The photo's bytes, or null when it can no longer be read (the folder is gone or no longer permitted). */
    fun open(context: Context): InputStream? = runCatching {
        file?.inputStream() ?: document?.let { context.contentResolver.openInputStream(it) }
    }.getOrNull()

    internal companion object {
        fun ofFile(file: File) = ReceiptPhoto(file, null)
        fun ofDocument(uri: Uri) = ReceiptPhoto(null, uri)
    }
}

/** Where [ReceiptPhotoStore.keepPreferred] put a photo. */
enum class ReceiptKeepResult {
    /** In the folder the user chose. */
    IN_FOLDER,

    /** In Actua's private storage, as no folder is chosen. */
    PRIVATE,

    /** In private storage because the chosen folder could not be used (gone, unmounted or no longer permitted). */
    PRIVATE_FOLDER_UNAVAILABLE,

    /** Not kept at all. */
    FAILED,
}

/**
 * Receipt photos kept with a transaction, outside Actual sync.
 *
 * Photos live outside the budget database and never enter Actual sync, standard exports, local or Google
 * Drive backups. WebDAV backups explicitly include them inside authenticated encryption. The transaction link
 * is the file name, scoped by budget id. Android backup and device transfer exclude the private photo folder.
 * New photos can instead go to a user-selected folder. Private photos (including restored copies) take
 * precedence on lookup; writing a new photo to the chosen folder removes any older private copy.
 */
class ReceiptPhotoStore(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(context.applicationContext.filesDir, DIRECTORY)
    private val folder = ReceiptPhotoFolder(context)

    /** The kept photo of [transactionId] in [budgetId], from the chosen folder or private storage, or null. */
    fun photo(budgetId: String, transactionId: String): ReceiptPhoto? {
        if (!isSafeName(budgetId) || !isSafeName(transactionId)) return null
        photoFile(budgetId, transactionId)?.let { return ReceiptPhoto.ofFile(it) }
        folder.usableTree()?.let { tree ->
            folder.find(tree, budgetId, transactionId)?.let { return ReceiptPhoto.ofDocument(it) }
        }
        return null
    }

    /**
     * Keeps [pending] as the photo of [transactionId]: in the chosen folder when there is one and it works,
     * otherwise in private storage, and the result says which. The pending cache copy is consumed either way.
     */
    fun keepPreferred(budgetId: String, transactionId: String, pending: File): ReceiptKeepResult {
        if (!isSafeName(budgetId) || !isSafeName(transactionId)) return ReceiptKeepResult.FAILED
        val chosen = folder.state()
        val tree = chosen.uri
        if (tree != null && chosen.usable && pending.isFile && folder.write(tree, budgetId, transactionId, pending)) {
            photoFile(budgetId, transactionId)?.delete()
            pending.delete()
            return ReceiptKeepResult.IN_FOLDER
        }
        val kept = keep(budgetId, transactionId, pending)
        return when {
            !kept -> ReceiptKeepResult.FAILED
            tree != null -> ReceiptKeepResult.PRIVATE_FOLDER_UNAVAILABLE
            else -> ReceiptKeepResult.PRIVATE
        }
    }

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

    /** Deletes the photo of [transactionId] from private storage and the chosen folder; a no-op when there is none. */
    fun delete(budgetId: String, transactionId: String) {
        fileFor(budgetId, transactionId)?.delete()
        if (isSafeName(budgetId) && isSafeName(transactionId)) {
            folder.usableTree()?.let { folder.delete(it, budgetId, transactionId) }
        }
    }

    /**
     * Gives the photo of [fromId] to [toId], for when [fromId] is merged into [toId] and deleted. When [toId]
     * already has a photo it is kept and the photo of [fromId] is deleted, so no photo is left without its
     * transaction. The moved photo goes where [keepPreferred] puts new photos. Returns true when [toId] gained
     * a photo. Blocking: call it off the main thread.
     */
    fun move(budgetId: String, fromId: String, toId: String): Boolean {
        if (!isSafeName(budgetId) || !isSafeName(fromId) || !isSafeName(toId) || fromId == toId) return false
        val source = photo(budgetId, fromId) ?: return false
        if (photo(budgetId, toId) != null) {
            delete(budgetId, fromId)
            return false
        }
        val directory = File(appContext.cacheDir, PENDING_DIRECTORY).apply { mkdirs() }
        val pending = File(directory, "${UUID.randomUUID()}.jpg")
        val copied = try {
            source.open(appContext)?.use { input -> pending.outputStream().use { input.copyTo(it) } } != null
        } catch (_: IOException) {
            false
        }
        if (!copied) {
            pending.delete()
            return false
        }
        val moved = keepPreferred(budgetId, toId, pending) != ReceiptKeepResult.FAILED
        pending.delete()
        // The photo stays with the deleted id when it could not be moved, so a later attempt can still find it.
        if (moved) delete(budgetId, fromId)
        return moved
    }

    fun delete(budgetId: String, transactionIds: Collection<String>) {
        transactionIds.forEach { delete(budgetId, it) }
    }

    /** Deletes every photo of [budgetId], for when the budget is removed from this device. */
    fun deleteAll(budgetId: String) {
        val directory = directoryFor(budgetId) ?: return
        directory.deleteRecursively()
        folder.usableTree()?.let { folder.deleteBudget(it, budgetId) }
    }

    /** Effective photos for one budget, including the currently accessible user folder. */
    fun backupPhotos(budgetId: String): Map<String, ReceiptPhoto> {
        require(isSafeName(budgetId))
        val photos = linkedMapOf<String, ReceiptPhoto>()
        folder.usableTree()?.let { tree ->
            folder.photoIds(tree, budgetId).forEach { id ->
                val uri = folder.find(tree, budgetId, id) ?: error("Receipt disappeared during backup")
                photos[id] = ReceiptPhoto.ofDocument(uri)
            }
        }
        directoryFor(budgetId)?.listFiles()?.filter { it.isFile && it.extension == "jpg" && isSafeName(it.nameWithoutExtension) }
            ?.forEach { photos[it.nameWithoutExtension] = ReceiptPhoto.ofFile(it) }
        return photos
    }

    /**
     * Installs validated photos in private storage by directory rename, rolling back if the budget restore
     * fails. Merge preserves photos missing from older backups. Private copies take precedence over SAF copies
     * so restore never needs to overwrite a user's external files or depend on a folder permission grant.
     */
    fun <T> withRestoredPhotos(budgetId: String, photos: File, restoreBudget: () -> T): T {
        val restoredPhotos = photos.listFiles() ?: error("Cannot read staged receipts")
        if (restoredPhotos.isEmpty()) return restoreBudget()
        val destination = directoryFor(budgetId) ?: error("Invalid receipt budget id")
        check(root.mkdirs() || root.isDirectory)
        val staged = File(root, ".restore-${UUID.randomUUID()}")
        val previous = File(root, ".previous-${UUID.randomUUID()}")
        var installed = false
        var movedPrevious = false
        var committed = false
        try {
            check(staged.mkdir())
            if (destination.exists()) destination.copyRecursively(staged, overwrite = true)
            restoredPhotos.forEach { photo ->
                require(photo.isFile && photo.extension == "jpg" && isSafeName(photo.nameWithoutExtension))
                photo.copyTo(File(staged, photo.name), overwrite = true)
            }
            if (destination.exists()) {
                check(destination.renameTo(previous)) { "Cannot preserve receipt photos" }
                movedPrevious = true
            }
            check(staged.renameTo(destination)) { "Cannot install receipt photos" }
            installed = true
            val result = restoreBudget()
            committed = true
            return result
        } catch (error: Throwable) {
            if (installed) check(destination.deleteRecursively()) { "Cannot roll back receipt photos" }
            if (movedPrevious) check(previous.renameTo(destination)) { "Cannot recover receipt photos" }
            throw error
        } finally {
            staged.deleteRecursively()
            // A failed rollback must retain the previous directory for recovery.
            if (committed) previous.deleteRecursively()
        }
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
            val bitmap = decodeUpright(context, source, ReceiptPhotoSizing.MAX_EDGE)
            try {
                FileOutputStream(target).use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, ReceiptPhotoSizing.JPEG_QUALITY, it)) }
            } finally {
                bitmap.recycle()
            }
        }

        /**
         * Decodes [source] (a camera photo or a picked image), turns it upright and scales it down so its
         * longest edge is at most [maxEdge]. The caller owns the bitmap and should recycle it. Throws
         * [IOException] when the image cannot be opened or decoded. Blocking: call it off the main thread.
         */
        fun decodeUpright(context: Context, source: Uri, maxEdge: Int): Bitmap {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // decodeStream returns null in bounds-only mode, so success is read from the options, not the result.
            (resolver.openInputStream(source) ?: throw IOException("Cannot open the image")).use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")
            val options = BitmapFactory.Options().apply {
                inSampleSize = ReceiptPhotoSizing.sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
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
            val (width, height) = ReceiptPhotoSizing.fitWithin(bitmap.width, bitmap.height, maxEdge)
            if (width != bitmap.width || height != bitmap.height) {
                val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
            return bitmap
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
