package com.azimulkabir.actua.data.preferences

import android.content.Context

/**
 * Device-local controls for optional payee-location features.
 *
 * Recording is deliberately disabled by default. The preference only represents the user's
 * opt-in; callers must still verify foreground permission and obtain a fresh location before
 * reading or writing coordinates.
 */
class LocationPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var recordPayeeLocations: Boolean
        get() = preferences.getBoolean(RECORD_PAYEE_LOCATIONS, false)
        set(value) {
            preferences.edit().putBoolean(RECORD_PAYEE_LOCATIONS, value).apply()
        }

    companion object {
        internal const val PREFERENCES_NAME = "location_preferences"
        internal const val RECORD_PAYEE_LOCATIONS = "record_payee_locations"
    }
}
