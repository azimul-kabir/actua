package com.azimulkabir.actua.data.sync

import android.content.Context

/**
 * Per budget, the newest message-log timestamp that a completed sync had already sent or received.
 * The clock saved with every local write can't tell edits from synced changes, so this device-local
 * marker is what says how many log entries haven't reached the server. No budget contents are stored.
 */
class SyncedLogMarkers(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("actua-synced-log", Context.MODE_PRIVATE)

    /** Null when no completed sync (or fresh download) has been recorded for [budgetId] on this device. */
    fun read(budgetId: String): String? = preferences.getString(budgetId, null)

    /** [timestamp] is the log's high-water mark, or null for an empty log (everything later is unsynced). */
    fun record(budgetId: String, timestamp: String?) {
        preferences.edit().putString(budgetId, timestamp ?: HlcTimestamp.ZERO.toString()).apply()
    }
}
