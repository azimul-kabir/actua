package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PendingMessagesDatabaseTest {
    private lateinit var db: SQLiteDatabase

    @Before fun setUp() {
        db = SQLiteDatabase.create(null)
        db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)")
        db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT)")
    }

    @After fun tearDown() = db.close()

    private fun pending(): List<String> = db.rawQuery(
        "SELECT dataset || '/' || row || '/' || `column` || '=' || value FROM messages_pending ORDER BY 1", null,
    ).use { buildList { while (it.moveToNext()) add(it.getString(0)) } }

    private fun defer(column: String, timestamp: String, value: String, row: String = "a1") =
        PendingMessages.defer(db, "accounts", row, column, timestamp, value)

    @Test fun deferKeepsOnlyTheNewestValuePerCell() {
        defer("future", "2026-11-02T10:00:00.000Z-0000-aaaaaaaaaaaaaaaa", "S:old")
        defer("future", "2026-11-02T12:00:00.000Z-0000-aaaaaaaaaaaaaaaa", "S:new")
        defer("future", "2026-11-02T11:00:00.000Z-0000-aaaaaaaaaaaaaaaa", "S:middle")
        assertEquals(listOf("accounts/a1/future=S:new"), pending())
    }

    @Test fun replayAppliesACellOnceItsColumnExists() {
        defer("future", "2026-11-02T10:00:00.000Z-0000-aaaaaaaaaaaaaaaa", "S:hello")
        PendingMessages.replay(db)
        // Column still unknown: the cell stays pending.
        assertEquals(listOf("accounts/a1/future=S:hello"), pending())

        db.execSQL("ALTER TABLE accounts ADD COLUMN future TEXT")
        PendingMessages.replay(db)
        assertEquals(emptyList<String>(), pending())
        db.rawQuery("SELECT future FROM accounts WHERE id = 'a1'", null).use {
            it.moveToFirst()
            assertEquals("hello", it.getString(0))
        }
    }

    @Test fun replayDropsCellsASeededNewerWriteSupersedes() {
        defer("name", "2026-11-02T10:00:00.000Z-0000-aaaaaaaaaaaaaaaa", "S:stale")
        db.execSQL(
            "INSERT INTO messages_crdt (timestamp, dataset, row, `column`, value) VALUES (?, 'accounts', 'a1', 'name', 'S:fresh')",
            arrayOf("2026-11-02T12:00:00.000Z-0000-bbbbbbbbbbbbbbbb"),
        )
        PendingMessages.replay(db)
        assertEquals(emptyList<String>(), pending())
        db.rawQuery("SELECT 1 FROM accounts", null).use { assertEquals(0, it.count) }
    }

    @Test fun migrationCreatesTheTableAndIsIdempotent() {
        db.execSQL("CREATE TABLE __migrations__ (id INT PRIMARY KEY NOT NULL)")
        ActualMigrations.apply(db, PendingMessages.MIGRATION, null)
        ActualMigrations.apply(db, PendingMessages.MIGRATION, null)
        assertEquals(emptyList<String>(), pending())
        db.rawQuery("SELECT id FROM __migrations__", null).use {
            it.moveToFirst()
            assertEquals(PendingMessages.MIGRATION, it.getLong(0))
        }
    }
}
