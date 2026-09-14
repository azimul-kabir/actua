package com.azimulkabir.actua.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationSyncBannerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun syncBannerKeepsIndicatorVisibleAndSpacedFromText() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(280.dp).testTag("syncStatusBannerHost")) {
                    SyncStatusBanner()
                }
            }
        }

        compose.onNodeWithTag("syncStatusBannerIndicator").assertExists()
        compose.onNodeWithText("Syncing budget… Showing local data.").assertExists()

        val hostBounds = compose.onNodeWithTag("syncStatusBannerHost").fetchSemanticsNode().boundsInRoot
        val indicatorBounds = compose.onNodeWithTag("syncStatusBannerIndicator").fetchSemanticsNode().boundsInRoot
        val textBounds = compose.onNodeWithText("Syncing budget… Showing local data.").fetchSemanticsNode().boundsInRoot
        val minStartInset = with(compose.density) { 16.dp.toPx() }
        val minGap = with(compose.density) { 8.dp.toPx() }

        assertTrue(indicatorBounds.left - hostBounds.left >= minStartInset)
        assertTrue(textBounds.left - indicatorBounds.right >= minGap)
    }
}
