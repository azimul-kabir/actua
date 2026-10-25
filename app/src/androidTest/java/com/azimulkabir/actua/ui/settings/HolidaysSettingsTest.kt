package com.azimulkabir.actua.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.home.Holiday
import com.azimulkabir.actua.ui.components.formatDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HolidaysSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val eid = Holiday(LocalDate.of(2030, 6, 1), "Eid")
    private val victory = Holiday(LocalDate.of(2030, 12, 16))

    @Test fun anEmptyListSaysSoAndOffersToAdd() {
        compose.setContent { MaterialTheme { HolidaysSettings(emptyList(), {}) } }

        compose.onNodeWithTag("holidaysEmpty").assertIsDisplayed()
        compose.onNodeWithTag("addHoliday").assertIsDisplayed()
    }

    @Test fun listedHolidaysShowTheirDateAndLabel() {
        compose.setContent { MaterialTheme { HolidaysSettings(listOf(eid, victory), {}) } }

        compose.onNodeWithText(formatDate(eid.date)).assertIsDisplayed()
        compose.onNodeWithText("Eid").assertIsDisplayed()
        compose.onNodeWithText(formatDate(victory.date)).assertIsDisplayed()
    }

    @Test fun removingAHolidayReportsTheListWithoutIt() {
        var changed: List<Holiday>? = null
        compose.setContent { MaterialTheme { HolidaysSettings(listOf(eid, victory), { changed = it }) } }

        compose.onNodeWithContentDescription("Remove ${formatDate(eid.date)}").performClick()

        assertEquals(listOf(victory), changed)
    }
}
