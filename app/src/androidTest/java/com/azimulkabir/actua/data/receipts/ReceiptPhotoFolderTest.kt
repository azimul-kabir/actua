package com.azimulkabir.actua.data.receipts

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The receipt photo folder is chosen with the system folder picker, which a test cannot drive, so these tests
 * cover the part that must never go wrong: with no folder, or a folder Actua has no permission to use, photos
 * are kept in private storage, reads and deletes do not throw, and the user is told which one happened.
 */
class ReceiptPhotoFolderTest {
    private lateinit var context: Context
    private lateinit var store: ReceiptPhotoStore
    private val budgets = mutableListOf<String>()

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearFolder()
        store = ReceiptPhotoStore(context)
    }

    @After fun tearDown() {
        clearFolder()
        budgets.forEach { store.deleteAll(it) }
    }

    private fun clearFolder() {
        context.getSharedPreferences(ReceiptPhotoFolder.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** A folder setting for a provider that does not exist, so Actua holds no permission for it. */
    private fun chooseUnreachableFolder() {
        context.getSharedPreferences(ReceiptPhotoFolder.PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString("uri", "content://com.example.nowhere/tree/primary%3AReceipts")
            .putString("name", "Receipts")
            .commit()
    }

    private fun budget() = "budget-${UUID.randomUUID()}".also(budgets::add)

    private fun pending(content: String = "photo") =
        File(context.cacheDir, "pending-${UUID.randomUUID()}.jpg").apply { writeText(content) }

    @Test fun withNoFolderPhotosAreKeptPrivately() {
        val budget = budget()

        assertEquals(ReceiptKeepResult.PRIVATE, store.keepPreferred(budget, "txn", pending("bytes")))

        val photo = store.photo(budget, "txn")
        assertNotNull(photo)
        assertFalse(photo!!.inFolder)
        assertEquals("bytes", photo.open(context)?.use { String(it.readBytes()) })
    }

    @Test fun anUnreachableFolderIsReportedAndPhotosFallBackToPrivateStorage() {
        chooseUnreachableFolder()
        val state = ReceiptPhotoFolder(context).state()
        assertNotNull(state.uri)
        assertEquals("Receipts", state.name)
        assertFalse(state.usable)
        assertNull(ReceiptPhotoFolder(context).usableTree())

        val budget = budget()
        assertEquals(ReceiptKeepResult.PRIVATE_FOLDER_UNAVAILABLE, store.keepPreferred(budget, "txn", pending()))

        val photo = store.photo(budget, "txn")
        assertNotNull(photo)
        assertFalse(photo!!.inFolder)
    }

    @Test fun readingAndDeletingWithAnUnreachableFolderNeverThrows() {
        chooseUnreachableFolder()
        val budget = budget()
        store.keepPreferred(budget, "a", pending())

        assertNull(store.photo(budget, "missing"))
        store.delete(budget, "a")
        store.delete(budget, "never-kept")
        store.deleteAll(budget)

        assertNull(store.photo(budget, "a"))
    }

    @Test fun clearingTheFolderGoesBackToPrivateStorage() {
        chooseUnreachableFolder()
        ReceiptPhotoFolder(context).clear()

        val state = ReceiptPhotoFolder(context).state()
        assertNull(state.uri)
        assertFalse(state.usable)
        assertEquals(ReceiptKeepResult.PRIVATE, store.keepPreferred(budget(), "txn", pending()))
    }

    @Test fun unsafeIdsAreNotKeptEvenWithAFolder() {
        chooseUnreachableFolder()

        assertEquals(ReceiptKeepResult.FAILED, store.keepPreferred(budget(), "../x", pending()))
        assertEquals(ReceiptKeepResult.FAILED, store.keepPreferred("../b", "txn", pending()))
        assertNull(store.photo("..", "txn"))
    }

    @Test fun aMissingPendingFileIsNotKept() {
        assertEquals(
            ReceiptKeepResult.FAILED,
            store.keepPreferred(budget(), "txn", File(context.cacheDir, "nothing-here.jpg")),
        )
        assertTrue(store.photo(budget(), "txn") == null)
    }
}
