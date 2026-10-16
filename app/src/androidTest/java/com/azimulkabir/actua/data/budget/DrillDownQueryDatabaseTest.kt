package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.model.DrillDownDirection
import com.azimulkabir.actua.model.DrillDownQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DrillDownQueryDatabaseTest {

    private fun withDatabase(block: (ActualBudgetDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "drill-down-${UUID.randomUUID()}.sqlite").also(BlankBudgetFactory::create)
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                // Accounts
                db.execSQL("INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type) VALUES ('chk', 'Checking', 0, 0, 0, 1.0, 'checking')")
                db.execSQL("INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type) VALUES ('sav', 'Savings', 0, 0, 0, 2.0, 'savings')")
                db.execSQL("INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type) VALUES ('inv', 'Investment', 1, 0, 0, 3.0, 'investment')")

                // Payees
                db.execSQL("INSERT INTO payees (id, name, tombstone) VALUES ('payee-salary', 'Employer', 0)")
                db.execSQL("INSERT INTO payees (id, name, tombstone) VALUES ('payee-market', 'Supermarket', 0)")
                db.execSQL("INSERT INTO payees (id, name, transfer_acct, tombstone) VALUES ('payee-transfer', 'Transfer to Savings', 'sav', 0)")

                // Categories
                db.execSQL("INSERT INTO category_groups (id, name, is_income, sort_order, tombstone) VALUES ('gi', 'Income', 1, 1.0, 0)")
                db.execSQL("INSERT INTO category_groups (id, name, is_income, sort_order, tombstone) VALUES ('ge', 'Expenses', 0, 2.0, 0)")
                db.execSQL("INSERT INTO categories (id, name, cat_group, is_income, sort_order, tombstone) VALUES ('cat-salary', 'Salary', 'gi', 1, 1.0, 0)")
                db.execSQL("INSERT INTO categories (id, name, cat_group, is_income, sort_order, tombstone) VALUES ('cat-groceries', 'Groceries', 'ge', 0, 2.0, 0)")
                db.execSQL("INSERT INTO categories (id, name, cat_group, is_income, sort_order, tombstone) VALUES ('cat-rent', 'Rent', 'ge', 0, 3.0, 0)")

                // Transactions
                // tx-salary: on-budget income
                db.execSQL("INSERT INTO transactions (id, acct, date, amount, description, category, tombstone, isParent, isChild, cleared, reconciled) " +
                    "VALUES ('tx-salary', 'chk', 20260601, 500000, 'payee-salary', 'cat-salary', 0, 0, 0, 1, 0)")
                // tx-groceries-1: on-budget expense
                db.execSQL("INSERT INTO transactions (id, acct, date, amount, description, category, tombstone, isParent, isChild, cleared, reconciled) " +
                    "VALUES ('tx-groceries-1', 'chk', 20260605, -50000, 'payee-market', 'cat-groceries', 0, 0, 0, 1, 0)")
                // tx-groceries-2: on-budget expense
                db.execSQL("INSERT INTO transactions (id, acct, date, amount, description, category, tombstone, isParent, isChild, cleared, reconciled) " +
                    "VALUES ('tx-groceries-2', 'chk', 20260610, -30000, 'payee-market', 'cat-groceries', 0, 0, 0, 1, 0)")
                // tx-rent: on-budget expense
                db.execSQL("INSERT INTO transactions (id, acct, date, amount, description, category, tombstone, isParent, isChild, cleared, reconciled) " +
                    "VALUES ('tx-rent', 'chk', 20260602, -120000, NULL, 'cat-rent', 0, 0, 0, 1, 0)")
                // tx-inv: off-budget transaction
                db.execSQL("INSERT INTO transactions (id, acct, date, amount, description, category, tombstone, isParent, isChild, cleared, reconciled) " +
                    "VALUES ('tx-inv', 'inv', 20260603, 200000, NULL, NULL, 0, 0, 0, 1, 0)")
                // Transfers
                db.execSQL("INSERT INTO transactions (id, acct, date, amount, description, category, transferred_id, tombstone, isParent, isChild, cleared, reconciled) " +
                    "VALUES ('tx-transfer-chk', 'chk', 20260612, -100000, 'payee-transfer', NULL, 'tx-transfer-sav', 0, 0, 0, 1, 0)")
                db.execSQL("INSERT INTO transactions (id, acct, date, amount, description, category, transferred_id, tombstone, isParent, isChild, cleared, reconciled) " +
                    "VALUES ('tx-transfer-sav', 'sav', 20260612, 100000, NULL, NULL, 'tx-transfer-chk', 0, 0, 0, 1, 0)")
                // Split parent and children
                db.execSQL("INSERT INTO transactions (id, acct, date, amount, description, category, tombstone, isParent, isChild, cleared, reconciled) " +
                    "VALUES ('tx-split-parent', 'chk', 20260615, -40000, 'payee-market', NULL, 0, 1, 0, 1, 0)")
                db.execSQL("INSERT INTO transactions (id, acct, date, amount, description, category, parent_id, tombstone, isParent, isChild, cleared, reconciled) " +
                    "VALUES ('tx-split-child-1', 'chk', 20260615, -25000, 'payee-market', 'cat-groceries', 'tx-split-parent', 0, 0, 1, 1, 0)")
                db.execSQL("INSERT INTO transactions (id, acct, date, amount, description, category, parent_id, tombstone, isParent, isChild, cleared, reconciled) " +
                    "VALUES ('tx-split-child-2', 'chk', 20260615, -15000, 'payee-market', 'cat-rent', 'tx-split-parent', 0, 0, 1, 1, 0)")
                // Tombstoned transaction
                db.execSQL("INSERT INTO transactions (id, acct, date, amount, description, category, tombstone, isParent, isChild, cleared, reconciled) " +
                    "VALUES ('tx-deleted', 'chk', 20260616, -99999, 'payee-market', 'cat-groceries', 1, 0, 0, 1, 0)")
            }

            ActualBudgetDatabase.open(file).use(block)
        } finally {
            file.delete()
        }
    }

    @Test
    fun dateRangeFiltersCorrectly() = withDatabase { db ->
        val query = DrillDownQuery(startDate = 20260604, endDate = 20260611)
        val results = db.fetchTransactionsForDrillDown(query)
        assertEquals(listOf("tx-groceries-2", "tx-groceries-1"), results.map { it.id })
    }

    @Test
    fun directionIncomeFiltersOnlyPositiveAmounts() = withDatabase { db ->
        val query = DrillDownQuery(
            direction = DrillDownDirection.INCOME,
            excludeTransfers = true,
            onBudgetOnly = true,
        )
        val results = db.fetchTransactionsForDrillDown(query)
        assertEquals(listOf("tx-salary"), results.map { it.id })
    }

    @Test
    fun directionExpenseFiltersOnlyNegativeAmounts() = withDatabase { db ->
        val query = DrillDownQuery(
            direction = DrillDownDirection.EXPENSE,
            startDate = 20260601,
            endDate = 20260610,
        )
        val results = db.fetchTransactionsForDrillDown(query)
        assertEquals(listOf("tx-groceries-2", "tx-groceries-1", "tx-rent"), results.map { it.id })
    }

    @Test
    fun categoryFilterIncludesSplitChildrenAndExcludesOtherCategories() = withDatabase { db ->
        val query = DrillDownQuery(categoryIds = setOf("cat-groceries"))
        val results = db.fetchTransactionsForDrillDown(query)
        // tx-split-child-1 is included, tx-split-parent is excluded, tx-deleted is excluded
        assertEquals(listOf("tx-split-child-1", "tx-groceries-2", "tx-groceries-1"), results.map { it.id })
    }

    @Test
    fun payeeFilterMatchesPayee() = withDatabase { db ->
        val query = DrillDownQuery(payeeId = "payee-salary")
        val results = db.fetchTransactionsForDrillDown(query)
        assertEquals(listOf("tx-salary"), results.map { it.id })
    }

    @Test
    fun excludeTransfersRemovesTransferLegs() = withDatabase { db ->
        val query = DrillDownQuery(excludeTransfers = true, accountIds = setOf("chk", "sav"))
        val results = db.fetchTransactionsForDrillDown(query)
        val ids = results.map { it.id }
        assertTrue("tx-transfer-chk should not be in $ids", "tx-transfer-chk" !in ids)
        assertTrue("tx-transfer-sav should not be in $ids", "tx-transfer-sav" !in ids)
    }

    @Test
    fun onBudgetOnlyExcludesOffBudgetAccounts() = withDatabase { db ->
        val query = DrillDownQuery(onBudgetOnly = true)
        val results = db.fetchTransactionsForDrillDown(query)
        val ids = results.map { it.id }
        assertTrue("tx-inv should not be in $ids", "tx-inv" !in ids)
    }

    @Test
    fun excludeIncomeCategoriesFiltersOutIncomeCategories() = withDatabase { db ->
        val query = DrillDownQuery(excludeIncomeCategories = true)
        val results = db.fetchTransactionsForDrillDown(query)
        val ids = results.map { it.id }
        assertTrue("tx-salary should not be in $ids", "tx-salary" !in ids)
    }

    @Test
    fun splitParentsAndTombstonesAreNeverReturned() = withDatabase { db ->
        val query = DrillDownQuery()
        val results = db.fetchTransactionsForDrillDown(query)
        val ids = results.map { it.id }
        assertTrue("tx-split-parent should not be in $ids", "tx-split-parent" !in ids)
        assertTrue("tx-deleted should not be in $ids", "tx-deleted" !in ids)
        assertTrue("tx-split-child-1 should be in $ids", "tx-split-child-1" in ids)
        assertTrue("tx-split-child-2 should be in $ids", "tx-split-child-2" in ids)
    }
}
