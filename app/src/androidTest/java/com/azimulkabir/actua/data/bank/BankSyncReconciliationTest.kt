package com.azimulkabir.actua.data.bank

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActualEntityWriter
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.network.ActualHttpResponse
import com.azimulkabir.actua.data.network.ActualServerClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Regression coverage for issue #602: bank sync must reconcile with an existing manually
 * entered transaction (even when it posts on a different date) instead of duplicating it. */
class BankSyncReconciliationTest {
    @Test fun financialIdPendingAndRawSyncedDataRoundTripThroughInsertAndFetch() = withDatabase { database, _ ->
        val writer = writer(database)
        val row = manualTransaction("bank-row", date = 20260910, amount = -1_200)
            .copy(financialId = "ext-1", pending = true, rawSyncedData = "{\"raw\":true}")

        writer.createTransaction(row, applyRules = false)

        val stored = requireNotNull(database.fetchTransaction("bank-row"))
        assertEquals("ext-1", stored.financialId)
        assertTrue(stored.pending)
        assertEquals("{\"raw\":true}", stored.rawSyncedData)
    }

    @Test fun syncReconcilesAManuallyEnteredTransactionPostedOnADifferentDate() = withDatabase { database, _ ->
        val writer = writer(database)
        val entities = ActualEntityWriter(database = database, nodeId = "6363636363636363", nowMillis = { FIXED_MILLIS })
        val manual = manualTransaction("manual-1", date = 20260910, amount = -1_200)
        writer.createTransaction(manual, applyRules = false)

        val server = ActualServerClient { request ->
            if (request.url.path.contains("simplefin/transactions")) {
                ActualHttpResponse(200, SIMPLE_FIN_RESPONSE.encodeToByteArray())
            } else {
                ActualHttpResponse(404, ByteArray(0))
            }
        }
        val service = BankSyncService(
            database = database, transactions = writer, entities = entities, server = server,
            idFactory = { "new-transaction" }, today = { java.time.LocalDate.of(2026, 9, 16) },
        )

        val result = service.sync("https://actual.test", "token")

        assertEquals(1, result.matched)
        assertEquals(0, result.imported)
        assertNull(database.fetchTransaction("new-transaction"))
        val reconciled = requireNotNull(database.fetchTransaction("manual-1"))
        assertEquals("bank-tx-1", reconciled.financialId)
        assertEquals(-1_200L, reconciled.amountCents)
        assertEquals(20260910, reconciled.date) // the manually entered date is preserved, not overwritten
        assertTrue(reconciled.cleared)
    }

    @Test fun fuzzyMatchCandidatesOnlyReturnsUnlinkedRowsInTheDateWindow() = withDatabase { database, _ ->
        val writer = writer(database)
        writer.createTransaction(manualTransaction("in-window", date = 20260910, amount = -1_200), applyRules = false)
        writer.createTransaction(manualTransaction("too-far", date = 20260101, amount = -1_200), applyRules = false)
        writer.createTransaction(
            manualTransaction("already-linked", date = 20260911, amount = -1_200)
                .copy(financialId = "already-linked-ext"),
            applyRules = false,
        )

        val candidates = database.fuzzyMatchCandidates(
            "checking", amountCents = -1_200, dateFrom = 20260903, dateTo = 20260917,
        )

        assertEquals(listOf("in-window"), candidates.map { it.id })
    }

    private fun writer(database: ActualBudgetDatabase) = ActualTransactionWriter(
        database = database, nodeId = "5252525252525252", nowMillis = { FIXED_MILLIS },
    )

    private fun manualTransaction(id: String, date: Int, amount: Long) = ActualTransaction(
        id = id, accountId = "checking", date = date, amountCents = amount,
        payeeId = null, payeeName = null, categoryId = null, categoryName = null,
        notes = null, cleared = false, reconciled = false, transferId = null,
        isParent = false, parentId = null, tombstone = false, sortOrder = 1.0,
        importedPayee = null, scheduleId = null, transferAccountId = null,
    )

    private fun withDatabase(block: (ActualBudgetDatabase, File) -> Unit) {
        val file = createDatabaseFile()
        try { ActualBudgetDatabase.open(file).use { block(it, file) } } finally { file.delete() }
    }

    private fun createDatabaseFile(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "bank-sync-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, offbudget INTEGER DEFAULT 0, " +
                    "closed INTEGER DEFAULT 0, tombstone INTEGER DEFAULT 0, type TEXT, sort_order REAL, " +
                    "account_id TEXT, account_sync_source TEXT, gocardless_requisition_id TEXT, " +
                    "bank_sync_status TEXT, last_sync TEXT)",
            )
            db.execSQL(
                "INSERT INTO accounts(id,name,type,account_id,account_sync_source) VALUES " +
                    "('checking','Checking','checking','ext-1','simpleFin')",
            )
            db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, cat_group TEXT, is_income INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)")
            db.execSQL("CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT)")
            db.execSQL("CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, transfer_acct TEXT, tombstone INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE zero_budgets (id TEXT PRIMARY KEY)")
            db.execSQL(
                "CREATE TABLE transactions (id TEXT PRIMARY KEY, acct TEXT, date INTEGER, description TEXT, " +
                    "category TEXT, amount INTEGER, notes TEXT, cleared INTEGER DEFAULT 0, reconciled INTEGER DEFAULT 0, " +
                    "transferred_id TEXT, isParent INTEGER DEFAULT 0, isChild INTEGER DEFAULT 0, parent_id TEXT, " +
                    "tombstone INTEGER DEFAULT 0, sort_order REAL, imported_description TEXT, schedule TEXT, " +
                    "starting_balance_flag INTEGER DEFAULT 0, financial_id TEXT, pending INTEGER DEFAULT 0, raw_synced_data TEXT)",
            )
            db.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)")
        }
        return file
    }

    private companion object {
        const val FIXED_MILLIS = 1_800_000_000_000L

        // 3 days after the manually entered transaction's date (20260910); same account/amount.
        val SIMPLE_FIN_RESPONSE = """
            {"data":{"ext-1":{"transactions":{"all":[
                {"transactionId":"bank-tx-1","date":"2026-09-13","transactionAmount":{"amount":"-12.00"},
                 "payeeName":"Coffee Shop","booked":true}
            ]}}}}
        """.trimIndent()
    }
}
