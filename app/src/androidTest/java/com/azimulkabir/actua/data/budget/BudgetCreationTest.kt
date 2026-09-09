package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.zip.ZipInputStream

class BudgetCreationTest {
    @Test fun createdBudgetIsActualCompatibleAndUploadMetadataIsDetached() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val name = "Creation ${UUID.randomUUID()}"
        val budget = files.createBudget(name)
        try {
            ActualBudgetDatabase.validate(files.databaseFile(budget.id))
            SQLiteDatabase.openDatabase(files.databaseFile(budget.id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertEquals(57, db.rawQuery("SELECT COUNT(*) FROM __migrations__", null).use { it.moveToFirst(); it.getInt(0) })
                assertEquals(7, db.rawQuery("SELECT COUNT(*) FROM categories", null).use { it.moveToFirst(); it.getInt(0) })
            }
            val archivedMetadata = ZipInputStream(files.uploadArchive(budget.id).inputStream()).use { zip ->
                generateSequence { zip.nextEntry }.first { it.name == "metadata.json" }
                JSONObject(zip.readBytes().decodeToString())
            }
            assertTrue(archivedMetadata.getBoolean("resetClock"))
            assertFalse(JSONObject(files.metadataFile(budget.id).readText()).has("resetClock"))

            files.saveCloudRegistration(budget.id, "cloud", "group")
            val registered = BudgetMetadata.fromJson(JSONObject(files.metadataFile(budget.id).readText()))
            assertEquals("cloud", registered.cloudFileId)
            assertEquals("group", registered.groupId)
        } finally {
            runCatching { files.deleteBudget(budget.id) }
        }
    }
}
