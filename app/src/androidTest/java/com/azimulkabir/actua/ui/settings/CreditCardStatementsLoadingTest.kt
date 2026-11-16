package com.azimulkabir.actua.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.model.CreditCardCycle
import org.junit.Rule
import org.junit.Test

class CreditCardStatementsLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyStateWaitsForTheFirstLoad() {
        val loading = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                CreditCardStatementsScreen(
                    accountName = "Synthetic card", statements = emptyList(), hideDecimalPlaces = false,
                    onBack = {}, onSelectStatement = {}, isLoading = loading.value,
                )
            }
        }
        compose.onNodeWithText("Synthetic card").assertExists()
        compose.onNodeWithText("No closed statements yet.").assertDoesNotExist()
        compose.runOnIdle { loading.value = false }
        compose.onNodeWithText("No closed statements yet.").assertExists()
    }

    @Test fun existingStatementsStayVisibleWhileRefreshing() {
        val statement = CreditCardCycle.StatementRecord(
            startDate = DayDate(2026, 9, 1), endDate = DayDate(2026, 9, 30), dueDate = DayDate(2026, 10, 15),
            statementBalance = 1_000, paymentsSince = 1_000, remainingDue = 0, totalSpend = 1_000,
        )
        compose.setContent {
            MaterialTheme {
                CreditCardStatementsScreen(
                    accountName = "Synthetic card", statements = listOf(statement), hideDecimalPlaces = false,
                    onBack = {}, onSelectStatement = {}, isLoading = true,
                )
            }
        }
        compose.onNodeWithText("Paid").assertExists()
        compose.onNodeWithText("No closed statements yet.").assertDoesNotExist()
    }
}
