package com.azimulkabir.actua.ui.settings

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #1313: changing the budget type in Settings asks first, like the Budget menu. */
@RunWith(AndroidJUnit4::class)
class SettingsBudgetTypeConfirmTest {
    @get:Rule val compose = createComposeRule()

    private val strings get() = ApplicationProvider.getApplicationContext<Context>()
    private val changes = mutableListOf<String>()

    private fun openBudgetTypeChoice() {
        compose.setContent { SettingsScreen(budgetType = "envelope", onBudgetTypeChange = { changes += it }) }
        compose.onNodeWithContentDescription(strings.getString(R.string.settings_open_settings)).performClick()
        compose.onNode(hasText(strings.getString(R.string.settings_page_budget)) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText(strings.getString(R.string.settings_budget_type)).performScrollTo().performClick()
        compose.onNodeWithText(strings.getString(R.string.settings_budget_type_tracking)).performClick()
    }

    @Test fun cancellingLeavesTheTypeUnchanged() {
        openBudgetTypeChoice()
        compose.onNodeWithText(strings.getString(R.string.budget_type_confirm_title_tracking)).assertExists()
        compose.onNodeWithText(strings.getString(R.string.common_cancel)).performClick()

        assertEquals(emptyList<String>(), changes)
        compose.onNodeWithText(strings.getString(R.string.budget_type_confirm_title_tracking)).assertDoesNotExist()
    }

    @Test fun confirmingAppliesTheNewType() {
        openBudgetTypeChoice()
        compose.onNodeWithText(strings.getString(R.string.budget_type_confirm_action)).performClick()

        assertEquals(listOf("tracking"), changes)
    }
}
