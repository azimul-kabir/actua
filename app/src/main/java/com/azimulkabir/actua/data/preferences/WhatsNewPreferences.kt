package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.core.content.edit

/**
 * Device-local bookkeeping for the "What's new" card shown once after an update: the last app
 * version whose notes the user has been shown (or that was installed fresh). It never touches
 * budget data and is never sent anywhere.
 */
class WhatsNewPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** The version code last recorded, or null if none has been (a fresh install, or an app that predates this card). */
    var lastSeenVersionCode: Int?
        get() = preferences.getInt(LAST_SEEN_VERSION_CODE, NONE).takeIf { it != NONE }
        set(value) {
            preferences.edit { putInt(LAST_SEEN_VERSION_CODE, value ?: NONE) }
        }

    /** Whether the app has never been updated since it was installed. */
    fun isFreshInstall(): Boolean = runCatching {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    companion object {
        internal const val PREFERENCES_NAME = "whats_new"
        internal const val LAST_SEEN_VERSION_CODE = "last_seen_version_code"
        private const val NONE = -1

        /**
         * Show the card when the app was updated: a newer version than the one last recorded, or no
         * record at all on an app that has been updated since it was installed (the first version with
         * this card). A fresh install never shows it, and neither does an older or equal version.
         */
        fun shouldShow(lastSeenVersionCode: Int?, currentVersionCode: Int, freshInstall: Boolean): Boolean =
            if (lastSeenVersionCode == null) !freshInstall else lastSeenVersionCode < currentVersionCode
    }
}
