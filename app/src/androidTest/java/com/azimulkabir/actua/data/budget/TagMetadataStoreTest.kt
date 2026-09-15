package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TagMetadataStoreTest {
    @Test
    fun readsTagColorsFromActualSchema() {
        withTempDatabase("tag-metadata-") { database ->
            database.execSQL(
                "CREATE TABLE tags (id TEXT PRIMARY KEY, tag TEXT UNIQUE, color TEXT, description TEXT)",
            )
            database.execSQL("INSERT INTO tags VALUES ('1','Home','#336699','Home tag')")
            database.execSQL("INSERT INTO tags VALUES ('2','home','#abcdef','Different case')")
            database.execSQL("INSERT INTO tags VALUES ('3','NoColor',NULL,'No colour')")
            database.execSQL("INSERT INTO tags VALUES ('4','BlankColor','','Blank colour')")

            val colors = readTagColors(database)

            assertEquals("#336699", colors["Home"])
            assertEquals("#abcdef", colors["home"])
            assertFalse(colors.containsKey("NoColor"))
            assertFalse(colors.containsKey("BlankColor"))
        }
    }

    @Test
    fun readsCanonicalMetadataAndFiltersTombstones() {
        withTempDatabase("tag-full-") { database ->
            database.execSQL(
                "CREATE TABLE tags (id TEXT PRIMARY KEY, tag TEXT UNIQUE, color TEXT, description TEXT, hidden INTEGER DEFAULT 0, tombstone INTEGER DEFAULT 0)",
            )
            database.execSQL("INSERT INTO tags VALUES ('1','school','#ffb300','School costs',1,0)")
            database.execSQL("INSERT INTO tags VALUES ('2','old','#000000','Deleted',0,1)")

            val snapshot = readTags(database)

            assertTrue(snapshot.capabilities.available)
            assertTrue(snapshot.capabilities.hidden)
            assertEquals(1, snapshot.tags.size)
            assertEquals("1", snapshot.tags.single().id)
            assertEquals("school", snapshot.tags.single().tag)
            assertEquals("#ffb300", snapshot.tags.single().color)
            assertEquals("School costs", snapshot.tags.single().description)
            assertTrue(snapshot.tags.single().hidden)
        }
    }

    @Test
    fun legacySchemaDefaultsOptionalMetadataSafely() {
        withTempDatabase("tag-legacy-") { database ->
            database.execSQL("CREATE TABLE tags (id TEXT PRIMARY KEY, tag TEXT UNIQUE, color TEXT)")
            database.execSQL("INSERT INTO tags VALUES ('1','legacy',NULL)")

            val snapshot = readTags(database)

            assertTrue(snapshot.capabilities.available)
            assertFalse(snapshot.capabilities.hidden)
            assertEquals(null, snapshot.tags.single().description)
            assertFalse(snapshot.tags.single().hidden)
        }
    }

    @Test
    fun missingTagsTableReturnsEmptyData() {
        withTempDatabase("no-tags-") { database ->
            assertEquals(emptyMap<String, String>(), readTagColors(database))
            val snapshot = readTags(database)
            assertTrue(snapshot.tags.isEmpty())
            assertFalse(snapshot.capabilities.available)
        }
    }

    private fun withTempDatabase(prefix: String, block: (SQLiteDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile(prefix, ".sqlite", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use(block)
        } finally {
            file.delete()
        }
    }
}
