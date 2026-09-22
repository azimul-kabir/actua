package com.azimulkabir.actua.data.preferences

import android.content.Context
import com.azimulkabir.actua.data.navigation.TabBarLayout
import com.azimulkabir.actua.data.navigation.TabBarLayoutCodec

/** Bottom tab bar order and visibility; device-local UI preference, not budget data. */
class TabBarPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("tab_bar_preferences", Context.MODE_PRIVATE)

    fun layout(): TabBarLayout = TabBarLayoutCodec.decode(preferences.getString(LAYOUT, null))

    fun save(layout: TabBarLayout) {
        preferences.edit().putString(LAYOUT, TabBarLayoutCodec.encode(layout)).apply()
    }

    fun restoreDefaults(): TabBarLayout = TabBarLayout.default().also { save(it) }

    private companion object { const val LAYOUT = "layout" }
}
