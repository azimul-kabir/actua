package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.navigation.TabBarLayout
import com.azimulkabir.actua.data.navigation.TabItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TabBarPreferencesTest {
    @Test
    fun savedLayoutSurvivesRestartAndRestoreDefaultsResetsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("tab_bar_preferences", Context.MODE_PRIVATE).edit().clear().commit()

        val fresh = TabBarPreferences(context)
        assertEquals(TabBarLayout.default(), fresh.layout())

        val customized = TabBarLayout(
            order = listOf(
                TabItem.MANAGE,
                TabItem.BUDGET,
                TabItem.ACCOUNTS,
                TabItem.ADD,
                TabItem.REPORTS,
                TabItem.HOME,
                TabItem.TRANSACTIONS,
            ),
            hidden = setOf(TabItem.HOME, TabItem.TRANSACTIONS),
        )
        fresh.save(customized)

        // Simulate a restart by reading the layout back through a new instance over the same store.
        val reloaded = TabBarPreferences(context)
        assertEquals(customized, reloaded.layout())

        assertEquals(TabBarLayout.default(), reloaded.restoreDefaults())
        assertEquals(TabBarLayout.default(), reloaded.layout())
    }
}
