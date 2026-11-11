package com.azimulkabir.actua.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLookWatcherTest {
    private val light = 0x10
    private val dark = 0x20

    @Test
    fun anUnchangedConfigurationLeavesWidgetsAlone() {
        val watcher = WidgetLookWatcher(light, "en-US")
        assertFalse("a rotation or font change keeps the same look", watcher.changed(light, "en-US"))
    }

    @Test
    fun switchingThemeRestylesWidgets() {
        val watcher = WidgetLookWatcher(light, "en-US")
        assertTrue(watcher.changed(dark, "en-US"))
        assertFalse("only the first change counts", watcher.changed(dark, "en-US"))
        assertTrue("and back again", watcher.changed(light, "en-US"))
    }

    @Test
    fun switchingLanguageRestylesWidgets() {
        val watcher = WidgetLookWatcher(light, "en-US")
        assertTrue(watcher.changed(light, "es-ES"))
        assertTrue(watcher.changed(light, "pt-BR,en-US"))
        assertFalse(watcher.changed(light, "pt-BR,en-US"))
    }
}
