package com.azimulkabir.actua.data.bank

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActualEntityWriter
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.network.ActualHttpResponse
import com.azimulkabir.actua.data.network.ActualHttpTransport
import com.azimulkabir.actua.data.network.ActualServerClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.SocketTimeoutException
import java.util.UUID

/** Regression coverage for issue #761: per-account bank-sync failures are reported and stored like Actual's. */
class BankSyncFailureTest {
    @Test fun simpleFinReadTimeoutIsReportedAsATimeoutNotAMissingAccount() = withDatabase { database ->
        val result = service(database) { throw SocketTimeoutException("Read timed out") }.sync("https://actual.test", "token")

        assertEquals(listOf("Checking: the bank took too long to respond. Try syncing again."), result.problems)
        assertEquals("timed-out", status(database, "checking"))
    }

    @Test fun accountLeftOutOfTheSimpleFinBatchIsStoredAsMissingLikeActual() = withDatabase { database ->
        val result = service(database) { ActualHttpResponse(200, """{"data":{"errors":{}}}""".encodeToByteArray()) }
            .sync("https://actual.test", "token")

        assertEquals(
            listOf("Checking: SimpleFIN did not return this account. Try syncing again, and relink it if this keeps happening."),
            result.problems,
        )
        assertEquals("account-missing", status(database, "checking"))
    }

    @Test fun goCardlessReadTimeoutIsReportedAsATimeout() = withDatabase(
        "INSERT INTO accounts(id,name,type,account_id,account_sync_source,gocardless_requisition_id) VALUES " +
            "('savings','Savings','savings','ext-2','goCardless','req-1')",
    ) { database ->
        val result = service(database) { request ->
            if (request.url.path.contains("gocardless")) throw SocketTimeoutException("Read timed out")
            ActualHttpResponse(200, """{"data":{"ext-1":{"transactions":{"all":[]}}}}""".encodeToByteArray())
        }.sync("https://actual.test", "token", accountId = "savings")

        assertEquals(listOf("Savings: the bank took too long to respond. Try syncing again."), result.problems)
        assertEquals("timed-out", status(database, "savings"))
    }

    @Test fun unsupportedProviderIsNamedAndItsStatusLeftAlone() = withDatabase(
        "INSERT INTO accounts(id,name,type,account_id,account_sync_source) VALUES " +
            "('broker','Broker','investment','ext-3','enableBanking')",
    ) { database ->
        val result = service(database) { error("no request expected") }.sync("https://actual.test", "token", accountId = "broker")

        assertEquals(listOf("Broker: Actua can't sync Enable Banking accounts yet. Sync this account in Actual."), result.problems)
        assertNull(status(database, "broker"))
    }

    @Test fun enableBankingAccountIsDownloadedAndImportedOnlyWhileTheExperimentIsOn() = withDatabase(
        "INSERT INTO accounts(id,name,type,account_id,account_sync_source) VALUES " +
            "('eu','EU Account','checking','uid-eu','enableBanking')",
    ) { database ->
        val rows = """{"status":"ok","data":{"transactions":{"all":[
            {"transactionId":"eb-1","date":"2026-09-10","payeeName":"Supermarkt","notes":"Card","booked":true,
             "transactionAmount":{"amount":"-12.34","currency":"EUR"}},
            {"transactionId":"eb-2","date":"2026-09-11","payeeName":"Employer","booked":false,
             "transactionAmount":{"amount":"2000.00","currency":"EUR"}}]}}}"""
        val paths = mutableListOf<String>()
        val transport = ActualHttpTransport { request ->
            paths += request.url.path
            ActualHttpResponse(200, rows.encodeToByteArray())
        }

        val off = service(database, transport = transport).sync("https://actual.test", "token", accountId = "eu")
        assertEquals(listOf("EU Account: Actua can't sync Enable Banking accounts yet. Sync this account in Actual."), off.problems)
        assertTrue("no request while the experiment is off", paths.isEmpty())
        assertNull(status(database, "eu"))

        val on = service(database, enableBanking = true, transport = transport).sync("https://actual.test", "token", accountId = "eu")
        assertEquals(listOf("/enablebanking/transactions"), paths)
        assertEquals(2, on.imported)
        assertTrue(on.problems.isEmpty())
        assertEquals("ok", status(database, "eu"))
        val imported = database.fetchTransactions("eu").associateBy { it.financialId }
        assertEquals(-1_234L, imported.getValue("eb-1").amountCents)
        assertEquals(true, imported.getValue("eb-1").cleared)
        assertEquals(200_000L, imported.getValue("eb-2").amountCents)
        assertEquals(true, imported.getValue("eb-2").pending)
    }

