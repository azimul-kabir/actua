package com.azimulkabir.actua.widget

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.security.AppLockPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Widgets leave amounts out while the app lock is on and "Hide widget amounts" is chosen (#1341). */
class WidgetAmountPrivacyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val lockPreferences = context.getSharedPreferences("app_lock_preferences", Context.MODE_PRIVATE)

    @Before fun start() { lockPreferences.edit().clear().commit() }
    @After fun finish() { lockPreferences.edit().clear().commit() }

    @Test fun amountsShowByDefaultAndWithTheOptionSkipped() {
        AppLockPreferences(context).enabled = true
        val shown = money(context, 123_45)
        assertNotEquals("••••", shown)
        assertFalse(balancesHidden(context))
        assertEquals(shown.takeIf { it.isNotBlank() }, spoken(context, 123_45))
    }

    @Test fun amountsAreMaskedAndNeverSpokenWhileTheLockAndTheOptionAreOn() {
        AppLockPreferences(context).apply { enabled = true; hideWidgetAmounts = true }
        assertEquals("••••", money(context, 123_45))
        assertTrue(balancesHidden(context))
        assertNull(spoken(context, 123_45))
    }

    @Test fun theOptionIsIgnoredOnceTheLockIsTurnedOff() {
        AppLockPreferences(context).apply { enabled = true; hideWidgetAmounts = true }
        assertTrue(balancesHidden(context))
        AppLockPreferences(context).enabled = false
        assertFalse(balancesHidden(context))
        assertNotEquals("••••", money(context, 123_45))
    }
}
