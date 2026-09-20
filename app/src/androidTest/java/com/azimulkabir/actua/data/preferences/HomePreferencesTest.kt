package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.home.HomeLayout
import com.azimulkabir.actua.data.home.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePreferencesTest {
    @Test
    fun savedLayoutSurvivesRestartAndRestoreDefaultsResetsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("home_preferences", Context.MODE_PRIVATE).edit().clear().commit()

        val fresh = HomePreferences(context)
        assertEquals(HomeLayout.default(), fresh.layout())

        val customized = HomeLayout(
            order = listOf(
                HomeSection.READY_TO_BUDGET,
                HomeSection.RECENT_ACTIVITY,
                HomeSection.FAVORITE_CATEGORIES,
                HomeSection.FAVORITE_ACCOUNTS,
                HomeSection.UPCOMING,
                HomeSection.THIS_MONTH,
                HomeSection.REPORTS,
            ),
            hidden = setOf(HomeSection.REPORTS),
        )
        fresh.save(customized)

        // Simulate a restart by reading the layout back through a new instance over the same store.
        val reloaded = HomePreferences(context)
        assertEquals(customized, reloaded.layout())

        assertEquals(HomeLayout.default(), reloaded.restoreDefaults())
        assertEquals(HomeLayout.default(), reloaded.layout())
    }
}