    @Test fun enableBankingConsentExpiryIsStoredAsReauthorizationRequired() = withDatabase(
        "INSERT INTO accounts(id,name,type,account_id,account_sync_source) VALUES " +
            "('eu','EU Account','checking','uid-eu','enableBanking')",
    ) { database ->
        val result = service(database, enableBanking = true) {
            ActualHttpResponse(200,
                """{"status":"ok","data":{"error_type":"ITEM_ERROR","error_code":"ITEM_LOGIN_REQUIRED"}}""".encodeToByteArray())
        }.sync("https://actual.test", "token", accountId = "eu")

        assertEquals(0, result.imported)
        assertEquals("reauth-required", status(database, "eu"))
    }

    private fun service(database: ActualBudgetDatabase, enableBanking: Boolean = false, transport: ActualHttpTransport) = BankSyncService(
        database = database,
        transactions = ActualTransactionWriter(database = database, nodeId = "5252525252525252", nowMillis = { FIXED_MILLIS }),
        entities = ActualEntityWriter(database = database, nodeId = "6363636363636363", nowMillis = { FIXED_MILLIS }),
        server = ActualServerClient(transport),
        today = { java.time.LocalDate.of(2026, 9, 16) },
        enableBankingEnabled = enableBanking,
    )

    private fun status(database: ActualBudgetDatabase, id: String): String? =
        database.fetchBankSyncAccounts().single { it.id == id }.status

    private fun withDatabase(vararg setup: String, block: (ActualBudgetDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "bank-sync-failure-${UUID.randomUUID()}.sqlite")
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                SCHEMA.forEach(db::execSQL)
                setup.forEach(db::execSQL)
            }
            ActualBudgetDatabase.open(file).use(block)
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val FIXED_MILLIS = 1_800_000_000_000L

        val SCHEMA = listOf(
            "CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, offbudget INTEGER DEFAULT 0, " +
                "closed INTEGER DEFAULT 0, tombstone INTEGER DEFAULT 0, type TEXT, sort_order REAL, " +
                "account_id TEXT, account_sync_source TEXT, gocardless_requisition_id TEXT, " +
                "bank_sync_status TEXT, last_sync TEXT)",
            "INSERT INTO accounts(id,name,type,account_id,account_sync_source) VALUES " +
                "('checking','Checking','checking','ext-1','simpleFin')",
            "CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, cat_group TEXT, is_income INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0)",
            "CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0)",
            "CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)",
            "CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT)",
            "CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, transfer_acct TEXT, tombstone INTEGER DEFAULT 0)",
            "CREATE TABLE zero_budgets (id TEXT PRIMARY KEY)",
            "CREATE TABLE transactions (id TEXT PRIMARY KEY, acct TEXT, date INTEGER, description TEXT, " +
                "category TEXT, amount INTEGER, notes TEXT, cleared INTEGER DEFAULT 0, reconciled INTEGER DEFAULT 0, " +
                "transferred_id TEXT, isParent INTEGER DEFAULT 0, isChild INTEGER DEFAULT 0, parent_id TEXT, " +
                "tombstone INTEGER DEFAULT 0, sort_order REAL, imported_description TEXT, schedule TEXT, " +
                "starting_balance_flag INTEGER DEFAULT 0, financial_id TEXT, pending INTEGER DEFAULT 0, raw_synced_data TEXT)",
            "CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)",
            "CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)",
        )
    }
}
