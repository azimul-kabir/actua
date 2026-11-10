package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.azimulkabir.actua.billing.SupportPurchaseState

/** Where [SupporterPerks] keeps the cached supporter status. */
interface SupporterPerksStore {
    var isSupporter: Boolean
    var lastCheckedAt: Long
}

private class SharedPreferencesSupporterPerksStore(context: Context) : SupporterPerksStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override var isSupporter: Boolean
        get() = preferences.getBoolean(IS_SUPPORTER, false)
        set(value) = preferences.edit { putBoolean(IS_SUPPORTER, value) }

    override var lastCheckedAt: Long
        get() = preferences.getLong(LAST_CHECKED_AT, 0L)
        set(value) = preferences.edit { putLong(LAST_CHECKED_AT, value) }

    private companion object {
        const val PREFERENCES_NAME = "supporter_perks"
        const val IS_SUPPORTER = "is_supporter"
        const val LAST_CHECKED_AT = "last_checked_at"
    }
}

/**
 * Device-local cache of whether the user is an Actua Supporter, so cosmetic extras can be gated
 * app-wide without opening a Play connection each time. Play Billing stays the source of truth:
 * the cache is refreshed whenever a billing manager has confirmed the user's purchases, and at
 * most once a day at app start. It never touches budget data and is never sent anywhere.
 *
 * Extras are cosmetic only. Each one registers a reset with [register]; when the status turns
 * from supporter to non-supporter (a refund or a lapsed subscription), [resetAll] puts every
 * extra back to its default.
 */
class SupporterPerks(private val store: SupporterPerksStore) {
    constructor(context: Context) : this(SharedPreferencesSupporterPerksStore(context))

    /** The last status Play confirmed. False until the first check, and always false on fdroid. */
    val isSupporter: Boolean get() = store.isSupporter

    /** Whether the last check is old enough that the app-start check should run again. */
    fun isCheckDue(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis - store.lastCheckedAt >= CHECK_INTERVAL_MILLIS

    /**
     * Stores [isSupporter] as confirmed by Play at [nowMillis]. Returns true when this turned a
     * supporter into a non-supporter, in which case every extra has been reset.
     */
    fun record(isSupporter: Boolean, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lapsed = store.isSupporter && !isSupporter
        store.isSupporter = isSupporter
        store.lastCheckedAt = nowMillis
        if (lapsed) resetAll()
        return lapsed
    }

    /** Records [state] once Play has answered; an unconfirmed state never changes the cache. */
    fun record(state: SupportPurchaseState, nowMillis: Long = System.currentTimeMillis()): Boolean =
        state.purchasesRestored && record(state.isSupporter, nowMillis)

    companion object {
        const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000

        private val resets = linkedMapOf<String, () -> Unit>()

        /** Registers how the extra called [id] returns to its default. Registering again replaces it. */
        @Synchronized
        fun register(id: String, reset: () -> Unit) {
            resets[id] = reset
        }

        /** Resets every registered extra to its default. */
        fun resetAll() {
            val actions = synchronized(this) { resets.values.toList() }
            actions.forEach { it() }
        }

        @Synchronized
        internal fun clearRegistrations() = resets.clear()
    }
}
