package com.azimulkabir.actua.ui.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.preferences.BudgetDisplayOptions
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With the Favorites filter on, Budget lists just the favorite category rows, in budget order, with no group
 * headers; with it off the groups are listed as before. The other filters still apply to the flat list.
 */
@RunWith(AndroidJUnit4::class)
class BudgetFavoritesFlatListTest {
    @get:Rule
    val compose = createComposeRule()

    private fun category(name: String, id: String, hidden: Boolean = false) =
        BudgetCategory(name = name, assigned = 10, spent = 4, id = id, hidden = hidden)

    private val groups = listOf(
        BudgetGroup("Category Group 1", listOf(category("Rent", "rent"), category("Wardrobe", "wardrobe"))),
        BudgetGroup("Category Group 2", listOf(category("Fun", "fun"), category("Gifts", "gifts"), category("Misc", "misc", hidden = true))),
    )
    private val favorites = setOf("wardrobe", "fun", "misc")

    private fun show(favoritesOnly: Boolean, showHidden: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                BudgetScreen(
                    groups = groups,
                    overview = BudgetOverview(0, 0, 0, 0),
                    favoritesOnly = favoritesOnly,
                    favoriteCategoryIds = favorites,
                    displayOptions = BudgetDisplayOptions(showHidden = showHidden),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun shown(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun theFavoritesFilterListsOnlyTheFavoriteRowsWithNoGroupHeaders() {
        show(favoritesOnly = true)

        assertTrue("a favorite in the first group", shown("Wardrobe"))
        assertTrue("a favorite in the second group", shown("Fun"))
        assertTrue("a category that is not a favorite is left out", !shown("Rent") && !shown("Gifts"))
        assertTrue("no group headers", !shown("Category Group 1") && !shown("Category Group 2"))
    }

    @Test fun theFlatListKeepsTheBudgetOrder() {
        show(favoritesOnly = true)

        val wardrobe = compose.onAllNodesWithText("Wardrobe", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.top
        val fun_ = compose.onAllNodesWithText("Fun", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.top
        assertTrue("the first group's favorite is above the second group's", wardrobe < fun_)
    }

    @Test fun aHiddenFavoriteIsOnlyListedWhenHiddenCategoriesAreShown() {
        show(favoritesOnly = true)
        assertTrue("hidden favorite is left out by default", !shown("Misc", substring = true))
    }

    @Test fun aHiddenFavoriteIsListedWithShowHidden() {
        show(favoritesOnly = true, showHidden = true)
        assertTrue(shown("Misc", substring = true))
    }

    @Test fun withTheFilterOffTheGroupsAreListedAsBefore() {
        show(favoritesOnly = false)

        assertTrue(shown("Category Group 1") && shown("Category Group 2"))
        assertTrue("every visible category, favorite or not", shown("Rent") && shown("Wardrobe") && shown("Gifts"))
    }

    @Test fun noFavoritesShowsAMessageInsteadOfAnEmptyList() {
        compose.setContent {
            MaterialTheme {
                BudgetScreen(
                    groups = groups,
                    overview = BudgetOverview(0, 0, 0, 0),
                    favoritesOnly = true,
                    favoriteCategoryIds = emptySet(),
                )
            }
        }
        compose.waitForIdle()
        assertTrue(shown("No favorite categories yet"))
        compose.onAllNodesWithText("Rent", useUnmergedTree = true).assertCountEquals(0)
    }
}
