package com.azimulkabir.actua.data.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Enable Banking client behaviour against Actual's sync-server routes (`app-enablebanking.ts`), with synthetic data. */
class ActualServerEnableBankingTest {
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

    @Test fun `reports configured from the status route and treats a missing route as not configured`() {
        val ok = FakeTransport(mapOf("/enablebanking/status" to (200 to """{"status":"ok","data":{"configured":true}}""")))
        assertTrue(ActualServerClient(ok).enableBankingStatus(server, "token"))
        assertEquals("token", ok.requests.single().headers["X-ACTUAL-TOKEN"])

        assertFalse(ActualServerClient(FakeTransport(emptyMap())).enableBankingStatus(server, "token"))
    }

    @Test fun `configure sends the application id and key and accepts a configured answer`() {
        val transport = FakeTransport(mapOf("/enablebanking/configure" to (200 to """{"status":"ok","data":{"configured":true}}""")))

        ActualServerClient(transport).enableBankingConfigure(server, "token", "app-id", "-----BEGIN PRIVATE KEY-----")

        val body = transport.body("/enablebanking/configure")
        assertEquals("app-id", body.getString("applicationId"))
        assertEquals("-----BEGIN PRIVATE KEY-----", body.getString("secretKey"))
    }

    @Test fun `configure surfaces the server's reason when Enable Banking rejects the credentials`() {
        val transport = FakeTransport(mapOf("/enablebanking/configure" to
            (200 to """{"status":"ok","data":{"error_code":"CONFIGURATION_FAILED","error_type":"Invalid application id"}}""")))
        try {
            ActualServerClient(transport).enableBankingConfigure(server, "token", "bad", "key")
            fail("expected a rejection")
        } catch (error: IllegalStateException) {
            assertEquals("Invalid application id", error.message)
        }
    }

