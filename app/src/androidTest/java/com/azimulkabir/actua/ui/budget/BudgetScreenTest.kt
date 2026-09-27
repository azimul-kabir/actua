package com.azimulkabir.actua.ui.budget

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression coverage for issue #619: category group collapse state must survive leaving and returning to Budget. */
@RunWith(AndroidJUnit4::class)
class BudgetScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() {
        context.getSharedPreferences("budget_ui_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("active_budget", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun collapsedGroupSurvivesLeavingAndReturningToBudgetScreen() {
        var showScreen by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (showScreen) BudgetScreen()
            }
        }

        compose.onNodeWithContentDescription("Collapse Monthly bills").performClick()
        compose.onNodeWithContentDescription("Expand Monthly bills").assertExists()

        // Simulate leaving the Budget tab (screen torn down) and coming back to it.
        compose.runOnIdle { showScreen = false }
        compose.runOnIdle { showScreen = true }

        compose.onNodeWithContentDescription("Expand Monthly bills").assertExists()
    }

    @Test fun collapseAllPersistsAcrossRecomposition() {
        var showScreen by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (showScreen) BudgetScreen()
            }
        }

        compose.onNodeWithContentDescription("Budget display options").performClick()
        compose.onNodeWithText("Collapse all groups").performClick()
        compose.onNodeWithContentDescription("Expand Monthly bills").assertExists()

        compose.runOnIdle { showScreen = false }
        compose.runOnIdle { showScreen = true }

        compose.onNodeWithContentDescription("Expand Monthly bills").assertExists()
        compose.onNodeWithContentDescription("Expand Daily spending").assertExists()
    }
}
