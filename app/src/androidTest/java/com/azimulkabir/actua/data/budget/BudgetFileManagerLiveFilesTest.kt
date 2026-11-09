package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.UUID

/** #1528: swapping a downloaded or imported budget in never loses the budget it replaces. */
class BudgetFileManagerLiveFilesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = BudgetFileManager(context)

    @Test fun aFailureAtAnyRenameStepKeepsTheOriginalBudgetAndItsWalWrites() {
        var step = 1
        while (true) {
            val budget = files.createBudget("Swap ${UUID.randomUUID()}")
            try {
                val originalMetadata = files.metadataFile(budget.id).readText()
                // A committed write held in the WAL by an open connection, as the app's repository has.
                val holder = SQLiteDatabase.openDatabase(files.databaseFile(budget.id).path, null, SQLiteDatabase.OPEN_READWRITE)
                holder.enableWriteAheadLogging()
                holder.execSQL("CREATE TABLE IF NOT EXISTS swap_marker (value TEXT)")
                holder.execSQL("INSERT INTO swap_marker VALUES ('original')")
                val directory = files.budgetDirectory(budget.id)
                val newDatabase = File(directory, ".new.sqlite").apply { writeText("not the original") }
                val newMetadata = File(directory, ".new.json").apply { writeText("{}") }

                var renames = 0
                val failed = runCatching {
                    files.replaceLiveFiles(budget.id, newDatabase, newMetadata) { from, to ->
                        if (++renames == step) false else from.renameTo(to)
                    }
                }.isFailure
                holder.close()
                if (!failed) {
                    // Every step has been failed once; this run had no step left to fail.
                    assertTrue("At least the moves and both installs were exercised", step > 3)
                    return
                }

                assertEquals("step $step", originalMetadata, files.metadataFile(budget.id).readText())
                SQLiteDatabase.openDatabase(files.databaseFile(budget.id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    assertEquals("step $step", "ok", db.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst(); it.getString(0) })
                    val marker = db.rawQuery("SELECT value FROM swap_marker", null).use { if (it.moveToFirst()) it.getString(0) else null }
                    assertEquals("step $step: the WAL write survives", "original", marker)
                }
                assertEquals("step $step", emptyList<String>(), leftovers(directory))
            } finally {
                runCatching { files.deleteBudget(budget.id) }
            }
            step++
            if (step > 20) fail("The swap never succeeded")
        }
    }

    @Test fun importingOverABudgetWithWalWritesInstallsACleanCopy() {
        val source = files.createBudget("Source ${UUID.randomUUID()}")
        val target = files.createBudget("Target ${UUID.randomUUID()}")
        try {
            SQLiteDatabase.openDatabase(files.databaseFile(source.id).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("CREATE TABLE swap_marker (value TEXT)")
                it.execSQL("INSERT INTO swap_marker VALUES ('downloaded')")
            }
            val archive = BackupService(context, files).makeBackup(source.id)

            val holder = SQLiteDatabase.openDatabase(files.databaseFile(target.id).path, null, SQLiteDatabase.OPEN_READWRITE)
            holder.enableWriteAheadLogging()
            holder.execSQL("CREATE TABLE IF NOT EXISTS local_only (value TEXT)")
            holder.execSQL("INSERT INTO local_only VALUES ('replaced')")
            try {
                archive.inputStream().use { files.importBudget(it, targetBudgetId = target.id) }
            } finally {
                holder.close()
            }

            SQLiteDatabase.openDatabase(files.databaseFile(target.id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertEquals("ok", db.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst(); it.getString(0) })
                assertEquals("downloaded", db.rawQuery("SELECT value FROM swap_marker", null).use { it.moveToFirst(); it.getString(0) })
                // No old WAL was paired with the new database.
                assertEquals(0, db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE name = 'local_only'", null)
                    .use { it.moveToFirst(); it.getInt(0) })
            }
            assertEquals(target.id, files.metadata(target.id)?.id)
            assertEquals(emptyList<String>(), leftovers(files.budgetDirectory(target.id)))
        } finally {
            runCatching { files.deleteBudget(source.id) }
            runCatching { files.deleteBudget(target.id) }
        }
    }

    /** Swap leftovers that must never remain next to the live files. */
    private fun leftovers(directory: File): List<String> = directory.list().orEmpty()
        .filter { it.startsWith(".previous-") }
        .sorted()
}
