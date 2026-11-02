package com.azimulkabir.actua.data.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Actual 26.11's sign-out: `POST /account/logout` with `{ token }`, best effort (#1409). */
class ActualServerLogoutTest {
    @Test
    fun `logout posts the token to the account logout endpoint`() {
        val seen = mutableListOf<ActualHttpRequest>()
        val client = ActualServerClient { request ->
            seen += request
            ActualHttpResponse(200, """{"status":"ok","data":{}}""".encodeToByteArray())
        }

        assertTrue(client.logout("https://actual.test/", "secret-token"))

        val request = seen.single()
        assertEquals("POST", request.method)
        assertEquals("https://actual.test/account/logout", request.url.toString())
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("secret-token", JSONObject(request.body!!.decodeToString()).getString("token"))
        assertEquals(5_000, request.readTimeoutMillis)
    }

    @Test
    fun `a server without the endpoint still counts as having answered`() {
        val old = ActualServerClient { ActualHttpResponse(404, "Cannot POST /account/logout".encodeToByteArray()) }
        assertTrue(old.logout("https://actual.test", "t"))
        val rejected = ActualServerClient { ActualHttpResponse(400, """{"status":"error","reason":"invalid-token"}""".encodeToByteArray()) }
        assertTrue(rejected.logout("https://actual.test", "t"))
    }

    @Test
    fun `an unreachable server never throws so signing out locally still works`() {
        val offline = ActualServerClient { throw IOException("offline") }
        assertFalse(offline.logout("https://actual.test", "t"))
        val timeout = ActualServerClient { throw java.net.SocketTimeoutException("timeout") }
        assertFalse(timeout.logout("https://actual.test", "t"))
    }
}
