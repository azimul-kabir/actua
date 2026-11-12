package com.azimulkabir.actua.ui.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class RulesDataTest {
    @Test
    fun initialStateIsEmptyButSupported() {
        val initial = RulesData.Initial
        assertTrue("must not flash the unsupported message before loading", initial.supported)
        assertTrue(initial.rules.isEmpty())
        assertTrue(initial.scheduleOwnedRuleIds.isEmpty())
        assertTrue(initial.editorData.accounts.isEmpty())
    }
}
