package com.azimulkabir.actua.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.model.TransactionStatusFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Exporting transactions to CSV (#1335, #1336) reads the live rows and never writes. */
class ActuaRepositoryAccountCsvExportTest {
    @Test
    fun exportsLiveRowsOfOneAccountInActualsLayout() = withSeededRepository { repository, rowCount ->
        val out = StringBuilder()
        val written = repository.exportAccountTransactionsCsv("checking", out)

        assertEquals(
            listOf(
                "Account,Date,Payee,Notes,Category_Group,Category,Amount,Split_Amount,Cleared",
                "Checking,2026-03-20,Market,(SPLIT INTO 2) ,,,0.00,-30.00,Not cleared",
                "Checking,2026-03-20,Market,(SPLIT 1 OF 2) ,Usual Expenses,Food,-20.00,0.00,Not cleared",
                "Checking,2026-03-20,Market,(SPLIT 2 OF 2) ,Usual Expenses,Household,-10.00,0.00,Not cleared",
                "Checking,2026-03-15,Corner Shop,\"milk, eggs\",Usual Expenses,Food,-12.50,0.00,Cleared",
                "Checking,2026-03-10,Savings,,,,-1000.00,0.00,Not cleared",
                "Checking,2026-03-01,Corner Shop,,Usual Expenses,Food,-5.00,0.00,Reconciled",
            ),
            out.toString().split("\r\n").dropLast(1),
        )
        // Transactions, not rows: the split parent's two lines belong to it.
        assertEquals(4, written)
        assertEquals("export must not write", 9, rowCount())
    }

    @Test
    fun otherAccountsAndDeletedRowsAreExcluded() = withSeededRepository { repository, _ ->
        val out = StringBuilder()
        repository.exportAccountTransactionsCsv("savings", out)

        val rows = out.toString().split("\r\n").dropLast(1)
        assertEquals(
            listOf(
                "Account,Date,Payee,Notes,Category_Group,Category,Amount,Split_Amount,Cleared",
                "Savings,2026-03-12,Corner Shop,,Usual Expenses,Food,7.00,0.00,Not cleared",
                "Savings,2026-03-10,Checking,,,,1000.00,0.00,Not cleared",
            ),
            rows,
        )
    }

    @Test
    fun exportsExactlyTheGivenIdsInTheGivenOrderAcrossAccounts() = withSeededRepository { repository, rowCount ->
        val out = StringBuilder()
        // What a filtered register shows, plus ids that must be skipped: a deleted row, a split
        // child (never a list row) and one that doesn't exist.
        val written = repository.exportTransactionsCsv(listOf("t2", "s1", "sp", "gone", "c1", "nope"), out)

        assertEquals(
            listOf(
                HEADER,
                "Checking,2026-03-01,Corner Shop,,Usual Expenses,Food,-5.00,0.00,Reconciled",
                "Savings,2026-03-12,Corner Shop,,Usual Expenses,Food,7.00,0.00,Not cleared",
                "Checking,2026-03-20,Market,(SPLIT INTO 2) ,,,0.00,-30.00,Not cleared",
                "Checking,2026-03-20,Market,(SPLIT 1 OF 2) ,Usual Expenses,Food,-20.00,0.00,Not cleared",
                "Checking,2026-03-20,Market,(SPLIT 2 OF 2) ,Usual Expenses,Household,-10.00,0.00,Not cleared",
            ),
            out.toString().split("\r\n").dropLast(1),
        )
        assertEquals(3, written)
        assertEquals("export must not write", 9, rowCount())
    }

    @Test
    fun anEmptyIdListWritesOnlyTheHeader() = withSeededRepository { repository, _ ->
        val out = StringBuilder()
        assertEquals(0, repository.exportTransactionsCsv(emptyList(), out))
        assertEquals(listOf(HEADER), out.toString().split("\r\n").dropLast(1))
    }

    @Test
    fun aLongIdListIsExportedInFullDespiteSqliteBindLimits() = withSeededRepository(extraSeed = ::seedBulk) { repository, _ ->
        // More ids than one SQLite batch (500) and than the 999 bind-variable limit of older Android.
        val ids = (0 until 120).map { "b%03d".format(it) } + List(1_100) { "missing-$it" }
        val out = StringBuilder()
        assertEquals(120, repository.exportTransactionsCsv(ids, out))
        assertEquals(121, out.toString().split("\r\n").dropLast(1).size)
    }

    @Test
    fun searchExportIncludesEveryMatchNotJustOnePage() = withSeededRepository(extraSeed = ::seedBulk) { repository, _ ->
        // The on-screen search pages 50 at a time (it asks for 51 to know whether there is more).
        assertEquals(51, repository.transactions("Bulk Store", limit = 51, offset = 0).size)

        val out = StringBuilder()
        val written = repository.exportSearchTransactionsCsv("Bulk Store", TransactionStatusFilter.ALL, out)

        assertEquals(120, written)
        assertEquals(121, out.toString().split("\r\n").dropLast(1).size)
        assertEquals(repository.transactions("Bulk Store").size, written)
    }

    @Test
    fun searchExportHonoursTheStatusFilterLikeTheScreen() = withSeededRepository(extraSeed = ::seedBulk) { repository, _ ->
        fun exported(filter: TransactionStatusFilter) =
            repository.exportSearchTransactionsCsv("Bulk Store", filter, StringBuilder())

        assertEquals(30, exported(TransactionStatusFilter.CLEARED))
        assertEquals(90, exported(TransactionStatusFilter.UNCLEARED))
        assertEquals(30, repository.transactions("Bulk Store", statusFilter = TransactionStatusFilter.CLEARED).size)
    }

    @Test
    fun searchExportRejectsABlankTerm() = withSeededRepository { repository, _ ->
        val failure = runCatching { repository.exportSearchTransactionsCsv("  ", TransactionStatusFilter.ALL, StringBuilder()) }
            .exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    /** 120 transactions for payee "Bulk Store", the first 30 cleared. */
    private fun seedBulk(db: SQLiteDatabase) {
        db.execSQL("INSERT INTO payees (id, name, transfer_acct, tombstone) VALUES ('bulk', 'Bulk Store', NULL, 0)")
        db.execSQL("INSERT INTO payee_mapping (id, targetId) VALUES ('bulk', 'bulk')")
        for (i in 0 until 120) {
            db.execSQL(
                """INSERT INTO transactions
                       (id, isParent, isChild, parent_id, acct, category, description, amount, date, notes, cleared, reconciled, sort_order, tombstone)
                   VALUES ('b%03d', 0, 0, NULL, 'checking', 'food', 'bulk', -100, 20260201, NULL, %d, 0, %d, 0)"""
                    .format(i, if (i < 30) 1 else 0, i),
            )
        }
    }

    private companion object {
        const val HEADER = "Account,Date,Payee,Notes,Category_Group,Category,Amount,Split_Amount,Cleared"
    }

    private fun withSeededRepository(extraSeed: (SQLiteDatabase) -> Unit = {}, block: (ActuaRepository, () -> Int) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Account CSV ${UUID.randomUUID()}")
        val previousBudget = ActiveBudgetStore(context).budgetId
        val path = files.databaseFile(budget.id).path
        try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    """INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type)
                       VALUES ('checking', 'Checking', 0, 0, 0, 1, 'checking'),
                              ('savings', 'Savings', 0, 0, 0, 2, 'savings')""",
                )
                db.execSQL("INSERT INTO category_groups (id, name, is_income, hidden, tombstone, sort_order) VALUES ('usual', 'Usual Expenses', 0, 0, 0, 1)")
                db.execSQL(
                    """INSERT INTO categories (id, name, cat_group, is_income, hidden, tombstone, sort_order)
                       VALUES ('food', 'Food', 'usual', 0, 0, 0, 1), ('household', 'Household', 'usual', 0, 0, 0, 2)""",
                )
                db.execSQL("INSERT INTO category_mapping (id, transferId) VALUES ('food', 'food'), ('household', 'household')")
                db.execSQL(
                    """INSERT INTO payees (id, name, transfer_acct, tombstone)
                       VALUES ('shop', 'Corner Shop', NULL, 0), ('market', 'Market', NULL, 0),
                              ('to-savings', '', 'savings', 0), ('to-checking', '', 'checking', 0)""",
                )
                db.execSQL("INSERT INTO payee_mapping (id, targetId) VALUES ('shop', 'shop'), ('market', 'market'), ('to-savings', 'to-savings'), ('to-checking', 'to-checking')")
                db.execSQL(
                    """INSERT INTO transactions
                           (id, isParent, isChild, parent_id, acct, category, description, amount, date, notes, cleared, reconciled, sort_order, tombstone)
                       VALUES
                           ('t1', 0, 0, NULL, 'checking', 'food', 'shop', -1250, 20260315, 'milk, eggs', 1, 0, 1, 0),
                           ('t2', 0, 0, NULL, 'checking', 'food', 'shop', -500, 20260301, NULL, 1, 1, 1, 0),
                           ('gone', 0, 0, NULL, 'checking', 'food', 'shop', -999, 20260316, 'deleted', 0, 0, 1, 1),
                           ('sp', 1, 0, NULL, 'checking', NULL, 'market', -3000, 20260320, NULL, 0, 0, 1, 0),
                           ('c1', 0, 1, 'sp', 'checking', 'food', 'market', -2000, 20260320, NULL, 0, 0, 2, 0),
                           ('c2', 0, 1, 'sp', 'checking', 'household', 'market', -1000, 20260320, NULL, 0, 0, 1, 0),
                           ('x1', 0, 0, NULL, 'checking', NULL, 'to-savings', -100000, 20260310, NULL, 0, 0, 1, 0),
                           ('x2', 0, 0, NULL, 'savings', NULL, 'to-checking', 100000, 20260310, NULL, 0, 0, 1, 0),
                           ('s1', 0, 0, NULL, 'savings', 'food', 'shop', 700, 20260312, NULL, 0, 0, 1, 0)""",
                )
                // Link the transfer legs the way Actual does so the register treats them as one transfer.
                db.execSQL("UPDATE transactions SET transferred_id = 'x2' WHERE id = 'x1'")
                db.execSQL("UPDATE transactions SET transferred_id = 'x1' WHERE id = 'x2'")
                extraSeed(db)
            }
            ActiveBudgetStore(context).budgetId = budget.id
            val repository = ActuaRepository(context)
            try {
                block(repository) {
                    SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        db.rawQuery("SELECT COUNT(*) FROM transactions", null).use { it.moveToFirst(); it.getInt(0) }
                    }
                }
            } finally {
                repository.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = previousBudget
            runCatching { files.deleteBudget(budget.id) }
        }
    }
}
