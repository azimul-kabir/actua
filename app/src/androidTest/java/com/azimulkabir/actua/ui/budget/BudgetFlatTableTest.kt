package com.azimulkabir.actua.ui.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.BudgetDisplayOptions
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetFlatTableTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun lightTableWithoutProgressBars() = checkFlatTable(dark = false, stacked = false, progress = false)

    @Test fun darkStackedTableWithProgressBars() = checkFlatTable(dark = true, stacked = true, progress = true)

    private fun checkFlatTable(dark: Boolean, stacked: Boolean, progress: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expense = BudgetGroup(
            name = "Household",
            categories = listOf(BudgetCategory(name = "Groceries", assigned = 100, spent = 50)),
        )
        val income = BudgetGroup(
            name = "Income sources",
            isIncome = true,
            categories = listOf(BudgetCategory(name = "Salary", assigned = 0, spent = 0, isIncome = true)),
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (stacked) 1.5f else 1f)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.width(360.dp)) {
                        BudgetScreen(
                            groups = listOf(expense, income),
                            overview = BudgetOverview(0L, 0L, 0L, 0L),
                            budgetView = "Table",
                            displayOptions = BudgetDisplayOptions(
                                flatTable = true,
                                showOverview = false,
                                showCategoryFilters = false,
                                showBudgetStatusBanners = false,
                                showGroupTotals = true,
                                showProgressBars = progress,
                            ),
                        )
                    }
                }
            }
        }

        val category = compose.onNode(hasText("Groceries") and hasClickAction())
        val incomeHeader = compose.onNode(hasText(income.name) and hasClickAction())
        assertEquals(category.getUnclippedBoundsInRoot().bottom, incomeHeader.getUnclippedBoundsInRoot().top)

        for (group in listOf(expense, income)) {
            val header = compose.onNode(hasText(group.name) and hasClickAction())
            val row = compose.onNodeWithText(group.categories.single().name)
            val collapse = context.getString(R.string.budget_collapse_group, group.name)
            val expand = context.getString(R.string.budget_expand_group, group.name)

            compose.onNodeWithContentDescription(collapse, useUnmergedTree = true).assertDoesNotExist()
            row.assertIsDisplayed()
            header.assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, context.getString(R.string.budget_group_expanded),
            ))
            assertEquals(collapse, header.fetchSemanticsNode().config[SemanticsActions.OnClick].label)

            header.performClick()
            row.assertDoesNotExist()
            header.assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, context.getString(R.string.budget_group_collapsed),
            ))
            assertEquals(expand, header.fetchSemanticsNode().config[SemanticsActions.OnClick].label)

            header.performClick()
            row.assertIsDisplayed()
        }
    }
}
