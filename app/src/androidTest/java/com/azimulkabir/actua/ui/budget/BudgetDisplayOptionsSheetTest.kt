package com.azimulkabir.actua.ui.budget

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.BudgetDisplayOptions
import com.azimulkabir.actua.ui.theme.CategoryStatusColorState
import com.azimulkabir.actua.ui.theme.LocalCategoryStatusColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #1312: the short Budget menu, the collapsible Display options sheet, and Copy last month in the sparkle sheet. */
@RunWith(AndroidJUnit4::class)
class BudgetDisplayOptionsSheetTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun label(id: Int) = context.getString(id)

    private var options by mutableStateOf(BudgetDisplayOptions())
    private var copied = 0
    private var managed = 0

    private fun setBudget(statusColors: CategoryStatusColorState? = null) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCategoryStatusColors provides statusColors) {
                    BudgetScreen(
                        displayOptions = options,
                        onDisplayOptionsChange = { options = it },
                        onCopyPreviousMonth = { copied++ },
                        onManageCategories = { managed++ },
                    )
                }
            }
        }
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription(label(R.string.budget_display_options)).performClick()
    }

    private fun openSheet() {
        openMenu()
        compose.onNodeWithText(label(R.string.budget_display_options_menu)).performClick()
    }

    private fun section(id: Int) = compose.onNodeWithText(label(id))

    @Test fun theMenuIsShortAndDisplaySwitchesLiveInTheSheet() {
        setBudget()
        openMenu()
        compose.onNodeWithText(label(R.string.budget_plan_view)).assertExists()
        compose.onNodeWithText(label(R.string.budget_expand_all_groups)).assertExists()
        compose.onNodeWithText(label(R.string.budget_collapse_all_groups)).assertExists()
        compose.onNodeWithText(label(R.string.budget_manage_categories)).assertExists()
        compose.onNodeWithText(label(R.string.budget_display_options_menu)).assertExists()
        compose.onNodeWithText(label(R.string.budget_show_overview)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.budget_show_balance_pills)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.budget_copy_last_month)).assertDoesNotExist()
    }

    @Test fun sectionsExpandAndCollapseAndRowsStartOpen() {
        setBudget()
        openSheet()
        // Each category row starts open; Page and Categories shown start collapsed.
        compose.onNodeWithText(label(R.string.budget_show_goal_icons)).assertExists()
        compose.onNodeWithText(label(R.string.budget_show_overview)).assertDoesNotExist()

        section(R.string.budget_options_section_page).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.budget_show_overview)).assertExists()

        section(R.string.budget_options_section_rows).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.budget_show_goal_icons)).assertDoesNotExist()
    }

    @Test fun aSwitchInTheSheetAppliesImmediatelyAndUpdatesTheSummary() {
        setBudget()
        openSheet()
        compose.onNodeWithText("6 of 7 on").assertExists()

        compose.onNodeWithText(label(R.string.budget_show_goal_icons)).performScrollTo().performClick()

        assertFalse(options.showGoalIcons)
        assertTrue(options.showBalancePills)
        compose.onNodeWithText("5 of 7 on").assertExists()
    }

    @Test fun statusDotsFollowTheSharedSetting() {
        val state = CategoryStatusColorState(context)
        val before = state.showDots
        try {
            state.updateShowDots(true)
            setBudget(state)
            openSheet()
            compose.onNodeWithText(label(R.string.budget_show_status_dots)).performScrollTo().performClick()
            assertFalse(state.showDots)
        } finally {
            state.updateShowDots(before)
        }
    }

    @Test fun colorBalancesFollowTheSharedSetting() {
        val state = CategoryStatusColorState(context)
        val before = state.colorBudgetedAmounts
        try {
            state.updateColorBudgetedAmounts(false)
            setBudget(state)
            openSheet()
            compose.onNodeWithText(label(R.string.settings_color_balances)).performScrollTo().performClick()
            assertTrue(state.colorBudgetedAmounts)
        } finally {
            state.updateColorBudgetedAmounts(before)
        }
    }

    @Test fun copyLastMonthLivesInTheSparkleSheet() {
        setBudget()
        compose.onNodeWithContentDescription(label(R.string.budget_add_to_budget)).performClick()
        compose.onNodeWithText(label(R.string.budget_copy_last_month)).performClick()
        assertEquals(1, copied)
        // The sheet closes after copying.
        compose.onNodeWithText(label(R.string.budget_copy_last_month)).assertDoesNotExist()
    }

    @Test fun manageCategoriesIsARowInTheMenuNotAToolbarButton() {
        setBudget()
        // The toolbar no longer has a Manage Categories button; the menu row opens it and closes the menu.
        compose.onNodeWithContentDescription(label(R.string.budget_manage_categories)).assertDoesNotExist()
        openMenu()
        compose.onNodeWithText(label(R.string.budget_manage_categories)).performClick()
        assertEquals(1, managed)
        compose.onNodeWithText(label(R.string.budget_manage_categories)).assertDoesNotExist()
    }
}
