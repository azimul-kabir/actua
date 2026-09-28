package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.network.ActualHttpResponse
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.RemoteBudgetFile
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID
import com.azimulkabir.actua.data.network.ActualServerException
import com.azimulkabir.actua.data.sync.LoadedEncryptionKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BudgetDownloadServiceTest {
    @Test
    fun downloadedActualArchiveIsInstalledWithCloudIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val source = files.createBudget("Download ${UUID.randomUUID()}")
        val archive = archiveFromBudget(files, source.id)
        val server = fakeServer(archive, fileInfo("cloud-file", "sync-group"))
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
            val server = fakeServer(archive, fileInfo("cloud-file", "sync-group"))
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

    @Test
    fun openingADownloadedBudgetKeepsUnsyncedLocalEditsAndMakesNoRequest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Offline ${UUID.randomUUID()}")
        try {
            files.saveCloudRegistration(budget.id, "cloud-offline", "sync-group")
            // The server still has the snapshot from before the offline edit.
            val staleSnapshot = archiveFromBudget(files, budget.id)
            val requests = mutableListOf<String>()
            val server = ActualServerClient { request ->
                requests += request.url.path
                ActualHttpResponse(200, staleSnapshot)
            }
            val service = BudgetDownloadService(server, files, BudgetEncryptionKeyStore(context))
            ActualBudgetDatabase.open(files.databaseFile(budget.id)).use { database ->
                ActualEntityWriter(database, nodeId = "abababababababab")
                    .renameCategory(GENERAL_CATEGORY_ID, "Renamed offline")
            }
            val pending = unsyncedMessages(files, budget.id)
            assertTrue(pending.isNotEmpty())

            val opened = service.openOrDownload(
                "https://actual.test", "token",
                RemoteBudgetFile("cloud-offline", "sync-group", "Offline", null),
            )

            assertEquals(budget.id, opened.id)
            assertTrue("Opening a downloaded budget must not contact the server", requests.isEmpty())
            assertEquals(pending, unsyncedMessages(files, budget.id))
            SQLiteDatabase.openDatabase(files.databaseFile(budget.id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT name FROM categories WHERE id = ?", arrayOf(GENERAL_CATEGORY_ID)).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Renamed offline", it.getString(0))
                }
            }
        } finally {
            runCatching { files.deleteBudget(budget.id) }
        }
    }

    @Test
    fun budgetWithoutAnInstalledDatabaseIsDownloaded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val source = files.createBudget("Missing ${UUID.randomUUID()}")
        try {
            val archive = archiveFromBudget(files, source.id)
            files.saveCloudRegistration(source.id, "cloud-missing", "sync-group")
            files.databaseFile(source.id).delete()
            var downloads = 0
            val server = fakeServer(archive, fileInfo("cloud-missing", "sync-group")) { downloads++ }
            val service = BudgetDownloadService(server, files, BudgetEncryptionKeyStore(context))
            val remote = RemoteBudgetFile("cloud-missing", "sync-group", "Missing", null)

            assertNull(service.localCopy(remote))
            val opened = service.openOrDownload("https://actual.test", "token", remote)

            assertEquals(1, downloads)
            assertEquals(source.id, opened.id)
            assertTrue(files.databaseFile(source.id).isFile)
        } finally {
            runCatching { files.deleteBudget(source.id) }
        }
    }

    @Test
    fun serverFileInfoReplacesStaleArchiveMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val source = files.createBudget("Stale ${UUID.randomUUID()}")
        try {
            // The snapshot was uploaded while the file had another group and key.
            files.metadataFile(source.id).writeText(
                org.json.JSONObject(files.metadataFile(source.id).readText())
                    .put("groupId", "old-group").put("encryptKeyId", "old-key").toString(),
            )
            val archive = archiveFromBudget(files, source.id)
            files.deleteBudget(source.id)
            val service = BudgetDownloadService(
                fakeServer(archive, fileInfo("cloud-stale", "current-group")),
                files,
                BudgetEncryptionKeyStore(context),
            )

            // The list row is stale too; only the file info is trusted.
            val metadata = service.download(
                "https://actual.test", "token", RemoteBudgetFile("cloud-stale", "old-group", "Stale", "old-key"),
            )

            assertEquals("cloud-stale", metadata.cloudFileId)
            assertEquals("current-group", metadata.groupId)
            assertNull(metadata.encryptKeyId)
            val installed = org.json.JSONObject(files.metadataFile(metadata.id).readText())
            assertTrue(installed.has("encryptKeyId") && installed.isNull("encryptKeyId"))
            assertEquals(java.time.LocalDate.now().toString(), installed.getString("lastUploaded"))
        } finally {
            runCatching { files.deleteBudget(source.id) }
        }
    }

    @Test
    fun encryptedFileIsDecryptedAndRecordsTheServerKeyId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val keys = BudgetEncryptionKeyStore(context)
        val source = files.createBudget("Encrypted ${UUID.randomUUID()}")
        val fileId = "cloud-encrypted-${UUID.randomUUID()}"
        try {
            // The archive predates encryption, so its metadata has no key id.
            val plaintext = archiveFromBudget(files, source.id)
            files.deleteBudget(source.id)
            val key = ByteArray(32) { it.toByte() }
            val iv = ByteArray(12) { (it + 1).toByte() }
            val sealed = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                doFinal(plaintext)
            }
            val ciphertext = sealed.copyOf(sealed.size - 16)
            val authTag = sealed.copyOfRange(sealed.size - 16, sealed.size)
            keys.store(fileId, LoadedEncryptionKey("server-key", key))
            val encoder = java.util.Base64.getEncoder()
            val info = fileInfo(
                fileId, "sync-group",
                """{"keyId":"server-key","algorithm":"aes-256-gcm","iv":"${encoder.encodeToString(iv)}","authTag":"${encoder.encodeToString(authTag)}"}""",
            )
            val service = BudgetDownloadService(fakeServer(ciphertext, info), files, keys)

            // The list row claims the file is unencrypted; the file info decides.
            val metadata = service.download("https://actual.test", "token", RemoteBudgetFile(fileId, "sync-group", "Encrypted", null))

            assertEquals("server-key", metadata.encryptKeyId)
            ActualBudgetDatabase.open(files.databaseFile(metadata.id), readOnly = true).close()
        } finally {
            keys.remove(fileId)
            runCatching { files.deleteBudget(source.id) }
        }
    }

    @Test
    fun deletedOrMissingServerFileIsNotDownloaded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        var downloads = 0
        val missing = ActualServerClient { request ->
            if (request.url.path.endsWith("get-user-file-info")) {
                ActualHttpResponse(400, """{"status":"error","reason":"file-not-found"}""".encodeToByteArray())
            } else { downloads++; ActualHttpResponse(200, byteArrayOf()) }
        }
        val service = BudgetDownloadService(missing, files, BudgetEncryptionKeyStore(context))

        org.junit.Assert.assertThrows(ActualServerException.FileNotFound::class.java) {
            service.download("https://actual.test", "token", RemoteBudgetFile("gone", "g", "Gone", null))
        }
        assertEquals(0, downloads)
    }

    private fun fileInfo(fileId: String, groupId: String?, encryptMeta: String = "null"): String =
        """{"status":"ok","data":{"fileId":"$fileId","groupId":${groupId?.let { "\"$it\"" } ?: "null"},"name":"Budget","deleted":0,"encryptMeta":$encryptMeta}}"""

    /** Serves [info] for get-user-file-info and [archive] for the download. */
    private fun fakeServer(archive: ByteArray, info: String, onDownload: () -> Unit = {}) = ActualServerClient { request ->
        if (request.url.path.endsWith("get-user-file-info")) {
            ActualHttpResponse(200, info.encodeToByteArray())
        } else {
            onDownload()
            ActualHttpResponse(200, archive)
        }
    }

    private fun unsyncedMessages(files: BudgetFileManager, budgetId: String): List<String> =
        SQLiteDatabase.openDatabase(files.databaseFile(budgetId).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT timestamp, dataset, row, `column` FROM messages_crdt ORDER BY timestamp", null).use {
                buildList { while (it.moveToNext()) add("${it.getString(0)} ${it.getString(1)} ${it.getString(2)} ${it.getString(3)}") }
            }
        }

    private fun archiveFromBudget(files: BudgetFileManager, budgetId: String): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            files.writeArchive(budgetId, bytes)
            bytes.toByteArray()
        }

    private companion object {
        /** "General" in the blank budget template. */
        const val GENERAL_CATEGORY_ID = "af375fd4-d759-46b3-bffe-74a856151d57"
    }
}
