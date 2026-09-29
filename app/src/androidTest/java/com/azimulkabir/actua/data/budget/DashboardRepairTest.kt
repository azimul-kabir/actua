package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Budgets created by Actua before #716 have no dashboard page; the post-sync repair adds Actual's (#723). */
class DashboardRepairTest {
    @Test fun budgetWithoutPagesGetsActualsSyncedDefaultDashboardOnce() = withBudget(clearDashboard = true) { database, file ->
        assertTrue(ActualEntityWriter(database).ensureDashboardPage())

        val pages = database.fetchDashboardPages()
        assertEquals(listOf(ActualEntityWriter.DEFAULT_DASHBOARD_PAGE_ID to "Main"), pages.map { it.id to it.name })
        val widgets = database.fetchDashboardWidgets(ActualEntityWriter.DEFAULT_DASHBOARD_PAGE_ID)
        assertEquals(BlankBudgetFactory.DEFAULT_DASHBOARD.size, widgets.size)
        assertEquals(
            BlankBudgetFactory.DEFAULT_DASHBOARD.indices.map(ActualEntityWriter::defaultDashboardWidgetId).toSet(),
            widgets.map { it.id }.toSet(),
        )
        assertEquals(
            BlankBudgetFactory.DEFAULT_DASHBOARD.map { it.type to it.meta }.toSet(),
            widgets.map { it.type to it.metaJson }.toSet(),
        )
        val synced = syncedRows(file)
        assertTrue(ActualEntityWriter.DEFAULT_DASHBOARD_PAGE_ID in synced.getValue("dashboard_pages"))
        assertEquals(widgets.map { it.id }.toSet(), synced.getValue("dashboard"))

        val messageCount = messageCount(file)
        assertFalse("a budget that has a page is left alone", ActualEntityWriter(database).ensureDashboardPage())
        assertEquals(messageCount, messageCount(file))
    }

    @Test fun existingWidgetsMoveOntoTheNewPageWithoutAddingDefaults() = withBudget(clearDashboard = true) { database, file ->
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL("INSERT INTO dashboard (id, type, width, height, x, y, meta, dashboard_page_id) VALUES ('loose','net-worth-card',6,2,0,0,NULL,NULL)")
            raw.execSQL("INSERT INTO dashboard (id, type, width, height, x, y, meta, dashboard_page_id) VALUES ('stranded','cash-flow-card',6,2,6,0,NULL,'deleted-page')")
            raw.execSQL("INSERT INTO dashboard_pages (id, name, tombstone) VALUES ('deleted-page','Old',1)")
        }

        assertTrue(ActualEntityWriter(database).ensureDashboardPage())

        assertEquals(listOf(ActualEntityWriter.DEFAULT_DASHBOARD_PAGE_ID), database.fetchDashboardPages().map { it.id })
        assertEquals(
            setOf("loose", "stranded"),
            database.fetchDashboardWidgets(ActualEntityWriter.DEFAULT_DASHBOARD_PAGE_ID).map { it.id }.toSet(),
        )
        assertEquals(setOf("loose", "stranded"), syncedRows(file).getValue("dashboard"))
    }

    @Test fun newAndActualCreatedBudgetsAreUntouched() = withBudget(clearDashboard = false) { database, file ->
        assertFalse(ActualEntityWriter(database).ensureDashboardPage())
        assertEquals(0, messageCount(file))
    }

    @Test fun repairIdsAreTheSameOnEveryDevice() {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        withBudget(clearDashboard = true) { database, _ ->
            ActualEntityWriter(database).ensureDashboardPage()
            first += database.fetchDashboardPages().map { it.id }
            first += database.fetchDashboardWidgets(first.single()).map { it.id }.sorted()
        }
        withBudget(clearDashboard = true) { database, _ ->
            ActualEntityWriter(database).ensureDashboardPage()
            second += database.fetchDashboardPages().map { it.id }
            second += database.fetchDashboardWidgets(second.single()).map { it.id }.sorted()
        }
        assertEquals(first, second)
    }

    @Test fun schemasWithoutDashboardPagesAreSkipped() = withBudget(clearDashboard = true) { _, file ->
        // Before Actual migration 1765518577215 there are no pages; Actual creates Main itself.
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL("DROP TABLE dashboard_pages")
        }
        ActualBudgetDatabase.open(file).use { database ->
            assertFalse(ActualEntityWriter(database).ensureDashboardPage())
        }
        assertEquals(0, messageCount(file))
    }

    private fun withBudget(clearDashboard: Boolean, block: (ActualBudgetDatabase, File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "dashboard-repair-${UUID.randomUUID()}.sqlite")
        BlankBudgetFactory.create(file)
        if (clearDashboard) {
            // What budgets created by Actua before #716 look like.
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
                raw.execSQL("DELETE FROM dashboard")
                raw.execSQL("DELETE FROM dashboard_pages")
            }
        }
        try { ActualBudgetDatabase.open(file).use { block(it, file) } } finally { file.delete() }
    }

    private fun syncedRows(file: File): Map<String, Set<String>> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("SELECT dataset, row FROM messages_crdt", null).use { cursor ->
                buildMap<String, MutableSet<String>> {
                    while (cursor.moveToNext()) getOrPut(cursor.getString(0)) { mutableSetOf() } += cursor.getString(1)
                }
            }
        }

    private fun messageCount(file: File): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("SELECT COUNT(*) FROM messages_crdt", null).use { it.moveToFirst(); it.getInt(0) }
        }
}
