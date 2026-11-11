package com.azimulkabir.actua

import android.app.Application
import android.content.res.Configuration
import com.azimulkabir.actua.widget.WidgetLookWatcher
import com.azimulkabir.actua.widget.WidgetUpdater

/**
 * Restyles the home-screen widgets when the device theme or language changes while Actua's process is
 * alive. The launcher can't tell a widget about either, and a styled widget on Android 12-13 keeps the
 * colors it was last given. Otherwise they catch up at the next refresh.
 */
class ActuaApplication : Application() {
    private lateinit var widgetLook: WidgetLookWatcher

    override fun onCreate() {
        super.onCreate()
        val configuration = resources.configuration
        widgetLook = WidgetLookWatcher(night(configuration), configuration.locales.toLanguageTags())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (widgetLook.changed(night(newConfig), newConfig.locales.toLanguageTags())) {
            WidgetUpdater.requestAllAsync(this)
        }
    }

    private fun night(configuration: Configuration) = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
}
