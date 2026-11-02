package com.azimulkabir.actua.data.budget

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.azimulkabir.actua.data.sync.CrdtValue

/**
 * Actual's `messages_pending` table (migration `1788468782000`, `sync/messages-pending.ts`,
 * `deferMessage` and `replayPendingMessages`). An inbound sync message that targets a table or
 * column this client doesn't have, or carries a value format it can't decode, is kept here instead
 * of being dropped, newest value per cell. It is applied once a migration adds the missing schema.
 * The table travels in the budget snapshot, so a newer Actual that opens a file uploaded by Actua
 * can still replay those cells.
 */
internal object PendingMessages {
    const val MIGRATION = 1788468782000L
    const val TABLE = "messages_pending"

    /** Upstream's DDL, with `column` quoted as the rest of the schema does. */
    const val CREATE_TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS messages_pending (dataset TEXT NOT NULL, row TEXT NOT NULL, " +
            "`column` TEXT NOT NULL, timestamp TEXT NOT NULL, value TEXT NOT NULL, " +
            "PRIMARY KEY (dataset, row, `column`))"

    /** Datasets Actual accepts but never writes to a table (`apply` in `sync/index.ts`). */
    private val noOpDatasets = setOf("prefs", "spreadsheet_cells")

    /**
     * Whether an inbound cell can't be applied here yet: its table or column is unknown to this
     * client ([schema] maps syncable tables to their columns) or its value format is.
     */
    fun shouldDefer(schema: Map<String, Set<String>>, dataset: String, column: String, value: String): Boolean {
        if (dataset in noOpDatasets) return false
        val columns = schema[dataset] ?: return true
        return column !in columns || !CrdtValue.isKnownFormat(value)
    }

    /** Stores [dataset]/[row]/[column] unless a newer value for the cell is already pending. */
    fun defer(database: SQLiteDatabase, dataset: String, row: String, column: String, timestamp: String, value: String) {
        database.execSQL(CREATE_TABLE_SQL)
        val existing = database.rawQuery(
            "SELECT timestamp FROM $TABLE WHERE dataset = ? AND row = ? AND `column` = ?",
            arrayOf(dataset, row, column),
        ).use { if (it.moveToFirst()) it.getString(0) else null }
        if (existing != null && existing >= timestamp) return
        database.execSQL(
            "INSERT OR REPLACE INTO $TABLE (dataset, row, `column`, timestamp, value) VALUES (?, ?, ?, ?, ?)",
            arrayOf(dataset, row, column, timestamp, value),
        )
    }

    /** Drops pending cells that a newer write in the message log has superseded. */
    fun deleteStale(database: SQLiteDatabase) {
        if (!database.hasPendingTable()) return
        database.execSQL(
            """
                DELETE FROM $TABLE WHERE EXISTS (
                    SELECT 1 FROM messages_crdt c
                    WHERE c.dataset = $TABLE.dataset AND c.row = $TABLE.row
                      AND c.`column` = $TABLE.`column` AND c.timestamp > $TABLE.timestamp)
            """.trimIndent(),
        )
    }

    private data class Pending(val dataset: String, val row: String, val column: String, val value: String)

    /**
     * Applies pending cells whose schema exists now, oldest first, and removes them. Cells that still
     * target unknown schema stay. A cell that fails a row constraint is retried while other cells make
     * progress (a row may need a sibling cell first) and dropped when it still fails, as upstream does.
     */
    fun replay(database: SQLiteDatabase, schema: Map<String, Set<String>> = StoredMessageReplay.schema(database)) {
        if (!database.hasPendingTable()) return
        deleteStale(database)
        val pending = database.rawQuery(
            "SELECT dataset, row, `column`, value FROM $TABLE ORDER BY timestamp", null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(Pending(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3))) }
        }.filterNot { shouldDefer(schema, it.dataset, it.column, it.value) }

        var failed = applyAll(database, pending)
        while (failed.isNotEmpty()) {
            val retried = applyAll(database, failed)
            if (retried.size == failed.size) {
                retried.forEach { remove(database, it) }
                return
            }
            failed = retried
        }
    }

    private fun applyAll(database: SQLiteDatabase, cells: List<Pending>): List<Pending> {
        val failed = mutableListOf<Pending>()
        cells.forEach { cell ->
            try {
                upsert(database, cell)
                remove(database, cell)
            } catch (_: SQLiteConstraintException) {
                failed += cell
            }
        }
        return failed
    }

    private fun upsert(database: SQLiteDatabase, cell: Pending) {
        val values = ContentValues()
        with(StoredMessageReplay) { values.putCrdt(cell.column, cell.value) }
        val table = "\"${cell.dataset.replace("\"", "\"\"")}\""
        if (database.update(table, values, "id = ?", arrayOf(cell.row)) == 0) {
            values.put("id", cell.row)
            database.insertOrThrow(table, null, values)
        }
    }

    private fun remove(database: SQLiteDatabase, cell: Pending) {
        database.delete(TABLE, "dataset = ? AND row = ? AND `column` = ?", arrayOf(cell.dataset, cell.row, cell.column))
    }

    private fun SQLiteDatabase.hasPendingTable(): Boolean = rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(TABLE),
    ).use { it.moveToFirst() }
}
