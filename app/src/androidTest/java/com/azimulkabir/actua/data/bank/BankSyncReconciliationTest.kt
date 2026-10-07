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

    // Issue #762: some providers send a new id for a transaction already imported under another
    // one. Actual's bank sync (strictIdChecking off) fuzzy-matches it instead of duplicating it.
    @Test fun reDownloadUnderANewIdMatchesTheAlreadyImportedTransaction() = withDatabase { database, _ ->
        val writer = writer(database)
        writer.createTransaction(
            manualTransaction("imported-1", date = 20260913, amount = -1_200).copy(financialId = "old-bank-id", cleared = true),
            applyRules = false,
        )
        val service = service(database, writer, simpleFinResponse(bankRow("new-bank-id", "2026-09-13", "-12.00")))

        val result = service.sync("https://actual.test", "token")

        assertEquals(0, result.imported)
        assertEquals(1, result.matched)
        assertEquals(listOf("imported-1"), transactionIds(database))
        assertEquals("new-bank-id", requireNotNull(database.fetchTransaction("imported-1")).financialId)
    }

    @Test fun identicalPurchasesWithTheirOwnIdsAreStillImportedSeparately() = withDatabase { database, _ ->
        val writer = writer(database)
        val first = service(database, writer, simpleFinResponse(
            bankRow("coffee-a", "2026-09-13", "-12.00"), bankRow("coffee-b", "2026-09-13", "-12.00"),
        ), idPrefix = "first")
        assertEquals(2, first.sync("https://actual.test", "token").imported)

        // The next download still sends both under their own ids, plus a third identical purchase.
        val second = service(database, writer, simpleFinResponse(
            bankRow("coffee-a", "2026-09-13", "-12.00"), bankRow("coffee-b", "2026-09-13", "-12.00"),
            bankRow("coffee-c", "2026-09-14", "-12.00"),
        ), idPrefix = "second")
        val result = second.sync("https://actual.test", "token")

        assertEquals(1, result.imported)
        assertEquals(0, result.matched)
        assertEquals(
            setOf("coffee-a", "coffee-b", "coffee-c"),
            transactionIds(database).mapTo(mutableSetOf()) { requireNotNull(database.fetchTransaction(it)).financialId },
        )
    }

    // Issue #851: rules run on every downloaded row before matching, and a matched transaction takes
    // the rule-derived category where its own is empty, as in Actual's reconcileTransactions.
    @Test fun matchedTransactionTakesTheRuleCategoryWhereItsOwnIsEmpty() = withDatabase(COFFEE_CATEGORY_RULE) { database, _ ->
        val writer = writer(database)
        writer.createTransaction(manualTransaction("manual-1", date = 20260910, amount = -1_200), applyRules = false)
        val service = service(database, writer, simpleFinResponse(bankRow("bank-1", "2026-09-13", "-12.00")))

        val result = service.sync("https://actual.test", "token")

        assertEquals(1, result.matched)
        assertEquals(listOf("manual-1"), transactionIds(database))
        assertEquals("food", requireNotNull(database.fetchTransaction("manual-1")).categoryId)
    }

    // Issue #851: a payee a rule replaced is never created from the bank's name.
    @Test fun ruleRenamedPayeeLeavesNoBankNamedPayeeBehind() = withDatabase(COFFEE_RENAME_RULE) { database, _ ->
        val writer = writer(database)
        val service = service(database, writer, simpleFinResponse(bankRow("bank-1", "2026-09-13", "-12.00")))

        val result = service.sync("https://actual.test", "token")

        assertEquals(1, result.imported)
        assertNull(database.findPayeeByName("Coffee Shop"))
        val cafe = requireNotNull(database.findPayeeByName("Cafe"))
        val imported = requireNotNull(database.fetchTransaction(transactionIds(database).single()))
        assertEquals(cafe.id, imported.payeeId)
        assertEquals("Coffee Shop", imported.importedPayee)
    }

    @Test fun fuzzyMatchCandidatesReturnsManualAndImportedRowsInTheDateWindow() = withDatabase { database, _ ->
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

        assertEquals(
            mapOf("in-window" to null, "already-linked" to "already-linked-ext"),
            candidates.associate { it.id to it.financialId },
        )
    }

    private fun service(database: ActualBudgetDatabase, writer: ActualTransactionWriter, response: String, idPrefix: String = "new") =
        BankSyncService(
            database = database, transactions = writer,
            entities = ActualEntityWriter(database = database, nodeId = "6363636363636363", nowMillis = { FIXED_MILLIS }),
            server = ActualServerClient { request ->
                if (request.url.path.contains("simplefin/transactions")) ActualHttpResponse(200, response.encodeToByteArray())
                else ActualHttpResponse(404, ByteArray(0))
            },
            idFactory = idSequence(idPrefix),
            today = { java.time.LocalDate.of(2026, 9, 16) },
        )

    private fun idSequence(prefix: String): () -> String {
        var next = 0
        return { "$prefix-${++next}" }
    }

    /** Every live -12.00 transaction in the account; all rows these tests create use that amount. */
    private fun transactionIds(database: ActualBudgetDatabase): List<String> =
        database.fuzzyMatchCandidates("checking", amountCents = -1_200, dateFrom = 20260101, dateTo = 20261231).map { it.id }

    private fun bankRow(id: String, date: String, amount: String) =
        """{"transactionId":"$id","date":"$date","transactionAmount":{"amount":"$amount"},"payeeName":"Coffee Shop","booked":true}"""

    private fun simpleFinResponse(vararg rows: String) =
        """{"data":{"ext-1":{"transactions":{"all":[${rows.joinToString(",")}]}}}}"""

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

    private fun withDatabase(vararg rules: String, block: (ActualBudgetDatabase, File) -> Unit) {
        val file = createDatabaseFile(rules.toList())
        try { ActualBudgetDatabase.open(file).use { block(it, file) } } finally { file.delete() }
    }

    private fun createDatabaseFile(rules: List<String> = emptyList()): File {
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
            if (rules.isNotEmpty()) {
                db.execSQL("CREATE TABLE rules (id TEXT PRIMARY KEY, stage TEXT, conditions_op TEXT, conditions TEXT, actions TEXT, tombstone INTEGER DEFAULT 0)")
                rules.forEach(db::execSQL)
            }
            db.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)")
        }
        return file
    }

    private companion object {
        const val FIXED_MILLIS = 1_800_000_000_000L

        val COFFEE_CATEGORY_RULE = """INSERT INTO rules VALUES ('coffee-category',NULL,'and',
            '[{"op":"contains","field":"imported_description","value":"coffee"}]',
            '[{"op":"set","field":"category","value":"food"}]',0)"""

        val COFFEE_RENAME_RULE = """INSERT INTO rules VALUES ('coffee-rename','pre','and',
            '[{"op":"contains","field":"imported_description","value":"coffee"}]',
            '[{"op":"set","field":"payee_name","value":"Cafe"}]',0)"""

        // 3 days after the manually entered transaction's date (20260910); same account/amount.
        val SIMPLE_FIN_RESPONSE = """
            {"data":{"ext-1":{"transactions":{"all":[
                {"transactionId":"bank-tx-1","date":"2026-09-13","transactionAmount":{"amount":"-12.00"},
                 "payeeName":"Coffee Shop","booked":true}
            ]}}}}
        """.trimIndent()
    }
}
