package com.azimulkabir.actua.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Exporting an account register to CSV (#1335) reads the live rows and never writes. */
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

    private fun withSeededRepository(block: (ActuaRepository, () -> Int) -> Unit) {
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
