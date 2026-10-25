package com.azimulkabir.actua.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.home.MonthStartRule
import com.azimulkabir.actua.data.home.StartDayAdjustment
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthStartDayRowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun steppingChangesTheDayAndStopsAtTheBounds() {
        var rule by mutableStateOf(MonthStartRule())
        compose.setContent { MaterialTheme { MonthStartDayRow(rule, { rule = it.copy(day = it.day.coerceIn(1, 31)) }) } }

        compose.onNodeWithTag("summaryStartDay").assertTextEquals("1")
        compose.onNodeWithContentDescription("Start the period one day earlier").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Start the period one day later").performClick()
        compose.onNodeWithTag("summaryStartDay").assertTextEquals("2")
        compose.onNodeWithContentDescription("Start the period one day earlier").assertIsEnabled()

        rule = rule.copy(day = 31)
        compose.onNodeWithContentDescription("Start the period one day later").assertIsNotEnabled()
    }

    @Test fun weekendDaysAreAlwaysShownAndToggle() {
        var rule by mutableStateOf(MonthStartRule(25))
        compose.setContent { MaterialTheme { MonthStartDayRow(rule, { rule = it }) } }

        // They apply to schedules too, so they don't wait for a month start adjustment.
        assertEquals(StartDayAdjustment.NONE, rule.adjustment)

        // Sat-Sun by default: adding Friday and removing Sunday gives the Friday-Saturday weekend.
        compose.onNodeWithTag("weekendDayFRIDAY").performClick()
        compose.onNodeWithTag("weekendDaySUNDAY").performClick()
        assertEquals(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), rule.weekendDays)
    }
}
