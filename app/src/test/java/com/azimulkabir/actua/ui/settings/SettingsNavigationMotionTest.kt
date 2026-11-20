package com.azimulkabir.actua.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationMotionTest {
    @Test
    fun deeperDestinationsUseForwardMotion() {
        assertTrue(isForwardSettingsNavigation(SettingsPage.Manage, SettingsPage.General))
        assertTrue(isForwardSettingsNavigation(SettingsPage.General, SettingsPage.Transactions))
        assertTrue(isForwardSettingsNavigation(SettingsPage.General, SettingsPage.Display))
        assertTrue(isForwardSettingsNavigation(SettingsPage.General, SettingsPage.Privacy))
        assertTrue(isForwardSettingsNavigation(SettingsPage.General, SettingsPage.About))
        assertTrue(isForwardSettingsNavigation(SettingsPage.General, SettingsPage.SupporterExtras))
        assertTrue(isForwardSettingsNavigation(SettingsPage.SupporterExtras, SettingsPage.AppIconPicker))
        assertTrue(isForwardSettingsNavigation(SettingsPage.General, SettingsPage.Budget))
        assertTrue(isForwardSettingsNavigation(SettingsPage.Budget, SettingsPage.CategoryColors))
        assertTrue(isForwardSettingsNavigation(SettingsPage.Budget, SettingsPage.Holidays))
        assertTrue(isForwardSettingsNavigation(SettingsPage.General, SettingsPage.Support))
        assertTrue(isForwardSettingsNavigation(SettingsPage.SupporterExtras, SettingsPage.Support))
        assertTrue(isForwardSettingsNavigation(SettingsPage.AppIconPicker, SettingsPage.Support))
        assertTrue(isForwardSettingsNavigation(SettingsPage.AccentPicker, SettingsPage.Support))
        assertTrue(isForwardSettingsNavigation(SettingsPage.WidgetStylePicker, SettingsPage.Support))
    }

    @Test
    fun parentDestinationsUseReverseMotion() {
        assertFalse(isForwardSettingsNavigation(SettingsPage.Transactions, SettingsPage.General))
        assertFalse(isForwardSettingsNavigation(SettingsPage.Display, SettingsPage.General))
        assertFalse(isForwardSettingsNavigation(SettingsPage.SupporterExtras, SettingsPage.General))
        assertFalse(isForwardSettingsNavigation(SettingsPage.Privacy, SettingsPage.General))
        assertFalse(isForwardSettingsNavigation(SettingsPage.About, SettingsPage.General))
        assertFalse(isForwardSettingsNavigation(SettingsPage.General, SettingsPage.Manage))
        assertFalse(isForwardSettingsNavigation(SettingsPage.CategoryColors, SettingsPage.Budget))
        assertFalse(isForwardSettingsNavigation(SettingsPage.Support, SettingsPage.General))
        assertFalse(isForwardSettingsNavigation(SettingsPage.Support, SettingsPage.SupporterExtras))
        assertFalse(isForwardSettingsNavigation(SettingsPage.Support, SettingsPage.AccentPicker))
    }
}
