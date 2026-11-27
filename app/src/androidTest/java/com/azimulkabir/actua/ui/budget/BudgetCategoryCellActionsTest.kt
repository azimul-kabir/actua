package com.azimulkabir.actua.ui.budget

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class BudgetCategoryCellActionsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val category = BudgetCategory(name = "Cell actions fixture", assigned = 25, spent = 40, id = "cell-actions")
    private var transactions: Triple<BudgetCategory, Boolean, Boolean>? = null
    private var details: String? = null

    private fun showTable(fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme {
                    BudgetScreen(
                        groups = listOf(BudgetGroup("Cell actions group", listOf(category))),
                        overview = BudgetOverview(0, 0, 0, 0),
                        budgetView = "Table",
                        displayOptions = BudgetDisplayOptions(
                            showOverview = false,
                            showBudgetStatusBanners = false,
                            showCategoryFilters = false,
                            showProgressBars = false,
                        ),
                        onShowCategoryTransactions = { selected, monthOnly, returnToDetails ->
                            transactions = Triple(selected, monthOnly, returnToDetails)
                        },
                        onCategoryDetailsChange = { details = it },
                    )
                }
            }
        }
    }

    private fun target(clickLabel: Int) = compose.onNode(SemanticsMatcher(context.getString(clickLabel)) {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == context.getString(clickLabel) &&
            it.config.getOrNull(SemanticsActions.OnLongClick)?.label == context.getString(R.string.budget_category_options)
    }).assertHeightIsAtLeast(48.dp)

    @Test fun spentOpensTransactionsForTheSelectedMonth() {
        showTable()
        target(R.string.budget_transactions_month).performTouchInput { click() }
        compose.runOnIdle { assertEquals(Triple(category, true, false), transactions) }
    }

    @Test fun largeTextStacksTheTargetsAndKeepsSpentAndLongPressWorking() {
        showTable(fontScale = 1.5f)
        val detailsBounds = compose.onNodeWithText(category.name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val spent = target(R.string.budget_transactions_month)
        val spentBounds = spent.fetchSemanticsNode().boundsInRoot
        check(detailsBounds.bottom <= spentBounds.top)
        target(R.string.budget_edit_budgeted)
        target(R.string.budget_cover_overspending)
        spent.performTouchInput { click() }
        compose.runOnIdle { assertEquals(Triple(category, true, false), transactions) }
        spent.performTouchInput { longClick() }
        compose.onNodeWithText(context.getString(R.string.budget_edit_budgeted)).assertExists()
    }

    @Test fun budgetedOpensTheBudgetEditor() {
        showTable()
        target(R.string.budget_edit_budgeted).performTouchInput { click() }
        compose.onNodeWithContentDescription(context.getString(R.string.budget_details)).assertExists()
    }

    @Test fun anOverspentBalanceOpensTheMoveSheet() {
        showTable()
        target(R.string.budget_cover_overspending).performTouchInput { click() }
        compose.onNodeWithText(context.getString(R.string.budget_amount)).assertExists()
    }

    @Test fun theNameOpensDetails() {
        showTable()
        compose.onNodeWithText(category.name, useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(category.id, details) }
    }

    @Test fun blankRowSpaceOpensDetails() {
        showTable()
        target(R.string.budget_budget_details).performTouchInput { click(Offset(2f, centerY)) }
        compose.runOnIdle { assertEquals(category.id, details) }
    }
}
