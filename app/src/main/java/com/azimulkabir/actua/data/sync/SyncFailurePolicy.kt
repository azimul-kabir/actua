package com.azimulkabir.actua.data.sync

import com.azimulkabir.actua.data.network.ActualServerException

/**
 * Retry decisions for a failed sync run, following Actual's error classes (docs/SYNC_PARITY.md,
 * section 8). Nothing here touches local data: pending edits stay in the message log either way.
 */
internal object SyncFailurePolicy {
    /**
     * False when the failure persists until the user acts (signs in, fixes the device clock or the
     * budget in Actual), so WorkManager shouldn't retry it. The next scheduled or manual sync still runs.
     */
    fun isRetryable(error: Throwable): Boolean = when (error) {
        is ActualServerException.SessionExpired,
        is ActualServerException.Unauthorized,
        is ActualServerException.FileAccessDenied,
        is ActualServerException.SyncRejected,
        is ActualSyncException.ClockDrift,
        is ActualSyncException.DecryptionFailed,
        -> false
        else -> true
    }

    /**
     * The fallback is another address of the same server, so it would give the same answer when
     * that server rejected the session or file, or when the failure is local to this device.
     * Only an authorization refusal is retried there, since a different proxy may front it.
     */
    fun triesFallback(error: Throwable): Boolean =
        isRetryable(error) || error is ActualServerException.Unauthorized
}