    @Test fun `configure explains a server without Enable Banking`() {
        try {
            ActualServerClient(FakeTransport(emptyMap())).enableBankingConfigure(server, "token", "id", "key")
            fail("expected an unsupported-server error")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("doesn't support Enable Banking"))
        }
    }

    @Test fun `banks are listed by name for the requested country`() {
        val transport = FakeTransport(mapOf("/enablebanking/aspsps" to (200 to """
            {"status":"ok","data":[
              {"name":"Zeta Bank","country":"DE","maximum_consent_validity":7776000,"psu_types":["personal","business"]},
              {"name":"alpha Bank","country":"DE"},
              {"country":"DE"}
            ]}""".trimIndent())))

        val banks = ActualServerClient(transport).enableBankingBanks(server, "token", "DE")

        assertEquals("DE", transport.body("/enablebanking/aspsps").getString("country"))
        assertEquals(listOf("alpha Bank", "Zeta Bank"), banks.map { it.name })
        assertEquals(7_776_000, banks.last().maxConsentValiditySeconds)
        assertEquals(listOf("personal", "business"), banks.last().psuTypes)
        assertNull(banks.first().maxConsentValiditySeconds)
    }

    @Test fun `a banks error object is raised instead of read as an empty list`() {
        val transport = FakeTransport(mapOf("/enablebanking/aspsps" to (200 to """{"status":"ok","data":{"error":"Not configured"}}""")))
        try {
            ActualServerClient(transport).enableBankingBanks(server, "token", "DE")
            fail("expected an error")
        } catch (error: IllegalStateException) {
            assertEquals("Not configured", error.message)
        }
    }

    @Test fun `start auth posts the bank and the server callback and returns the url and state`() {
        val transport = FakeTransport(mapOf("/enablebanking/start-auth" to
            (200 to """{"status":"ok","data":{"url":"https://bank.example/consent","state":"state-1"}}""")))
        val bank = EnableBankingBank("Test Bank", "DE", maxConsentValiditySeconds = 5_184_000, psuTypes = listOf("personal"))

        val start = ActualServerClient(transport).enableBankingStartAuth(
            server, "token", bank, "https://actual.example/enablebanking/auth_callback",
        )

        assertEquals(EnableBankingAuthStart("https://bank.example/consent", "state-1"), start)
        val body = transport.body("/enablebanking/start-auth")
        assertEquals("Test Bank", body.getJSONObject("aspsp").getString("name"))
        assertEquals("DE", body.getJSONObject("aspsp").getString("country"))
        assertEquals("https://actual.example/enablebanking/auth_callback", body.getString("redirectUrl"))
        assertEquals("personal", body.getString("psuType"))
        assertEquals(5_184_000, body.getInt("maxConsentValidity"))
    }

    @Test fun `start auth omits an unknown consent validity and raises a server error`() {
        val ok = FakeTransport(mapOf("/enablebanking/start-auth" to (200 to """{"data":{"url":"u","state":"s"}}""")))
        ActualServerClient(ok).enableBankingStartAuth(server, "token", EnableBankingBank("B", "FR", null, emptyList()), "r")
        assertFalse(ok.body("/enablebanking/start-auth").has("maxConsentValidity"))

        val failing = FakeTransport(mapOf("/enablebanking/start-auth" to (200 to """{"data":{"error":"bank unavailable"}}""")))
        try {
            ActualServerClient(failing).enableBankingStartAuth(server, "token", EnableBankingBank("B", "FR", null, emptyList()), "r")
            fail("expected an error")
        } catch (error: IllegalStateException) {
            assertEquals("bank unavailable", error.message)
        }
    }

    @Test fun `poll auth waits with a long read timeout and returns the granted accounts`() {
        val transport = FakeTransport(mapOf("/enablebanking/poll-auth" to (200 to """
            {"status":"ok","data":{"session_id":"s","accounts":[
              {"account_id":"uid-1","name":"Main","institution":"Test Bank","iban":"DE00 0000","balance":1234},
              {"account_id":"uid-2","institution":"Test Bank","iban":"DE11 1111"},
              {"name":"no id"}
            ]}}""".trimIndent())))

        val result = ActualServerClient(transport).enableBankingPollAuth(server, "token", "state-1")

        assertEquals(ENABLE_BANKING_POLL_TIMEOUT_MILLIS, transport.requests.single().readTimeoutMillis)
        assertEquals("state-1", transport.body("/enablebanking/poll-auth").getString("state"))
        val accounts = (result as EnableBankingAuthResult.Available).accounts
        assertEquals(listOf("uid-1", "uid-2"), accounts.map { it.accountId })
        assertEquals("Main", accounts[0].name)
        assertEquals("DE11 1111", accounts[1].name)
        assertEquals("Test Bank", accounts[1].institution)
    }

    @Test fun `poll auth reports a failed or timed out authorization as an error`() {
        val client = ActualServerClient()
        val error = client.parseEnableBankingAuth(JSONObject("""{"data":{"error":"Polling timed out"}}"""))
        assertEquals(EnableBankingAuthResult.Error("Polling timed out"), error)
    }

    @Test fun `transactions download uses the long timeout and Actual's normalized rows in cents`() {
        val transport = FakeTransport(mapOf("/enablebanking/transactions" to (200 to """
            {"status":"ok","data":{"transactions":{"all":[
              {"transactionId":"eb-1","date":"2026-09-18","payeeName":"Landlord","notes":"Rent","booked":true,
               "transactionAmount":{"amount":"-900.00","currency":"EUR"}},
              {"transactionId":"eb-2","date":"2026-09-19","payeeName":"","booked":false,
               "transactionAmount":{"amount":"12.34","currency":"EUR"}}
            ]}},"balances":[],"startingBalance":0}}""".trimIndent())))

        val download = ActualServerClient(transport).downloadEnableBankingTransactions(server, "token", "uid-1", "2026-07-01")

        assertEquals(BANK_SYNC_READ_TIMEOUT_MILLIS, transport.requests.single().readTimeoutMillis)
        val body = transport.body("/enablebanking/transactions")
        assertEquals("uid-1", body.getString("accountId"))
        assertEquals("2026-07-01", body.getString("startDate"))
        assertEquals("uid-1", download.externalAccountId)
        assertEquals("ok", download.status)
        assertEquals(listOf(-90_000L, 1_234L), download.transactions.map { it.amountCents })
        // Actual keeps a blank payee name blank (#1002).
        assertEquals(listOf("Landlord", ""), download.transactions.map { it.payeeName })
        assertEquals(listOf(true, false), download.transactions.map { it.booked })
        assertEquals("Rent", download.transactions.first().notes)
    }

    @Test fun `an expired consent is stored as reauthorization required`() {
        val transport = FakeTransport(mapOf("/enablebanking/transactions" to
            (200 to """{"status":"ok","data":{"error_type":"ITEM_ERROR","error_code":"ITEM_LOGIN_REQUIRED"}}""")))

        val download = ActualServerClient(transport).downloadEnableBankingTransactions(server, "token", "uid-1", "2026-07-01")

        assertEquals("reauth-required", download.status)
        assertTrue(download.transactions.isEmpty())
    }

    @Test fun `rate limits and unknown errors map to Actual's statuses`() {
        fun status(code: String) = ActualServerClient(FakeTransport(mapOf("/enablebanking/transactions" to
            (200 to """{"data":{"error_type":"$code","error_code":"$code"}}""")))
        ).downloadEnableBankingTransactions(server, "token", "uid-1", "2026-07-01").status

        assertEquals("rate-limit-exceeded", status("RATE_LIMIT_EXCEEDED"))
        assertEquals("failed", status("INTERNAL_ERROR"))
        assertEquals("failed", status("INVALID_INPUT"))
    }

    @Test fun `a server without Enable Banking says so for downloads`() {
        try {
            ActualServerClient(FakeTransport(emptyMap())).downloadEnableBankingTransactions(server, "token", "uid-1", "2026-07-01")
            fail("expected an unsupported-server error")
        } catch (error: IllegalStateException) {
            assertEquals("This Actual server does not support Enable Banking bank sync.", error.message)
        }
    }
}
