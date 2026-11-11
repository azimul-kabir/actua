package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.core.content.edit

/**
 * Device-local bookkeeping for the one-time "rate Actua" request: how many transactions the user
 * has added, and whether the request was already made. It never touches budget data and is never
 * sent anywhere.
 */
class RatingPromptPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val addedTransactions: Int
        get() = preferences.getInt(ADDED_TRANSACTIONS, 0)

    var asked: Boolean
        get() = preferences.getBoolean(ASKED, false)
        set(value) {
            preferences.edit { putBoolean(ASKED, value) }
        }

    /** Counts one transaction the user added. Stops counting once the threshold can't matter. */
    fun recordTransactionAdded() {
        val count = addedTransactions
        if (asked || count >= TRANSACTION_THRESHOLD) return
        preferences.edit { putInt(ADDED_TRANSACTIONS, count + 1) }
    }

    /** Whether the request should be made now, by install age and transactions added. */
    fun isDue(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isDue(installedAtMillis(), nowMillis, addedTransactions, asked)

    private fun installedAtMillis(): Long? = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).firstInstallTime
    }.getOrNull()

    companion object {
        internal const val PREFERENCES_NAME = "rating_prompt"
        internal const val ADDED_TRANSACTIONS = "added_transactions"
        internal const val ASKED = "asked"
        const val DAYS_THRESHOLD = 14L
        const val TRANSACTION_THRESHOLD = 50
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /**
         * Due once [DAYS_THRESHOLD] days have passed since install and [TRANSACTION_THRESHOLD]
         * transactions were added, and only if it was never asked. Both are needed so the request
         * comes from someone who has really used the app. An unknown install time is never due.
         */
        fun isDue(installedAtMillis: Long?, nowMillis: Long, addedTransactions: Int, asked: Boolean): Boolean {
            if (asked || installedAtMillis == null) return false
            return nowMillis - installedAtMillis >= DAYS_THRESHOLD * DAY_MILLIS &&
                addedTransactions >= TRANSACTION_THRESHOLD
        }
    }
}
