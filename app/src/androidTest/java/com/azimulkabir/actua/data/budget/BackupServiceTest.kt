package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class BackupServiceTest {
    @Test fun backupStripsSyncStateAndRestoreCanRevert() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "backup-test-${UUID.randomUUID()}"
        val files = BudgetFileManager(context)
        val directory = files.budgetDirectory(id).also { it.mkdirs() }
        try {
            seed(files, id, "backup-state")
            val service = BackupService(context, files)
            service.makeBackup(id, Instant.parse("2026-09-05T01:02:03Z"))
            val archive = service.availableBackups(id).filterIsInstance<BackupItem.Archive>().single()

            SQLiteDatabase.openDatabase(files.databaseFile(id).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("UPDATE notes SET value='current-state'")
            }
            service.restore(id, archive.id)
            assertEquals("backup-state", note(files, id))
            assertTrue(service.availableBackups(id).first() is BackupItem.Latest)
            val restoredMetadata = JSONObject(files.metadataFile(id).readText())
            assertTrue(restoredMetadata.isNull("groupId"))
            assertEquals("cloud", restoredMetadata.getString("cloudFileId"))

            SQLiteDatabase.openDatabase(files.databaseFile(id).path, null, SQLiteDatabase.OPEN_READONLY).use {
                assertEquals(0, it.rawQuery("SELECT COUNT(*) FROM messages_crdt", null).use { c -> c.moveToFirst(); c.getInt(0) })
            }
            service.restore(id, BackupService.LATEST_ID)
            assertEquals("current-state", note(files, id))
            assertFalse(files.latestDatabaseFile(id).exists())
            assertEquals("group", JSONObject(files.metadataFile(id).readText()).getString("groupId"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun seed(files: BudgetFileManager, id: String, note: String) {
        SQLiteDatabase.openOrCreateDatabase(files.databaseFile(id), null).use { database ->
            listOf("accounts", "categories", "category_groups", "payee_mapping", "payees", "transactions", "zero_budgets")
                .forEach { database.execSQL("CREATE TABLE $it (id TEXT PRIMARY KEY)") }
            database.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            database.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)")
            database.execSQL("CREATE TABLE notes (value TEXT)")
            database.execSQL("INSERT INTO notes VALUES (?)", arrayOf(note))
            database.execSQL("INSERT INTO messages_crdt VALUES (1, '1:0:a', 'notes', '1', 'value', 'S:test')")
        }
        files.metadataFile(id).writeText(JSONObject()
            .put("id", id).put("budgetName", "Backup test").put("cloudFileId", "cloud")
            .put("groupId", "group").put("encryptKeyId", "key").toString())
    }

    private fun note(files: BudgetFileManager, id: String): String =
        SQLiteDatabase.openDatabase(files.databaseFile(id).path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT value FROM notes", null).use { it.moveToFirst(); it.getString(0) }
        }
}
