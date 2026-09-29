package com.azimulkabir.actua.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session rejection mapping, matching Actual's `validateSession` and `getServerErrorReason` (59fe126f). */
class ActualServerSessionTest {
    @Test
    fun `token-expired and token-not-found are rejected sessions`() {
        assertTrue(isRejectedSession("""{"status":"error","reason":"token-expired"}""".encodeToByteArray()))
        assertTrue(
            isRejectedSession(
                """{"status":"error","reason":"unauthorized","details":"token-not-found"}""".encodeToByteArray(),
            ),
        )
    }

    @Test
    fun `other unauthorized bodies are not rejected sessions`() {
        assertFalse(isRejectedSession("""{"status":"error","reason":"unauthorized"}""".encodeToByteArray()))
        assertFalse(isRejectedSession("file-access-not-allowed".encodeToByteArray()))
        assertFalse(isRejectedSession(byteArrayOf()))
        assertFalse(isRejectedSession("<html>Access denied</html>".encodeToByteArray()))
    }

    @Test
    fun `rejected sessions raise SessionExpired from file and sync endpoints`() {
        val expired = ActualServerClient {
            ActualHttpResponse(401, """{"status":"error","reason":"token-expired"}""".encodeToByteArray())
        }
        assertThrows(ActualServerException.SessionExpired::class.java) { expired.listFiles("https://actual.test", "token") }
        assertThrows(ActualServerException.SessionExpired::class.java) {
            expired.postSync("https://actual.test", "token", byteArrayOf())
        }

        val unknown = ActualServerClient {
            ActualHttpResponse(401, """{"status":"error","reason":"unauthorized","details":"token-not-found"}""".encodeToByteArray())
        }
        assertThrows(ActualServerException.SessionExpired::class.java) {
            unknown.downloadFile("https://actual.test", "token", "file-1")
        }
    }

    @Test
    fun `proxy and file-access rejections stay Unauthorized`() {
        val proxy = ActualServerClient { ActualHttpResponse(401, "Unauthorized".encodeToByteArray()) }
        assertThrows(ActualServerException.Unauthorized::class.java) { proxy.listFiles("https://actual.test", "token") }

        val forbidden = ActualServerClient { ActualHttpResponse(403, "file-access-not-allowed".encodeToByteArray()) }
        assertThrows(ActualServerException.Unauthorized::class.java) {
            forbidden.deleteFile("https://actual.test", "token", "file-1")
        }
    }
}
