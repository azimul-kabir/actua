package com.azimulkabir.actua.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardCycle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditCardEditorTest {
    @get:Rule val compose = createComposeRule()

    private data class Saved(val accountId: String, val day: Int, val due: CreditCardCycle.PaymentDue, val limit: Long?)

    @Test fun addingACardSavesTheClosingDayAndDefaultDueOffset() {
        var saved: Saved? = null
        compose.setContent {
            MaterialTheme {
                CreditCardsScreen(
                    cards = emptyList(),
                    accounts = listOf(Account(name = "Visa", balance = 0, type = "credit", id = "visa")),
                    hideDecimalPlaces = false,
                    onBack = {},
                    onSave = { id, day, due, limit -> saved = Saved(id, day, due, limit) },
                    onRemove = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Add credit card").performClick()
        compose.onNodeWithText("Visa", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("20")
        compose.onNodeWithText("Save").performClick()

        assertEquals(
            Saved("visa", 20, CreditCardCycle.PaymentDue.DaysAfter(CreditCardCycle.DEFAULT_DUE_OFFSET_DAYS), null),
            saved,
        )
    }

    @Test fun dayOfMonthDueSavesAFixedDueDay() {
        var saved: Saved? = null
        compose.setContent {
            MaterialTheme {
                CreditCardsScreen(
                    cards = emptyList(),
                    accounts = listOf(Account(name = "Visa", balance = 0, type = "credit", id = "visa")),
                    hideDecimalPlaces = false,
                    onBack = {},
                    onSave = { id, day, due, limit -> saved = Saved(id, day, due, limit) },
                    onRemove = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Add credit card").performClick()
        compose.onNodeWithText("Day of month").performClick()
        compose.onNodeWithText("Save").performClick()

        assertEquals(CreditCardCycle.PaymentDue.DayOfMonth(1), saved?.due)
        assertEquals(15, saved?.day)
    }
}
