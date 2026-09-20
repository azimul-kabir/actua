package com.azimulkabir.actua.data.preferences

import android.content.Context
import com.azimulkabir.actua.data.home.HomeLayout
import com.azimulkabir.actua.data.home.HomeLayoutCodec

/** Home dashboard section order and visibility; device-local UI preference, not budget data. */
class HomePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("home_preferences", Context.MODE_PRIVATE)

    fun layout(): HomeLayout = HomeLayoutCodec.decode(preferences.getString(LAYOUT, null))

    fun save(layout: HomeLayout) {
        preferences.edit().putString(LAYOUT, HomeLayoutCodec.encode(layout)).apply()
    }

    fun restoreDefaults(): HomeLayout = HomeLayout.default().also { save(it) }

    private companion object { const val LAYOUT = "layout" }
}
