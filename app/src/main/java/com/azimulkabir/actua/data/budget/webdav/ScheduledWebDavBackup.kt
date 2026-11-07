package com.azimulkabir.actua.data.budget.webdav

import java.io.File

/**
 * One scheduled WebDAV upload: what to do with the newest local backup, and whether a failure is worth
 * retrying. The WorkManager worker only supplies the pieces and acts on the [Outcome], so this logic is the same
 * as it is in tests.
 *
 * A failure that another attempt cannot fix (a wrong password, a missing folder, an untrusted certificate, a
 * server without MOVE, a full server) is recorded and not retried; the next local backup tries again. One that
 * may pass (no network, a server error, a short upload) is retried with backoff up to [MAX_RETRIES] times and
 * recorded only if it still fails.
 */
class ScheduledWebDavBackup(
    private val isConfigured: () -> Boolean,
    private val hasPassphrase: () -> Boolean,
    private val latestArchive: (budgetId: String) -> File?,
    private val upload: (budgetId: String, archive: File) -> Unit,
    private val recordError: (String?) -> Unit,
    private val describe: (Throwable) -> String,
    private val passphraseMissingMessage: String,
) {
    enum class Outcome { SUCCESS, RETRY, FAILURE }

    /** [attempt] is WorkManager's `runAttemptCount`: 0 on the first run. */
    fun run(budgetId: String, attempt: Int): Outcome {
        // Disconnected since this was scheduled: nothing to do, and nothing to complain about.
        if (!isConfigured()) return Outcome.SUCCESS
        if (!hasPassphrase()) {
            recordError(passphraseMissingMessage)
            return Outcome.FAILURE
        }
        // No local backup yet: the next one schedules its own upload.
        val archive = latestArchive(budgetId) ?: return Outcome.SUCCESS

        return try {
            upload(budgetId, archive)
            Outcome.SUCCESS
        } catch (error: Exception) {
            if (isRetryable(error) && attempt < MAX_RETRIES) {
                Outcome.RETRY
            } else {
                recordError(describe(error))
                Outcome.FAILURE
            }
        }
    }

    companion object {
        const val MAX_RETRIES = 3

        /** Whether trying the same upload again could succeed without the user changing anything. */
        fun isRetryable(error: Throwable): Boolean = when (error) {
            is WebDavException.Network, is WebDavException.UploadIncomplete -> true
            is WebDavException.Server -> error.status >= 500 || error.status == 408 || error.status == 429
            is WebDavException.Unauthorized,
            is WebDavException.NotFound,
            is WebDavException.InsufficientStorage,
            is WebDavException.Tls,
            is WebDavException.Redirect,
            is WebDavException.MoveUnsupported,
            is WebDavException.MoveRejected,
            is WebDavException.NotTls,
            is WebDavException.InvalidSettings -> false
            else -> true
        }
    }
}
