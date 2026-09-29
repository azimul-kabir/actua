package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.HlcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Removing a transfer takes its counterpart with it, like loot-core's `removeTransfer`
 * (`server/transactions/transfer.ts` at 59fe126f): delete, bulk delete, transfer → expense,
 * dropped split lines and split collapse.
 */
class TransferRemovalTest {
    @Test
    fun deletingATransferLegTombstonesTheOtherLegInOneBatch() = withDatabase { database ->
        val writer = ActualTransactionWriter(database)
        val before = messages(database).size

        writer.deleteTransaction(requireNotNull(database.fetchTransaction("transfer-out")))

        assertNull(database.fetchTransaction("transfer-out"))
        assertNull(database.fetchTransaction("transfer-in"))
        val written = messages(database).drop(before)
        assertEquals(
            setOf("transfer-out" to "tombstone", "transfer-in" to "tombstone", "transfer-out" to "transferred_id"),
            written.map { it.row to it.column }.toSet(),
        )
        assertEquals(3, written.size)
        assertEquals("0:", written.single { it.column == "transferred_id" }.value)
        assertTrue(written.filter { it.column == "tombstone" }.all { it.value == "N:1" })
    }

    @Test
    fun bulkDeletingBothLegsTombstonesEachLegOnce() = withDatabase { database ->
        val writer = ActualTransactionWriter(database)
        val before = messages(database).size

        writer.deleteTransactions(listOf("transfer-out", "transfer-in").map { requireNotNull(database.fetchTransaction(it)) })

        assertNull(database.fetchTransaction("transfer-out"))
        assertNull(database.fetchTransaction("transfer-in"))
        val tombstones = messages(database).drop(before).filter { it.column == "tombstone" }.map { it.row }
        assertEquals(listOf("transfer-in", "transfer-out"), tombstones.sorted())
    }

    @Test
    fun deletingALegWhoseCounterpartIsASplitChildUnlinksTheChild() = withDatabase { database ->
        val writer = ActualTransactionWriter(database)

        writer.deleteTransaction(requireNotNull(database.fetchTransaction("savings-leg")))

        assertNull(database.fetchTransaction("savings-leg"))
        val child = database.fetchChildTransactions("split-parent").single { it.id == "split-transfer" }
        assertNull(child.transferId)
        assertNull(child.payeeId)
        assertEquals(-400L, child.amountCents)
        assertTrue(database.fetchTransaction("split-parent")?.isParent == true)
    }

    @Test
    fun deletingAnOrdinaryTransactionWritesOnlyItsTombstone() = withDatabase { database ->
        val writer = ActualTransactionWriter(database)
        val before = messages(database).size

        writer.deleteTransaction(requireNotNull(database.fetchTransaction("ordinary")))

        val written = messages(database).drop(before)
        assertEquals(listOf("ordinary" to "tombstone"), written.map { it.row to it.column })
    }

