package com.azimulkabir.actua.tasker

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Tasker plugin form's account picker (#1042). */
@RunWith(AndroidJUnit4::class)
class TaskerQueueTransactionScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pickingAnAccountFillsTheAccountField() {
        var saved: Map<String, String>? = null
        compose.setContent {
            MaterialTheme {
                TaskerQueueTransactionScreen(
                    initial = mapOf("amount" to "%amount"),
                    callerLabel = "Tasker",
                    accountNames = listOf("Checking", "Amex Gold"),
                    integrationOn = { true },
                    onTurnOn = {},
                    onCancel = {},
                    onSave = { saved = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Choose account").performScrollTo().performClick()
        compose.onNodeWithText("Amex Gold").performClick()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(mapOf("amount" to "%amount", "account" to "Amex Gold"), saved)
        }
    }

    @Test fun withNoAccountsTheFieldIsPlainText() {
        compose.setContent {
            MaterialTheme {
                TaskerQueueTransactionScreen(
                    initial = emptyMap(), callerLabel = "Tasker", accountNames = emptyList(),
                    integrationOn = { true }, onTurnOn = {}, onCancel = {}, onSave = {},
                )
            }
        }
        compose.onNodeWithText("Account").performScrollTo()
        compose.onAllNodesWithContentDescription("Choose account").assertCountEquals(0)
    }
}
