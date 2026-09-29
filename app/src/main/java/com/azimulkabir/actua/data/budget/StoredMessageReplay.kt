package com.azimulkabir.actua.data.budget

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.azimulkabir.actua.data.sync.CrdtValue

/**
 * Cells whose table or column didn't exist locally are kept in `messages_crdt` but not applied. When
 * a migration adds that table or column, the latest stored value of each such cell is written in.
 */
internal object StoredMessageReplay {
    private val internalTables = setOf("messages_crdt", "messages_clock", "migrations", "__migrations__", "__meta__")

    /** Syncable tables (those with an `id`) and their columns. */
    fun schema(database: SQLiteDatabase): Map<String, Set<String>> {
        val tables = database.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }.filter { it !in internalTables && !it.startsWith("sqlite_") }
        return tables.associateWith { table ->
            database.rawQuery("PRAGMA table_info(${quote(table)})", null).use { cursor ->
                val name = cursor.getColumnIndexOrThrow("name")
                buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
            }
        }.filterValues { "id" in it }
    }

    /** Replays stored values for every table and column that exists now but not in [before]. */
    fun replay(database: SQLiteDatabase, before: Map<String, Set<String>>) {
        if (!database.hasMessageLog()) return
        schema(database).forEach { (table, columns) ->
            val newTable = table !in before
            val added = columns - before[table].orEmpty() - "id"
            if (added.isEmpty()) return@forEach
            val rows = linkedMapOf<String, ContentValues>()
            database.rawQuery(
                """
                    SELECT m.row, m.`column`, m.value FROM messages_crdt m
                    JOIN (SELECT row, `column`, MAX(timestamp) timestamp FROM messages_crdt
                          WHERE dataset = ? GROUP BY row, `column`) latest
                      ON latest.row = m.row AND latest.`column` = m.`column` AND latest.timestamp = m.timestamp
                    WHERE m.dataset = ?
                """.trimIndent(), arrayOf(table, table),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val column = cursor.getString(1)
                    if (column !in added) continue
                    rows.getOrPut(cursor.getString(0)) { ContentValues() }.putCrdt(column, cursor.getString(2))
                }
            }
            rows.forEach { (id, values) ->
                val updated = database.update(quote(table), values, "id = ?", arrayOf(id))
                if (updated == 0 && newTable) {
                    values.put("id", id)
                    database.insertWithOnConflict(quote(table), null, values, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
        }
    }

    private fun ContentValues.putCrdt(column: String, serialized: String) {
        when (val value = CrdtValue.deserialize(serialized)) {
            CrdtValue.Null -> putNull(column)
            is CrdtValue.Integer -> put(column, value.value)
            is CrdtValue.Decimal -> put(column, value.value)
            is CrdtValue.Text -> put(column, value.value)
        }
    }

    private fun SQLiteDatabase.hasMessageLog(): Boolean = rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'messages_crdt'", null,
    ).use { it.moveToFirst() }

    private fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
