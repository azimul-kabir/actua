package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.HlcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** `transactions-merge` through SQLite and the CRDT message log (loot-core `merge.ts` at 59fe126f). */
class TransactionMergeWriterTest {
    @Test
    fun mergingAManualRowIntoABankImportKeepsTheImportInOneBatch() = withDatabase { database ->
        val writer = ActualTransactionWriter(database)
        val before = messages(database).size

        val kept = writer.mergeTransactions("manual", "imported")

        assertEquals("imported", kept)
        assertNull(database.fetchTransaction("manual"))
        val merged = requireNotNull(database.fetchTransaction("imported"))
        assertEquals("grocery", merged.categoryId)
        assertEquals("store", merged.payeeId)
        assertEquals("weekly shop", merged.notes)
        assertEquals("bank-1", merged.financialId)
        assertEquals(-1000L, merged.amountCents)
        val written = messages(database).drop(before).map { it.row to it.column }.toSet()
        assertEquals(
            setOf(
                "imported" to "description", "imported" to "category", "imported" to "notes",
                "manual" to "tombstone",
            ),
            written,
        )
    }

    @Test
    fun anInvalidPairWritesNothing() = withDatabase { database ->
        val writer = ActualTransactionWriter(database)
        val before = messages(database).size

        assertThrows(IllegalArgumentException::class.java) { writer.mergeTransactions("manual", "transfer-out") }
        assertThrows(IllegalArgumentException::class.java) { writer.mergeTransactions("manual", "transfer-in") }

        assertEquals(before, messages(database).size)
        assertTrue(database.fetchTransaction("manual") != null)
    }

    @Test
    fun mergingAnOrdinaryRowWithATransferKeepsTheTransferLink() = withDatabase { database ->
        val writer = ActualTransactionWriter(database)

        val kept = writer.mergeTransactions("transfer-out", "imported-2")

        assertEquals("imported-2", kept)
        assertNull(database.fetchTransaction("transfer-out"))
        val merged = requireNotNull(database.fetchTransaction("imported-2"))
        assertEquals("transfer-in", merged.transferId)
        assertEquals("transfer-savings", merged.payeeId)
        assertNull(merged.categoryId)
        val otherLeg = requireNotNull(database.fetchTransaction("transfer-in"))
        assertEquals("imported-2", otherLeg.transferId)
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
        val file = File(context.cacheDir, "transaction-merge-${UUID.randomUUID()}.sqlite")
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
            db.execSQL("INSERT INTO categories VALUES ('grocery','Groceries','essential',0,0,0,1)")
            db.execSQL("INSERT INTO category_mapping VALUES ('grocery','grocery')")
            db.execSQL("INSERT INTO payees VALUES ('store','Store',NULL,0), ('transfer-savings','','savings',0), ('transfer-checking','','checking',0)")
            db.execSQL("INSERT INTO payee_mapping VALUES ('store','store'), ('transfer-savings','transfer-savings'), ('transfer-checking','transfer-checking')")

            insert(db, "manual", "checking", "grocery", -1000, "store", 20260901, notes = "weekly shop")
            insert(db, "imported", "checking", null, -1000, null, 20260902, financialId = "bank-1")
            insert(db, "transfer-out", "checking", null, -2500, "transfer-savings", 20260903, transfer = "transfer-in")
            insert(db, "transfer-in", "savings", null, 2500, "transfer-checking", 20260903, transfer = "transfer-out")
            insert(db, "imported-2", "checking", "grocery", -2500, null, 20260904, financialId = "bank-2")
        }
        return file
    }

    private fun insert(
        db: SQLiteDatabase, id: String, account: String, category: String?, amount: Int, payee: String?, date: Int,
        transfer: String? = null, notes: String? = null, financialId: String? = null,
    ) {
        db.execSQL(
            "INSERT INTO transactions VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(id, 0, 0, account, category, amount, payee, notes, date, null, transfer, 0, 0, 1.0, 0, null, financialId, 0, null),
        )
    }
}
