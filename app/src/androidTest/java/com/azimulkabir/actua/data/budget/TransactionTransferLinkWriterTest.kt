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

/** Desktop "Make transfer" through SQLite and the CRDT message log (`onSetTransfer` at 59fe126f). */
class TransactionTransferLinkWriterTest {
    @Test
    fun linkingTwoRowsWritesOnlyTheirPayeesLinksAndCategoriesInOneBatch() = withDatabase { database ->
        val writer = ActualTransactionWriter(database)
        val rowsBefore = database.transactionCount()
        val before = messages(database).size

        writer.linkAsTransfer("payment", "received")

        val payment = requireNotNull(database.fetchTransaction("payment"))
        val received = requireNotNull(database.fetchTransaction("received"))
        assertEquals("transfer-savings", payment.payeeId)
        assertEquals("received", payment.transferId)
        assertNull(payment.categoryId)
        assertEquals("transfer-checking", received.payeeId)
        assertEquals("payment", received.transferId)
        // Dates, notes, cleared/reconciled and the bank import are kept.
        assertEquals(20260901, payment.date)
        assertEquals(20260903, received.date)
        assertEquals("card payment", payment.notes)
        assertTrue(payment.cleared && payment.reconciled)
        assertEquals("bank-1", payment.financialId)
        assertEquals(rowsBefore, database.transactionCount())
        val written = messages(database).drop(before).map { it.row to it.column }.toSet()
        assertEquals(
            setOf(
                "payment" to "description", "payment" to "transferred_id", "payment" to "category",
                "received" to "description", "received" to "transferred_id",
            ),
            written,
        )
    }

    @Test
    fun anInvalidPairWritesNothing() = withDatabase { database ->
        val writer = ActualTransactionWriter(database)
        val before = messages(database).size

        assertThrows(IllegalArgumentException::class.java) { writer.linkAsTransfer("payment", "other") }
        assertThrows(IllegalArgumentException::class.java) { writer.linkAsTransfer("payment", "transfer-in") }
        assertThrows(IllegalArgumentException::class.java) { writer.linkAsTransfer("payment", "transfer-out") }
        assertThrows(IllegalArgumentException::class.java) { writer.linkAsTransfer("payment", "missing") }

        assertEquals(before, messages(database).size)
        assertNull(requireNotNull(database.fetchTransaction("payment")).transferId)
    }

    private fun ActualBudgetDatabase.transactionCount(): Int =
        listOf("payment", "received", "other", "transfer-out", "transfer-in").count { fetchTransaction(it) != null }

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
        val file = File(context.cacheDir, "transaction-transfer-link-${UUID.randomUUID()}.sqlite")
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

            insert(db, "payment", "checking", "grocery", -2500, "store", 20260901, notes = "card payment", financialId = "bank-1", cleared = 1, reconciled = 1)
            insert(db, "received", "savings", null, 2500, null, 20260903)
            insert(db, "other", "savings", null, 1000, null, 20260904)
            insert(db, "transfer-out", "checking", null, -1000, "transfer-savings", 20260905, transfer = "transfer-in")
            insert(db, "transfer-in", "savings", null, 1000, "transfer-checking", 20260905, transfer = "transfer-out")
        }
        return file
    }

    private fun insert(
        db: SQLiteDatabase, id: String, account: String, category: String?, amount: Int, payee: String?, date: Int,
        transfer: String? = null, notes: String? = null, financialId: String? = null, cleared: Int = 0, reconciled: Int = 0,
    ) {
        db.execSQL(
            "INSERT INTO transactions VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(id, 0, 0, account, category, amount, payee, notes, date, null, transfer, cleared, reconciled, 1.0, 0, null, financialId, 0, null),
        )
    }
}
