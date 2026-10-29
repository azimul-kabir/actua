package com.azimulkabir.actua.data.preferences

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.DisconnectResetActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPrivacyPreferencesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun clear() {
        context.getSharedPreferences(ScreenPrivacyPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun hidingIsOffByDefaultAndSurvivesRestart() {
        clear()
        assertFalse(ScreenPrivacyPreferences(context).hideInRecents)

        ScreenPrivacyPreferences(context).hideInRecents = true

        assertTrue(ScreenPrivacyPreferences(context).hideInRecents)
        clear()
    }

    @Test
    fun applySetsAndClearsSecureFlagOnTheWindow() {
        clear()
        ActivityScenario.launch(DisconnectResetActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)

                ScreenPrivacyPreferences.apply(activity, hide = true)
                assertEquals(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE,
                )

                ScreenPrivacyPreferences.apply(activity, hide = false)
                assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    @Test
    fun activitiesStartSecureWhenPreferenceIsEnabled() {
        clear()
        ScreenPrivacyPreferences(context).hideInRecents = true
        ActivityScenario.launch(DisconnectResetActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE,
                )
            }
        }
        clear()
    }
}
