package com.azimulkabir.actua.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.home.HomeLayout
import com.azimulkabir.actua.data.home.HomeLayoutPlanner
import com.azimulkabir.actua.data.home.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomizeHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toggling_a_switch_hides_the_section_and_reports_the_change() {
        var latest: HomeLayout? = null
        compose.setContent {
            MaterialTheme {
                CustomizeHomeScreen(layout = HomeLayout.default(), onBack = {}, onLayoutChange = { latest = it })
            }
        }

        compose.onNodeWithContentDescription("Show ${HomeSection.REPORTS.title} on Home").performClick()

        assertTrue(HomeSection.REPORTS in requireNotNull(latest).hidden)
        compose.onNodeWithContentDescription("Show ${HomeSection.REPORTS.title} on Home").assertExists()
    }

    @Test fun ready_to_budget_has_no_switch_and_is_labeled_required() {
        compose.setContent {
            MaterialTheme {
                CustomizeHomeScreen(layout = HomeLayout.default(), onBack = {}, onLayoutChange = {})
            }
        }

        compose.onNodeWithText("Required").assertExists()
        compose.onNodeWithContentDescription("Show ${HomeSection.READY_TO_BUDGET.title} on Home").assertDoesNotExist()
    }

    @Test fun move_down_arrow_reorders_and_reports_the_new_order() {
        var latest: HomeLayout? = null
        compose.setContent {
            MaterialTheme {
                CustomizeHomeScreen(layout = HomeLayout.default(), onBack = {}, onLayoutChange = { latest = it })
            }
        }

        compose.onNodeWithContentDescription("Move ${HomeSection.FAVORITE_CATEGORIES.title} down").performClick()

        val expected = HomeLayoutPlanner.moveSectionDown(HomeSection.entries.toList(), HomeSection.FAVORITE_CATEGORIES)
        assertEquals(expected, requireNotNull(latest).order)
    }

    @Test fun restore_defaults_resets_a_customized_layout() {
        var latest: HomeLayout? = null
        val customized = HomeLayoutPlanner.setHidden(HomeLayout.default(), HomeSection.REPORTS, true)
        compose.setContent {
            MaterialTheme {
                CustomizeHomeScreen(layout = customized, onBack = {}, onLayoutChange = { latest = it })
            }
        }

        compose.onNodeWithContentDescription("Restore default layout").performClick()

        assertEquals(HomeLayout.default(), latest)
        assertFalse(HomeSection.REPORTS in requireNotNull(latest).hidden)
    }
}
