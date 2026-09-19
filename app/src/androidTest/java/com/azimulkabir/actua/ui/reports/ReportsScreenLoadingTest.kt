package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportsScreenLoadingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptySnapshotShowsLoadingStateInsteadOfEmptyStateWhileLoadIsPending() {
        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0),
                    hideDecimalPlaces = false,
                    isLoading = true,
                )
            }
        }

        compose.onNodeWithText("Reports").assertExists()
        compose.onNodeWithTag("reportsLoadingIndicator").assertExists()
        compose.onNodeWithText("No report data yet").assertDoesNotExist()
    }

    @Test
    fun retainedSnapshotRemainsVisibleDuringRefresh() {
        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(
                        months = emptyList(),
                        categories = emptyList(),
                        netWorthCents = 0,
                        dashboards = listOf(ReportDashboardPage("overview", "Overview", emptyList())),
                    ),
                    hideDecimalPlaces = false,
                    isLoading = true,
                )
            }
        }

        compose.onNodeWithText("Overview").assertExists()
        compose.onNodeWithTag("reportsLoadingIndicator").assertDoesNotExist()
    }
}
