package com.azimulkabir.actua.ui.transactions

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddTransactionScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reverseTransferAccountsSwapsOnlyTheTransferDirection() {
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

        compose.onNodeWithContentDescription("Reverse transfer accounts").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()

        assertEquals("Savings", saved?.account)
        assertEquals("Checking", saved?.transferAccount)
        assertEquals(-1_250L, saved?.amountCents)
        assertEquals("Rent transfer", saved?.notes)
        assertEquals(true, saved?.cleared)
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

        compose.onNodeWithText("Save").performScrollTo().performClick()

        assertEquals(0L, saved?.amountCents)
    }
}
