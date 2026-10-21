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
import com.azimulkabir.actua.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression coverage for issue #619: category group collapse state must survive leaving and returning to Budget. */
@RunWith(AndroidJUnit4::class)
class BudgetScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    /** Group-header and menu labels come from resources, so assertions follow the app language. */
    private fun label(resId: Int, vararg args: Any) = context.getString(resId, *args)

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

        compose.onNodeWithContentDescription(label(R.string.budget_collapse_group, "Monthly bills")).performClick()
        compose.onNodeWithContentDescription(label(R.string.budget_expand_group, "Monthly bills")).assertExists()

        // Simulate leaving the Budget tab (screen torn down) and coming back to it.
        compose.runOnIdle { showScreen = false }
        compose.runOnIdle { showScreen = true }

        compose.onNodeWithContentDescription(label(R.string.budget_expand_group, "Monthly bills")).assertExists()
    }

    @Test fun collapseAllPersistsAcrossRecomposition() {
        var showScreen by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (showScreen) BudgetScreen()
            }
        }

        compose.onNodeWithContentDescription(label(R.string.budget_display_options)).performClick()
        compose.onNodeWithText(label(R.string.budget_collapse_all_groups)).performClick()
        compose.onNodeWithContentDescription(label(R.string.budget_expand_group, "Monthly bills")).assertExists()

        compose.runOnIdle { showScreen = false }
        compose.runOnIdle { showScreen = true }

        compose.onNodeWithContentDescription(label(R.string.budget_expand_group, "Monthly bills")).assertExists()
        compose.onNodeWithContentDescription(label(R.string.budget_expand_group, "Daily spending")).assertExists()
    }
}
