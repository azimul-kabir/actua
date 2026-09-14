package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.network.ActualHttpResponse
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.RemoteBudgetFile
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

class BudgetDownloadServiceTest {
    @Test
    fun downloadedActualArchiveIsInstalledWithCloudIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val source = files.createBudget("Download ${UUID.randomUUID()}")
        val archive = archiveFromBudget(files, source.id)
        val server = ActualServerClient { ActualHttpResponse(200, archive) }
        val service = BudgetDownloadService(server, files, BudgetEncryptionKeyStore(context))
        val remote = RemoteBudgetFile("cloud-file", "sync-group", "Main", null)

        try {
            files.deleteBudget(source.id)
            val metadata = service.download("https://actual.test", "token", remote)
            assertEquals(source.id, metadata.id)
            assertEquals("cloud-file", metadata.cloudFileId)
            assertEquals("sync-group", metadata.groupId)
            assertTrue(files.databaseFile(metadata.id).isFile)
            ActualBudgetDatabase.open(files.databaseFile(metadata.id), readOnly = true).close()
            val active = ActiveBudgetStore(context)
            active.budgetId = metadata.id
            assertEquals(metadata.id, ActiveBudgetStore(context).budgetId)
            active.budgetId = null
        } finally {
            runCatching { files.deleteBudget(source.id) }
        }
    }

    @Test
    fun downloadedBudgetThatFailsOpenValidationIsRejectedBeforeInstall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val source = files.createBudget("Broken ${UUID.randomUUID()}")
        return try {
            SQLiteDatabase.openDatabase(files.databaseFile(source.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    """
                        INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type)
                        VALUES ('checking', 'Checking', 0, 0, 0, 1, 'checking')
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                        INSERT INTO transactions
                            (id, acct, amount, date, sort_order, tombstone, cleared, pending, reconciled)
                        VALUES
                            ('bad-date', 'checking', -500, 20241340, 1, 0, 1, 0, 0)
                    """.trimIndent(),
                )
            }
            val archive = archiveFromBudget(files, source.id)
            val server = ActualServerClient { ActualHttpResponse(200, archive) }
            val service = BudgetDownloadService(server, files, BudgetEncryptionKeyStore(context))
            val remote = RemoteBudgetFile("cloud-file", "sync-group", "Broken", null)
            files.deleteBudget(source.id)

            val error = org.junit.Assert.assertThrows(BudgetFileException.InvalidBudgetData::class.java) {
                service.download("https://actual.test", "token", remote)
            }
            assertEquals(
                "This budget contains an unreadable date in transaction bad-date and could not be opened safely.",
                error.message,
            )
            assertFalse(files.budgetDirectory(source.id).exists())
        } finally {
            runCatching { files.deleteBudget(source.id) }
        }
    }

    private fun archiveFromBudget(files: BudgetFileManager, budgetId: String): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            files.writeArchive(budgetId, bytes)
            bytes.toByteArray()
        }
}
