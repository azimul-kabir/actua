package com.azimulkabir.actua.ui.components

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetProgressState
import com.azimulkabir.actua.ui.theme.CategoryStatusColorState
import com.azimulkabir.actua.ui.theme.LocalCategoryStatusColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryStatusDotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eachStatusExposesItsLabelForAccessibility() {
        BudgetProgressState.entries.forEach { status ->
            compose.setContent {
                MaterialTheme {
                    CategoryStatusDot(status)
                }
            }
            compose.onNodeWithContentDescription(status.label).assertExists()
        }
    }

    @Test fun dotIsHiddenWhenTheSettingIsTurnedOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("show_category_status_dots", false).commit()
        val state = CategoryStatusColorState(context)

        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCategoryStatusColors provides state) {
                    CategoryStatusDot(BudgetProgressState.FUNDED)
                }
            }
        }

        compose.onNodeWithContentDescription(BudgetProgressState.FUNDED.label).assertDoesNotExist()

        context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
            .edit().remove("show_category_status_dots").commit()
    }
}
