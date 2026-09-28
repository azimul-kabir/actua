package com.azimulkabir.actua.data.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Request-shape cases from the server/file-lifecycle parity audit (docs/SERVER_FILE_PARITY.md),
 * checked against Actual's client and sync-server at 59fe126f.
 */
class ActualServerFileProtocolParityTest {
    @Test
    fun `new budget upload matches Actual's first upload of an unencrypted file`() {
        val transport = RecordingTransport { ActualHttpResponse(200, """{"status":"ok","groupId":"g-new"}""".encodeToByteArray()) }

        val groupId = ActualServerClient(transport).uploadFile("https://actual.test", "token", "file-1", "Budget", byteArrayOf(1))

        assertEquals("g-new", groupId)
        val headers = transport.last.headers
        assertEquals("application/encrypted-file", headers["Content-Type"])
        assertEquals("token", headers["X-ACTUAL-TOKEN"])
        assertEquals("file-1", headers["X-ACTUAL-FILE-ID"])
        assertEquals("2", headers["X-ACTUAL-FORMAT"])
        // A first upload has no group yet, and the server assigns one. Sending a group id
        // for a new file, or encryption metadata for plaintext, would change server semantics.
        assertFalse(headers.containsKey("X-ACTUAL-GROUP-ID"))
        assertFalse(headers.containsKey("X-ACTUAL-ENCRYPT-META"))
    }

    @Test
    fun `budget names are encoded like JavaScript encodeURIComponent`() {
        val transport = RecordingTransport { ActualHttpResponse(200, """{"status":"ok","groupId":"g"}""".encodeToByteArray()) }

        ActualServerClient(transport).uploadFile("https://actual.test", "token", "file-1", "Café Ü (home) ~!*'", byteArrayOf())

        // encodeURIComponent("Café Ü (home) ~!*'") in Node.
        assertEquals("Caf%C3%A9%20%C3%9C%20(home)%20~!*'", transport.last.headers["X-ACTUAL-NAME"])
    }

    @Test
    fun `custom headers never replace Actual protocol headers`() {
        val transport = RecordingTransport { ActualHttpResponse(200, byteArrayOf()) }
        val client = ActualServerClient(transport).apply {
            customHeaders = mapOf(
                "CF-Access-Client-Id" to "client-id",
                "X-ACTUAL-TOKEN" to "wrong-token",
                "X-ACTUAL-FILE-ID" to "wrong-file",
                "Content-Type" to "text/plain",
            )
        }

        client.postSync("https://actual.test", "token", byteArrayOf(1))

        val headers = transport.last.headers
        assertEquals("client-id", headers["CF-Access-Client-Id"])
        assertEquals("token", headers["X-ACTUAL-TOKEN"])
        assertEquals("application/actual-sync", headers["Content-Type"])

        client.downloadFile("https://actual.test", "token", "file-1")
        assertEquals("file-1", transport.last.headers["X-ACTUAL-FILE-ID"])
    }

    @Test
    fun `server path prefix is kept like Actual's joinURL`() {
        val transport = RecordingTransport {
            ActualHttpResponse(200, """{"status":"ok","data":[]}""".encodeToByteArray())
        }

        ActualServerClient(transport).listFiles("https://example.test/actual/", "token")

        assertEquals("/actual/sync/list-user-files", transport.last.url.path)
    }

    @Test
    fun `servers without login-methods fall back to password like Actual`() {
        val client = ActualServerClient(RecordingTransport { ActualHttpResponse(404, byteArrayOf()) })

        assertEquals(listOf(LoginMethod("password", "Password", true)), client.loginMethods("https://actual.test"))
    }

    @Test
    fun `login error returned with HTTP 200 surfaces the server reason`() {
        // Header-auth servers reply 200 {status:"error"} (e.g. proxy-not-trusted).
        val client = ActualServerClient(RecordingTransport {
            ActualHttpResponse(200, """{"status":"error","reason":"proxy-not-trusted"}""".encodeToByteArray())
        })

        val error = assertThrows(IllegalStateException::class.java) { client.login("https://actual.test", "secret") }

        assertEquals("proxy-not-trusted", error.message)
    }

    @Test
    fun `OpenID return URL has no trailing slash before Actual appends openid-cb`() {
        val transport = RecordingTransport {
            ActualHttpResponse(200, """{"status":"ok","data":{"returnUrl":"https://idp.test/auth"}}""".encodeToByteArray())
        }

        ActualServerClient(transport).startOpenIdLogin("https://actual.test", "http://localhost:4321/")

        val body = JSONObject(transport.last.body!!.decodeToString())
        assertEquals("http://localhost:4321", body.getString("returnUrl"))
        assertFalse(body.has("password"))
    }

    private class RecordingTransport(
        private val response: (ActualHttpRequest) -> ActualHttpResponse,
    ) : ActualHttpTransport {
        lateinit var last: ActualHttpRequest
        override fun execute(request: ActualHttpRequest): ActualHttpResponse {
            last = request
            return response(request)
        }
    }
}
