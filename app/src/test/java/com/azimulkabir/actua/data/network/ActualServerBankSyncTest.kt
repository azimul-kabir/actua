package com.azimulkabir.actua.data.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ActualServerBankSyncTest {
    private val client = ActualServerClient()

    @Test
    fun `parses normalized SimpleFIN rows rounding amounts like Actual`() {
        val response = JSONObject("""
            {"data":{"external-1":{"startingBalance":1250,"transactions":{"all":[
              {"transactionId":"bank-tx-1","date":"2026-09-20","payeeName":"Market",
               "notes":"weekly shop","booked":true,"transactionAmount":{"amount":"-12.34"}},
              {"transactionId":"bad-amount","date":"2026-09-20","payeeName":"Ignored",
               "transactionAmount":{"amount":"12.345"}}
            ]}},"errors":{}}}
        """.trimIndent())

        val download = client.parseSimpleFinDownloads(response, listOf("external-1")).single()

        assertEquals("external-1", download.externalAccountId)
        assertEquals("ok", download.status)
        assertEquals(2, download.transactions.size)
        assertEquals(-1234L, download.transactions.first().amountCents)
        assertEquals(20260920, download.transactions.first().date)
        assertEquals(true, download.transactions.first().booked)
        // Actual's amountToInteger: 12.345 * 100 is 1234.5 as a double, and Math.round gives 1235; a missing `booked` is pending.
        assertEquals(1235L, download.transactions[1].amountCents)
        assertEquals(false, download.transactions[1].booked)
    }

    @Test
    fun `maps provider errors to Actual bank status`() {
        val response = JSONObject("""
            {"data":{"errors":{"external-1":[
              {"error_code":"ITEM_LOGIN_REQUIRED","reason":"Reconnect this bank"}
            ]}}}
        """.trimIndent())

        val download = client.parseSimpleFinDownloads(response, listOf("external-1")).single()

        assertEquals("reauth-required", download.status)
        assertEquals("Reconnect this bank", download.problem)
        assertEquals(emptyList<BankSyncTransaction>(), download.transactions)
    }

    @Test
    fun `bank-sync downloads get a longer read timeout and other requests keep the default`() {
        val requests = mutableListOf<ActualHttpRequest>()
        val client = ActualServerClient { request ->
            requests += request
            val body = if (request.url.path.endsWith("/transactions")) """{"data":{"transactions":{"all":[]}}}"""
                else """{"status":"ok","data":{"configured":true}}"""
            ActualHttpResponse(200, body.encodeToByteArray())
        }

        client.downloadSimpleFinTransactions("https://actual.test", "token", listOf("a"), listOf("2026-09-01"))
        client.downloadGoCardlessTransactions("https://actual.test", "token", "req", "a", "2026-09-01", "2026-09-30")
        client.simpleFinStatus("https://actual.test", "token")

        assertEquals(
            listOf(
                "/simplefin/transactions" to BANK_SYNC_READ_TIMEOUT_MILLIS,
                "/gocardless/transactions" to BANK_SYNC_READ_TIMEOUT_MILLIS,
                "/simplefin/status" to null,
            ),
            requests.map { it.url.path to it.readTimeoutMillis },
        )
    }
}
