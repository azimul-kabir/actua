package com.azimulkabir.actua.ui.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.azimulkabir.actua.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GlobalSearchScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun databaseResultsAppearWithoutLoadedTransactionsAndQueryChangesDiscardOldResults() {
        val requests = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                GlobalSearchScreen(
                    transactions = emptyList(), accounts = emptyList(), payees = emptyList(), categories = emptyList(),
                    searchTransactions = { query, _, _ ->
                        requests += query
                        listOf(Transaction(query, "20260908", "Parent for $query", "Split", "Checking", -10, false))
                    },
                    hideDecimalPlaces = false, onBack = {}, onTransactionEdit = {},
                    onTransactionDelete = {}, onTransactionClearedChange = { _, _ -> },
                    onAccountClick = {}, onCategoryClick = {}, onPayeeClick = {},
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("child-only")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Parent for child-only").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Parent for child-only").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("different")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Parent for different").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Parent for child-only").assertDoesNotExist()
        compose.onNodeWithText("Parent for different").assertIsDisplayed()
        assertEquals(listOf("child-only", "different"), requests)
    }
}
