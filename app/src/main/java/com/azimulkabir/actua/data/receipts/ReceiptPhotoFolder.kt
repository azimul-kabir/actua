package com.azimulkabir.actua.data.receipts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * The receipt photo folder as the user left it: [uri] is the folder picked with the system folder picker (null
 * for Actua's private storage), [name] its display name, and [usable] whether Actua still holds permission to
 * it. A folder that is set but not usable (revoked, or the card or app behind it is gone) is reported as such.
 */
data class ReceiptPhotoFolderState(val uri: Uri?, val name: String?, val usable: Boolean)

/**
 * An optional folder for kept receipt photos, chosen with the Storage Access Framework like the local backup
 * folder. Photos go to `<folder>/Actua receipts/<budget id>/<transaction id>.jpg`, one subfolder per budget.
 * The folder is outside Actua's control: the app lock does not protect it, other apps with access to it can
 * read it, and a sync app that mirrors it will copy the photos. Actua itself never uploads them.
 *
 * Every operation tolerates a missing, unmounted or revoked folder: it reports failure instead of throwing.
 */
class ReceiptPhotoFolder(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun state(): ReceiptPhotoFolderState {
        val uri = preferences.getString(KEY_URI, null)?.let(Uri::parse)
            ?: return ReceiptPhotoFolderState(null, null, false)
        return ReceiptPhotoFolderState(uri, preferences.getString(KEY_NAME, null), hasPermission(uri))
    }

    /** The folder photos are read from and written to now: set, and still permitted. Null means private storage. */
    fun usableTree(): Uri? = state().takeIf { it.usable }?.uri

    /** Keeps [tree] as the folder for new photos and holds on to its permission. Photos already kept stay where they are. */
    fun select(tree: Uri, displayName: String?) {
        resolver.takePersistableUriPermission(tree, PERMISSION)
        val previous = preferences.getString(KEY_URI, null)?.let(Uri::parse)
        if (previous != null && previous != tree) release(previous)
        val name = displayName ?: documentName(tree)
        preferences.edit().putString(KEY_URI, tree.toString())
            .apply { if (name == null) remove(KEY_NAME) else putString(KEY_NAME, name) }
            .apply()
    }

    /** Goes back to private storage. Photos already in the folder stay there, but Actua can no longer show them. */
    fun clear() {
        preferences.getString(KEY_URI, null)?.let(Uri::parse)?.let(::release)
        preferences.edit().clear().apply()
    }

    /** The document of the photo of [transactionId] in [budgetId]'s subfolder of [tree], or null. */
    fun find(tree: Uri, budgetId: String, transactionId: String): Uri? = runCatching {
        val appDirectory = child(root(tree), DIRECTORY) ?: return null
        val budgetDirectory = child(appDirectory, budgetId) ?: return null
        child(budgetDirectory, "$transactionId.jpg")
    }.getOrNull()

    /** Writes [source] as the photo of [transactionId]; false (nothing thrown) when the folder cannot be written. */
    fun write(tree: Uri, budgetId: String, transactionId: String, source: File): Boolean = try {
        val root = root(tree)
        val appDirectory = child(root, DIRECTORY) ?: createDirectory(root, DIRECTORY)
        val budgetDirectory = child(appDirectory, budgetId) ?: createDirectory(appDirectory, budgetId)
        val name = "$transactionId.jpg"
        child(budgetDirectory, name)?.let { DocumentsContract.deleteDocument(resolver, it) }
        val target = DocumentsContract.createDocument(resolver, budgetDirectory, "image/jpeg", name)
        val output = target?.let { resolver.openOutputStream(it, "w") }
        if (output == null) {
            target?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            false
        } else {
            output.use { stream -> source.inputStream().use { it.copyTo(stream) } }
            true
        }
    } catch (_: Exception) {
        false
    }

    fun delete(tree: Uri, budgetId: String, transactionId: String) {
        runCatching { find(tree, budgetId, transactionId)?.let { DocumentsContract.deleteDocument(resolver, it) } }
    }

    /** Deletes [budgetId]'s whole subfolder, for when the budget is removed from the device. */
    fun deleteBudget(tree: Uri, budgetId: String) {
        runCatching {
            val appDirectory = child(root(tree), DIRECTORY) ?: return
            child(appDirectory, budgetId)?.let { DocumentsContract.deleteDocument(resolver, it) }
        }
    }

    private fun hasPermission(tree: Uri): Boolean = resolver.persistedUriPermissions.any {
        it.uri == tree && it.isReadPermission && it.isWritePermission
    }

    private fun release(tree: Uri) {
        runCatching { resolver.releasePersistableUriPermission(tree, PERMISSION) }
    }

    private fun root(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun createDirectory(parent: Uri, name: String): Uri =
        DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw IllegalStateException("Cannot create a folder")

    private fun child(parent: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) if (cursor.getString(1) == name) {
                return DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0))
            }
        }
        return null
    }

    private fun documentName(tree: Uri): String? = runCatching {
        resolver.query(
            root(tree), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    companion object {
        /** The preferences file is device-bound (it holds a permission grant) and excluded from backup and transfer. */
        const val PREFERENCES = "actua-receipt-photo-folder"
        const val DIRECTORY = "Actua receipts"
        private const val KEY_URI = "uri"
        private const val KEY_NAME = "name"
        private const val PERMISSION = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
