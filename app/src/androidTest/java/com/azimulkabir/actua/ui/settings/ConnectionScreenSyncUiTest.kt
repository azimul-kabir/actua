package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionScreenSyncUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun manualSyncLoadingContentCentersSpinnerAndLabelAsOneGroup() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(240.dp).testTag("manualSyncButtonHost")) {
                    ManualSyncButtonContent(
                        syncing = true,
                        demoActive = false,
                    )
                }
            }
        }

        compose.onNodeWithTag("manualSyncButtonIndicator").assertExists()
        compose.onNodeWithText("Syncing…").assertExists()

        val rootBounds = compose.onNodeWithTag("manualSyncButtonHost").fetchSemanticsNode().boundsInRoot
        val contentBounds = compose.onNodeWithTag("manualSyncButtonContent").fetchSemanticsNode().boundsInRoot
        val rootCenter = (rootBounds.left + rootBounds.right) / 2f
        val contentCenter = (contentBounds.left + contentBounds.right) / 2f

        assertTrue(abs(rootCenter - contentCenter) <= 2f)
    }

    @Test
    fun manualSyncButtonShowsDemoLabelWithoutSpinner() {
        compose.setContent {
            MaterialTheme {
                ManualSyncButtonContent(
                    syncing = false,
                    demoActive = true,
                )
            }
        }

        compose.onNodeWithText("Demo is local only").assertExists()
        compose.onNodeWithTag("manualSyncButtonIndicator").assertDoesNotExist()
    }
}
