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
                assertEquals(59, db.rawQuery("SELECT COUNT(*) FROM __migrations__", null).use { it.moveToFirst(); it.getInt(0) })
                assertEquals(7, db.rawQuery("SELECT COUNT(*) FROM categories", null).use { it.moveToFirst(); it.getInt(0) })
                assertTrue(db.rawQuery("SELECT 1 FROM account_groups LIMIT 1", null).use { it.columnCount == 1 })
                assertTrue(db.rawQuery("PRAGMA table_info(accounts)", null).use { cursor ->
                    val name = cursor.getColumnIndexOrThrow("name")
                    generateSequence { if (cursor.moveToNext()) cursor.getString(name) else null }
                        .any { it == "account_group_id" }
                })
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

    @Test fun createdBudgetHasActualsDefaultDashboardPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val first = files.createBudget("Dashboard ${UUID.randomUUID()}")
        val second = files.createBudget("Dashboard ${UUID.randomUUID()}")
        try {
            fun pageIds(budgetId: String) = SQLiteDatabase.openDatabase(
                files.databaseFile(budgetId).path, null, SQLiteDatabase.OPEN_READONLY,
            ).use { db ->
                db.rawQuery("SELECT id, name FROM dashboard_pages WHERE tombstone = 0", null).use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
                }
            }
            val pages = pageIds(first.id)
            assertEquals(listOf("Main"), pages.map { it.second })
            val pageId = pages.single().first

            SQLiteDatabase.openDatabase(files.databaseFile(first.id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val widgets = db.rawQuery(
                    "SELECT type, width, height, x, y, meta, dashboard_page_id FROM dashboard WHERE tombstone = 0 ORDER BY y, x",
                    null,
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            assertEquals(pageId, cursor.getString(6))
                            add(BlankBudgetFactory.DashboardWidget(
                                cursor.getString(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getInt(4),
                                if (cursor.isNull(5)) null else cursor.getString(5),
                            ))
                        }
                    }
                }
                assertEquals(BlankBudgetFactory.DEFAULT_DASHBOARD.sortedWith(compareBy({ it.y }, { it.x })), widgets)
                assertEquals(0, db.rawQuery("SELECT COUNT(*) FROM messages_crdt", null).use { it.moveToFirst(); it.getInt(0) })
            }
            assertTrue("each budget gets its own page id", pageIds(second.id).single().first != pageId)

            BudgetOpenProbe.validate(files.databaseFile(first.id))
            ActualBudgetDatabase.open(files.databaseFile(first.id)).use { database ->
                assertEquals(BlankBudgetFactory.DEFAULT_DASHBOARD.size, database.fetchDashboardWidgets(pageId).size)
            }
        } finally {
            runCatching { files.deleteBudget(first.id) }
            runCatching { files.deleteBudget(second.id) }
        }
    }
}
