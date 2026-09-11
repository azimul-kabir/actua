package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.sync.HlcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** High-risk mutation tests that also prove unrelated transaction rows stay byte-for-byte equivalent. */
class ActualDataIntegrityRegressionTest {
    @Test fun ordinaryUpdateChangesOnlyRequestedRowAndColumns() = withDatabase { database, file ->
        val writer = writer(database)
        val edited = transaction("edited", "checking", -1_250, category = "groceries", payee = "shop")
        val untouched = transaction("untouched", "checking", -2_000, category = "rent", payee = "landlord")
        writer.createTransaction(edited, applyRules = false)
        writer.createTransaction(untouched, applyRules = false)
        val untouchedBefore = rowSnapshot(file, untouched.id)
        val beforeMessages = database.getMessagesSince(HlcTimestamp.ZERO.toString()).size

        writer.updateTransaction(edited.copy(amountCents = -1_500, notes = "updated"), setOf("amount", "notes"))

        assertEquals(-1_500L, requireNotNull(database.fetchTransaction(edited.id)).amountCents)
        assertEquals(untouchedBefore, rowSnapshot(file, untouched.id))
        val newMessages = database.getMessagesSince(HlcTimestamp.ZERO.toString()).drop(beforeMessages)
        assertEquals(setOf("amount", "notes"), newMessages.map { it.column }.toSet())
        assertTrue(newMessages.all { it.dataset == "transactions" && it.row == edited.id })
    }

    @Test fun transferRoundTripPreservesPairAndUnrelatedRows() = withDatabase { database, file ->
        val writer = writer(database)
        val untouched = transaction("untouched", "checking", -300, category = "groceries")
        writer.createTransaction(untouched, applyRules = false)
        val untouchedBefore = rowSnapshot(file, untouched.id)
        val source = transaction("transfer-out", "checking", -5_000, transfer = "transfer-in")
        val target = transaction("transfer-in", "savings", 5_000, transfer = "transfer-out")

        writer.createTransfer(source, target)

        val storedSource = requireNotNull(database.fetchTransaction(source.id))
        val storedTarget = requireNotNull(database.fetchTransaction(target.id))
        assertEquals(storedTarget.id, storedSource.transferId)
        assertEquals(storedSource.id, storedTarget.transferId)
        assertEquals(0L, storedSource.amountCents + storedTarget.amountCents)
        assertEquals(untouchedBefore, rowSnapshot(file, untouched.id))
    }

    @Test fun splitCreateAndDeleteTouchesOnlySplitFamily() = withDatabase { database, file ->
        val writer = writer(database)
        val untouched = transaction("untouched", "checking", -450, category = "groceries")
        writer.createTransaction(untouched, applyRules = false)
        val untouchedBefore = rowSnapshot(file, untouched.id)
        val parent = transaction("split-parent", "checking", -1_000, parentFlag = true)
        val childA = transaction("split-a", "checking", -600, category = "groceries", parent = parent.id)
        val childB = transaction("split-b", "checking", -400, category = "rent", parent = parent.id)

        writer.createSplit(parent, listOf(childA, childB))
        assertEquals(listOf(childA.id, childB.id), database.fetchChildTransactions(parent.id).map { it.id }.sorted())
        writer.deleteTransaction(parent)

        assertTrue(database.fetchTransaction(parent.id) == null)
        assertTrue(database.fetchChildTransactions(parent.id).isEmpty())
        assertEquals(untouchedBefore, rowSnapshot(file, untouched.id))
        val tombstones = database.getMessagesSince(HlcTimestamp.ZERO.toString())
            .filter { it.dataset == "transactions" && it.column == "tombstone" && it.value == "N:1" }
            .map { it.row }.toSet()
        assertTrue(tombstones.containsAll(setOf(parent.id, childA.id, childB.id)))
        assertFalse(tombstones.contains(untouched.id))
    }

