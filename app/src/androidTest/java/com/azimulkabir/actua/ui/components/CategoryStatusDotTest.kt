package com.azimulkabir.actua.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetProgressState
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
}
