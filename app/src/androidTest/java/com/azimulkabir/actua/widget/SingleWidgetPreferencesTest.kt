package com.azimulkabir.actua.widget

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleWidgetPreferencesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clean() {
        context.getSharedPreferences("home_widget_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theChoiceSurvivesARestartAndIsKeptPerWidget() {
        WidgetPreferences(context).save(WidgetKind.SingleAccount, 11, setOf("checking"))
        WidgetPreferences(context).save(WidgetKind.SingleAccount, 12, setOf("savings"))
        WidgetPreferences(context).save(WidgetKind.SingleCategory, 11, setOf("groceries"))

        // A new instance over the same store stands in for a restart.
        val reloaded = WidgetPreferences(context)
        assertEquals(setOf("checking"), reloaded.selected(WidgetKind.SingleAccount, 11))
        assertEquals(setOf("savings"), reloaded.selected(WidgetKind.SingleAccount, 12))
        assertEquals("a category widget has its own choice", setOf("groceries"), reloaded.selected(WidgetKind.SingleCategory, 11))
    }

    @Test
    fun reconfiguringReplacesTheChoice() {
        val preferences = WidgetPreferences(context)
        preferences.save(WidgetKind.SingleCategory, 5, setOf("rent"))
        preferences.save(WidgetKind.SingleCategory, 5, setOf("fuel"))
        assertEquals(setOf("fuel"), WidgetPreferences(context).selected(WidgetKind.SingleCategory, 5))
    }

    @Test
    fun nothingIsChosenUntilSetupHasRun() {
        assertTrue(WidgetPreferences(context).selected(WidgetKind.SingleAccount, 99).isEmpty())
    }

    @Test
    fun removingTheWidgetForgetsItsChoice() {
        val preferences = WidgetPreferences(context)
        preferences.save(WidgetKind.SingleAccount, 7, setOf("checking"))
        preferences.remove(WidgetKind.SingleAccount, intArrayOf(7))
        assertTrue(WidgetPreferences(context).selected(WidgetKind.SingleAccount, 7).isEmpty())
    }
}
