package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.reports.SavedReportRow
import com.azimulkabir.actua.data.sync.HlcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class CustomReportAndDashboardWriterTest {

    private fun testReport(
        id: String = "",
        name: String = "My Report",
        startDate: String? = "2026-01-01",
        endDate: String? = "2026-02-28",
        dateStatic: Boolean = true,
        dateRange: String? = null,
        groupBy: String = "Category",
        balanceType: String = "Payment",
        graphType: String = "DonutGraph",
    ) = SavedReportRow(
        id = id,
        name = name,
        startDate = startDate,
        endDate = endDate,
        dateStatic = dateStatic,
        dateRange = dateRange,
        groupBy = groupBy,
        balanceType = balanceType,
        showOffBudget = false,
        showHidden = false,
        showUncategorized = false,
        selectedCategories = null,
        graphType = graphType,
        conditions = null,
        conditionsOp = "and",
        interval = "Monthly",
        sortBy = "desc",
    )

    @Test
    fun saveAndFetchCustomReportCreatesCrdtMessages() = withDatabase { database, _ ->
        val writer = ActualEntityWriter(database, nodeId = "node111111111111")
        val report = testReport(name = "My Spending", graphType = "DonutGraph", balanceType = "Payment")

        val id = writer.saveCustomReport(report)
        assertTrue(id.isNotBlank())

        val fetched = database.fetchSavedReport(id)
        assertNotNull(fetched)
        assertEquals("My Spending", fetched!!.name)
        assertEquals("DonutGraph", fetched.graphType)
        assertEquals("Payment", fetched.balanceType)

        // Check CRDT messages logged
        val messages = database.getMessagesSince(HlcTimestamp.ZERO.toString())
            .filter { it.dataset == "custom_reports" && it.row == id }
        assertTrue(messages.isNotEmpty())
        assertTrue(messages.any { it.column == "name" })
        assertTrue(messages.any { it.column == "graph_type" })
    }

    @Test
    fun updateCustomReportModifiesExistingRow() = withDatabase { database, _ ->
        val writer = ActualEntityWriter(database, nodeId = "node222222222222")
        val initial = testReport(id = "rep-1", name = "Initial Report", graphType = "BarGraph")
        writer.saveCustomReport(initial)

        val updated = initial.copy(name = "Updated Report", graphType = "LineGraph")
        val returnedId = writer.saveCustomReport(updated)
        assertEquals("rep-1", returnedId)

        val fetched = database.fetchSavedReport("rep-1")
        assertNotNull(fetched)
        assertEquals("Updated Report", fetched!!.name)
        assertEquals("LineGraph", fetched.graphType)
    }

    @Test
    fun deleteCustomReportTombstonesReportAndReferencingDashboardWidgets() = withDatabase { database, _ ->
        val writer = ActualEntityWriter(database, nodeId = "node333333333333")
        val report = testReport(id = "rep-to-del", name = "To Delete")
        writer.saveCustomReport(report)

        // Add a dashboard widget referencing this custom report
        val widgetId = writer.addDashboardWidget(
            pageId = null,
            type = "custom",
            metaJson = """{"customReportId":"rep-to-del"}""",
            width = 6,
            height = 2,
        )

        writer.deleteCustomReport("rep-to-del")

        // Report should not be returned by fetchSavedReport or fetchSavedReports
        assertNull(database.fetchSavedReport("rep-to-del"))
        assertTrue(database.fetchSavedReports().none { it.id == "rep-to-del" })

        // Check tombstone message
        val reportMessages = database.getMessagesSince(HlcTimestamp.ZERO.toString())
            .filter { it.dataset == "custom_reports" && it.row == "rep-to-del" && it.column == "tombstone" }
        assertEquals(1, reportMessages.size)

        // Referencing dashboard widget should also be tombstoned
        val widgetMessages = database.getMessagesSince(HlcTimestamp.ZERO.toString())
            .filter { it.dataset == "dashboard" && it.row == widgetId && it.column == "tombstone" }
        assertEquals(1, widgetMessages.size)
    }

    @Test
    fun dashboardWidgetAddDeleteAndReorder() = withDatabase { database, file ->
        val writer = ActualEntityWriter(database, nodeId = "node444444444444")
        val w1 = writer.addDashboardWidget(pageId = null, type = "net-worth", metaJson = null)
        val w2 = writer.addDashboardWidget(pageId = null, type = "cash-flow", metaJson = null)
        val w3 = writer.addDashboardWidget(pageId = null, type = "spending", metaJson = null)

        // Reorder widgets: w3, w1, w2
        writer.reorderDashboardWidgets(pageId = null, listOf(w3, w1, w2))

        // Verify y coordinates
        var y1: Int? = null
        var y2: Int? = null
        var y3: Int? = null
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT id, y FROM dashboard WHERE id IN ('$w1', '$w2', '$w3')", null).use { c ->
                while (c.moveToNext()) {
                    when (c.getString(0)) {
                        w1 -> y1 = c.getInt(1)
                        w2 -> y2 = c.getInt(1)
                        w3 -> y3 = c.getInt(1)
                    }
                }
            }
        }
        assertEquals(0, y3)
        assertEquals(2, y1)
        assertEquals(4, y2)

        // Delete w2
        writer.deleteDashboardWidget(w2)

        val tombstoneMsg = database.getMessagesSince(HlcTimestamp.ZERO.toString())
            .filter { it.dataset == "dashboard" && it.row == w2 && it.column == "tombstone" }
        assertEquals(1, tombstoneMsg.size)
    }

    @Test
    fun dashboardPagesLifecycle() = withDatabase { database, _ ->
        val writer = ActualEntityWriter(database, nodeId = "node555555555555")
        val pageId = writer.createDashboardPage("Investments")
        assertTrue(pageId.isNotBlank())

        val pages = database.fetchDashboardPages()
        val page = pages.firstOrNull { it.id == pageId }
        assertNotNull(page)
        assertEquals("Investments", page!!.name)

        // Rename
        writer.renameDashboardPage(pageId, "Portfolio")
        assertEquals("Portfolio", database.fetchDashboardPages().first { it.id == pageId }.name)

        // Add widget to page
        val widgetId = writer.addDashboardWidget(pageId, "net-worth", null)

        // Delete page
        writer.deleteDashboardPage(pageId)
        assertTrue(database.fetchDashboardPages().none { it.id == pageId })

        // Check that page widget was also tombstoned
        val widgetTombstone = database.getMessagesSince(HlcTimestamp.ZERO.toString())
            .filter { it.dataset == "dashboard" && it.row == widgetId && it.column == "tombstone" }
        assertEquals(1, widgetTombstone.size)
    }

    private fun withDatabase(block: (ActualBudgetDatabase, File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "custom-reports-test-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, type TEXT, offbudget INTEGER DEFAULT 0, closed INTEGER DEFAULT 0, sort_order REAL, account_group_id TEXT, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE account_groups (id TEXT PRIMARY KEY, name TEXT, sort_order REAL, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, cat_group TEXT, is_income INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)")
            db.execSQL("CREATE TABLE payee_mapping (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE payees (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE transactions (id TEXT PRIMARY KEY, acct TEXT, amount INTEGER, date INTEGER, cleared INTEGER DEFAULT 0, reconciled INTEGER DEFAULT 0, isChild INTEGER DEFAULT 0, isParent INTEGER DEFAULT 0, parent_id TEXT, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE zero_budgets (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, column TEXT NOT NULL, value BLOB NOT NULL)")

            db.execSQL(
                """CREATE TABLE custom_reports (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    startDate TEXT,
                    endDate TEXT,
                    is_static INTEGER DEFAULT 0,
                    date_range TEXT,
                    mode TEXT,
                    groupBy TEXT,
                    balanceType TEXT,
                    show_empty INTEGER DEFAULT 0,
                    show_off_budget INTEGER DEFAULT 0,
                    show_hidden_categories INTEGER DEFAULT 0,
                    include_current INTEGER DEFAULT 0,
                    trim INTEGER DEFAULT 0,
                    sort_by TEXT,
                    graph_type TEXT,
                    conditions TEXT,
                    conditions_op TEXT,
                    metadata TEXT,
                    interval TEXT,
                    color_scheme TEXT,
                    tombstone INTEGER DEFAULT 0
                )"""
            )
            db.execSQL(
                """CREATE TABLE dashboard_pages (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    sort_order REAL,
                    tombstone INTEGER DEFAULT 0
                )"""
            )
            db.execSQL(
                """CREATE TABLE dashboard (
                    id TEXT PRIMARY KEY,
                    type TEXT,
                    width INTEGER DEFAULT 6,
                    height INTEGER DEFAULT 2,
                    x INTEGER DEFAULT 0,
                    y INTEGER DEFAULT 0,
                    meta TEXT,
                    page_id TEXT,
                    tombstone INTEGER DEFAULT 0
                )"""
            )
        }
        try {
            ActualBudgetDatabase.open(file).use { block(it, file) }
        } finally {
            file.delete()
        }
    }
}
