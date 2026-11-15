package com.azimulkabir.actua.ui.navigation

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription

/** Repository opens run on IO, outside Compose's idling resources. */
internal fun AndroidComposeTestRule<*, *>.waitForNavigation() {
    waitUntil(15_000) {
        onAllNodesWithContentDescription("Manage", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}
