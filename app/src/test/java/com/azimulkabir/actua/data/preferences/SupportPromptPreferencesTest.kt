package com.azimulkabir.actua.data.preferences

import com.azimulkabir.actua.data.preferences.SupportPromptPreferences.Companion.isDue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportPromptPreferencesTest {

    private val day = 24L * 60 * 60 * 1000
    private val installed = 1_700_000_000_000L

    @Test
    fun notDueBeforeEitherThreshold() {
        assertFalse(isDue(installed, installed + 29 * day, addedTransactions = 99, shown = false))
    }

    @Test
    fun dueAfterThirtyDays() {
        assertTrue(isDue(installed, installed + 30 * day, addedTransactions = 0, shown = false))
    }

    @Test
    fun dueAfterOneHundredTransactionsEvenOnDayOne() {
        assertTrue(isDue(installed, installed + day, addedTransactions = 100, shown = false))
    }

    @Test
    fun neverDueOnceShown() {
        assertFalse(isDue(installed, installed + 365 * day, addedTransactions = 500, shown = true))
    }

    @Test
    fun unknownInstallTimeFallsBackToTheTransactionCount() {
        assertFalse(isDue(null, installed, addedTransactions = 10, shown = false))
        assertTrue(isDue(null, installed, addedTransactions = 100, shown = false))
    }
}
