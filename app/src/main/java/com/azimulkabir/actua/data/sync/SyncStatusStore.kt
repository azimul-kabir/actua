package com.azimulkabir.actua.data.sync

import android.content.Context
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

    fun started(trigger: String = "Sync", now: Long = System.currentTimeMillis()) {
        preferences.edit().putBoolean("running", true).putLong("lastAttempt", now)
            .putString("activeTrigger", trigger).remove("error").apply()
        SyncSignals.statusChanged()
    }

    fun succeeded(outcome: SyncOutcome, now: Long = System.currentTimeMillis()) {
        val started = preferences.getLong("lastAttempt", now)
        val trigger = preferences.getString("activeTrigger", null)
        preferences.edit().putBoolean("running", false).putLong("lastSuccess", now)
            .putLong("lastDuration", (now - started).coerceAtLeast(0L))
            .putInt("sent", outcome.sentMessages).putInt("received", outcome.receivedMessages)
            .apply {
                if (trigger == "App open") putLong("lastForegroundRefresh", now)
            }
            .remove("activeTrigger").remove("error").apply()
        SyncSignals.statusChanged()
    }

    fun failed(error: Throwable) {
        preferences.edit().putBoolean("running", false)
            .remove("activeTrigger")
            .putString("error", error.message ?: error::class.java.simpleName).apply()
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
}
