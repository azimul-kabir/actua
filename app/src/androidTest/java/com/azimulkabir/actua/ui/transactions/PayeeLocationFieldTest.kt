package com.azimulkabir.actua.ui.transactions

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.azimulkabir.actua.R
import org.junit.Rule
import org.junit.Test

class PayeeLocationFieldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** Labels are read from the resources the field renders with, so the suite tracks translations. */
    private val strings get() = compose.activity

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

        compose.onNodeWithText(strings.getString(R.string.location_nearby)).assertExists()
        compose.onNodeWithText(strings.getString(R.string.location_save)).assertDoesNotExist()
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
        compose.onNodeWithText(strings.getString(R.string.location_save)).assertExists()
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
        compose.onNodeWithText(strings.getString(R.string.location_save)).assertDoesNotExist()
    }
}
