package com.azimulkabir.actua.data.bank

import android.content.Context
import java.util.concurrent.TimeUnit

/** Device setting for downloading bank transactions each time the app opens. Off by default. */
class BankSyncOnOpenPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("bank_sync_on_open", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) = preferences.edit().putBoolean("enabled", value).apply()
}

object BankSyncOnOpen {
    /** An account synced more recently than this is left alone when the app opens. */
    val COOLDOWN_MILLIS: Long = TimeUnit.MINUTES.toMillis(30)

    /**
     * The linked, open accounts whose last bank sync (Actual's `last_sync`, epoch milliseconds, shared
     * with other clients) is missing, unreadable, in the future, or at least [cooldownMillis] old.
     */
    fun dueAccountIds(
        accounts: List<com.azimulkabir.actua.data.budget.ActualBudgetDatabase.BankSyncAccount>,
        nowMillis: Long,
        cooldownMillis: Long = COOLDOWN_MILLIS,
    ): Set<String> = accounts.filter { account ->
        val last = account.lastSync?.trim()?.toLongOrNull()
        !account.closed && (last == null || last > nowMillis || nowMillis - last >= cooldownMillis)
    }.mapTo(linkedSetOf()) { it.id }
}
