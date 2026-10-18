package com.azimulkabir.actua.data.budget.gdrive

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GoogleDrivePreferencesAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: GoogleDrivePreferences

    @Before
    fun setUp() {
        prefs = GoogleDrivePreferences(context)
        prefs.clear()
    }

    @After
    fun tearDown() {
        prefs.clear()
    }

    @Test
    fun effectiveClientIdDefaultsToConfigDefault() {
        assertNull(prefs.customClientId)
        assertEquals(GoogleDriveConfig.DEFAULT_CLIENT_ID, prefs.getEffectiveClientId())
    }

    @Test
    fun customClientIdOverridesDefault() {
        prefs.customClientId = "custom-id.apps.googleusercontent.com"
        assertEquals("custom-id.apps.googleusercontent.com", prefs.customClientId)
        assertEquals("custom-id.apps.googleusercontent.com", prefs.getEffectiveClientId())

        prefs.customClientId = null
        assertNull(prefs.customClientId)
        assertEquals(GoogleDriveConfig.DEFAULT_CLIENT_ID, prefs.getEffectiveClientId())
    }
}
