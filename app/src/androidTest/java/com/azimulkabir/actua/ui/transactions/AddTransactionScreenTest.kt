package com.azimulkabir.actua.ui.transactions

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.R
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddTransactionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** Labels are read from the resources the screen renders with, so the suite tracks translations. */
    private val strings get() = compose.activity

    @Test fun reverseTransferSwapsOnlyTheTransferDirection() {
        val editing = Transaction(
            id = "transfer",
            date = "20260920",
            payee = "",
            category = "",
            account = "Checking",
            transferAccount = "Savings",
            amountCents = -1_250,
            amount = -12,
            type = Type.TRANSFER,
            notes = "Rent transfer",
            cleared = true,
        )
        var saved: Transaction? = null
        compose.setContent {
            MaterialTheme {
                AddTransactionScreen(
                    editing = editing,
                    onBack = {},
                    onSave = { saved = it },
                    accountOptions = listOf("Checking", "Savings"),
                )
            }
        }

        compose.onNodeWithText(strings.getString(R.string.transaction_reverse_transfer)).performClick()
        compose.onNodeWithText(strings.getString(R.string.action_save)).performClick()

        assertEquals("Savings", saved?.account)
        assertEquals("Checking", saved?.transferAccount)
        assertEquals(-1_250L, saved?.amountCents)
        assertEquals("Rent transfer", saved?.notes)
        assertEquals(true, saved?.cleared)
    }

    @Test fun categoryPickerShowsAvailableBalance() {
        compose.setContent {
            MaterialTheme {
                PickerTextField(
                    label = strings.getString(R.string.transaction_category),
                    value = "",
                    options = listOf("Groceries"),
                    onValueChange = {},
                    supportingValues = mapOf("Groceries" to "$42.00"),
                    autoOpen = true,
                )
            }
        }

        compose.onNodeWithText("Groceries").assertExists()
        compose.onNodeWithText("$42.00").assertExists()
    }

    @Test fun zeroAmountTransactionCanBeSaved() {
        val editing = Transaction(
            id = "placeholder",
            date = "20260920",
            payee = "",
            category = "",
            account = "Checking",
            amountCents = 0,
            amount = 0,
            type = Type.EXPENSE,
            notes = "",
            cleared = false,
        )
        var saved: Transaction? = null
        compose.setContent {
            MaterialTheme {
                AddTransactionScreen(
                    editing = editing,
                    onBack = {},
                    onSave = { saved = it },
                    accountOptions = listOf("Checking", "Savings"),
                )
            }
        }

        compose.onNodeWithText(strings.getString(R.string.action_save)).performClick()

        assertEquals(0L, saved?.amountCents)
    }

    @Test fun editingTheIncomingLegOfATransferKeepsItsDirection() {
        // Savings' leg of a Checking → Savings transfer, as the Savings register shows it.
        val editing = Transaction(
            id = "transfer-in", date = "20260920", payee = "", category = "", account = "Savings",
            transferAccount = "Checking", amountCents = 1_250, amount = 12, type = Type.TRANSFER,
            cleared = false,
        )
        var saved: Transaction? = null
        compose.setContent {
            MaterialTheme {
                AddTransactionScreen(
                    editing = editing, onBack = {}, onSave = { saved = it },
                    accountOptions = listOf("Checking", "Savings"),
                )
            }
        }

        compose.onNodeWithText(
            strings.getString(R.string.transaction_transfer_direction, "Checking", "Savings"),
        ).assertExists()
        compose.onNodeWithText(strings.getString(R.string.action_save)).performClick()

        assertEquals(Type.TRANSFER, saved?.type)
        assertEquals("Checking", saved?.account)
        assertEquals("Savings", saved?.transferAccount)
        assertEquals(-1_250L, saved?.amountCents)
    }

    @Test fun signSwitchTurnsAnExpenseIntoIncome() {
        val editing = Transaction(
            id = "coffee", date = "20260920", payee = "Cafe", category = "Dining", account = "Checking",
            amountCents = -450, amount = -4, type = Type.EXPENSE, cleared = false,
        )
        var saved: Transaction? = null
        compose.setContent {
            MaterialTheme {
                AddTransactionScreen(
                    editing = editing, onBack = {}, onSave = { saved = it },
                    accountOptions = listOf("Checking"), categoryOptions = listOf("Dining"),
                )
            }
        }

        compose.onNodeWithText(strings.getString(R.string.transaction_switch_to_income)).performClick()
        compose.onNodeWithText(strings.getString(R.string.transaction_type_income)).assertExists()
        compose.onNodeWithText(strings.getString(R.string.action_save)).performClick()

        assertEquals(Type.INCOME, saved?.type)
        assertEquals(450L, saved?.amountCents)
        assertEquals("Cafe", saved?.payee)
        assertEquals("Dining", saved?.category)
    }

    @Test fun transferBetweenBudgetAccountsShowsALockedCategory() {
        val editing = Transaction(
            id = "transfer", date = "20260920", payee = "", category = "", account = "Checking",
            transferAccount = "Savings", amountCents = -1_000, amount = -10, type = Type.TRANSFER, cleared = false,
        )
        compose.setContent {
            MaterialTheme {
                AddTransactionScreen(
                    editing = editing, onBack = {}, onSave = {},
                    accountOptions = listOf("Checking", "Savings"),
                )
            }
        }

        compose.onNodeWithText(strings.getString(R.string.transaction_transfer_not_categorized)).assertExists()
        compose.onNodeWithText(strings.getString(R.string.transaction_split_multiple_categories)).assertDoesNotExist()
    }

    @Test fun splittingShowsEachLineAsAFormCard() {
        val editing = Transaction(
            id = "groceries", date = "20260920", payee = "Market", category = "Groceries", account = "Checking",
            amountCents = -1_000, amount = -10, cleared = false,
        )
        compose.setContent {
            MaterialTheme {
                AddTransactionScreen(
                    editing = editing, onBack = {}, onSave = {},
                    accountOptions = listOf("Checking"),
                )
            }
        }

        compose.onNodeWithText(strings.getString(R.string.transaction_split_multiple_categories)).performScrollTo().performClick()

        compose.onNodeWithText(strings.getString(R.string.transaction_split_categories)).assertExists()
        compose.onNodeWithText(strings.getString(R.string.transaction_split_number, 2)).performScrollTo().assertExists()
        compose.onAllNodesWithText(strings.getString(R.string.transaction_opposite_direction)).assertCountEquals(2)
        compose.onAllNodesWithText(strings.getString(R.string.transaction_add_amount)).assertCountEquals(1)
        compose.onNodeWithText(strings.getString(R.string.transaction_remove_split)).performClick()
        compose.onNodeWithText(strings.getString(R.string.transaction_split_multiple_categories)).performScrollTo().assertExists()
    }
}
