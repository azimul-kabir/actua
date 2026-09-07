package com.azimulkabir.actua.data.sync

import android.content.Context

data class SyncStatus(
    val running: Boolean,
    val lastAttemptMillis: Long,
    val lastSuccessMillis: Long,
    val sentMessages: Int,
    val receivedMessages: Int,
    val error: String?,
)

/** Device-local operational state; no credentials or budget contents are stored here. */
class SyncStatusStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("actua-sync-status", Context.MODE_PRIVATE)

    fun read() = SyncStatus(
        preferences.getBoolean("running", false), preferences.getLong("lastAttempt", 0),
        preferences.getLong("lastSuccess", 0), preferences.getInt("sent", 0),
        preferences.getInt("received", 0), preferences.getString("error", null),
    )

    fun started(now: Long = System.currentTimeMillis()) {
        preferences.edit().putBoolean("running", true).putLong("lastAttempt", now).remove("error").apply()
    }

    fun succeeded(outcome: SyncOutcome, now: Long = System.currentTimeMillis()) {
        preferences.edit().putBoolean("running", false).putLong("lastSuccess", now)
            .putInt("sent", outcome.sentMessages).putInt("received", outcome.receivedMessages).remove("error").apply()
    }

    fun failed(error: Throwable) {
        preferences.edit().putBoolean("running", false)
            .putString("error", error.message ?: error::class.java.simpleName).apply()
    }

    fun stoppedWithoutSync() { preferences.edit().putBoolean("running", false).apply() }
}
