package com.azimulkabir.actua.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.home.HomeSection
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.components.BalanceVisibility
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @After fun resetBalanceVisibility() {
        BalanceVisibility.hidden = false
    }

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

        compose.onAllNodesWithText(HomeSection.READY_TO_BUDGET.title).assertCountEquals(1)
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

    @Test fun favoriteCategoryRowRoutesToBudget() {
        var budgetClicked = false
        val projection = HomeDashboardProjection.empty().copy(
            favoriteCategories = listOf(BudgetCategory("Groceries", 0, 0, id = "groceries")),
        )
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    sections = listOf(HomeSection.FAVORITE_CATEGORIES),
                    projection = projection,
                    onBudgetClick = { budgetClicked = true },
                )
            }
        }

        compose.onNodeWithText("Groceries").performClick()

        assertTrue(budgetClicked)
    }

    @Test fun favoriteCategoriesRenderAsDashboardProgressCards() {
        val projection = HomeDashboardProjection.empty().copy(
            favoriteCategories = listOf(
                BudgetCategory("Groceries", 1_000, -400, id = "groceries"),
                BudgetCategory("Transport", 500, -100, id = "transport"),
            ),
        )
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    sections = listOf(HomeSection.FAVORITE_CATEGORIES),
                    projection = projection,
                )
            }
        }

        compose.onAllNodesWithTag("favoriteCategoryCard").assertCountEquals(2)
        compose.onNodeWithText("Groceries").assertExists()
        compose.onNodeWithText("Transport").assertExists()
    }

    @Test fun favoriteAccountRowRoutesToAccounts() {
        var accountsClicked = false
        val projection = HomeDashboardProjection.empty().copy(
            favoriteAccounts = listOf(Account("Checking", 0, "savings", id = "checking")),
        )
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    sections = listOf(HomeSection.FAVORITE_ACCOUNTS),
                    projection = projection,
                    onAccountsClick = { accountsClicked = true },
                )
            }
        }

        compose.onNodeWithText("Checking").performClick()

        assertTrue(accountsClicked)
    }

    @Test fun upcomingRowRoutesToSchedules() {
        var schedulesClicked = false
        val schedule = ScheduleListItem(
            ActualScheduleSummary(
                "rent", "Rent", null, null, null, null, null, null, null,
                ScheduleAmountOp.APPROXIMATE, null, null, false, false, null, null,
                false, null, null, null,
            ),
            ScheduleStatus.SCHEDULED, null, null,
        )
        val projection = HomeDashboardProjection.empty().copy(upcomingSchedules = listOf(schedule))
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    sections = listOf(HomeSection.UPCOMING),
                    projection = projection,
                    onSchedulesClick = { schedulesClicked = true },
                )
            }
        }

        compose.onNodeWithText("Rent").performClick()

        assertTrue(schedulesClicked)
    }

    @Test fun recentActivityRowRoutesToTransactions() {
        var transactionsClicked = false
        val transaction = Transaction("t1", "2026-09-20", "Coffee Shop", "Dining", "Checking", -500, false)
        val projection = HomeDashboardProjection.empty().copy(recentTransactions = listOf(transaction))
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    sections = listOf(HomeSection.RECENT_ACTIVITY),
                    projection = projection,
                    onTransactionsClick = { transactionsClicked = true },
                )
            }
        }

        compose.onNodeWithText("Coffee Shop").performClick()

        assertTrue(transactionsClicked)
    }

    @Test fun thisMonthCardRoutesToTransactions() {
        var transactionsClicked = false
        val transaction = Transaction("t1", "2026-09-20", "Coffee Shop", "Dining", "Checking", -500, false)
        val projection = HomeDashboardProjection.empty().copy(monthTransactions = listOf(transaction))
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    sections = listOf(HomeSection.THIS_MONTH),
                    projection = projection,
                    onTransactionsClick = { transactionsClicked = true },
                )
            }
        }

        compose.onNodeWithText("Activity").performClick()

        assertTrue(transactionsClicked)
    }

    @Test fun favoriteReportRowRoutesDirectlyToThatReport() {
        var clickedReportId: String? = null
        var reportsClicked = false
        val projection = HomeDashboardProjection.empty().copy(
            favoriteReports = listOf(ReportDashboardPage("net-worth", "Net Worth", emptyList())),
        )
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    sections = listOf(HomeSection.REPORTS),
                    projection = projection,
                    onReportClick = { clickedReportId = it },
                    onReportsClick = { reportsClicked = true },
                )
            }
        }

        compose.onNodeWithText("Net Worth").performClick()

        assertTrue(
            "Tapping a favorited report should route directly to it, not the generic Reports shortcut",
            clickedReportId == "net-worth" && !reportsClicked,
        )
    }

    @Test fun hidingBalancesMasksTheReadyToBudgetAmount() {
        BalanceVisibility.hidden = true
        val projection = HomeDashboardProjection.empty().copy(
            budgetOverview = BudgetOverview(500_00, 200_00, 100_00, 400_00),
        )
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    sections = listOf(HomeSection.READY_TO_BUDGET),
                    projection = projection,
                )
            }
        }

        compose.onNodeWithText("••••").assertExists()
    }

    @Test fun emptySectionsShowTheirEmptyStateCopy() {
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    sections = listOf(
                        HomeSection.FAVORITE_CATEGORIES,
                        HomeSection.FAVORITE_ACCOUNTS,
                        HomeSection.UPCOMING,
                        HomeSection.RECENT_ACTIVITY,
                    ),
                )
            }
        }

        compose.onNodeWithText("No favorite categories yet").assertExists()
        compose.onNodeWithText("No favorite accounts yet").assertExists()
        compose.onNodeWithText("No upcoming bills or schedules").assertExists()
        compose.onNodeWithText("No recent activity").assertExists()
    }

    @Test fun lastHomeSectionScrollsAboveTheTransactionFab() {
        val projection = HomeDashboardProjection.empty().copy(
            recentTransactions = List(5) { index ->
                Transaction("t$index", "20260920", "Last activity ${index + 1}", "Dining", "Checking", -500, false)
            },
        )
        compose.setContent {
            MaterialTheme {
                Scaffold(
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = {},
                            modifier = Modifier.testTag("transactionFab"),
                        ) { androidx.compose.material3.Text("Transaction") }
                    },
                ) { padding ->
                    HomeScreen(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        sections = HomeSection.entries.toList(),
                        projection = projection,
                    )
                }
            }
        }

        compose.onNodeWithTag("homeList").performScrollToNode(hasText("Last activity 5"))
        compose.waitForIdle()

        compose.onNodeWithText("Last activity 5").assertExists()
        compose.onNodeWithTag("transactionFab").assertExists()
    }
}
