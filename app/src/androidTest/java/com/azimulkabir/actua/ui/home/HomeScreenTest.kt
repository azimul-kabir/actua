package com.azimulkabir.actua.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportsEntryIsVisibleAndOpensReports() {
        val reportsOpened = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                HomeScreen(onReportsClick = { reportsOpened.value = true })
            }
        }

        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText("Dashboards and financial insights").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(reportsOpened.value) }
    }

    @Test fun dashboardSectionsRouteToTheirAuthoritativeDestinations() {
        val budgetOpened = mutableStateOf(false)
        val accountsOpened = mutableStateOf(false)
        val schedulesOpened = mutableStateOf(false)
        val transactionsOpened = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    onBudgetClick = { budgetOpened.value = true },
                    onAccountsClick = { accountsOpened.value = true },
                    onSchedulesClick = { schedulesOpened.value = true },
                    onTransactionsClick = { transactionsOpened.value = true },
                )
            }
        }

        compose.onNodeWithText("No favorite categories yet").performScrollTo().performClick()
        compose.onNodeWithText("No favorite accounts yet").performScrollTo().performClick()
        compose.onNodeWithText("No upcoming bills or schedules").performScrollTo().performClick()
        compose.onNodeWithText("No recent activity").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(budgetOpened.value)
            assertTrue(accountsOpened.value)
            assertTrue(schedulesOpened.value)
            assertTrue(transactionsOpened.value)
        }
    }
}
