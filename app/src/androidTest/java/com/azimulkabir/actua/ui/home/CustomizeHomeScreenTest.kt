package com.azimulkabir.actua.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
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

    /** Row labels and content descriptions come from resources, following the app language. */
    private fun label(resId: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId, *args)

    private fun sectionTitle(section: HomeSection) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(section.titleRes)

    @Test fun non_supporter_greeting_is_marked_and_has_no_controls() {
        compose.setContent {
            MaterialTheme {
                CustomizeHomeScreen(
                    layout = HomeLayoutPlanner.restrict(HomeLayout.default(), HomeLayoutPlanner.offered(false)),
                    onBack = {}, onLayoutChange = {}, isSupporter = false, showSupporterExtras = true,
                )
            }
        }
        compose.onNodeWithText(sectionTitle(HomeSection.GREETING)).assertExists()
        compose.onNodeWithText(label(R.string.settings_supporter_extras_marker)).assertExists()
        compose.onNodeWithContentDescription(label(R.string.home_show_section, sectionTitle(HomeSection.GREETING))).assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.home_drag_reorder, sectionTitle(HomeSection.GREETING))).assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.home_move_down, sectionTitle(HomeSection.GREETING))).assertDoesNotExist()
    }

    @Test fun supporter_can_hide_greeting() {
        var latest: HomeLayout? = null
        compose.setContent {
            MaterialTheme {
                CustomizeHomeScreen(
                    layout = HomeLayout.default(), onBack = {}, onLayoutChange = { latest = it },
                    isSupporter = true, showSupporterExtras = true,
                )
            }
        }
        compose.onNodeWithContentDescription(label(R.string.home_show_section, sectionTitle(HomeSection.GREETING))).performClick()
        assertTrue(HomeSection.GREETING in requireNotNull(latest).hidden)
    }

    @Test fun greeting_is_absent_when_supporter_extras_are_unavailable() {
        compose.setContent {
            MaterialTheme {
                CustomizeHomeScreen(
                    layout = HomeLayout.default(), onBack = {}, onLayoutChange = {}, showSupporterExtras = false,
                )
            }
        }
        compose.onNodeWithText(sectionTitle(HomeSection.GREETING)).assertDoesNotExist()
    }

    @Test fun toggling_a_switch_hides_the_section_and_reports_the_change() {
        var latest: HomeLayout? = null
        compose.setContent {
            MaterialTheme {
                CustomizeHomeScreen(isSupporter = true, showSupporterExtras = true, layout = HomeLayout.default(), onBack = {}, onLayoutChange = { latest = it })
            }
        }

        compose.onNodeWithContentDescription(label(R.string.home_show_section, sectionTitle(HomeSection.REPORTS))).performClick()

        assertTrue(HomeSection.REPORTS in requireNotNull(latest).hidden)
        compose.onNodeWithContentDescription(label(R.string.home_show_section, sectionTitle(HomeSection.REPORTS))).assertExists()
    }

    @Test fun ready_to_budget_has_no_switch_and_is_labeled_required() {
        compose.setContent {
            MaterialTheme {
                CustomizeHomeScreen(isSupporter = true, showSupporterExtras = true, layout = HomeLayout.default(), onBack = {}, onLayoutChange = {})
            }
        }

        compose.onNodeWithText(label(R.string.home_required)).assertExists()
        compose.onNodeWithContentDescription(
            label(R.string.home_show_section, sectionTitle(HomeSection.READY_TO_BUDGET)),
        ).assertDoesNotExist()
    }

    @Test fun move_down_arrow_reorders_and_reports_the_new_order() {
        var latest: HomeLayout? = null
        compose.setContent {
            MaterialTheme {
                CustomizeHomeScreen(isSupporter = true, showSupporterExtras = true, layout = HomeLayout.default(), onBack = {}, onLayoutChange = { latest = it })
            }
        }

        compose.onNodeWithContentDescription(
            label(R.string.home_move_down, sectionTitle(HomeSection.FAVORITE_CATEGORIES)),
        ).performClick()

        val expected = HomeLayoutPlanner.moveSectionDown(HomeSection.entries.toList(), HomeSection.FAVORITE_CATEGORIES)
        assertEquals(expected, requireNotNull(latest).order)
    }

    @Test fun restore_defaults_resets_a_customized_layout() {
        var latest: HomeLayout? = null
        val customized = HomeLayoutPlanner.setHidden(HomeLayout.default(), HomeSection.REPORTS, true)
        compose.setContent {
            MaterialTheme {
                CustomizeHomeScreen(isSupporter = true, showSupporterExtras = true, layout = customized, onBack = {}, onLayoutChange = { latest = it })
            }
        }

        compose.onNodeWithContentDescription(label(R.string.home_restore_layout)).performClick()

        assertEquals(HomeLayout.default(), latest)
        assertFalse(HomeSection.REPORTS in requireNotNull(latest).hidden)
    }
}
