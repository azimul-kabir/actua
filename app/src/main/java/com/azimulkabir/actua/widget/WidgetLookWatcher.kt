package com.azimulkabir.actua.widget

/**
 * Notices when a configuration change alters how widgets look, the device theme (light or dark) or
 * language, as opposed to a rotation or font-size change that leaves them as they are.
 */
internal class WidgetLookWatcher(private var night: Int, private var locales: String) {
    /** Whether [night] or [locales] differ from the last call (or the starting values), remembering the new ones. */
    fun changed(night: Int, locales: String): Boolean {
        val changed = night != this.night || locales != this.locales
        this.night = night
        this.locales = locales
        return changed
    }
}
