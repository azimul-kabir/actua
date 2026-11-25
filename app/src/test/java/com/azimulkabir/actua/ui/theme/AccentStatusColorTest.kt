package com.azimulkabir.actua.ui.theme

import androidx.compose.ui.graphics.Color
import com.azimulkabir.actua.model.BudgetProgressState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentStatusColorTest {
    private val violet = Color(0xFF6E11FF)
    private val forest = Color(0xFF0A7B2C)
    private val ocean = Color(0xFF106EC1)
    private val orchid = Color(0xFFC026D3)
    private val rose = Color(0xFFD41152)
    private val gold = Color(0xFF92610C)

    @Test
    fun coolAndGreenAccentsReadAsOnTrack() {
        listOf(violet, forest, ocean, orchid, Color(0xFF8839EF), Color(0xFF179299)).forEach {
            assertTrue("$it", accentReadsAsOnTrack(it))
        }
    }

    @Test
    fun redOrangeAmberAndGrayAccentsDoNot() {
        listOf(
            rose, gold, Color(0xFFFF0000), Color(0xFFFE640B), Color(0xFFDF8E1D), Color(0xFFDD7878),
            Color(0xFF808080), Color(0xFF6C7086),
        ).forEach { assertFalse("$it", accentReadsAsOnTrack(it)) }
    }

    @Test
    fun onTrackStatusesTakeThePrimaryAndFundedPastGoalTheTertiary() {
        listOf(BudgetProgressState.FUNDED, BudgetProgressState.SPENDING, BudgetProgressState.GOAL_REACHED).forEach {
            assertEquals(violet, accentStatusColor(it, violet, orchid))
        }
        assertEquals(orchid, accentStatusColor(BudgetProgressState.GOAL_OVERFUNDED, violet, orchid))
    }

    @Test
    fun warningAndNeutralStatusesKeepTheirDefaults() {
        listOf(
            BudgetProgressState.OVERSPENT, BudgetProgressState.GOAL_IN_PROGRESS,
            BudgetProgressState.SPENT, BudgetProgressState.UNASSIGNED,
        ).forEach { assertNull("$it", accentStatusColor(it, violet, orchid)) }
    }

    @Test
    fun aWarningColoredAccentKeepsEveryDefault() {
        BudgetProgressState.entries.forEach { assertNull("$it", accentStatusColor(it, rose, orchid)) }
    }

    @Test
    fun aWarningColoredTertiaryKeepsTheFundedPastGoalDefault() {
        assertNull(accentStatusColor(BudgetProgressState.GOAL_OVERFUNDED, violet, gold))
        assertEquals(violet, accentStatusColor(BudgetProgressState.FUNDED, violet, gold))
    }
}
