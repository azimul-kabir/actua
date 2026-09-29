package com.azimulkabir.actua.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Readable errors for Actual's login and file-access reason codes (sync-server `app-account.js`,
 * `app-sync.ts` at 59fe126f).
 */
class ActualServerLoginErrorsTest {
    private fun client(vararg routes: Pair<String, Pair<Int, String>>): Pair<ActualServerClient, MutableList<String>> {
        val paths = mutableListOf<String>()
        val table = routes.toMap()
        return ActualServerClient { request ->
            paths += request.url.path
            val (status, body) = table[request.url.path] ?: (404 to "")
            ActualHttpResponse(status, body.encodeToByteArray())
        } to paths
    }

    private fun loginRejection(vararg routes: Pair<String, Pair<Int, String>>): ActualServerException.LoginRejected {
        val (client, _) = client(*routes)
        return assertThrows(ActualServerException.LoginRejected::class.java) { client.login("https://actual.test", "secret") }
    }

    @Test
    fun `wrong password on a set-up server is an incorrect password`() {
        val error = loginRejection(
            "/account/login" to (400 to """{"status":"error","reason":"invalid-password"}"""),
            "/account/needs-bootstrap" to (200 to """{"status":"ok","data":{"bootstrapped":true}}"""),
        )
        assertEquals("invalid-password", error.reason)
        assertEquals("Incorrect server password.", error.message)
    }

    @Test
    fun `invalid-password from a server that isn't set up says so`() {
        val error = loginRejection(
            "/account/login" to (400 to """{"status":"error","reason":"invalid-password"}"""),
            "/account/needs-bootstrap" to (200 to """{"status":"ok","data":{"bootstrapped":false}}"""),
        )
        assertEquals(REASON_NEEDS_BOOTSTRAP, error.reason)
        assertEquals(
            "This Actual server hasn't been set up yet. Open it in a browser to create its password, then connect.",
            error.message,
        )
    }

    @Test
    fun `an unanswered bootstrap check keeps the password message`() {
        val error = loginRejection("/account/login" to (400 to """{"status":"error","reason":"invalid-password"}"""))
        assertEquals("invalid-password", error.reason)
    }

    @Test
    fun `rate limiting is explained with or without a JSON body`() {
        val json = loginRejection("/account/login" to (429 to """{"status":"error","reason":"too-many-requests"}"""))
        assertEquals("Too many sign-in attempts. Wait 15 minutes, then try again.", json.message)

        val plain = loginRejection("/account/login" to (429 to "Too Many Requests"))
        assertEquals("too-many-requests", plain.reason)
    }

    @Test
    fun `header-auth reasons returned with HTTP 200 are readable`() {
        val error = loginRejection("/account/login" to (200 to """{"status":"error","reason":"invalid-header"}"""))
        assertEquals(
            "This server signs in through an authentication proxy, but the proxy didn't send a password header.",
            error.message,
        )
    }

    @Test
    fun `unknown reasons are still shown`() {
        val error = loginRejection("/account/login" to (400 to """{"status":"error","reason":"something-new"}"""))
        assertEquals("Sign-in failed: something-new", error.message)
    }

    @Test
    fun `successful login makes no bootstrap request`() {
        val (client, paths) = client("/account/login" to (200 to """{"status":"ok","data":{"token":"t"}}"""))
        assertEquals("t", client.login("https://actual.test", "secret"))
        assertFalse(paths.contains("/account/needs-bootstrap"))
    }

    @Test
    fun `OpenID first-login password requirement keeps its reason code`() {
        val (client, _) = client("/account/login" to (400 to """{"status":"error","reason":"invalid-password"}"""))
        val error = assertThrows(ActualServerException.LoginRejected::class.java) {
            client.startOpenIdLogin("https://actual.test", "http://localhost:1234")
        }
        assertEquals("invalid-password", error.reason)
    }

    @Test
    fun `file-access-not-allowed is distinct from other 403s`() {
        val (denied, _) = client(
            "/sync/delete-user-file" to (403 to "file-access-not-allowed"),
            "/sync/download-user-file" to (403 to "file-access-not-allowed"),
        )
        assertThrows(ActualServerException.FileAccessDenied::class.java) {
            denied.deleteFile("https://actual.test", "token", "file-1")
        }
        assertThrows(ActualServerException.FileAccessDenied::class.java) {
            denied.downloadFile("https://actual.test", "token", "file-1")
        }

        val (proxy, _) = client("/sync/delete-user-file" to (403 to "<html>Forbidden</html>"))
        assertThrows(ActualServerException.Unauthorized::class.java) {
            proxy.deleteFile("https://actual.test", "token", "file-1")
        }
    }
}
