package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class BudgetOpenPerformanceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun successfulProbeIsReusedUntilInvalidated() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        insertInvalidDate(file)

        // Ordinary opens reuse the successful process check, rather than re-reading all history.
        ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        // Explicit validation (imports/restores) must still perform the full check.
        assertThrows(BudgetFileException.InvalidBudgetData::class.java) { BudgetOpenProbe.validate(file) }
        assertThrows(BudgetFileException.InvalidBudgetData::class.java) {
            ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        }
        BudgetOpenProbe.invalidate(file)
        assertThrows(BudgetFileException.InvalidBudgetData::class.java) {
            ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        }
        // A failed probe is never cached.
        assertThrows(BudgetFileException.InvalidBudgetData::class.java) {
            ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        }
    }

    @Test fun cachedProbeDoesNotApplyToAnotherBudgetOrReplacementFile() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        withBudget { other ->
            insertInvalidDate(other)
            assertThrows(BudgetFileException.InvalidBudgetData::class.java) {
                ActualBudgetDatabase.open(other).use { BudgetOpenProbe.validateOnce(other, it) }
            }
            val aside = File(file.parentFile, "old.sqlite")
            assertTrue(file.renameTo(aside))
            other.copyTo(file)
            assertThrows(BudgetFileException.InvalidBudgetData::class.java) {
                ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
            }
        }
    }

    @Test fun anOldConnectionCannotCacheValidationForAReplacement() = withBudget { file ->
        withBudget { other ->
            ActualBudgetDatabase.open(other).close()
            insertInvalidDate(other)
            val oldConnection = ActualBudgetDatabase.open(file)
            try {
                assertTrue(file.renameTo(File(file.parentFile, "old.sqlite")))
                other.copyTo(file)
                BudgetOpenProbe.validateOnce(file, oldConnection)
            } finally {
                oldConnection.close()
            }
            assertThrows(BudgetFileException.InvalidBudgetData::class.java) {
                ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
            }
        }
    }

    @Test fun schemaChangeForcesAnotherProbe() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        insertInvalidDate(file)
        raw(file) { it.execSQL("CREATE TABLE future_table (id TEXT PRIMARY KEY)") }
        assertThrows(BudgetFileException.InvalidBudgetData::class.java) {
            ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        }
    }

    @Test fun dataOnlyMigrationForcesAnotherProbe() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        insertInvalidDate(file)
        raw(file) {
            it.execSQL("INSERT INTO __migrations__ (id) VALUES (1694438752001)")
        }
        assertThrows(BudgetFileException.InvalidBudgetData::class.java) {
            ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        }
        raw(file) { db ->
            db.rawQuery("SELECT 1 FROM __migrations__ WHERE id = 1694438752001", null).use {
                assertFalse(it.moveToFirst())
            }
        }
    }

    @Test fun currentSchemaOpensWhileAnotherConnectionHoldsTheWriter() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        raw(file) { writer ->
            writer.enableWriteAheadLogging()
            writer.beginTransaction()
            try {
                writer.execSQL("UPDATE accounts SET name = 'Uncommitted'")
                ActualBudgetDatabase.open(file).use { reader ->
                    // This would time out if opening still tried to enter a migration transaction.
                    BudgetOpenProbe.validateOnce(file, reader)
                    reader.fetchAccounts()
                }
            } finally {
                writer.endTransaction()
            }
        }
    }

    @Test fun futureSchemaPendingCellsDoNotForceMigrationsOrAnotherProbe() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        insertInvalidDate(file)
        raw(file) {
            it.execSQL(
                "INSERT INTO messages_pending (dataset, row, `column`, timestamp, value) VALUES (?, ?, ?, ?, ?)",
                arrayOf("future_table", "row", "name", "2026-11-15T00:00:00.000Z-0000-aaaaaaaaaaaaaaaa", "S:Future"),
            )
        }
        ActualBudgetDatabase.open(file).use { BudgetOpenProbe.validateOnce(file, it) }
        raw(file) { db ->
            db.rawQuery("SELECT COUNT(*) FROM messages_pending", null).use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test fun corruptSchemaIsDetectedWithoutDeletingTheDatabase() = withBudget { file ->
        val corrupt = file.readBytes().also { it[100] = 0x7f }
        file.writeBytes(corrupt)
        assertThrows(BudgetFileException::class.java) { ActualBudgetDatabase.open(file) }
        assertTrue(file.exists())
        org.junit.Assert.assertArrayEquals(corrupt, file.readBytes())
        assertThrows(BudgetFileException::class.java) { ActualBudgetDatabase.validate(file) }
        org.junit.Assert.assertArrayEquals(corrupt, file.readBytes())
    }

    @Test fun lightweightAvailabilityDoesNotMigrateOrProbeTheBudget() {
        val files = BudgetFileManager(context)
        val active = ActiveBudgetStore(context)
        val previous = active.budgetId
        val metadata = files.createBudget("Availability ${UUID.randomUUID()}")
        try {
            active.budgetId = metadata.id
            val file = files.databaseFile(metadata.id)
            insertInvalidDate(file)
            raw(file) { it.execSQL("INSERT INTO __migrations__ (id) VALUES (1694438752001)") }
            assertTrue(files.hasReadableActiveBudget())
            raw(file) { db ->
                db.rawQuery("SELECT COUNT(*) FROM __migrations__ WHERE id = 1694438752001", null).use {
                    it.moveToFirst()
                    assertEquals(1, it.getInt(0))
                }
            }
            file.writeText("not a database")
            assertFalse(files.hasReadableActiveBudget())
            assertTrue(file.exists())
            active.budgetId = null
            assertFalse(files.hasReadableActiveBudget())
        } finally {
            active.budgetId = previous
            files.deleteBudget(metadata.id)
        }
    }

    private fun insertInvalidDate(file: File) = raw(file) {
        it.execSQL("INSERT INTO accounts (id, name) VALUES ('checking', 'Checking')")
        it.execSQL("INSERT INTO transactions (id, acct, amount, date, tombstone) VALUES ('bad-date', 'checking', -500, 20241340, 0)")
    }

    private fun raw(file: File, block: (SQLiteDatabase) -> Unit) =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use(block)

    private fun withBudget(block: (File) -> Unit) {
        val directory = File(context.cacheDir, "open-test-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val file = File(directory, "db.sqlite")
        try {
            BlankBudgetFactory.create(file)
            block(file)
        } finally {
            BudgetOpenProbe.invalidate(file)
            directory.deleteRecursively()
        }
    }
}
