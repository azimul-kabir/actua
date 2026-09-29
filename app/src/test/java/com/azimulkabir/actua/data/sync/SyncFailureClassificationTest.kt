package com.azimulkabir.actua.data.sync

import com.azimulkabir.actua.data.network.ActualHttpResponse
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.ActualServerException
import com.azimulkabir.actua.data.network.syncRejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Actual's `/sync` rejections (sync-server `app-sync.ts`, `app-sync/validation.js`) and local sync
 * errors map to typed errors and a retry decision; see docs/SYNC_PARITY.md section 8.
 */
class SyncFailureClassificationTest {
    @Test
    fun `sync server reasons become typed rejections that are not retried`() {
        listOf(
            "file-old-version", "file-needs-upload", "file-key-mismatch",
            "file-has-reset", "file-has-new-key", "file-not-found",
        ).forEach { reason ->
            val error = postSyncFailure(400, reason)
            assertTrue(reason, error is ActualServerException.SyncRejected)
            assertEquals(reason, (error as ActualServerException.SyncRejected).reason)
            assertFalse(reason, error.message.orEmpty().startsWith("HTTP"))
            assertFalse(reason, error.message.orEmpty().contains("refused to sync"))
            assertFalse(reason, SyncFailurePolicy.isRetryable(error))
            assertFalse(reason, SyncFailurePolicy.triesFallback(error))
        }
    }

    @Test
    fun `json reasons and unknown reason codes are rejections too`() {
        val sinceRequired = postSyncFailure(422, """{"status":"error","reason":"since-required"}""")
        assertEquals("since-required", (sinceRequired as ActualServerException.SyncRejected).reason)
        assertFalse(SyncFailurePolicy.isRetryable(sinceRequired))

        val unknown = postSyncFailure(400, "something-new\n")
        assertEquals("something-new", (unknown as ActualServerException.SyncRejected).reason)
        assertTrue(unknown.message.orEmpty().contains("something-new"))
    }

    @Test
    fun `server errors, throttling and non-reason bodies stay retryable`() {
        listOf(
            500 to "internal-error",
            503 to "<html>Bad gateway</html>",
            429 to "too-many-requests",
            408 to "timeout",
            400 to "<html>Bad request</html>",
            413 to "",
        ).forEach { (status, body) ->
            val error = postSyncFailure(status, body)
            assertTrue("$status $body", error is ActualServerException.Http)
            assertTrue("$status $body", SyncFailurePolicy.isRetryable(error))
            assertTrue("$status $body", SyncFailurePolicy.triesFallback(error))
        }
        assertTrue(SyncFailurePolicy.isRetryable(IOException("network-failure")))
    }

    @Test
    fun `sign-in and file-access failures are not retried`() {
        val expired = postSyncFailure(401, """{"status":"error","reason":"unauthorized","details":"token-not-found"}""")
        assertEquals(ActualServerException.SessionExpired, expired)
        assertFalse(SyncFailurePolicy.isRetryable(expired))
        assertFalse(SyncFailurePolicy.triesFallback(expired))

        val proxy = postSyncFailure(401, "Unauthorized")
        assertEquals(ActualServerException.Unauthorized, proxy)
        assertFalse(SyncFailurePolicy.isRetryable(proxy))
        // Another address may sit behind a different proxy.
        assertTrue(SyncFailurePolicy.triesFallback(proxy))

        val denied = postSyncFailure(403, "file-access-not-allowed")
        assertEquals(ActualServerException.FileAccessDenied, denied)
        assertFalse(SyncFailurePolicy.isRetryable(denied))
        assertFalse(SyncFailurePolicy.triesFallback(denied))
    }

    @Test
    fun `local clock drift and decryption failures are not retried but out-of-sync is`() {
        listOf(ActualSyncException.ClockDrift, ActualSyncException.DecryptionFailed).forEach { error ->
            assertFalse(error.toString(), SyncFailurePolicy.isRetryable(error))
            assertFalse(error.toString(), SyncFailurePolicy.triesFallback(error))
            assertTrue(error.toString(), error.message.orEmpty().contains("local changes are kept"))
        }
        // The next run rebuilds the Merkle tree from the log, which can recover.
        assertTrue(SyncFailurePolicy.isRetryable(ActualSyncException.OutOfSync))
    }

    @Test
    fun `reason parsing accepts plain text and json and ignores other bodies`() {
        assertEquals("file-has-reset", syncRejectionReason(" file-has-reset\n".encodeToByteArray()))
        assertEquals("file-not-found", syncRejectionReason("""{"status":"error","reason":"file-not-found"}""".encodeToByteArray()))
        assertNull(syncRejectionReason("""{"status":"error"}""".encodeToByteArray()))
        assertNull(syncRejectionReason("{not json".encodeToByteArray()))
        assertNull(syncRejectionReason("<html>Bad request</html>".encodeToByteArray()))
        assertNull(syncRejectionReason("File has reset".encodeToByteArray()))
        assertNull(syncRejectionReason(byteArrayOf()))
    }

    @Test
    fun `loop limit fails after ten passes with the same diff time`() {
        val limit = SyncLoopLimit()
        repeat(SyncLoopLimit.SAME_DIFF_LIMIT) { limit.recordDivergence(60_000L, localChanged = false) }
        assertOutOfSync { limit.recordDivergence(60_000L, localChanged = false) }
    }

    @Test
    fun `loop limit keeps going while the diff moves and stops at one hundred passes`() {
        val limit = SyncLoopLimit()
        repeat(SyncLoopLimit.TOTAL_LIMIT) { pass -> limit.recordDivergence(pass * 60_000L, localChanged = false) }
        assertOutOfSync { limit.recordDivergence(SyncLoopLimit.TOTAL_LIMIT * 60_000L, localChanged = false) }
    }

    @Test
    fun `local changes during a pass reset the loop count`() {
        val limit = SyncLoopLimit()
        repeat(SyncLoopLimit.TOTAL_LIMIT * 2) { limit.recordDivergence(60_000L, localChanged = true) }
        repeat(SyncLoopLimit.SAME_DIFF_LIMIT) { limit.recordDivergence(60_000L, localChanged = false) }
        assertOutOfSync { limit.recordDivergence(60_000L, localChanged = false) }
    }

    private fun postSyncFailure(status: Int, body: String): Exception {
        val client = ActualServerClient { ActualHttpResponse(status, body.encodeToByteArray()) }
        return try {
            client.postSync("https://actual.test", "token", byteArrayOf(1))
            fail("Expected $status $body to fail")
            throw AssertionError()
        } catch (error: ActualServerException) {
            error
        }
    }

    private fun assertOutOfSync(block: () -> Unit) {
        try {
            block()
            fail("Expected OutOfSync")
        } catch (_: ActualSyncException.OutOfSync) {
        }
    }
}
