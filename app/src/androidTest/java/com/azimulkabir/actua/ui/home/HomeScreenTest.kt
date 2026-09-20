package com.azimulkabir.actua.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.home.HomeSection
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeScreenShowsItsRootTitle() {
        compose.setContent {
            MaterialTheme {
                HomeScreen()
            }
        }

        compose.onNodeWithText("Home").assertExists()
    }

    @Test fun hiddenSectionsAreOmittedFromTheRenderedLayout() {
        compose.setContent {
            MaterialTheme {
                HomeScreen(sections = listOf(HomeSection.READY_TO_BUDGET, HomeSection.RECENT_ACTIVITY))
            }
        }

        // "Ready to Budget" renders twice on purpose: once as the section header, once as the
        // card's own label (see ReadyToBudgetCard), so it needs a count assertion rather than
        // onNodeWithText, which requires exactly one match.
        compose.onAllNodesWithText(HomeSection.READY_TO_BUDGET.title).assertCountEquals(2)
        compose.onNodeWithText(HomeSection.RECENT_ACTIVITY.title).assertExists()
        compose.onNodeWithText(HomeSection.REPORTS.title).assertDoesNotExist()
    }

    @Test fun customizeButtonInvokesItsCallback() {
        var clicked = false
        compose.setContent {
            MaterialTheme {
                HomeScreen(onCustomizeClick = { clicked = true })
            }
        }

        compose.onNodeWithContentDescription("Customize Home").performClick()

        assertTrue(clicked)
    }

    @Test fun reportsSectionRoutesToTheSharedReportsDestination() {
        var reportsClicked = false
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    sections = listOf(HomeSection.REPORTS),
                    onReportsClick = { reportsClicked = true },
                )
            }
        }

        compose.onNodeWithText("Dashboards and financial insights").performClick()

        assertTrue(
            "Home's Reports shortcut must route into the same Reports destination used " +
                "elsewhere in the app, not a duplicate implementation",
            reportsClicked,
        )
    }
}
