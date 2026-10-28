package com.azimulkabir.actua.data.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Pluggy.ai and Akahu client behaviour against Actual's sync-server routes, with synthetic data. */
class ActualServerPluggyAkahuTest {
    private class FakeTransport(private val responses: Map<String, Pair<Int, String>>) : ActualHttpTransport {
        val requests = mutableListOf<ActualHttpRequest>()
        override fun execute(request: ActualHttpRequest): ActualHttpResponse {
            requests += request
            val (status, body) = responses[request.url.path] ?: (404 to "")
            return ActualHttpResponse(status, body.encodeToByteArray())
        }
        fun body(path: String) = JSONObject(requests.last { it.url.path == path }.body!!.decodeToString())
    }

    private val server = "https://actual.example"

    @Test fun `status routes report configured and a missing route is not configured`() {
        val transport = FakeTransport(mapOf(
            "/pluggyai/status" to (200 to """{"status":"ok","data":{"configured":true,"source":"global"}}"""),
            "/akahu/status" to (200 to """{"status":"ok","data":{"configured":false}}"""),
        ))
        val client = ActualServerClient(transport)
        assertTrue(client.pluggyAiStatus(server, "token"))
        assertFalse(client.akahuStatus(server, "token"))
        assertFalse(ActualServerClient(FakeTransport(emptyMap())).pluggyAiStatus(server, "token"))
    }

    @Test fun `pluggy accounts are labelled and keyed like Actual's desktop client`() {
        val transport = FakeTransport(mapOf("/pluggyai/accounts" to (200 to """
            {"status":"ok","data":{"accounts":[
              {"id":"acc-bank","name":"Conta Corrente ","type":"BANK","taxNumber":"123","bankData":{"automaticallyInvestedBalance":10.5,"closingBalance":100.25}},
              {"id":"acc-card","name":"Cartao","type":"CREDIT","owner":"Maria","balance":-42.0},
              {"name":"no id"}
            ]}}""".trimIndent())))

        val result = ActualServerClient(transport).pluggyAiAccounts(server, "token") as ProviderAccountsResult.Available

        assertEquals(listOf("acc-bank", "acc-card"), result.accounts.map { it.id })
        assertEquals("Conta Corrente - 123", result.accounts[0].name)
        assertEquals("Conta Corrente ", result.accounts[0].institution)
        assertEquals("acc-bank", result.accounts[0].bankId)
        assertEquals(110.75, result.accounts[0].balance!!, 0.0001)
        assertEquals("Cartao - Maria", result.accounts[1].name)
        assertEquals(-42.0, result.accounts[1].balance!!, 0.0001)
        assertEquals("token", transport.requests.single().headers["X-ACTUAL-TOKEN"])
    }

    @Test fun `akahu accounts use the connection as institution and bank id`() {
        val transport = FakeTransport(mapOf("/akahu/accounts" to (200 to """
            {"status":"ok","data":{"accounts":[
              {"_id":"acc_1","name":"Everyday","connection":{"_id":"conn_1","name":"Kiwibank"},"balance":{"current":123.45}}
            ]}}""".trimIndent())))

        val account = (ActualServerClient(transport).akahuAccounts(server, "token") as ProviderAccountsResult.Available).accounts.single()

        assertEquals("acc_1", account.id)
        assertEquals("Everyday", account.name)
        assertEquals("Kiwibank", account.institution)
        assertEquals("Kiwibank", account.bankId)
        assertEquals(123.45, account.balance!!, 0.0001)
    }

    @Test fun `a provider error is returned instead of an empty account list`() {
        val pluggy = FakeTransport(mapOf("/pluggyai/accounts" to (200 to """{"status":"ok","data":{"error":"Invalid credentials"}}""")))
        assertEquals(ProviderAccountsResult.Error("Invalid credentials"), ActualServerClient(pluggy).pluggyAiAccounts(server, "t"))

        val akahu = FakeTransport(mapOf("/akahu/accounts" to (200 to """{"status":"error","data":{"error":"Missing user or app token"}}""")))
        assertEquals(ProviderAccountsResult.Error("Missing user or app token"), ActualServerClient(akahu).akahuAccounts(server, "t"))
    }

    @Test fun `a server without the provider routes is explained`() {
        try {
            ActualServerClient(FakeTransport(emptyMap())).akahuAccounts(server, "t")
            fail("expected an unsupported-server error")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("does not support Akahu"))
        }
    }

    @Test fun `downloads post the account and start date and read the normalized rows`() {
        val feed = """{"status":"ok","data":{"startingBalance":12345,"transactions":{"all":[
            {"transactionId":"t1","date":"2026-09-10","payeeName":"Shop","notes":"Card","booked":true,
             "transactionAmount":{"amount":-9.99,"currency":"NZD"}}]}}}"""
        val transport = FakeTransport(mapOf("/akahu/transactions" to (200 to feed), "/pluggyai/transactions" to (200 to feed)))
        val client = ActualServerClient(transport)

        val akahu = client.downloadAkahuTransactions(server, "token", "acc_1", "2026-07-01")
        val pluggy = client.downloadPluggyAiTransactions(server, "token", "pl-1", "2026-07-02")

        assertEquals("acc_1", transport.body("/akahu/transactions").getString("accountId"))
        assertEquals("2026-07-01", transport.body("/akahu/transactions").getString("startDate"))
        assertEquals("pl-1", transport.body("/pluggyai/transactions").getString("accountId"))
        assertEquals("ok", akahu.status)
        assertEquals(12_345L, akahu.balanceCents)
        assertEquals(-999L, akahu.transactions.single().amountCents)
        assertEquals("pl-1", pluggy.externalAccountId)
    }

    @Test fun `a download error object is a failed download, not an empty success`() {
        val transport = FakeTransport(mapOf("/pluggyai/transactions" to
            (200 to """{"status":"ok","data":{"error":"Item not found"}}""")))

        val download = ActualServerClient(transport).downloadPluggyAiTransactions(server, "token", "pl-1", "2026-07-01")

        assertEquals("failed", download.status)
        assertEquals("Item not found", download.problem)
        assertTrue(download.transactions.isEmpty())
        assertNull(download.balanceCents)
    }
}
