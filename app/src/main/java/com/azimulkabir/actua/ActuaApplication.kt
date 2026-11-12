package com.azimulkabir.actua

import android.app.Application
import android.content.res.Configuration
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.widget.WidgetLookWatcher
import com.azimulkabir.actua.widget.WidgetUpdater

/**
 * Restyles the home-screen widgets when the device theme or language changes while Actua's process is
 * alive. The launcher can't tell a widget about either, and a styled widget on Android 12-13 keeps the
 * colors it was last given. Otherwise they catch up at the next refresh. Also refreshes an Accent-tinted
 * look when the accent or Material You changes in Settings.
 */
class ActuaApplication : Application() {
    private lateinit var widgetLook: WidgetLookWatcher

    // SharedPreferences keeps its listeners weakly; this reference keeps the observer alive.
    private var stopObservingAccent: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        val configuration = resources.configuration
        widgetLook = WidgetLookWatcher(night(configuration), configuration.locales.toLanguageTags())
        // Accent-tinted widgets are drawn from the accent and Material You, which change from Settings
        // (or a supporter-status reset) while the process is alive.
        val display = DisplayPreferences(this)
        stopObservingAccent = display.observeAccentSource {
            if (display.widgetStyle.followsAccent) WidgetUpdater.requestAllAsync(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (widgetLook.changed(night(newConfig), newConfig.locales.toLanguageTags())) {
            WidgetUpdater.requestAllAsync(this)
        }
    }

    private fun night(configuration: Configuration) = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
}
