package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Actual's category-group-delete with target group reassignment (actua#1152). */
class CategoryGroupDeleteTest {
    @Test
    fun deletingCategoryGroupMovesCategoriesToTargetGroupAndTombstonesGroup() = withDatabase { database, writer ->
        val groupsBefore = database.fetchCategoryGroups()
        assertEquals(3, groupsBefore.size)
        val bills = groupsBefore.first { it.id == "bills" }
        assertEquals(2, bills.categories.size)

        writer.deleteCategoryGroup("bills", targetGroupId = "everyday")

        val groupsAfter = database.fetchCategoryGroups()
        assertEquals(listOf("everyday", "income"), groupsAfter.map { it.id })
        assertFalse(groupsAfter.any { it.id == "bills" })

        val everyday = groupsAfter.first { it.id == "everyday" }
        assertEquals(listOf("groceries", "rent", "power"), everyday.categories.map { it.id })
        assertEquals(listOf("everyday", "everyday", "everyday"), everyday.categories.map { it.groupId })

        // Sort orders must strictly increase
        val orders = everyday.categories.map { it.sortOrder }
        assertTrue(orders[0] < orders[1])
        assertTrue(orders[1] < orders[2])

        val messages = database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString())
        assertTrue(messages.any { it.dataset == "category_groups" && it.row == "bills" && it.column == "tombstone" })
        assertTrue(messages.any { it.dataset == "categories" && it.row == "rent" && it.column == "cat_group" })
        assertTrue(messages.any { it.dataset == "categories" && it.row == "power" && it.column == "cat_group" })
        assertTrue(messages.any { it.dataset == "categories" && it.row == "rent" && it.column == "sort_order" })
        assertTrue(messages.any { it.dataset == "categories" && it.row == "power" && it.column == "sort_order" })
    }

    @Test
    fun deletingEmptyCategoryGroupTombstonesItWithoutAffectingTarget() = withDatabase { database, writer ->
        writer.deleteCategoryGroup("empty", targetGroupId = "everyday")

        val groups = database.fetchCategoryGroups()
        assertFalse(groups.any { it.id == "empty" })
        val everyday = groups.first { it.id == "everyday" }
        assertEquals(listOf("groceries"), everyday.categories.map { it.id })

        val messages = database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString())
        assertTrue(messages.any { it.dataset == "category_groups" && it.row == "empty" && it.column == "tombstone" })
    }

    @Test
    fun cannotDeleteIncomeCategoryGroup() = withDatabase { _, writer ->
        val error = assertThrows(IllegalArgumentException::class.java) {
            writer.deleteCategoryGroup("income", targetGroupId = "everyday")
        }
        assertEquals("The income category group cannot be deleted", error.message)
    }

    @Test
    fun cannotTargetIncomeCategoryGroup() = withDatabase { _, writer ->
        val error = assertThrows(IllegalArgumentException::class.java) {
            writer.deleteCategoryGroup("bills", targetGroupId = "income")
        }
        assertEquals("Cannot move categories to the income category group", error.message)
    }

    @Test
    fun cannotTargetSameCategoryGroup() = withDatabase { _, writer ->
        val error = assertThrows(IllegalArgumentException::class.java) {
            writer.deleteCategoryGroup("bills", targetGroupId = "bills")
        }
        assertEquals("Source and target category groups must be different", error.message)
    }

    @Test
    fun duplicateCategoryNameInTargetGroupIsRejected() = withDatabase { database, writer ->
        // Create a duplicate category name in everyday group
        writer.createCategory(name = "Rent", groupId = "everyday")

        assertThrows(IllegalArgumentException::class.java) {
            writer.deleteCategoryGroup("bills", targetGroupId = "everyday")
        }

        // Verify bills group still exists
        assertTrue(database.fetchCategoryGroups().any { it.id == "bills" })
    }

    private fun withDatabase(block: (ActualBudgetDatabase, ActualEntityWriter) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "category-group-delete-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, type TEXT, offbudget INTEGER, closed INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, cat_group TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)")
            db.execSQL("CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, transfer_acct TEXT, tombstone INTEGER)")
            db.execSQL("CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT)")
            db.execSQL("CREATE TABLE zero_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER, carryover INTEGER)")
            db.execSQL("CREATE TABLE reflect_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER, carryover INTEGER)")
            db.execSQL("CREATE TABLE zero_budget_months (id TEXT PRIMARY KEY, buffered INTEGER)")
            db.execSQL("CREATE TABLE transactions (id TEXT PRIMARY KEY, isParent INTEGER, isChild INTEGER, parent_id TEXT, acct TEXT, category TEXT, amount INTEGER, date INTEGER, notes TEXT, imported_description TEXT, tombstone INTEGER, cleared INTEGER, error TEXT)")
            db.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)")
            db.execSQL("CREATE TABLE preferences (id TEXT PRIMARY KEY, value TEXT)")

            db.execSQL("INSERT INTO payees VALUES ('store','Store',NULL,0)")
            db.execSQL("INSERT INTO payee_mapping VALUES ('store','store')")

            db.execSQL(
                """INSERT INTO category_groups (id, name, is_income, hidden, tombstone, sort_order) VALUES
                    ('bills', 'Bills', 0, 0, 0, 100.0),
                    ('everyday', 'Everyday', 0, 0, 0, 200.0),
                    ('empty', 'Empty', 0, 0, 0, 300.0),
                    ('income', 'Income', 1, 0, 0, 400.0)""",
            )
            db.execSQL(
                """INSERT INTO categories (id, name, cat_group, is_income, hidden, tombstone, sort_order) VALUES
                    ('rent', 'Rent', 'bills', 0, 0, 0, 100.0),
                    ('power', 'Power', 'bills', 0, 0, 0, 200.0),
                    ('groceries', 'Groceries', 'everyday', 0, 0, 0, 100.0),
                    ('salary', 'Salary', 'income', 1, 0, 0, 100.0)""",
            )
            db.execSQL(
                """INSERT INTO category_mapping VALUES
                    ('rent','rent'), ('power','power'), ('groceries','groceries'), ('salary','salary')""",
            )
        }
        val database = ActualBudgetDatabase.open(file)
        val writer = ActualEntityWriter(database, nodeId = "0000000000000000")
        try {
            block(database, writer)
        } finally {
            database.close()
            file.delete()
        }
    }
}
