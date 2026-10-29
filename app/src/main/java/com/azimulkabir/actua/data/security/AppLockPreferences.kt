package com.azimulkabir.actua.data.security

import android.content.Context

/**
 * Device-local app-lock settings. Off by default. They are deliberately not part of the budget or
 * of any synced preference: locking one phone must not lock the same budget elsewhere.
 */
class AppLockPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var enabled: Boolean
        get() = preferences.getBoolean(ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(ENABLED, value).apply()
        }

    var timeout: AppLockTimeout
        get() = AppLockTimeout.fromStored(preferences.getString(TIMEOUT, null))
        set(value) {
            preferences.edit().putString(TIMEOUT, value.name).apply()
        }

    companion object {
        internal const val PREFERENCES_NAME = "app_lock_preferences"
        internal const val ENABLED = "enabled"
        internal const val TIMEOUT = "timeout"
    }
}
