package com.azimulkabir.actua.data.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ActualServerBankSyncTest {
    private val client = ActualServerClient()

    @Test
    fun `parses normalized SimpleFIN rows using exact integer cents`() {
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
        assertEquals(1, download.transactions.size)
        assertEquals(-1234L, download.transactions.single().amountCents)
        assertEquals(20260920, download.transactions.single().date)
        assertEquals(true, download.transactions.single().booked)
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
}
