package com.azimulkabir.actua.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.PayeeLocationSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PayeeLocationsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val locations = listOf(
        PayeeLocationSummary("loc-1", "payee-1", "Corner Shop", 51.5, -0.12, 0L),
        PayeeLocationSummary("loc-2", "payee-1", "Corner Shop", 51.6, -0.13, 0L),
    )

    @Test fun deletingOneLocationConfirmsFirst() {
        var deleted: String? = null
        compose.setContent {
            MaterialTheme {
                PayeeLocationsScreen(locations, writesSupported = true, onBack = {},
                    onDelete = { deleted = it }, onClearPayee = {})
            }
        }

        compose.onNodeWithText("Corner Shop").assertExists()
        compose.onAllNodesWithContentDescription("Delete location").onFirst().performClick()
        compose.onNodeWithText("Delete").performClick()

        assertEquals("loc-1", deleted)
    }

    @Test fun clearAllClearsThePayee() {
        var cleared: String? = null
        compose.setContent {
            MaterialTheme {
                PayeeLocationsScreen(locations, writesSupported = true, onBack = {},
                    onDelete = {}, onClearPayee = { cleared = it })
            }
        }

        compose.onNodeWithText("Clear all").performClick()
        compose.onNodeWithText("Delete").performClick()

        assertEquals("payee-1", cleared)
    }
}
