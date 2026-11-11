package com.azimulkabir.actua.widget

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.compose.runtime.Composable
import com.azimulkabir.actua.billing.supporterPerksAvailable
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.ScreenPrivacyPreferences
import com.azimulkabir.actua.data.security.AppLockPreferences
import com.azimulkabir.actua.ui.lock.AppLock
import com.azimulkabir.actua.ui.lock.AppLockGate
import com.azimulkabir.actua.ui.theme.AccentPalette
import com.azimulkabir.actua.ui.theme.ActuaTheme

/**
 * What the widget configuration screens share with the rest of the app: screen privacy, the app lock
 * (so budget names are never drawn while the app is locked) and the chosen appearance.
 */
abstract class WidgetConfigurationBaseActivity : AppCompatActivity() {
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
        if (!isChangingConfigurations) AppLock.onBackground()
    }

    private fun refreshAppLock() {
        val preferences = AppLockPreferences(this)
        AppLock.onForeground(preferences.enabled, preferences.timeout)
    }
}

/** The app's theme as the user set it, with the app lock in front of [content]. */
@Composable
internal fun WidgetConfigurationTheme(context: Context, content: @Composable () -> Unit) {
    val display = DisplayPreferences(context)
    ActuaTheme(
        appearance = display.appearance,
        dynamicColor = display.useDynamicColor,
        accent = if (supporterPerksAvailable()) display.accent else AccentPalette.Violet,
    ) {
        AppLockGate(locked = AppLock.locked, onUnlocked = AppLock::unlock, content = content)
    }
}
