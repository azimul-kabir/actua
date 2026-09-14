package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class TagMetadataStoreTest {
    @Test
    fun readsActiveTagColorsAndIgnoresTombstonesWithoutDroppingHiddenTags() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("tag-metadata-", ".sqlite", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
                database.execSQL(
                    "CREATE TABLE tags (id TEXT PRIMARY KEY, tag TEXT UNIQUE, color TEXT, description TEXT, tombstone INTEGER DEFAULT 0, hidden BOOLEAN DEFAULT 0)",
                )
                database.execSQL("INSERT INTO tags VALUES ('1','Home','#336699','Home tag',0,0)")
                database.execSQL("INSERT INTO tags VALUES ('2','home','#abcdef','Different case',0,0)")
                database.execSQL("INSERT INTO tags VALUES ('3','Hidden','#112233','Hidden tag',0,1)")
                database.execSQL("INSERT INTO tags VALUES ('4','Deleted','#ff0000','Deleted tag',1,0)")
                database.execSQL("INSERT INTO tags VALUES ('5','NoColor',NULL,'No colour',0,0)")
                database.execSQL("INSERT INTO tags VALUES ('6','Legacy','#445566','Legacy active tag',NULL,0)")

                val colors = readTagColors(database)

                assertEquals("#336699", colors["Home"])
                assertEquals("#abcdef", colors["home"])
                assertEquals("#112233", colors["Hidden"])
                assertEquals("#445566", colors["Legacy"])
                assertFalse(colors.containsKey("Deleted"))
                assertFalse(colors.containsKey("NoColor"))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun missingTagsTableReturnsEmptyMap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("no-tags-", ".sqlite", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
                assertEquals(emptyMap<String, String>(), readTagColors(database))
            }
        } finally {
            file.delete()
        }
    }
}
