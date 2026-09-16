package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.UUID

/** Actual's "Hold for next month" / "Reset next month's buffer" via zero_budget_months.buffered. */
class ActualBudgetBufferTest {
    @Test
    fun holdingForNextMonthReducesThisMonthAndCarriesIntoTheNext() = withDatabase { database ->
        val before = database.fetchBudgetMonth("2026-08")
        assertEquals(4_000L, before.toBudgetCents)
        assertEquals(0L, before.bufferedCents)

        ActualBudgetWriter(database, "aaaaaaaaaaaaaaaa").setBuffered("2026-08", 2_000)

        val august = database.fetchBudgetMonth("2026-08")
        assertEquals(2_000L, august.toBudgetCents)
        assertEquals(2_000L, august.bufferedCents)

        val september = database.fetchBudgetMonth("2026-09")
        assertEquals(4_000L, september.toBudgetCents)
        assertEquals(0L, september.bufferedCents)
    }

    @Test
    fun resettingTheBufferReturnsTheFullAmountToThisMonth() = withDatabase { database ->
        val writer = ActualBudgetWriter(database, "bbbbbbbbbbbbbbbb")
        writer.setBuffered("2026-08", 2_000)
        assertEquals(2_000L, database.fetchBudgetMonth("2026-08").bufferedCents)

        writer.resetBuffer("2026-08")

        val august = database.fetchBudgetMonth("2026-08")
        assertEquals(0L, august.bufferedCents)
        assertEquals(4_000L, august.toBudgetCents)
    }

    private fun withDatabase(block: (ActualBudgetDatabase) -> Unit) {
        val file = createDatabaseFile()
        try {
            ActualBudgetDatabase.open(file).use(block)
        } finally {
            file.delete()
        }
    }

    private fun createDatabaseFile(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "buffer-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, type TEXT, offbudget INTEGER, closed INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, cat_group TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)")
            db.execSQL("CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, transfer_acct TEXT, tombstone INTEGER)")
            db.execSQL("CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT)")
            db.execSQL("CREATE TABLE transactions (id TEXT PRIMARY KEY, isParent INTEGER, isChild INTEGER, acct TEXT, category TEXT, amount INTEGER, description TEXT, notes TEXT, date INTEGER, imported_description TEXT, transferred_id TEXT, cleared INTEGER, reconciled INTEGER, sort_order REAL, tombstone INTEGER, parent_id TEXT)")
            db.execSQL("CREATE TABLE zero_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER, carryover INTEGER, goal INTEGER, long_goal INTEGER)")
            db.execSQL("CREATE TABLE zero_budget_months (id TEXT PRIMARY KEY, buffered INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)")

            db.execSQL("INSERT INTO accounts VALUES ('checking','Checking','checking',0,0,0,1)")
            db.execSQL("INSERT INTO category_groups VALUES ('income','Income',1,0,0,1), ('essential','Essentials',0,0,0,2)")
            db.execSQL("INSERT INTO categories VALUES ('salary','Salary','income',1,0,0,1), ('rent','Rent','essential',0,0,0,1)")
            db.execSQL("INSERT INTO category_mapping VALUES ('salary','salary'), ('rent','rent')")

            insertTransaction(db, "august-salary", "checking", "salary", 5_000, 20260805)
            insertTransaction(db, "august-rent", "checking", "rent", -1_000, 20260810)
            db.execSQL("INSERT INTO zero_budgets(id,month,category,amount,carryover,goal,long_goal) VALUES ('august-rent-budget',202608,'rent',1000,0,NULL,0)")
        }
        return file
    }

    private fun insertTransaction(db: SQLiteDatabase, id: String, account: String, category: String, amount: Int, date: Int) {
        db.execSQL(
            "INSERT INTO transactions VALUES (?,0,0,?,?,?,NULL,NULL,?,NULL,NULL,0,0,1.0,0,NULL)",
            arrayOf<Any?>(id, account, category, amount, date),
        )
    }
}
