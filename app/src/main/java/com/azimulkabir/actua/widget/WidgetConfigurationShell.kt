package com.azimulkabir.actua.widget

import android.content.Context
import androidx.compose.runtime.Composable
import com.azimulkabir.actua.billing.supporterPerksAvailable
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.ui.lock.AppLock
import com.azimulkabir.actua.ui.lock.AppLockActivity
import com.azimulkabir.actua.ui.lock.AppLockGate
import com.azimulkabir.actua.ui.theme.AccentPalette
import com.azimulkabir.actua.ui.theme.ActuaTheme

/** What the widget configuration screens share with the rest of the app: screen privacy and the app lock. */
abstract class WidgetConfigurationBaseActivity : AppLockActivity()

/** The app's theme as the user set it, with the app lock in front of [content]. */
@Composable
internal fun WidgetConfigurationTheme(context: Context, content: @Composable () -> Unit) {
    val display = DisplayPreferences(context)
    ActuaTheme(
        appearance = display.appearance,
        dynamicColor = display.useDynamicColor,
        accent = if (supporterPerksAvailable()) display.accent else AccentPalette.Violet,
        customAccent = if (supporterPerksAvailable()) display.customAccentColor else null,
        catppuccin = if (supporterPerksAvailable()) display.catppuccinTheme() else null,
    ) {
        AppLockGate(locked = AppLock.locked, onUnlocked = AppLock::unlock, content = content)
    }
}
