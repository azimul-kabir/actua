package com.azimulkabir.actua.data.receipts

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.BudgetFileManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Receipt photos are local to the device and tied to a transaction only by their file name. */
class ReceiptPhotoStoreTest {
    private lateinit var context: Context
    private lateinit var store: ReceiptPhotoStore
    private val budgetsToClean = mutableListOf<String>()

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        store = ReceiptPhotoStore(context)
    }

    @After fun tearDown() {
        budgetsToClean.forEach { store.deleteAll(it) }
    }

    private fun pending(content: String = "photo"): File =
        File(context.cacheDir, "pending-${UUID.randomUUID()}.jpg").apply { writeText(content) }

    private fun budgetId() = "budget-${UUID.randomUUID()}".also(budgetsToClean::add)

    @Test fun aKeptPhotoIsFoundAndTheCacheCopyIsMoved() {
        val budget = budgetId()
        val source = pending("jpeg bytes")

        assertTrue(store.keep(budget, "txn-1", source))

        assertFalse(source.exists())
        assertEquals("jpeg bytes", store.photoFile(budget, "txn-1")?.readText())
    }

    @Test fun aTransactionWithoutAPhotoHasNone() {
        assertNull(store.photoFile(budgetId(), "txn-without"))
    }

    @Test fun deletingATransactionsPhotoRemovesOnlyThatPhoto() {
        val budget = budgetId()
        store.keep(budget, "a", pending())
        store.keep(budget, "b", pending())

        store.delete(budget, listOf("a"))

        assertNull(store.photoFile(budget, "a"))
        assertNotNull(store.photoFile(budget, "b"))
    }

    @Test fun aPhotoNeverShowsInAnotherBudget() {
        val first = budgetId()
        val second = budgetId()
        store.keep(first, "same-id", pending())

        assertNotNull(store.photoFile(first, "same-id"))
        assertNull(store.photoFile(second, "same-id"))
    }

    @Test fun unsafeIdsAreRefusedAndEscapeNothing() {
        val budget = budgetId()
        assertFalse(store.keep(budget, "../escape", pending()))
        assertFalse(store.keep("../other", "txn", pending()))
        assertNull(store.photoFile(budget, ".."))
        assertNull(store.photoFile(budget, "a/b"))
    }

    @Test fun keepingAMissingFileFails() {
        assertFalse(store.keep(budgetId(), "txn", File(context.cacheDir, "does-not-exist.jpg")))
    }

    @Test fun removingABudgetDeletesItsPhotosAndOnlyItsPhotos() {
        val files = BudgetFileManager(context)
        val removed = files.createBudget("Photos ${UUID.randomUUID()}")
        val kept = files.createBudget("Photos ${UUID.randomUUID()}")
        budgetsToClean += removed.id
        budgetsToClean += kept.id
        try {
            store.keep(removed.id, "txn", pending())
            store.keep(kept.id, "txn", pending())

            files.deleteBudget(removed.id)

            assertNull(store.photoFile(removed.id, "txn"))
            assertNotNull(store.photoFile(kept.id, "txn"))
        } finally {
            runCatching { files.deleteBudget(kept.id) }
        }
    }

    @Test fun anArchiveTakenWithPhotosPresentContainsNoPhoto() {
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Archive ${UUID.randomUUID()}")
        budgetsToClean += budget.id
        try {
            store.keep(budget.id, "txn", pending("secret receipt"))

            val exported = ByteArrayOutputStream().use { out -> files.writeArchive(budget.id, out); out.toByteArray() }
            val uploaded = files.uploadArchive(budget.id)

            for (archive in listOf(exported, uploaded)) {
                val names = ZipInputStream(archive.inputStream()).use { zip ->
                    generateSequence { zip.nextEntry }.map { it.name }.toList()
                }
                assertEquals(setOf("db.sqlite", "metadata.json"), names.toSet())
                assertFalse(String(archive, Charsets.ISO_8859_1).contains("secret receipt"))
            }
        } finally {
            runCatching { files.deleteBudget(budget.id) }
        }
    }
}
