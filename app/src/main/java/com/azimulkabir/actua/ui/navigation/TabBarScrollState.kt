package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azimulkabir.actua.data.preferences.DisplayPreferences

/**
 * Whether the floating tab bar is showing, and the "hide while scrolling" preference that drives
 * it. Kept out of [AppNavigation], whose composable is already near the JVM method size limit.
 */
class TabBarScrollState(private val preferences: DisplayPreferences) {
    var visible by mutableStateOf(true)
        private set
    var hideOnScroll by mutableStateOf(preferences.hideTabBarOnScroll)
        private set

    /** Scrolling down (negative [deltaY]) hides the bar when enabled; scrolling up always shows it. */
    fun onScroll(deltaY: Float) {
        if (deltaY < -2f && hideOnScroll) visible = false
        if (deltaY > 2f) visible = true
    }

    /** Whether the bar's tabs show names under their icons. */
    var showLabels by mutableStateOf(preferences.showBottomNavigationLabels)
        private set

    fun updateShowLabels(enabled: Boolean) {
        preferences.showBottomNavigationLabels = enabled
        showLabels = enabled
    }

    fun show() { visible = true }

    fun updateHideOnScroll(enabled: Boolean) {
        preferences.hideTabBarOnScroll = enabled
        hideOnScroll = enabled
        if (!enabled) visible = true
    }
}
