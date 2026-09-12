package com.azimulkabir.actua.data.preferences

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPreferencesTest {
    @Test fun recordingIsOffByDefaultAndPersistsOptIn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(LocationPreferences.PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        val preferences = LocationPreferences(context)
        assertFalse(preferences.recordPayeeLocations)

        preferences.recordPayeeLocations = true
        assertTrue(LocationPreferences(context).recordPayeeLocations)

        preferences.recordPayeeLocations = false
        assertFalse(LocationPreferences(context).recordPayeeLocations)
    }
}
