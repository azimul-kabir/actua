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

/** Actual's `must-category-transfer` and `category-delete` with a transfer (actua#926). */
class CategoryDeleteTransferTest {
    @Test
    fun aCategoryNeedsATransferWhenTransactionsOrABudgetAmountUseIt() = withDatabase { database, _ ->
        // Transactions in the category, one of them through a merged category's mapping.
        assertTrue(database.categoryDeleteRequiresTransfer("grocery"))
        // Only a budget amount.
        assertTrue(database.categoryDeleteRequiresTransfer("budgeted"))
        // A zero budget row and no transactions; no rows at all.
        assertFalse(database.categoryDeleteRequiresTransfer("spare"))
        assertFalse(database.categoryDeleteRequiresTransfer("salary"))
        // The merged category's transactions belong to grocery now, not to it.
        assertFalse(database.categoryDeleteRequiresTransfer("dining"))
    }

    @Test
    fun deletingWithATransferMovesBudgetsAndForwardsEveryMapping() = withDatabase { database, writer ->
        writer.deleteCategory("grocery", transferId = "rent")

        assertTrue(database.fetchCategoryGroups().flatMap { it.categories }.none { it.id == "grocery" })
        // Budgets: September adds onto rent's row; August creates rent's row from grocery's amount.
        assertEquals(6_000L, database.budgetCell("2026-09", "rent")!!.amountCents)
        assertEquals(2_000L, database.budgetCell("2026-08", "rent")!!.amountCents)
        assertEquals(0L, database.budgetCell("2026-07", "rent")!!.amountCents)
        // The category's own mapping and the one left by an earlier merge both point at rent.
        assertEquals(listOf("dining", "grocery", "rent"), database.categoryMappingsTo("rent"))
        assertEquals("rent", database.fetchTransaction("in-grocery")!!.categoryId)
        assertEquals("rent", database.fetchTransaction("in-dining")!!.categoryId)

        val messages = database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString())
        assertTrue(messages.any { it.dataset == "category_mapping" && it.row == "dining" && it.column == "transferId" })
        assertTrue(messages.any { it.dataset == "zero_budgets" && it.row == "202608-rent" && it.column == "category" })
        assertTrue(messages.any { it.dataset == "categories" && it.row == "grocery" && it.column == "tombstone" })
    }

    @Test
    fun anIncomeCategoryTransfersItsMappingButNoBudget() = withDatabase { database, writer ->
        writer.deleteCategory("salary", transferId = "bonus")

        assertEquals(listOf("bonus", "salary"), database.categoryMappingsTo("bonus"))
        val messages = database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString())
        assertTrue(messages.none { it.dataset == "zero_budgets" })
    }

    @Test
    fun aTransferBetweenIncomeAndExpenseIsRejected() = withDatabase { database, writer ->
        assertThrows(IllegalArgumentException::class.java) { writer.deleteCategory("grocery", transferId = "salary") }
        assertThrows(IllegalArgumentException::class.java) { writer.deleteCategory("grocery", transferId = "grocery") }
        assertTrue(database.fetchCategoryGroups().flatMap { it.categories }.any { it.id == "grocery" })
    }

    private fun withDatabase(block: (ActualBudgetDatabase, ActualEntityWriter) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "category-delete-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, type TEXT, offbudget INTEGER, closed INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, cat_group TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)")
            db.execSQL("CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, transfer_acct TEXT, tombstone INTEGER)")
            db.execSQL("CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT)")
            db.execSQL("CREATE TABLE transactions (id TEXT PRIMARY KEY, isParent INTEGER, isChild INTEGER, acct TEXT, category TEXT, amount INTEGER, description TEXT, notes TEXT, date INTEGER, imported_description TEXT, transferred_id TEXT, cleared INTEGER, reconciled INTEGER, sort_order REAL, tombstone INTEGER, parent_id TEXT, financial_id TEXT, pending INTEGER DEFAULT 0, raw_synced_data TEXT)")
            db.execSQL("CREATE TABLE zero_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER, carryover INTEGER)")
            db.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)")
            db.execSQL("CREATE TABLE preferences (id TEXT PRIMARY KEY, value TEXT)")

            db.execSQL("INSERT INTO accounts VALUES ('checking','Checking','checking',0,0,0,1)")
            db.execSQL("INSERT INTO category_groups VALUES ('essential','Essentials',0,0,0,1), ('income','Income',1,0,0,2)")
            db.execSQL(
                """INSERT INTO categories VALUES ('grocery','Groceries','essential',0,0,0,1), ('rent','Rent','essential',0,0,0,2),
                    ('budgeted','Budgeted','essential',0,0,0,3), ('salary','Salary','income',1,0,0,4), ('bonus','Bonus','income',1,0,0,5),
                    ('dining','Dining','essential',0,0,1,6), ('spare','Spare','essential',0,0,0,7)""",
            )
            // "dining" was merged into grocery earlier, so its transactions already read as grocery.
            db.execSQL(
                """INSERT INTO category_mapping VALUES ('grocery','grocery'), ('rent','rent'), ('budgeted','budgeted'),
                    ('salary','salary'), ('bonus','bonus'), ('dining','grocery'), ('spare','spare')""",
            )
            db.execSQL("INSERT INTO payees VALUES ('store','Store',NULL,0)")
            db.execSQL("INSERT INTO payee_mapping VALUES ('store','store')")
            db.execSQL(
                """INSERT INTO zero_budgets VALUES ('202609-grocery',202609,'grocery',5000,0), ('202609-rent',202609,'rent',1000,0),
                    ('202608-grocery',202608,'grocery',2000,0), ('202609-budgeted',202609,'budgeted',700,0),
                    ('202610-spare',202610,'spare',0,0)""",
            )
            insertTransaction(db, "in-grocery", "grocery", 20260901)
            insertTransaction(db, "in-dining", "dining", 20260902)
        }
        try {
            ActualBudgetDatabase.open(file).use { database ->
                block(database, ActualEntityWriter(database, nodeId = "eeeeeeeeeeeeeeee"))
            }
        } finally {
            file.delete()
        }
    }

    private fun insertTransaction(db: SQLiteDatabase, id: String, category: String, date: Int) {
        db.execSQL(
            """INSERT INTO transactions (id, isParent, isChild, acct, category, amount, description, notes, date,
                imported_description, transferred_id, cleared, reconciled, sort_order, tombstone, parent_id,
                financial_id, pending, raw_synced_data) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            arrayOf<Any?>(id, 0, 0, "checking", category, -1000, "store", null, date, null, null, 0, 0, 1.0, 0, null, null, 0, null),
        )
    }
}
