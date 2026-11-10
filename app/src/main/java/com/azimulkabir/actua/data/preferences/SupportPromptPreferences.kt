package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.core.content.edit

/**
 * Device-local bookkeeping for the one-time "Support Actua" prompt: how many transactions the
 * user has added in Actua, and whether the prompt has already been shown. It never touches
 * budget data and is never sent anywhere.
 */
class SupportPromptPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val addedTransactions: Int
        get() = preferences.getInt(ADDED_TRANSACTIONS, 0)

    var shown: Boolean
        get() = preferences.getBoolean(SHOWN, false)
        set(value) {
            preferences.edit { putBoolean(SHOWN, value) }
        }

    /** Counts one transaction the user added. Stops counting once the threshold can't matter. */
    fun recordTransactionAdded() {
        val count = addedTransactions
        if (shown || count >= TRANSACTION_THRESHOLD) return
        preferences.edit { putInt(ADDED_TRANSACTIONS, count + 1) }
    }

    /** Whether the prompt should be shown now, by install age and transactions added. */
    fun isDue(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isDue(installedAtMillis(), nowMillis, addedTransactions, shown)

    private fun installedAtMillis(): Long? = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).firstInstallTime
    }.getOrNull()

    companion object {
        internal const val PREFERENCES_NAME = "support_prompt"
        internal const val ADDED_TRANSACTIONS = "added_transactions"
        internal const val SHOWN = "shown"
        const val DAYS_THRESHOLD = 30L
        const val TRANSACTION_THRESHOLD = 100
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /**
         * Due once [DAYS_THRESHOLD] days have passed since install or [TRANSACTION_THRESHOLD]
         * transactions were added, whichever comes first, and only if it was never shown. An
         * unknown install time only defers to the transaction count.
         */
        fun isDue(installedAtMillis: Long?, nowMillis: Long, addedTransactions: Int, shown: Boolean): Boolean {
            if (shown) return false
            if (addedTransactions >= TRANSACTION_THRESHOLD) return true
            return installedAtMillis != null && nowMillis - installedAtMillis >= DAYS_THRESHOLD * DAY_MILLIS
        }
    }
}
