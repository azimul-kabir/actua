package com.azimulkabir.actua.ui.transactions

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardConfig
import com.azimulkabir.actua.model.CreditCardStatus
import org.junit.Rule
import org.junit.Test

/**
 * Covers #276: Cleared/Balance/Uncleared are always visible in a three-column row, while
 * Reconciled (and, for credit cards, Available credit / Credit limit) stay behind a
 * collapsible toggle.
 */
class AccountDetailsTest {
    @get:Rule val compose = createComposeRule()

    private val account = Account(
        name = "Checking",
        balance = 500,
        type = "checking",
        clearedCents = 40_000L,
        unclearedCents = 10_000L,
        reconciledCents = 30_000L,
    )

    private fun cardStatus(limitCents: Long? = 200_000L) = CreditCardStatus(
        accountId = account.id,
        accountName = account.name,
        balanceCents = account.balanceCents,
        config = CreditCardConfig(statementDay = 15, limitCents = limitCents),
        cycleSpendCents = 5_000L,
        availableCreditCents = 150_000L,
        closed = false,
    )

    private fun hasContentDescriptionContaining(substring: String) = SemanticsMatcher(
        "ContentDescription contains '$substring'"
    ) { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains(substring) } == true
    }

    @Test
    fun clearedBalanceAndUnclearedAreAlwaysVisible() {
        compose.setContent {
            MaterialTheme {
                AccountDetails(account, null, "", {}, hideDecimals = false, showSummary = true, showNotes = false)
            }
        }

        compose.onNode(hasContentDescriptionContaining("Cleared")).assertExists()
        compose.onNode(hasContentDescriptionContaining("Balance")).assertExists()
        compose.onNode(hasContentDescriptionContaining("Uncleared")).assertExists()
    }

    @Test
    fun reconciledStaysCollapsedUntilToggled() {
        compose.setContent {
            MaterialTheme {
                AccountDetails(account, null, "", {}, hideDecimals = false, showSummary = true, showNotes = false)
            }
        }

        // Only the toggle row itself says "Reconciled" until it is expanded.
        compose.onAllNodesWithText("Reconciled").assertCountEquals(1)

        compose.onNodeWithText("Reconciled").performClick()

        compose.onAllNodesWithText("Reconciled").assertCountEquals(2)
    }

    @Test
    fun creditLimitSitsUnderAvailableCreditWhenExpanded() {
        compose.setContent {
            MaterialTheme {
                AccountDetails(account, cardStatus(), "", {}, hideDecimals = false, showSummary = true, showNotes = false)
            }
        }

        compose.onNode(hasText("Available credit")).assertDoesNotExist()
        compose.onNode(hasText("Credit limit")).assertDoesNotExist()

        compose.onNodeWithText("Reconciled").performClick()

        val order = compose.onAllNodesWithText("Available credit").fetchSemanticsNodes() +
            compose.onAllNodesWithText("Credit limit").fetchSemanticsNodes()
        // Available credit must appear before Credit limit in layout order (top to bottom).
        assert(order[0].boundsInRoot.top < order[1].boundsInRoot.top)
    }
}
