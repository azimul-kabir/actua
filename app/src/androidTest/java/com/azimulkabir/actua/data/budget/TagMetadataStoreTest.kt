package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class TagMetadataStoreTest {
    @Test
    fun readsTagColorsFromActualSchema() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("tag-metadata-", ".sqlite", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
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
