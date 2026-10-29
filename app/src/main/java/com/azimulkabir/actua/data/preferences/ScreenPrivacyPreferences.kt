package com.azimulkabir.actua.data.preferences

import android.app.Activity
import android.content.Context
import android.view.WindowManager

/**
 * Device-local switch for hiding Actua's windows from the recents screen, screenshots and screen
 * recording. Off by default; it never touches budget data.
 *
 * Android has no recents-only flag: [WindowManager.LayoutParams.FLAG_SECURE] is what blanks the
 * recents card, and it blocks screenshots and screen capture as a side effect.
 */
class ScreenPrivacyPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var hideInRecents: Boolean
        get() = preferences.getBoolean(HIDE_IN_RECENTS, false)
        set(value) {
            preferences.edit().putBoolean(HIDE_IN_RECENTS, value).apply()
        }

    companion object {
        internal const val PREFERENCES_NAME = "screen_privacy_preferences"
        internal const val HIDE_IN_RECENTS = "hide_in_recents"

        /**
         * Sets or clears [WindowManager.LayoutParams.FLAG_SECURE] on [activity]'s window from the
         * stored preference. Call before the first frame (right after `super.onCreate`) in every
         * activity that can show budget data, and again after the setting changes.
         */
        fun apply(activity: Activity) = apply(activity, ScreenPrivacyPreferences(activity).hideInRecents)

        fun apply(activity: Activity, hide: Boolean) {
            val window = activity.window
            if (hide) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
