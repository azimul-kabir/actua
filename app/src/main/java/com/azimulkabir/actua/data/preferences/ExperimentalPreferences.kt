package com.azimulkabir.actua.data.preferences

import android.content.Context

/**
 * Opt-in switches for features that follow Actual's experimental flags. Device-local like Actual's
 * own `flags.*` preferences: they are never synced and don't change any budget data. Everything
 * defaults to off, so Actua behaves as before until a user turns one on.
 */
class ExperimentalPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "experimental_preferences", Context.MODE_PRIVATE,
    )

    /** Actual's `enableBanking` flag: Enable Banking (EU banks) as a bank-sync provider. */
    var enableBanking: Boolean
        get() = preferences.getBoolean(ENABLE_BANKING, false)
        set(value) { preferences.edit().putBoolean(ENABLE_BANKING, value).apply() }

    private companion object { const val ENABLE_BANKING = "enable_banking" }
}
