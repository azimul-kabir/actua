package com.azimulkabir.actua.ui.lock

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.azimulkabir.actua.data.preferences.ScreenPrivacyPreferences
import com.azimulkabir.actua.data.security.AppLockPreferences

/**
 * Base for every activity other than `MainActivity` that can show budget data or act on it: widget
 * setup, the Tasker edit screen and the disconnect confirmation. It keeps the process-wide [AppLock]
 * up to date, so wrap the activity's content in [AppLockGate] with `AppLock.locked`; while locked that
 * content is not composed at all, and the request it was opened for continues once the user unlocks.
 */
abstract class AppLockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenPrivacyPreferences.apply(this)
        // Decide before the first frame so a locked app never draws budget content, not even briefly.
        refreshAppLock()
    }

    override fun onStart() {
        super.onStart()
        refreshAppLock()
    }

    override fun onStop() {
        super.onStop()
        // A rotation or theme change stops the activity too, but the user hasn't left the app.
        if (!isChangingConfigurations) AppLock.onBackground()
    }

    private fun refreshAppLock() {
        val preferences = AppLockPreferences(this)
        AppLock.onForeground(preferences.enabled, preferences.timeout)
    }
}
