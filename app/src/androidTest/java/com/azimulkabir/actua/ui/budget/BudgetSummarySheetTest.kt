package com.azimulkabir.actua.ui.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the acceptance criteria from #260 and #266: the Ready/To Budget summary must be a
 * direct entry point into "Move to a category" / "Hold for next month", with no overflow menu
 * and no popup nested inside another modal, and it must open straight into the expanded
 * Move to Category editor without requiring a second tap.
 */
@RunWith(AndroidJUnit4::class)
class BudgetSummarySheetTest {
    @get:Rule
    val compose = createComposeRule()

    /** Sheet labels come from resources, so assertions follow the app language. */
    private fun label(resId: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun overview(toBudgetCents: Long, bufferedCents: Long = 0L) = BudgetOverview(
        toBudgetCents = toBudgetCents,
        budgetedCents = 0L,
        spentCents = 0L,
        availableCents = 0L,
        bufferedCents = bufferedCents,
    )

    private val oneGroup = listOf(
        BudgetGroup(
            name = "Bills",
            categories = listOf(BudgetCategory(name = "Rent", assigned = 0, spent = 0)),
        ),
    )

    @Test
    fun smokeBudgetScreenComposesAndShowsReadyToBudget() {
        compose.setContent {
            MaterialTheme { BudgetScreen(groups = emptyList(), overview = overview(toBudgetCents = 1_200L)) }
        }

        compose.onNodeWithText(label(R.string.budget_ready_to_budget)).assertExists()
    }

    @Test
    fun readyToBudgetOpensBothActionsDirectlyWithoutAnOverflowMenu() {
        compose.setContent {
            MaterialTheme { BudgetScreen(groups = emptyList(), overview = overview(toBudgetCents = 1_200L)) }
        }

        // A single tap on the summary itself is the only step needed to reach both actions.
        compose.onNodeWithText(label(R.string.budget_ready_to_budget)).performClick()

        compose.onNodeWithText(label(R.string.budget_summary)).assertExists()
        compose.onNodeWithText(label(R.string.budget_move_to_category)).assertExists()
        compose.onNodeWithText(label(R.string.budget_hold_for_next_month)).assertExists()
    }

    @Test
    fun readyToBudgetOpensDirectlyIntoMoveToCategoryWithoutASecondTap() {
        compose.setContent {
            MaterialTheme { BudgetScreen(groups = oneGroup, overview = overview(toBudgetCents = 1_200L)) }
        }

        // A single tap on Ready to Budget should land straight in the expanded Move to
        // Category editor: category selector, amount, and calculator all visible already,
        // with no need to tap "Move to Category" a second time.
        compose.onNodeWithText(label(R.string.budget_ready_to_budget)).performClick()

        compose.onNodeWithText("Bills · Rent").assertExists()
        compose.onNodeWithText(label(R.string.budget_choose_category_fund)).assertExists()
        compose.onNodeWithText(label(R.string.budget_amount)).assertExists()
    }

    @Test
    fun categoryLongPressAddsAndRemovesFavoritesUsingTheSharedCallback() {
        val category = BudgetCategory(name = "Groceries", assigned = 0, spent = 0, id = "groceries")
        var change: Pair<String, Boolean>? = null
        var favoriteIds by mutableStateOf(emptySet<String>())
        compose.setContent {
            MaterialTheme {
                BudgetScreen(
                    groups = listOf(BudgetGroup(name = "Bills", categories = listOf(category))),
                    overview = overview(toBudgetCents = 1_200L),
                    favoriteCategoryIds = favoriteIds,
                    onFavoriteCategoryChange = { id, favorite ->
                        change = id to favorite
                        favoriteIds = if (favorite) favoriteIds + id else favoriteIds - id
                    },
                )
            }
        }

        compose.onNodeWithText("Groceries").performTouchInput {
            down(center); advanceEventTime(1_000); up()
        }
        compose.onNodeWithText(label(R.string.budget_add_favorite)).performClick()

        org.junit.Assert.assertEquals("groceries" to true, change)

        compose.onNodeWithText("Groceries").performTouchInput {
            down(center); advanceEventTime(1_000); up()
        }
        compose.onNodeWithText(label(R.string.budget_remove_favorite)).performClick()

        org.junit.Assert.assertEquals("groceries" to false, change)
    }
}
