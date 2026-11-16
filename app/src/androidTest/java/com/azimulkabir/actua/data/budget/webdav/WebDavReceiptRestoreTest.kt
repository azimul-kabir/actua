package com.azimulkabir.actua.data.budget.webdav

import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.backup.BudgetRestoreSink
import com.azimulkabir.actua.data.receipts.ReceiptPhotoStore
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class WebDavReceiptRestoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = BudgetFileManager(context)
    private val service = BackupService(context, files)
    private val photos = ReceiptPhotoStore(context)
    private val sink = WebDavReceiptRestoreSink(context, files, BudgetRestoreSink(files, service))
    private val budgets = mutableListOf<String>()
    private val work = File(context.cacheDir, "receipt-test-${UUID.randomUUID()}").apply { mkdirs() }

    @After fun cleanup() {
        budgets.forEach { if (files.budgetDirectory(it).exists()) files.deleteBudget(it) }
        work.deleteRecursively()
    }

    private fun budget() = files.createBudget("Receipt test").id.also(budgets::add)
    private fun keep(id: String, transaction: String, content: String) {
        assertTrue(photos.keep(id, transaction, File(work, "${UUID.randomUUID()}.jpg").apply { writeText(content) }))
    }
    private fun bundle(id: String): File {
        val archive = service.makeBackup(id)
        return File(work, "${UUID.randomUUID()}.zip").also { target ->
            WebDavReceiptArchive.write(archive, photos.backupPhotos(id).mapValues { (_, photo) ->
                { photo.open(context)!! }
            }, target)
        }
    }

    @Test fun newBudgetRemapsReceiptIdsWithoutChangingSourceOrOtherBudgets() {
        val source = budget()
        val other = budget()
        keep(source, "txn", "source receipt")
        keep(other, "txn", "other receipt")
        val restored = bundle(source).inputStream().use(sink::importAsNew).also { budgets += it.id }
        assertNotEquals(source, restored.id)
        assertEquals("source receipt", photos.photoFile(restored.id, "txn")!!.readText())
        assertEquals("source receipt", photos.photoFile(source, "txn")!!.readText())
        assertEquals("other receipt", photos.photoFile(other, "txn")!!.readText())
    }

    @Test fun existingBudgetRecoversPhotoPreservesOthersAndKeepsLocalBackupStandard() {
        val id = budget()
        keep(id, "txn", "backup receipt")
        val archive = bundle(id)
        keep(id, "txn", "new receipt")
        keep(id, "other", "preserved")
        archive.inputStream().use { sink.restoreInto(id, it) }
        assertEquals("backup receipt", photos.photoFile(id, "txn")!!.readText())
        assertEquals("preserved", photos.photoFile(id, "other")!!.readText())
        files.backupsDirectory(id).listFiles()!!.filter { it.extension == "zip" }.forEach { backup ->
            ZipFile(backup).use { zip -> assertFalse(zip.entries().asSequence().any { it.name.startsWith("ReceiptPhotos/") }) }
        }
    }

    @Test fun failedBudgetValidationRollsBackPhotosAndLegacyRestorePreservesThem() {
        val source = budget()
        val target = budget()
        keep(source, "txn", "wrong budget")
        keep(target, "txn", "original")
        val invalidForTarget = bundle(source)
        assertTrue(runCatching { invalidForTarget.inputStream().use { sink.restoreInto(target, it) } }.isFailure)
        assertEquals("original", photos.photoFile(target, "txn")!!.readText())
        service.makeBackup(target).inputStream().use { sink.restoreInto(target, it) }
        assertEquals("original", photos.photoFile(target, "txn")!!.readText())
    }
}
