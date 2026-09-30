package com.azimulkabir.actua.ui.accounts

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloseAccountSheetTest {
    @get:Rule val compose = createComposeRule()

    private val checking = Account(name = "Checking", balance = 50, type = "checking", id = "checking")
    private val savings = Account(name = "Savings", balance = 0, type = "savings", id = "savings")

    @Test fun aBalanceMustBeTransferredBeforeClosing() {
        var closed: Pair<String?, String?>? = null
        compose.setContent {
            MaterialTheme {
                CloseAccountSheet(
                    account = checking,
                    accounts = listOf(checking, savings),
                    hideDecimalPlaces = false,
                    loadOptions = { CloseAccountOptions(hasTransactions = true, categoryGroups = emptyList()) },
                    onDismiss = {},
                    onClose = { transfer, category -> closed = transfer to category },
                    onForceClose = {},
                )
            }
        }
        val closeButton = compose.onNode(hasText("Close account") and hasClickAction())

        closeButton.performClick()
        compose.onNodeWithText("Transfer is required").assertExists()
        assertNull(closed)

        compose.onNodeWithText("Transfer to").performClick()
        compose.onNodeWithText("Savings").performClick()
        closeButton.performClick()

        assertEquals("savings" to null, closed)
    }
}
