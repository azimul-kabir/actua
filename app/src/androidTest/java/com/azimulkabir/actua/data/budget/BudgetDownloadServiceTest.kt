package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.network.ActualHttpResponse
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.RemoteBudgetFile
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BudgetDownloadServiceTest {
    @Test
    fun downloadedActualArchiveIsInstalledWithCloudIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = makeBudgetArchive(context)
        val server = ActualServerClient { ActualHttpResponse(200, archive) }
        val files = BudgetFileManager(context)
        val service = BudgetDownloadService(server, files, BudgetEncryptionKeyStore(context))
        val remote = RemoteBudgetFile("cloud-file", "sync-group", "Main", null)

        val metadata = service.download("https://actual.test", "token", remote)
        try {
            assertEquals("local-budget", metadata.id)
            assertEquals("cloud-file", metadata.cloudFileId)
            assertEquals("sync-group", metadata.groupId)
            assertTrue(files.databaseFile(metadata.id).isFile)
            ActualBudgetDatabase.open(files.databaseFile(metadata.id), readOnly = true).close()
            val active = ActiveBudgetStore(context)
            active.budgetId = metadata.id
            assertEquals(metadata.id, ActiveBudgetStore(context).budgetId)
            active.budgetId = null
        } finally {
            files.budgetDirectory(metadata.id).deleteRecursively()
        }
    }

    private fun makeBudgetArchive(context: android.content.Context): ByteArray {
        val databaseFile = File(context.cacheDir, "download-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            listOf("accounts", "categories", "category_groups", "payee_mapping", "payees", "transactions", "zero_budgets")
                .forEach { database.execSQL("CREATE TABLE $it (id TEXT PRIMARY KEY)") }
            database.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            database.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, column TEXT NOT NULL, value BLOB NOT NULL)")
        }
        return try {
            ByteArrayOutputStream().use { bytes ->
                ZipOutputStream(bytes).use { zip ->
                    zip.putNextEntry(ZipEntry("db.sqlite"))
                    databaseFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("metadata.json"))
                    zip.write("""{"id":"local-budget","budgetName":"Main"}""".encodeToByteArray())
                    zip.closeEntry()
                }
                bytes.toByteArray()
            }
        } finally {
            databaseFile.delete()
        }
    }
}
