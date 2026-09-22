package com.azimulkabir.actua.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.navigation.TabBarLayout
import com.azimulkabir.actua.data.navigation.TabBarLayoutPlanner
import com.azimulkabir.actua.data.navigation.TabItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomizeTabBarScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toggling_a_switch_hides_the_tab_and_reports_the_change() {
        var latest: TabBarLayout? = null
        compose.setContent {
            MaterialTheme {
                CustomizeTabBarScreen(layout = TabBarLayout.default(), onBack = {}, onLayoutChange = { latest = it })
            }
        }

        compose.onNodeWithContentDescription("Show ${TabItem.TRANSACTIONS.label} in the bottom bar").performClick()

        assertTrue(TabItem.TRANSACTIONS in requireNotNull(latest).hidden)
        compose.onNodeWithContentDescription("Show ${TabItem.TRANSACTIONS.label} in the bottom bar").assertExists()
    }

    @Test fun manage_has_no_switch_and_is_labeled_required() {
        compose.setContent {
            MaterialTheme {
                CustomizeTabBarScreen(layout = TabBarLayout.default(), onBack = {}, onLayoutChange = {})
            }
        }

        compose.onNodeWithText("Required").assertExists()
        compose.onNodeWithContentDescription("Show ${TabItem.MANAGE.label} in the bottom bar").assertDoesNotExist()
    }

    @Test fun move_down_arrow_reorders_and_reports_the_new_order() {
        var latest: TabBarLayout? = null
        compose.setContent {
            MaterialTheme {
                CustomizeTabBarScreen(layout = TabBarLayout.default(), onBack = {}, onLayoutChange = { latest = it })
            }
        }

        compose.onNodeWithContentDescription("Move ${TabItem.BUDGET.label} down").performClick()

        val expected = TabBarLayoutPlanner.moveTabDown(TabBarLayout.default().order, TabItem.BUDGET)
        assertEquals(expected, requireNotNull(latest).order)
    }

    @Test fun restore_defaults_resets_a_customized_layout() {
        var latest: TabBarLayout? = null
        val customized = TabBarLayoutPlanner.setHidden(TabBarLayout.default(), TabItem.TRANSACTIONS, true)
        compose.setContent {
            MaterialTheme {
                CustomizeTabBarScreen(layout = customized, onBack = {}, onLayoutChange = { latest = it })
            }
        }

        compose.onNodeWithContentDescription("Restore default tab bar").performClick()

        assertEquals(TabBarLayout.default(), latest)
        assertFalse(TabItem.TRANSACTIONS in requireNotNull(latest).hidden)
    }

    @Test fun switch_disables_once_the_minimum_visible_tab_count_is_reached() {
        var layout = TabBarLayoutPlanner.setHidden(TabBarLayout.default(), TabItem.TRANSACTIONS, true)
        layout = TabBarLayoutPlanner.setHidden(layout, TabItem.ACCOUNTS, true)
        // Visible now: Home, Budget, Manage - exactly the 3-tab minimum.
        compose.setContent {
            MaterialTheme {
                CustomizeTabBarScreen(layout = layout, onBack = {}, onLayoutChange = {})
            }
        }

        compose.onNodeWithContentDescription("Show ${TabItem.BUDGET.label} in the bottom bar").assertIsNotEnabled()
    }

    @Test fun switch_disables_once_the_maximum_visible_tab_count_is_reached() {
        // default() already has 5 visible tabs (the maximum); a hidden tab's switch must be disabled.
        compose.setContent {
            MaterialTheme {
                CustomizeTabBarScreen(layout = TabBarLayout.default(), onBack = {}, onLayoutChange = {})
            }
        }

        compose.onNodeWithContentDescription("Show ${TabItem.REPORTS.label} in the bottom bar").assertIsNotEnabled()
    }

    @Test fun add_tab_shows_an_explanation_of_what_it_does() {
        compose.setContent {
            MaterialTheme {
                CustomizeTabBarScreen(layout = TabBarLayout.default(), onBack = {}, onLayoutChange = {})
            }
        }

        compose.onNodeWithText("Replaces the floating + Transaction button").assertExists()
    }
}
