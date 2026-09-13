package com.azimulkabir.actua.ui.transactions

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class PayeeLocationFieldTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun blankPayeeShowsCompactNearbyAction() {
        compose.setContent {
            MaterialTheme {
                PickerTextField(
                    label = "Payee",
                    value = "",
                    options = listOf("Cafe"),
                    onValueChange = {},
                    onFindNearby = { NearbyPayeeSearchResult() },
                )
            }
        }

        compose.onNodeWithText("Nearby").assertExists()
        compose.onNodeWithText("Save location").assertDoesNotExist()
    }

    @Test
    fun existingOrdinaryPayeeShowsSaveAction() {
        compose.setContent {
            MaterialTheme {
                PickerTextField(
                    label = "Payee",
                    value = "Cafe",
                    options = listOf("Cafe", "Transfer: Cash"),
                    onValueChange = {},
                    onFindNearby = { NearbyPayeeSearchResult() },
                    onSavePayeeLocation = {
                        PayeeLocationSaveResult(true, "Location saved for Cafe.")
                    },
                )
            }
        }
        compose.onNodeWithText("Save location").assertExists()
    }

    @Test
    fun transferPayeeDoesNotShowSaveAction() {
        compose.setContent {
            MaterialTheme {
                PickerTextField(
                    label = "Payee",
                    value = "Transfer: Cash",
                    options = listOf("Cafe", "Transfer: Cash"),
                    onValueChange = {},
                    onFindNearby = { NearbyPayeeSearchResult() },
                    onSavePayeeLocation = {
                        PayeeLocationSaveResult(true, "Unexpected")
                    },
                )
            }
        }
        compose.onNodeWithText("Save location").assertDoesNotExist()
    }
}
