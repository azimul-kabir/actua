package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.network.ActualHttpResponse
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.ActualServerException
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import com.azimulkabir.actua.data.sync.HlcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Recovery after the server reports `file-has-reset` (actua#1343). */
class BudgetResetRecoveryTest {
    @Test
    fun unsyncedChangesCountsOnlyEditsAfterTheLastSyncedClock() = withLocalBudget { env ->
        assertEquals("a budget without a saved clock counts none", 0, env.recovery.unsyncedChanges(env.local.id))

        env.markSynced()
        assertEquals(0, env.recovery.unsyncedChanges(env.local.id))

        env.editOffline("Edited offline")
        assertTrue(env.recovery.unsyncedChanges(env.local.id) > 0)
    }

    @Test
    fun recoveryBacksUpFirstThenReplacesTheBudgetInPlace() = withLocalBudget { env ->
        env.markSynced()
        env.editOffline("Edited offline")
        val unsynced = env.recovery.unsyncedChanges(env.local.id)
        assertTrue(unsynced > 0)
        val serverArchive = env.serverArchive("Server name")
        val requests = mutableListOf<String>()
        val recovery = env.recoveryWith(ActualServerClient { request ->
            requests += request.url.path
            if (request.url.path.endsWith("get-user-file-info")) {
                ActualHttpResponse(200, env.fileInfo().encodeToByteArray())
            } else {
                ActualHttpResponse(200, serverArchive)
            }
        })

        val result = recovery.recover("https://actual.test", "token", env.local.id)

        // The local id is kept even though the server archive carries another budget id.
        assertEquals(env.local.id, result.metadata.id)
        assertEquals(unsynced, result.unsyncedChanges)
        assertEquals(CLOUD_FILE_ID, result.metadata.cloudFileId)
        assertEquals(CLOUD_FILE_ID, env.files.metadata(env.local.id)?.cloudFileId)
        assertEquals("Server name", env.categoryName(env.files.databaseFile(env.local.id).path))
        assertEquals("the new file has nothing unsynced", 0, env.recovery.unsyncedChanges(env.local.id))
        assertTrue(requests.any { it.endsWith("download-user-file") })

        // The backup written first still holds the offline edit and is listed for restore.
        assertTrue(result.backup.isFile)
        assertTrue(env.backups.availableBackups(env.local.id).any { it is BackupItem.Archive && it.id == result.backup.name })
        val (backupDatabase, backupMetadata) = env.files.extractBackup(result.backup)
        try {
            assertEquals(env.local.id, backupMetadata.id)
            assertEquals("Edited offline", env.categoryName(backupDatabase.path))
        } finally {
            backupDatabase.delete()
        }
    }

    @Test
    fun invalidDownloadLeavesTheLocalBudgetUntouchedAfterBackingUp() = withLocalBudget { env ->
        env.markSynced()
        env.editOffline("Edited offline")
        val before = env.messageLog()
        val recovery = env.recoveryWith(ActualServerClient { request ->
            if (request.url.path.endsWith("get-user-file-info")) {
                ActualHttpResponse(200, env.fileInfo().encodeToByteArray())
            } else {
                ActualHttpResponse(200, "not a zip archive".encodeToByteArray())
            }
        })

        assertThrows(BudgetFileException.InvalidArchive::class.java) {
            recovery.recover("https://actual.test", "token", env.local.id)
        }

        assertEquals("Edited offline", env.categoryName(env.files.databaseFile(env.local.id).path))
        assertEquals(before, env.messageLog())
        assertEquals(CLOUD_FILE_ID, env.files.metadata(env.local.id)?.cloudFileId)
        assertTrue("a backup is written before any download", env.backups.availableBackups(env.local.id).any { it is BackupItem.Archive })
    }

    @Test
    fun serverFileGoneLeavesTheLocalBudgetUntouched() = withLocalBudget { env ->
        env.editOffline("Edited offline")
        val recovery = env.recoveryWith(ActualServerClient {
            ActualHttpResponse(400, """{"status":"error","reason":"file-not-found"}""".encodeToByteArray())
        })

        assertThrows(ActualServerException.FileNotFound::class.java) {
            recovery.recover("https://actual.test", "token", env.local.id)
        }

        assertEquals("Edited offline", env.categoryName(env.files.databaseFile(env.local.id).path))
        assertTrue(env.files.databaseFile(env.local.id).isFile)
    }

    @Test
    fun budgetNotLinkedToTheServerIsRefusedWithoutBackupOrRequests() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val local = files.createBudget("LocalOnly ${UUID.randomUUID()}")
        try {
            var requests = 0
            val server = ActualServerClient { requests++; ActualHttpResponse(200, byteArrayOf()) }
            val backups = BackupService(context, files)
            val recovery = BudgetResetRecovery(files, backups, BudgetDownloadService(server, files, BudgetEncryptionKeyStore(context)))

            assertThrows(BudgetRecoveryException.NotServerBacked::class.java) {
                recovery.recover("https://actual.test", "token", local.id)
            }

            assertEquals(0, requests)
            assertTrue(backups.availableBackups(local.id).isEmpty())
        } finally {
            runCatching { files.deleteBudget(local.id) }
        }
    }

    private class Env(
        val files: BudgetFileManager,
        val backups: BackupService,
        val local: BudgetMetadata,
        private val context: android.content.Context,
    ) {
        val recovery = recoveryWith(ActualServerClient { ActualHttpResponse(500, byteArrayOf()) })

        fun recoveryWith(server: ActualServerClient) =
            BudgetResetRecovery(files, backups, BudgetDownloadService(server, files, BudgetEncryptionKeyStore(context)))

        fun fileInfo() =
            """{"status":"ok","data":{"fileId":"$CLOUD_FILE_ID","groupId":"new-group","name":"Budget","deleted":0,"encryptMeta":null}}"""

        /** A snapshot of another budget, as the server's reset file would be. */
        fun serverArchive(categoryName: String): ByteArray {
            val server = files.createBudget("Server ${UUID.randomUUID()}")
            try {
                ActualBudgetDatabase.open(files.databaseFile(server.id)).use { database ->
                    ActualEntityWriter(database, nodeId = "cdcdcdcdcdcdcdcd").renameCategory(GENERAL_CATEGORY_ID, categoryName)
                }
                return ByteArrayOutputStream().use { bytes -> files.writeArchive(server.id, bytes); bytes.toByteArray() }
            } finally {
                files.deleteBudget(server.id)
            }
        }

        /** Saves a sync clock older than any edit made afterwards, as a completed sync would. */
        fun markSynced() {
            ActualBudgetDatabase.open(files.databaseFile(local.id)).use { database ->
                database.saveClock(
                    ActualBudgetDatabase.ClockRecord(
                        HlcTimestamp(OLD_SYNC_MILLIS, 0, "0000000000000000").toString(),
                        database.deriveMerkleFromMessageLog().root,
                    ),
                )
            }
        }

        fun editOffline(name: String) {
            ActualBudgetDatabase.open(files.databaseFile(local.id)).use { database ->
                ActualEntityWriter(database, nodeId = "abababababababab").renameCategory(GENERAL_CATEGORY_ID, name)
            }
        }

        fun categoryName(databasePath: String): String =
            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT name FROM categories WHERE id = ?", arrayOf(GENERAL_CATEGORY_ID)).use {
                    assertTrue(it.moveToFirst()); it.getString(0)
                }
            }

        fun messageLog(): List<String> =
            SQLiteDatabase.openDatabase(files.databaseFile(local.id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT timestamp, dataset, row, `column` FROM messages_crdt ORDER BY timestamp", null).use {
                    buildList { while (it.moveToNext()) add("${it.getString(0)} ${it.getString(1)} ${it.getString(2)} ${it.getString(3)}") }
                }
            }
    }

    private fun withLocalBudget(block: (Env) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val local = files.createBudget("Reset ${UUID.randomUUID()}")
        try {
            files.saveCloudRegistration(local.id, CLOUD_FILE_ID, "old-group")
            block(Env(files, BackupService(context, files), files.metadata(local.id)!!, context))
        } finally {
            runCatching { files.deleteBudget(local.id) }
        }
    }

    private companion object {
        const val CLOUD_FILE_ID = "cloud-reset-file"
        /** 2020-01-01T00:00:00Z. */
        const val OLD_SYNC_MILLIS = 1_577_836_800_000L
        /** "General" in the blank budget template. */
        const val GENERAL_CATEGORY_ID = "af375fd4-d759-46b3-bffe-74a856151d57"
    }
}
