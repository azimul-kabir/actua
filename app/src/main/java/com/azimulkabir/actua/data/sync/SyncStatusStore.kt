package com.azimulkabir.actua.data.sync

import android.content.Context
import com.azimulkabir.actua.data.network.ActualServerException
import java.io.IOException
import java.security.cert.CertificateException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SyncStatus(
    val running: Boolean,
    val lastAttemptMillis: Long,
    val lastSuccessMillis: Long,
    val sentMessages: Int,
    val receivedMessages: Int,
    val error: String?,
    val lastBackgroundRefreshMillis: Long,
    val activeTrigger: String?,
    val lastDurationMillis: Long,
    val lastForegroundRefreshMillis: Long,
)

/**
 * Stable persisted codes for sync failures, translated only when displayed. Screens map these to
 * `sync_error_*` strings; anything that is not a code (legacy installs, message-bearing errors)
 * is shown as-is.
 */
enum class SyncErrorCode(val persistedValue: String) {
    ENCRYPTION_KEY_UNAVAILABLE("encryption_key_unavailable"),
    UNAUTHORIZED("unauthorized"),
    CERTIFICATE("certificate"),
    NETWORK("network"),
    OUT_OF_SYNC("out_of_sync"),
    CLOCK("clock"),
    INVALID_SERVER_RESPONSE("invalid_server_response"),
    SERVER_REQUEST("server_request"),
    UNKNOWN("unknown"),
}

/** Marks the locked-encrypted-budget failure so its code, not an exception text, is persisted. */
sealed class SyncStatusException : Exception() {
    data object EncryptionKeyUnavailable : SyncStatusException()
}


/** In-process signals let visible screens react to headless WorkManager completion. */
object SyncSignals {
    private val mutableStatusGeneration = MutableStateFlow(0L)
    private val mutableDataGeneration = MutableStateFlow(0L)

    val statusGeneration: StateFlow<Long> = mutableStatusGeneration.asStateFlow()
    val dataGeneration: StateFlow<Long> = mutableDataGeneration.asStateFlow()

    internal fun statusChanged() { mutableStatusGeneration.update { it + 1 } }
    fun dataChanged() { mutableDataGeneration.update { it + 1 } }
}

/** Device-local operational state; no credentials or budget contents are stored here. */
class SyncStatusStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("actua-sync-status", Context.MODE_PRIVATE)

    fun read() = SyncStatus(
        preferences.getBoolean("running", false), preferences.getLong("lastAttempt", 0),
        preferences.getLong("lastSuccess", 0), preferences.getInt("sent", 0),
        preferences.getInt("received", 0), preferences.getString("error", null),
        preferences.getLong("lastBackgroundRefresh", 0),
        preferences.getString("activeTrigger", null),
        preferences.getLong("lastDuration", 0),
        preferences.getLong("lastForegroundRefresh", 0),
    )

    fun started(trigger: String = SYNC_TRIGGER_MANUAL, now: Long = System.currentTimeMillis()) {
        val stableTrigger = stableTriggerCode(trigger)
        preferences.edit().putBoolean("running", true).putLong("lastAttempt", now)
            .putString("activeTrigger", stableTrigger).remove("error").apply()
        SyncSignals.statusChanged()
    }

    fun succeeded(outcome: SyncOutcome, now: Long = System.currentTimeMillis()) {
        val started = preferences.getLong("lastAttempt", now)
        val trigger = preferences.getString("activeTrigger", null)
        preferences.edit().putBoolean("running", false).putLong("lastSuccess", now)
            .putLong("lastDuration", (now - started).coerceAtLeast(0L))
            .putInt("sent", outcome.sentMessages).putInt("received", outcome.receivedMessages)
            .apply {
                if (trigger == SYNC_TRIGGER_APP_OPEN) putLong("lastForegroundRefresh", now)
            }
            .remove("activeTrigger").remove("error").apply()
        SyncSignals.statusChanged()
    }

    fun failed(error: Throwable) {
        // Errors with specific actionable wording (Actual's sync rejection reasons) keep their
        // message for display as-is; everything else persists a stable localized-at-display code.
        val persisted = (error as? ActualServerException.SyncRejected)?.message
            ?: syncErrorCode(error).persistedValue
        preferences.edit().putBoolean("running", false)
            .remove("activeTrigger")
            .putString("error", persisted).apply()
        SyncSignals.statusChanged()
    }

    fun stoppedWithoutSync() {
        preferences.edit().putBoolean("running", false).remove("activeTrigger").apply()
        SyncSignals.statusChanged()
    }

    fun backgroundRefreshFinished(now: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("lastBackgroundRefresh", now).apply()
        SyncSignals.statusChanged()
    }

    fun foregroundRefreshFinished(now: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("lastForegroundRefresh", now).apply()
        SyncSignals.statusChanged()
    }

    /** Maps previously persisted display labels to the stable codes, so old installs keep working. */
    private fun stableTriggerCode(value: String?): String = when (value) {
        SYNC_TRIGGER_MANUAL, "Manual", "Sync" -> SYNC_TRIGGER_MANUAL
        SYNC_TRIGGER_AFTER_CHANGE, "After change" -> SYNC_TRIGGER_AFTER_CHANGE
        SYNC_TRIGGER_APP_OPEN, "App open" -> SYNC_TRIGGER_APP_OPEN
        SYNC_TRIGGER_BACKGROUND, "Background" -> SYNC_TRIGGER_BACKGROUND
        SYNC_TRIGGER_PULL_REFRESH, "Pull to refresh" -> SYNC_TRIGGER_PULL_REFRESH
        else -> SYNC_TRIGGER_BACKGROUND
    }

    private fun syncErrorCode(error: Throwable): SyncErrorCode {
        val causes = generateSequence(error) { it.cause }.toList()
        return when {
            causes.any { it is SyncStatusException.EncryptionKeyUnavailable } ->
                SyncErrorCode.ENCRYPTION_KEY_UNAVAILABLE
            causes.any { it is ActualServerException.Unauthorized || it is ActualServerException.SessionExpired } ->
                SyncErrorCode.UNAUTHORIZED
            causes.any { it is CertificateException } -> SyncErrorCode.CERTIFICATE
            causes.any { it is ActualSyncException.OutOfSync } -> SyncErrorCode.OUT_OF_SYNC
            causes.any {
                it is HlcException.ClockDrift || it is HlcException.CounterOverflow ||
                    it is ActualSyncException.ClockDrift
            } -> SyncErrorCode.CLOCK
            causes.any { it is ActualServerException.InvalidResponse } -> SyncErrorCode.INVALID_SERVER_RESPONSE
            causes.any { it is ActualServerException.Http } -> SyncErrorCode.SERVER_REQUEST
            causes.any { it is IOException } -> SyncErrorCode.NETWORK
            else -> SyncErrorCode.UNKNOWN
        }
    }
}
