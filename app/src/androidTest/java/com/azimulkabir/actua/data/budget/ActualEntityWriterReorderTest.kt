package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.UUID

/** Regression coverage for issue #223: drag-to-reorder category groups and categories. */
class ActualEntityWriterReorderTest {
    @Test
    fun moveCategoryReordersWithinItsGroup() = withDatabase { database, _ ->
        val writer = ActualEntityWriter(database, nodeId = "aaaaaaaaaaaaaaaa")
        writer.moveCategory("electric", "bills", "rent")
        val group = database.fetchCategoryGroups().single { it.id == "bills" }
        assertEquals(listOf("electric", "rent"), group.categories.sortedBy { it.sortOrder }.map { it.id })
    }

    @Test
    fun moveCategoryAcrossGroupsUpdatesGroupAndAdoptsDestinationFlags() = withDatabase { database, _ ->
        val writer = ActualEntityWriter(database, nodeId = "bbbbbbbbbbbbbbbb")
        writer.moveCategory("rent", "fun", "dining")

        val groups = database.fetchCategoryGroups()
        val bills = groups.single { it.id == "bills" }
        val fun_ = groups.single { it.id == "fun" }
        assertEquals(listOf("electric"), bills.categories.map { it.id })
        assertEquals(listOf("rent", "dining"), fun_.categories.sortedBy { it.sortOrder }.map { it.id })

        val moved = fun_.categories.single { it.id == "rent" }
        assertEquals("fun", moved.groupId)
        assertEquals(false, moved.isIncome)
        assertEquals(false, moved.hidden)
    }

    @Test
    fun moveCategoryToEndOfGroupWhenBeforeIdNull() = withDatabase { database, _ ->
        val writer = ActualEntityWriter(database, nodeId = "cccccccccccccccc")
        writer.moveCategory("rent", "bills", null)
        val group = database.fetchCategoryGroups().single { it.id == "bills" }
        assertEquals(listOf("electric", "rent"), group.categories.sortedBy { it.sortOrder }.map { it.id })
    }

    @Test
    fun moveCategoryGroupReordersGroups() = withDatabase { database, _ ->
        val writer = ActualEntityWriter(database, nodeId = "dddddddddddddddd")
        writer.moveCategoryGroup("fun", "bills")
        val groups = database.fetchCategoryGroups().sortedBy { it.sortOrder }
        assertEquals(listOf("fun", "bills", "income"), groups.map { it.id })
    }

    @Test
    fun moveCategoryGroupCannotMoveTheIncomeGroup() = withDatabase { database, _ ->
        val writer = ActualEntityWriter(database, nodeId = "eeeeeeeeeeeeeeee")
        try {
            writer.moveCategoryGroup("income", "bills")
            fail("Expected the income group's position to be fixed")
        } catch (_: IllegalArgumentException) {
            // expected: moveCategoryGroup enforces this with require(), not error()
        }
        val groups = database.fetchCategoryGroups().sortedBy { it.sortOrder }
        assertEquals(listOf("bills", "fun", "income"), groups.map { it.id })
    }

    @Test
    fun reorderingSurvivesReopeningTheDatabase() = withDatabase { database, file ->
        val writer = ActualEntityWriter(database, nodeId = "ffffffffffffffff")
        writer.moveCategory("electric", "bills", "rent")
        writer.moveCategoryGroup("fun", "bills")

        ActualBudgetDatabase.open(file).use { reopened ->
            val groups = reopened.fetchCategoryGroups().sortedBy { it.sortOrder }
            assertEquals(listOf("fun", "bills", "income"), groups.map { it.id })
            assertEquals(
                listOf("electric", "rent"),
                groups.single { it.id == "bills" }.categories.sortedBy { it.sortOrder }.map { it.id },
            )
            assertTrue(reopened.maxMessageTimestamp() != null)
        }
    }

    private fun withDatabase(block: (ActualBudgetDatabase, File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "entity-writer-reorder-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, cat_group TEXT, is_income INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)")
            db.execSQL("CREATE TABLE payee_mapping (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE payees (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE transactions (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE zero_budgets (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, column TEXT NOT NULL, value BLOB NOT NULL)")

            // The income group's sort_order sits far above the expense groups', matching real
            // Actual data, so shoving expense sort_order values around it never interleaves it.
            db.execSQL("INSERT INTO category_groups VALUES ('bills','Bills',0,0,1.0,0), ('fun','Fun',0,0,2.0,0), ('income','Income',1,0,1000000.0,0)")
            db.execSQL("INSERT INTO categories VALUES ('rent','Rent','bills',0,0,1.0,0), ('electric','Electric','bills',0,0,2.0,0), ('dining','Dining','fun',0,0,1.0,0), ('salary','Salary','income',1,0,1.0,0)")
        }
        try {
            ActualBudgetDatabase.open(file).use { block(it, file) }
        } finally {
            file.delete()
        }
    }
}
