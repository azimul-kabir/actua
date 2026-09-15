package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class ActualTagWriterTest {
    @Test
    fun createUpdateAndDeleteUseCanonicalTagsDataset() {
        withDatabase { database, file ->
            var pushes = 0
            val writer = ActualTagWriter(
                database,
                nodeId = "aaaaaaaaaaaaaaaa",
                idFactory = { "tag-1" },
                onWrite = { pushes++ },
            )

            val id = writer.create("school", "#6A1B9A", "School expenses")
            assertEquals("tag-1", id)
            assertTag(file, "school", "#6A1B9A", "School expenses", hidden = false, tombstone = false)

            writer.update(
                id,
                color = "#123456",
                description = null,
                hidden = true,
                updateColor = true,
                updateDescription = true,
            )
            assertTag(file, "school", "#123456", null, hidden = true, tombstone = false)

            writer.update(id, tag = "education")
            assertTag(file, "education", "#123456", null, hidden = true, tombstone = false)

            writer.delete(id)
            assertTag(file, "education", "#123456", null, hidden = true, tombstone = true)
            assertEquals(4, pushes)

            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { readable ->
                assertTrue(readTags(readable).tags.isEmpty())
            }
        }
    }

    @Test
    fun tagNamesFollowActualValidation() {
        withDatabase { database, _ ->
            val writer = ActualTagWriter(database, nodeId = "bbbbbbbbbbbbbbbb")
            assertFailure { writer.create("") }
            assertFailure { writer.create("two words") }
            assertFailure { writer.create("#school") }
        }
    }

    private fun assertFailure(block: () -> Unit) {
        var failed = false
        try { block() } catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }

    private fun assertTag(
        file: File,
        name: String,
        color: String?,
        description: String?,
        hidden: Boolean,
        tombstone: Boolean,
    ) {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT tag, color, description, hidden, tombstone FROM tags WHERE id='tag-1'",
                null,
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(name, cursor.getString(0))
                assertEquals(color, if (cursor.isNull(1)) null else cursor.getString(1))
                assertEquals(description, if (cursor.isNull(2)) null else cursor.getString(2))
                assertEquals(hidden, cursor.getInt(3) == 1)
                assertEquals(tombstone, cursor.getInt(4) == 1)
            }
        }
    }

    private fun withDatabase(block: (ActualBudgetDatabase, File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "tags-writer-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE tags (id TEXT PRIMARY KEY, tag TEXT UNIQUE, color TEXT, description TEXT, hidden INTEGER DEFAULT 0, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, column TEXT NOT NULL, value BLOB NOT NULL)")
        }
        try {
            ActualBudgetDatabase.open(file).use { block(it, file) }
        } finally {
            file.delete()
        }
    }
}
