package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayPreferencesTest {
    @Before fun clearPreferences() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun legacyMoreStartPageMigratesToManage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
            .edit().putString("start_page", "More").commit()

        val preferences = DisplayPreferences(context)
        assertEquals("Manage", preferences.startPage)
        assertEquals(
            "Manage",
            context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
                .getString("start_page", null),
        )

        preferences.startPage = "More"
        assertEquals("Manage", preferences.startPage)
    }

    @Test fun formattingDefaultsAndSelectionsPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("System default", DisplayPreferences(context).dateFormat)
        assertEquals("System default", DisplayPreferences(context).numberFormat)

        DisplayPreferences(context).dateFormat = "DD/MM/YYYY"
        DisplayPreferences(context).numberFormat = "1,23,456.78"

        val restored = DisplayPreferences(context)
        assertEquals("DD/MM/YYYY", restored.dateFormat)
        assertEquals("1,23,456.78", restored.numberFormat)
    }
}