    @Test
    fun savingATransferAsAnExpenseRemovesTheOtherLeg() = withDatabase { database ->
        val service = formService(database)
        val original = requireNotNull(database.fetchTransaction("transfer-out"))

        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", categoryId = "grocery", date = 20260902,
        ), original)

        val edited = requireNotNull(database.fetchTransaction("transfer-out"))
        assertNull(edited.transferId)
        assertEquals("store", edited.payeeId)
        assertEquals("grocery", edited.categoryId)
        assertNull(database.fetchTransaction("transfer-in"))
        assertEquals("0:", messages(database).last { it.row == "transfer-out" && it.column == "transferred_id" }.value)
    }

    @Test
    fun droppingASplitLineRemovesItsTransferCounterpart() = withDatabase { database ->
        val service = formService(database)
        val parent = requireNotNull(database.fetchTransaction("split-parent"))
        val kept = database.fetchChildTransactions(parent.id).first { it.id == "split-plain" }

        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", date = 20260903,
            splits = listOf(
                ActualSplitLineForm(childId = kept.id, categoryId = "grocery", amount = "6"),
                ActualSplitLineForm(categoryId = "rent", amount = "4"),
            ),
        ), parent)
        assertNull(database.fetchTransaction("savings-leg"))
        assertTrue(database.fetchChildTransactions(parent.id).none { it.id == "split-transfer" })
    }

    @Test
    fun collapsingASplitRemovesItsTransferChildCounterparts() = withDatabase { database ->
        val service = formService(database)

        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", categoryId = "grocery", date = 20260903, collapseSplit = true,
        ), requireNotNull(database.fetchTransaction("split-parent")))

        val collapsed = requireNotNull(database.fetchTransaction("split-parent"))
        assertTrue(!collapsed.isParent)
        assertTrue(database.fetchChildTransactions("split-parent").isEmpty())
        assertNull(database.fetchTransaction("savings-leg"))
    }

    private fun formService(database: ActualBudgetDatabase): ActualTransactionFormService {
        var next = 0
        val ids = { "removal-${++next}" }
        return ActualTransactionFormService(database, ActualTransactionWriter(database, idFactory = ids), idFactory = ids)
    }

    private fun messages(database: ActualBudgetDatabase): List<CrdtMessage> =
        database.getMessagesSince(HlcTimestamp.ZERO.toString()).filter { it.dataset == "transactions" }

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
        val file = File(context.cacheDir, "transfer-removal-${UUID.randomUUID()}.sqlite")
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
            db.execSQL("CREATE TABLE rules (id TEXT PRIMARY KEY, stage TEXT, conditions_op TEXT, conditions TEXT, actions TEXT, tombstone INTEGER)")
            db.execSQL("CREATE TABLE schedules (id TEXT PRIMARY KEY, rule TEXT, name TEXT, posts_transaction INTEGER, completed INTEGER, custom_upcoming_length TEXT, tombstone INTEGER)")
            db.execSQL("CREATE TABLE schedules_next_date (id TEXT PRIMARY KEY, schedule_id TEXT, local_next_date INTEGER, local_next_date_ts INTEGER, base_next_date INTEGER, base_next_date_ts INTEGER)")

            db.execSQL("INSERT INTO accounts VALUES ('checking','Checking','checking',0,0,0,1), ('savings','Savings','savings',0,0,0,2)")
            db.execSQL("INSERT INTO category_groups VALUES ('essential','Essentials',0,0,0,1)")
            db.execSQL("INSERT INTO categories VALUES ('grocery','Groceries','essential',0,0,0,1), ('rent','Rent','essential',0,0,0,2)")
            db.execSQL("INSERT INTO category_mapping VALUES ('grocery','grocery'), ('rent','rent')")
            db.execSQL("INSERT INTO payees VALUES ('store','Store',NULL,0), ('transfer-savings','','savings',0), ('transfer-checking','','checking',0)")
            db.execSQL("INSERT INTO payee_mapping VALUES ('store','store'), ('transfer-savings','transfer-savings'), ('transfer-checking','transfer-checking')")

            insert(db, "ordinary", 0, 0, "checking", "grocery", -1000, "store", 20260901, 1.0)
            insert(db, "transfer-out", 0, 0, "checking", null, -1000, "transfer-savings", 20260902, 2.0, transfer = "transfer-in")
            insert(db, "transfer-in", 0, 0, "savings", null, 1000, "transfer-checking", 20260902, 2.0, transfer = "transfer-out")
            // A split whose second child is a transfer to savings (Actual keeps the parent out of the pair).
            insert(db, "split-parent", 1, 0, "checking", null, -1000, "store", 20260903, 3.0)
            insert(db, "split-plain", 0, 1, "checking", "grocery", -600, "store", 20260903, 2.0, parent = "split-parent")
            insert(db, "split-transfer", 0, 1, "checking", null, -400, "transfer-savings", 20260903, 1.0,
                transfer = "savings-leg", parent = "split-parent")
            insert(db, "savings-leg", 0, 0, "savings", null, 400, "transfer-checking", 20260903, 3.0, transfer = "split-transfer")
        }
        return file
    }

    private fun insert(
        db: SQLiteDatabase, id: String, parentFlag: Int, childFlag: Int, account: String, category: String?,
        amount: Int, payee: String?, date: Int, sort: Double, transfer: String? = null, parent: String? = null,
    ) {
        db.execSQL(
            "INSERT INTO transactions VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(id, parentFlag, childFlag, account, category, amount, payee, null, date, null, transfer, 0, 0, sort, 0, parent, null, 0, null),
        )
    }
}