    @Test fun reconciliationLocksOnlyClearedRowsInRequestedAccount() = withDatabase { database, file ->
        val writer = writer(database)
        val cleared = transaction("cleared", "checking", -100).copy(cleared = true)
        val uncleared = transaction("uncleared", "checking", -200)
        val otherAccount = transaction("other", "savings", -300).copy(cleared = true)
        writer.createTransaction(cleared, applyRules = false)
        writer.createTransaction(uncleared, applyRules = false)
        writer.createTransaction(otherAccount, applyRules = false)
        val unclearedBefore = rowSnapshot(file, uncleared.id)
        val otherBefore = rowSnapshot(file, otherAccount.id)

        assertEquals(1, writer.reconcileClearedTransactions("checking"))

        assertTrue(requireNotNull(database.fetchTransaction(cleared.id)).reconciled)
        assertEquals(unclearedBefore, rowSnapshot(file, uncleared.id))
        assertEquals(otherBefore, rowSnapshot(file, otherAccount.id))
        assertFalse(requireNotNull(database.fetchTransaction(otherAccount.id)).reconciled)
    }

    @Test fun scheduleLinkMutationIsIdempotentAndScoped() = withDatabase { database, file ->
        val writer = writer(database)
        val linked = transaction("linked", "checking", -100)
        val untouched = transaction("untouched", "checking", -200)
        writer.createTransaction(linked, applyRules = false)
        writer.createTransaction(untouched, applyRules = false)
        val untouchedBefore = rowSnapshot(file, untouched.id)

        writer.setScheduleLink(linked, "schedule-1")
        val afterFirst = database.getMessagesSince(HlcTimestamp.ZERO.toString()).size
        writer.setScheduleLink(requireNotNull(database.fetchTransaction(linked.id)), "schedule-1")
        val afterSecond = database.getMessagesSince(HlcTimestamp.ZERO.toString()).size

        assertEquals("schedule-1", database.fetchTransaction(linked.id)?.scheduleId)
        assertEquals(afterFirst, afterSecond)
        assertEquals(untouchedBefore, rowSnapshot(file, untouched.id))
    }

    private fun writer(database: ActualBudgetDatabase) = ActualTransactionWriter(
        database = database, nodeId = "5252525252525252", nowMillis = { 1_800_000_000_000L },
    )

    private fun transaction(
        id: String, account: String, amount: Long, category: String? = null, payee: String? = null,
        transfer: String? = null, parentFlag: Boolean = false, parent: String? = null,
    ) = ActualTransaction(
        id = id, accountId = account, date = 20260912, amountCents = amount,
        payeeId = payee, payeeName = null, categoryId = category, categoryName = null,
        notes = null, cleared = false, reconciled = false, transferId = transfer,
        isParent = parentFlag, parentId = parent, tombstone = false, sortOrder = 1.0,
        importedPayee = null, scheduleId = null, transferAccountId = null, startingBalance = false,
    )

    private fun withDatabase(block: (ActualBudgetDatabase, File) -> Unit) {
        val file = createDatabaseFile()
        try { ActualBudgetDatabase.open(file).use { block(it, file) } } finally { file.delete() }
    }

    private fun createDatabaseFile(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "integrity-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, offbudget INTEGER DEFAULT 0, closed INTEGER DEFAULT 0, tombstone INTEGER DEFAULT 0, type TEXT, sort_order REAL)")
            db.execSQL("INSERT INTO accounts(id,name,type) VALUES ('checking','Checking','checking'),('savings','Savings','savings')")
            db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, cat_group TEXT, is_income INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)")
            db.execSQL("CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT)")
            db.execSQL("CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, transfer_acct TEXT, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE zero_budgets (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE transactions (id TEXT PRIMARY KEY, acct TEXT, date INTEGER, description TEXT, category TEXT, amount INTEGER, notes TEXT, cleared INTEGER DEFAULT 0, reconciled INTEGER DEFAULT 0, transferred_id TEXT, isParent INTEGER DEFAULT 0, isChild INTEGER DEFAULT 0, parent_id TEXT, tombstone INTEGER DEFAULT 0, sort_order REAL, imported_description TEXT, schedule TEXT, starting_balance_flag INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, column TEXT NOT NULL, value BLOB NOT NULL)")
        }
        return file
    }

    private fun rowSnapshot(file: File, id: String): List<String?> =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT acct,date,description,category,amount,notes,cleared,reconciled,transferred_id,isParent,isChild,parent_id,tombstone,sort_order,imported_description,schedule,starting_balance_flag FROM transactions WHERE id=?", arrayOf(id)).use { cursor ->
                assertTrue("Missing transaction $id", cursor.moveToFirst())
                (0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }
            }
        }
}