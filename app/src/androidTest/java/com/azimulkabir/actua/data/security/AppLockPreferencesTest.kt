package com.azimulkabir.actua.data.security

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPreferencesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun clear() {
        context.getSharedPreferences(AppLockPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theLockIsOffAndStrictByDefault() {
        clear()
        val preferences = AppLockPreferences(context)
        assertFalse(preferences.enabled)
        assertEquals(AppLockTimeout.IMMEDIATELY, preferences.timeout)
    }

    @Test
    fun settingsSurviveRestart() {
        clear()
        AppLockPreferences(context).apply {
            enabled = true
            timeout = AppLockTimeout.FIFTEEN_MINUTES
        }

        val reloaded = AppLockPreferences(context)
        assertTrue(reloaded.enabled)
        assertEquals(AppLockTimeout.FIFTEEN_MINUTES, reloaded.timeout)
        clear()
    }

    @Test
    fun anUnreadableStoredTimeoutFallsBackToImmediately() {
        clear()
        context.getSharedPreferences(AppLockPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putString(AppLockPreferences.TIMEOUT, "something-from-a-future-version").commit()

        assertEquals(AppLockTimeout.IMMEDIATELY, AppLockPreferences(context).timeout)
        clear()
    }
}
