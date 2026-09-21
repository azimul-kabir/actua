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
        assertTrue(isForwardSettingsNavigation(SettingsPage.General, SettingsPage.Budget))
        assertTrue(isForwardSettingsNavigation(SettingsPage.Budget, SettingsPage.CategoryColors))
    }

    @Test
    fun parentDestinationsUseReverseMotion() {
        assertFalse(isForwardSettingsNavigation(SettingsPage.Transactions, SettingsPage.General))
        assertFalse(isForwardSettingsNavigation(SettingsPage.Display, SettingsPage.General))
        assertFalse(isForwardSettingsNavigation(SettingsPage.Privacy, SettingsPage.General))
        assertFalse(isForwardSettingsNavigation(SettingsPage.About, SettingsPage.General))
        assertFalse(isForwardSettingsNavigation(SettingsPage.General, SettingsPage.Manage))
        assertFalse(isForwardSettingsNavigation(SettingsPage.CategoryColors, SettingsPage.Budget))
    }
}
