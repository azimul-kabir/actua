package com.azimulkabir.actua.data.budget.gdrive

import android.content.Context

/**
 * Manages non-secret user preferences and status for Google Drive backups.
 */
class GoogleDrivePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("actua_gdrive_prefs", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) = preferences.edit().putBoolean("enabled", value).apply()

    var customClientId: String?
        get() = preferences.getString("custom_client_id", null)?.takeIf(String::isNotBlank)
        set(value) = preferences.edit().putString("custom_client_id", value).apply()

    var lastSuccessfulBackupMillis: Long
        get() = preferences.getLong("last_backup_millis", 0L)
        set(value) = preferences.edit().putLong("last_backup_millis", value).apply()

    var lastBackupError: String?
        get() = preferences.getString("last_backup_error", null)
        set(value) = preferences.edit().putString("last_backup_error", value).apply()

    fun getEffectiveClientId(): String = customClientId ?: GoogleDriveConfig.DEFAULT_CLIENT_ID

    fun clear() {
        preferences.edit().clear().apply()
    }
}
