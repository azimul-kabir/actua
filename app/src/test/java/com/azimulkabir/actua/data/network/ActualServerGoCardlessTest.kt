package com.azimulkabir.actua.data.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A minimal fake transport keyed by request path, for exercising [ActualServerClient] without real HTTP. */
private class FakeTransport(private val responses: Map<String, Pair<Int, String>>) : ActualHttpTransport {
    val requests = mutableListOf<ActualHttpRequest>()
    override fun execute(request: ActualHttpRequest): ActualHttpResponse {
        requests += request
        val (status, body) = responses[request.url.path] ?: (404 to "")
        return ActualHttpResponse(status, body.encodeToByteArray())
    }
}

class ActualServerGoCardlessTest {
    @Test
    fun `parses a single-account GoCardless transactions download`() {
        val client = ActualServerClient()
        val response = JSONObject("""
            {"data":{"transactions":{"all":[
              {"transactionId":"gc-tx-1","date":"2026-09-18","payeeName":"Landlord",
               "notes":null,"booked":true,"transactionAmount":{"amount":"-900.00"}}
            ]}}}
        """.trimIndent())

        val download = client.parseSingleBankSyncDownload(response, "account-1")

        assertEquals("account-1", download.externalAccountId)
        assertEquals("ok", download.status)
        assertEquals(1, download.transactions.size)
        assertEquals(-90000L, download.transactions.single().amountCents)
        assertEquals("Landlord", download.transactions.single().payeeName)
    }

    @Test
    fun `maps a GoCardless provider error to Actual bank status`() {
        val client = ActualServerClient()
        val response = JSONObject("""
            {"data":{"error_code":"ACCOUNT_NEEDS_ATTENTION","reason":"Reconnect this bank"}}
        """.trimIndent())

        val download = client.parseSingleBankSyncDownload(response, "account-1")

        assertEquals("attention-required", download.status)
        assertEquals("Reconnect this bank", download.problem)
        assertTrue(download.transactions.isEmpty())
    }

    @Test
    fun `reports provider configured from the status endpoint`() {
        val transport = FakeTransport(
            mapOf(
                "/simplefin/status" to (200 to """{"status":"ok","data":{"configured":true}}"""),
                "/gocardless/status" to (200 to """{"status":"ok","data":{"configured":false}}"""),
            ),
        )
        val client = ActualServerClient(transport)

        assertTrue(client.simpleFinStatus("https://actual.example", "token"))
        assertEquals(false, client.goCardlessStatus("https://actual.example", "token"))
    }

    @Test
    fun `an unsupported status route is treated as not configured`() {
        val client = ActualServerClient(FakeTransport(emptyMap()))
        assertEquals(false, client.simpleFinStatus("https://actual.example", "token"))
    }

    @Test
    fun `setSecret posts the name and value to the secrets endpoint`() {
        val transport = FakeTransport(mapOf("/secret/" to (200 to """{"status":"ok"}""")))
        val client = ActualServerClient(transport)

        client.setSecret("https://actual.example", "token", "simplefin_token", "setup-token-123")

        val request = transport.requests.single()
        val body = JSONObject(request.body!!.decodeToString())
        assertEquals("simplefin_token", body.getString("name"))
        assertEquals("setup-token-123", body.getString("value"))
    }
}
