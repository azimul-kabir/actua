package com.azimulkabir.actua.data.preferences

import com.azimulkabir.actua.data.preferences.RatingPromptPreferences.Companion.isDue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingPromptPreferencesTest {

    private val day = 24L * 60 * 60 * 1000
    private val installed = 1_700_000_000_000L

    @Test
    fun dueOnlyWhenBothThresholdsAreMet() {
        assertTrue(isDue(installed, installed + 14 * day, addedTransactions = 50, asked = false))
    }

    @Test
    fun notDueBeforeFourteenDaysEvenWithManyTransactions() {
        assertFalse(isDue(installed, installed + 13 * day, addedTransactions = 500, asked = false))
    }

    @Test
    fun notDueBeforeFiftyTransactionsEvenAfterAYear() {
        assertFalse(isDue(installed, installed + 365 * day, addedTransactions = 49, asked = false))
    }

    @Test
    fun neverDueOnceAsked() {
        assertFalse(isDue(installed, installed + 365 * day, addedTransactions = 500, asked = true))
    }

    @Test
    fun unknownInstallTimeIsNeverDue() {
        assertFalse(isDue(null, installed + 365 * day, addedTransactions = 500, asked = false))
    }
}
