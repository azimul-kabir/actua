package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.HlcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.UUID

/** Bulk edits run through [ActualTransactionWriter.batch]: one transaction, clock save and `onWrite` (#1601). */
class BatchTransactionWriteTest {
    private val ids = listOf("t1", "t2", "t3")

    @Test
    fun aBatchWritesTheSameMessagesAsSingleSaves() {
        val single = run { database, writer ->
            ids.forEach { writer.mutate(updates = listOf(edit(database, it))) }
        }
        val batched = run { database, writer ->
            writer.batch { ids.forEach { writer.mutate(updates = listOf(edit(database, it))) } }
        }

        assertEquals(single.messages, batched.messages)
        assertEquals(single.notes, batched.notes)
        assertEquals(3, batched.messages.size)
    }

    @Test
    fun aBatchSavesTheClockAndNotifiesOnce() {
        val single = run { database, writer ->
            ids.forEach { writer.mutate(updates = listOf(edit(database, it))) }
        }
        val batched = run { database, writer ->
            writer.batch { ids.forEach { writer.mutate(updates = listOf(edit(database, it))) } }
        }

        assertEquals(3, single.onWrites)
        assertEquals(1, batched.onWrites)
        // The final clock (latest timestamp and Merkle root) matches the one the single saves end on.
        assertEquals(single.clock, batched.clock)
    }

    @Test
    fun aFailureMidBatchRollsBackEveryRowAndDoesNotNotify() = withDatabase { database ->
        var onWrites = 0
        val writer = writer(database) { onWrites++ }
        val clockBefore = database.loadClock()

        try {
            writer.batch {
                writer.mutate(updates = listOf(edit(database, "t1")))
                writer.mutate(updates = listOf(edit(database, "t2").let { (old, new) -> old to new.copy(date = 1) }))
            }
            fail("expected the invalid date to abort the batch")
        } catch (_: IllegalArgumentException) {
        }

        assertEquals(0, onWrites)
        assertNull(database.fetchTransaction("t1")?.notes)
        assertTrue(messages(database).isEmpty())
        assertEquals(clockBefore, database.loadClock())
    }

    private class Result(val messages: List<CrdtMessage>, val notes: List<String?>, val onWrites: Int, val clock: Any?)

    private fun run(block: (ActualBudgetDatabase, ActualTransactionWriter) -> Unit): Result {
        var result: Result? = null
        withDatabase { database ->
            var onWrites = 0
            block(database, writer(database) { onWrites++ })
            result = Result(messages(database), ids.map { database.fetchTransaction(it)?.notes }, onWrites, database.loadClock())
        }
        return requireNotNull(result)
    }

    // A fixed clock and node id make the HLC timestamps of two runs comparable.
    private fun writer(database: ActualBudgetDatabase, onWrite: () -> Unit) =
        ActualTransactionWriter(database, nodeId = "0123456789ABCDEF", nowMillis = { 1_700_000_000_000L }, onWrite = onWrite)

    private fun edit(database: ActualBudgetDatabase, id: String) = requireNotNull(database.fetchTransaction(id)).let {
        it to it.copy(notes = "edited $id")
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
        val file = File(context.cacheDir, "batch-write-${UUID.randomUUID()}.sqlite")
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
            db.execSQL("INSERT INTO accounts VALUES ('checking','Checking','checking',0,0,0,1)")
            ids.forEachIndexed { index, id ->
                db.execSQL(
                    "INSERT INTO transactions (id, isParent, isChild, acct, amount, date, cleared, reconciled, sort_order, tombstone) " +
                        "VALUES ('$id', 0, 0, 'checking', ${-100 * (index + 1)}, 20260101, 0, 0, ${index + 1}, 0)",
                )
            }
        }
        return file
    }
}
