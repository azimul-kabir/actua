package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.components.formatMoneyCents
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Slice 8 of #1106 (#1114): calendar month swipe, day breakdown and one-tap day. */
@RunWith(AndroidJUnit4::class)
class CalendarReportTest {
    @get:Rule val compose = createComposeRule()

    private val widget = ReportWidget(
        id = "calendar", kind = ReportWidgetKind.CALENDAR, name = "Calendar",
        points = listOf(
            ReportPoint("2026-07-03", 100_00, 20_00, listOf("jul-1")),
            ReportPoint(
                "2026-08-15", 500_00, 120_00, listOf("aug-1", "aug-2"),
                segments = listOf(
                    ReportCategory("Salary", 500_00, listOf("aug-1"), incomeCents = 500_00, expenseCents = 0),
                    ReportCategory("Groceries", -120_00, listOf("aug-2"), incomeCents = 0, expenseCents = 120_00),
                ),
            ),
        ),
    )

    private var requested: List<String>? = null

    private fun setContent() = compose.setContent {
        MaterialTheme {
            ReportsScreen(
                snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("o", "Overview", listOf(widget)))),
                hideDecimalPlaces = false,
                loadTransactions = { ids ->
                    requested = ids
                    listOf(Transaction("t", "2026-08-15", "Bakery", "Shopping", "Checking", -120, cleared = true))
                },
            )
        }
    }

    @Test
    fun swipingChangesMonthAndItsTotals() {
        setContent()
        compose.onNodeWithText("August 2026").assertIsDisplayed()
        compose.onNodeWithText("▲ ${formatMoneyCents(500_00, false)}").assertIsDisplayed()

        compose.onNodeWithContentDescription("Calendar for August 2026", substring = true)
            .performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("July 2026").assertIsDisplayed()
        compose.onNodeWithText("▲ ${formatMoneyCents(100_00, false)}").assertIsDisplayed()
        compose.onNodeWithText("▼ ${formatMoneyCents(20_00, false)}").assertIsDisplayed()

        compose.onNodeWithContentDescription("Calendar for July 2026", substring = true)
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("August 2026").assertIsDisplayed()
    }

    @Test
    fun longPressShowsTheDaysCategoryBreakdown() {
        setContent()
        compose.onNodeWithText("15").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("August 15 breakdown").assertIsDisplayed()
        compose.onNodeWithText("Salary").assertIsDisplayed()
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onNodeWithText("View transactions").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(listOf("aug-1", "aug-2"), requested)
        compose.onNodeWithText("Bakery").assertIsDisplayed()
    }

    @Test
    fun oneTapStillOpensTheDaysTransactions() {
        setContent()
        compose.onNodeWithText("15").performClick()
        compose.waitForIdle()
        assertEquals(listOf("aug-1", "aug-2"), requested)
        compose.onNodeWithText("Bakery").assertIsDisplayed()
    }
}
