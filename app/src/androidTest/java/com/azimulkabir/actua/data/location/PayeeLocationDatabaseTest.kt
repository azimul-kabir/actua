package com.azimulkabir.actua.data.location

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class PayeeLocationDatabaseTest {
    @Test fun migrationCreatesUpstreamTableAndIndexes() = withDatabase { _, file ->
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertTrue(tableExists(raw, "payee_locations"))
            val columns = raw.rawQuery("PRAGMA table_info(payee_locations)", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            assertEquals(setOf("id", "payee_id", "latitude", "longitude", "created_at", "tombstone"), columns)
            assertEquals(3, raw.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='payee_locations' AND name LIKE 'idx_payee_locations_%'",
                null,
            ).use { it.count })
            assertTrue(raw.rawQuery("SELECT 1 FROM __migrations__ WHERE id=1768872504000", null).use { it.moveToFirst() })
        }
    }

    @Test fun nearbyFetchRanksAndDeduplicatesPayees() = withDatabase { database, _ ->
        val writer = PayeeLocationWriter(database, nodeId = "aaaaaaaaaaaaaaaa", idFactory = sequenceIds(), nowMillis = { 100L })
        assertNotNull(writer.record("near", Coordinates(0.001, 0.0)))
        assertNotNull(writer.record("near", Coordinates(0.003, 0.0)))
        assertNotNull(writer.record("far", Coordinates(0.04, 0.0)))

        val nearby = database.fetchNearbyPayees(Coordinates(0.0, 0.0))
        assertEquals(listOf("near"), nearby.map { it.payee.id })
        assertEquals("location-1", nearby.single().location.id)
        assertEquals(111.0, nearby.single().distanceMeters, 10.0)
    }

    @Test fun writerDeduplicatesAndTombstonesWithCrdtMessages() = withDatabase { database, file ->
        var writes = 0
        val writer = PayeeLocationWriter(database, "aaaaaaaaaaaaaaaa", sequenceIds(), { 1_751_760_000_000 }, { writes++ })
        val first = writer.record("near", Coordinates(-33.85, 151.21))
        assertNotNull(first)
        assertEquals(null, writer.record("near", Coordinates(-33.849, 151.21)))
        assertEquals(1, writes)
        assertEquals(5, messageCount(file, "location-1"))
        assertTrue(writer.delete("location-1"))
        assertFalse(writer.delete("missing"))
        assertTrue(database.fetchPayeeLocations("near").isEmpty())
        assertEquals(6, messageCount(file, "location-1"))
        assertEquals(2, writes)
    }

    @Test fun partialSyncedRowsAreSkipped() = withDatabase { database, file ->
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL("INSERT INTO payee_locations (id, payee_id) VALUES ('partial', 'near')")
        }
        assertTrue(database.fetchPayeeLocations().isEmpty())
    }

    private fun withDatabase(block: (ActualBudgetDatabase, File) -> Unit) {
        val file = createLegacyDatabase()
        try { ActualBudgetDatabase.open(file).use { block(it, file) } } finally { file.delete() }
    }

    private fun createLegacyDatabase(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "locations-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY)")
            database.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY)")
            database.execSQL("CREATE TABLE category_groups (id TEXT PRIMARY KEY)")
            database.execSQL("CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT)")
            database.execSQL("CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, transfer_acct TEXT, tombstone INTEGER DEFAULT 0)")
            database.execSQL("INSERT INTO payees VALUES ('near', 'Near Cafe', NULL, 0)")
            database.execSQL("INSERT INTO payees VALUES ('far', 'Far Cafe', NULL, 0)")
            database.execSQL("CREATE TABLE transactions (id TEXT PRIMARY KEY)")
            database.execSQL("CREATE TABLE zero_budgets (id TEXT PRIMARY KEY)")
            database.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            database.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, column TEXT NOT NULL, value BLOB NOT NULL)")
        }
        return file
    }

    private fun sequenceIds(): () -> String {
        var next = 0
        return { "location-${++next}" }
    }

    private fun tableExists(database: SQLiteDatabase, table: String) =
        database.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }

    private fun messageCount(file: File, row: String) =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT COUNT(*) FROM messages_crdt WHERE dataset='payee_locations' AND row=?", arrayOf(row))
                .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        }
}
