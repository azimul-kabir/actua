package com.azimulkabir.actua.widget

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Simple or Highlighted row style of a list widget, kept per widget (#1662). */
@RunWith(AndroidJUnit4::class)
class WidgetRowStylePreferencesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clean() {
        context.getSharedPreferences("home_widget_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aWidgetIsSimpleUntilAnotherStyleIsChosen() {
        assertEquals(WidgetRowStyle.Simple, WidgetPreferences(context).rowStyle(WidgetKind.Categories, 3))
    }

    @Test
    fun theStyleIsKeptPerWidgetAndKindAndApartFromTheSelection() {
        val preferences = WidgetPreferences(context)
        preferences.save(WidgetKind.Categories, 3, setOf("groceries"))
        preferences.saveRowStyle(WidgetKind.Categories, 3, WidgetRowStyle.Highlighted)

        val reloaded = WidgetPreferences(context)
        assertEquals(WidgetRowStyle.Highlighted, reloaded.rowStyle(WidgetKind.Categories, 3))
        assertEquals(WidgetRowStyle.Simple, reloaded.rowStyle(WidgetKind.Categories, 4))
        assertEquals(WidgetRowStyle.Simple, reloaded.rowStyle(WidgetKind.Accounts, 3))
        assertEquals(setOf("groceries"), reloaded.selected(WidgetKind.Categories, 3))
    }

    @Test
    fun removingTheWidgetForgetsItsStyle() {
        val preferences = WidgetPreferences(context)
        preferences.saveRowStyle(WidgetKind.Accounts, 8, WidgetRowStyle.Highlighted)
        preferences.remove(WidgetKind.Accounts, intArrayOf(8))
        assertEquals(WidgetRowStyle.Simple, WidgetPreferences(context).rowStyle(WidgetKind.Accounts, 8))
    }
}
