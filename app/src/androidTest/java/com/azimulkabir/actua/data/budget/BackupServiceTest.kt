package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

    @Test fun openedBudgetAndItsBackupListOnlyActualsMigrationsInOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val id = files.createBudget("Migration test ${UUID.randomUUID()}").id
        try {
            // A budget opened by an earlier Actua version already carries private ids.
            SQLiteDatabase.openDatabase(files.databaseFile(id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                listOf(1694438752001L, 1780606215005L, 1787013118200L).forEach {
                    db.execSQL("INSERT INTO __migrations__ (id) VALUES (?)", arrayOf(it))
                }
            }
            ActualBudgetDatabase.open(files.databaseFile(id)).close()
            val upstream = BlankBudgetFactory.MIGRATIONS.toList()
            assertEquals(upstream, migrations(files.databaseFile(id)))

            val archive = BackupService(context, files).makeBackup(id)
            val (database, _) = files.extractBackup(archive)
            try {
                assertEquals(upstream, migrations(database))
            } finally {
                database.delete()
            }
        } finally {
            files.budgetDirectory(id).deleteRecursively()
        }
    }

    @Test fun importedBackupIsValidatedAndDoesNotChangeTheActiveBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "backup-import-${UUID.randomUUID()}"
        val files = BudgetFileManager(context)
        val directory = files.budgetDirectory(id).also { it.mkdirs() }
        try {
            seed(files, id, "imported-state")
            val service = BackupService(context, files)
            val exported = service.makeBackup(id, Instant.parse("2026-09-05T01:02:03Z"))
            val bytes = exported.readBytes()
            assertTrue(exported.delete())
            SQLiteDatabase.openDatabase(files.databaseFile(id).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("UPDATE notes SET value='active-state'")
            }

            val imported = service.importArchive(
                id,
                bytes.inputStream(),
                Instant.parse("2026-09-06T01:02:03Z"),
            )

            assertTrue(imported.isFile)
            assertEquals("active-state", note(files, id))
            assertEquals(imported.name, service.availableBackups(id).filterIsInstance<BackupItem.Archive>().single().id)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun invalidAndDifferentBudgetImportsFailWithoutAddingABackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "backup-target-${UUID.randomUUID()}"
        val otherId = "backup-other-${UUID.randomUUID()}"
        val files = BudgetFileManager(context)
        val directory = files.budgetDirectory(id).also { it.mkdirs() }
        val otherDirectory = files.budgetDirectory(otherId).also { it.mkdirs() }
        try {
            seed(files, id, "active-state")
            seed(files, otherId, "other-state")
            val service = BackupService(context, files)

            assertThrows(BudgetFileException::class.java) {
                service.importArchive(id, "not a zip".byteInputStream())
            }

            val otherArchive = service.makeBackup(otherId, Instant.parse("2026-09-05T01:02:03Z"))
            assertThrows(BudgetFileException.IncompatibleBudget::class.java) {
                otherArchive.inputStream().use { service.importArchive(id, it) }
            }

            assertTrue(service.availableBackups(id).isEmpty())
            assertEquals("active-state", note(files, id))
        } finally {
            directory.deleteRecursively()
            otherDirectory.deleteRecursively()
        }
    }

    @Test fun restoreAndRevertLeaveNoTemporaryFilesOrStaleSidecars() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "backup-swap-${UUID.randomUUID()}"
        val files = BudgetFileManager(context)
        val directory = files.budgetDirectory(id).also { it.mkdirs() }
        try {
            seed(files, id, "backup-state")
            val service = BackupService(context, files)
            val archive = service.makeBackup(id, Instant.parse("2026-09-05T01:02:03Z"))
            // A WAL-mode write, as the app makes, leaves sidecars next to the live database.
            SQLiteDatabase.openDatabase(files.databaseFile(id).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.enableWriteAheadLogging()
                it.execSQL("UPDATE notes SET value='current-state'")
            }

            service.restore(id, archive.name)
            assertEquals("backup-state", note(files, id))
            assertEquals(emptyList<String>(), strayFiles(directory))

            service.restore(id, BackupService.LATEST_ID)
            assertEquals("current-state", note(files, id))
            assertEquals(emptyList<String>(), strayFiles(directory))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun aFailedSwapPutsTheOriginalFilesBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "backup-rollback-${UUID.randomUUID()}"
        val files = BudgetFileManager(context)
        val directory = files.budgetDirectory(id).also { it.mkdirs() }
        try {
            seed(files, id, "original-state")
            val originalMetadata = files.metadataFile(id).readText()
            val replacementMetadata = java.io.File(directory, ".replacement.json").apply { writeText("{}") }

            // The metadata swaps in, then the database can't: everything must be put back.
            assertThrows(IllegalStateException::class.java) {
                files.replaceLiveFiles(id, java.io.File(directory, ".missing.sqlite"), replacementMetadata)
            }

            assertEquals("original-state", note(files, id))
            assertEquals(originalMetadata, files.metadataFile(id).readText())
            assertEquals(emptyList<String>(), strayFiles(directory))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun aRestoreRacingBackupsAndSyncLeavesAValidMatchingBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "backup-race-${UUID.randomUUID()}"
        val files = BudgetFileManager(context)
        val directory = files.budgetDirectory(id).also { it.mkdirs() }
        try {
            seed(files, id, "backup-state")
            val archive = BackupService(context, files).makeBackup(id, Instant.parse("2026-09-05T01:02:03Z"))
            SQLiteDatabase.openDatabase(files.databaseFile(id).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("UPDATE notes SET value='current-state'")
            }
            val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
            val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
            val start = java.util.concurrent.CountDownLatch(1)
            fun worker(body: () -> Unit) = kotlin.concurrent.thread {
                start.await()
                try { body() } catch (error: Throwable) { failures += error }
            }

            val threads = listOf(
                // Each caller creates its own BackupService, as the sync runner, workers and UI do.
                worker { repeat(3) { BackupService(context, files).restore(id, archive.name) } },
                // Two, so today's retention (newest three) keeps the archive being restored.
                worker { repeat(2) { BackupService(context, files).makeBackup(id) } },
                // A sync holds the same pause; it must never see a missing or half-copied database.
                worker {
                    repeat(20) {
                        com.azimulkabir.actua.data.sync.ActualSyncRunner.whileSyncPaused { seen += note(files, id) }
                    }
                },
            )
            start.countDown()
            threads.forEach { it.join(60_000) }

            assertEquals(emptyList<Throwable>(), failures)
            assertTrue(seen.all { it == "backup-state" || it == "current-state" })
            SQLiteDatabase.openDatabase(files.databaseFile(id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertEquals("ok", db.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst(); it.getString(0) })
            }
            // The live files belong together: a restored database comes with the restored metadata.
            val metadata = JSONObject(files.metadataFile(id).readText())
            when (note(files, id)) {
                "backup-state" -> assertTrue(metadata.isNull("groupId"))
                "current-state" -> assertEquals("group", metadata.getString("groupId"))
                else -> throw AssertionError("Unexpected budget contents")
            }
            assertEquals(emptyList<String>(), strayFiles(directory))
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

    private fun migrations(file: java.io.File): List<Long> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT id FROM __migrations__ ORDER BY id", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
            }
        }

    /** Files a restore must never leave behind: swap leftovers, staged copies and temporary files. */
    private fun strayFiles(directory: java.io.File): List<String> = directory.list().orEmpty()
        .filter { it.startsWith(".previous-") || it.startsWith(".restore-") || it.endsWith(".tmp") }
        .sorted()

    private fun note(files: BudgetFileManager, id: String): String =
        SQLiteDatabase.openDatabase(files.databaseFile(id).path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT value FROM notes", null).use { it.moveToFirst(); it.getString(0) }
        }
}
