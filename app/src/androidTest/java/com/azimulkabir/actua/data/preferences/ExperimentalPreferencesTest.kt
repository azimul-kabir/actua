package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class ExperimentalPreferencesTest {
    @Test
    fun enableBankingIsOffByDefaultAndTheChoiceSurvivesRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("experimental_preferences", Context.MODE_PRIVATE).edit().clear().commit()

        val fresh = ExperimentalPreferences(context)
        assertEquals(false, fresh.enableBanking)

        fresh.enableBanking = true
        assertEquals(true, ExperimentalPreferences(context).enableBanking)

        fresh.enableBanking = false
        assertEquals(false, ExperimentalPreferences(context).enableBanking)
        context.getSharedPreferences("experimental_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
