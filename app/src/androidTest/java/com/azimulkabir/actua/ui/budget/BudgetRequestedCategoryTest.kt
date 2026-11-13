package com.azimulkabir.actua.ui.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A category name can repeat across category groups, so the category a screen asks Budget to open is named
 * by its key (its id), and the details that open are that category's, not the first one with the name.
 */
@RunWith(AndroidJUnit4::class)
class BudgetRequestedCategoryTest {
    @get:Rule
    val compose = createComposeRule()

    private fun category(name: String, id: String) = BudgetCategory(name = name, assigned = 0, spent = 0, id = id)

    private val groups = listOf(
        BudgetGroup("Category Group 1", listOf(category("Wardrobe", "wardrobe-1"))),
        BudgetGroup("Category Group 3", listOf(category("Wardrobe", "wardrobe-3"))),
    )

    /** The key Budget reports for the details it opened after being asked for [requested]. */
    private fun openedFor(requested: String): String? {
        var reported: String? = null
        compose.setContent {
            MaterialTheme {
                BudgetScreen(
                    groups = groups,
                    overview = BudgetOverview(0, 0, 0, 0),
                    requestedCategoryDetails = requested,
                    onCategoryDetailsChange = { reported = it },
                )
            }
        }
        compose.waitForIdle()
        return reported
    }

    @Test fun aFavoriteInTheSecondGroupOpensThatGroupsCategory() {
        assertEquals("wardrobe-3", openedFor("wardrobe-3"))
    }

    @Test fun theFirstGroupsCategoryStillOpensByItsOwnKey() {
        assertEquals("wardrobe-1", openedFor("wardrobe-1"))
    }

    @Test fun aNameFromAStateSavedBeforeKeysStillOpensTheFirstMatch() {
        assertEquals("wardrobe-1", openedFor("Wardrobe"))
    }
}
