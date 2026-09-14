package com.azimulkabir.actua.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

        val contentBounds = compose.onNodeWithTag("syncStatusBannerContent").fetchSemanticsNode().boundsInRoot
        val indicatorBounds = compose.onNodeWithTag("syncStatusBannerIndicator").fetchSemanticsNode().boundsInRoot
        val textBounds = compose.onNodeWithText("Syncing budget… Showing local data.").fetchSemanticsNode().boundsInRoot
        val expectedStartInset = with(compose.density) { 20.dp.toPx() }
        val expectedGap = with(compose.density) { 10.dp.toPx() }
        val actualStartInset = indicatorBounds.left - contentBounds.left
        val actualGap = textBounds.left - indicatorBounds.right

        assertTrue(abs(actualStartInset - expectedStartInset) <= 2f)
        assertTrue(abs(actualGap - expectedGap) <= 2f)
        assertTrue(textBounds.left >= contentBounds.left)
        assertTrue(textBounds.right <= contentBounds.right)
    }

    @Test
    fun syncBannerKeepsIndicatorAndTextPresentOnNarrowWidth() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(180.dp).testTag("syncStatusBannerNarrowHost")) {
                    SyncStatusBanner()
                }
            }
        }

        compose.onNodeWithTag("syncStatusBannerIndicator").assertExists()
        compose.onNodeWithText("Syncing budget… Showing local data.").assertExists()

        val hostBounds = compose.onNodeWithTag("syncStatusBannerNarrowHost").fetchSemanticsNode().boundsInRoot
        val contentBounds = compose.onNodeWithTag("syncStatusBannerContent").fetchSemanticsNode().boundsInRoot
        val indicatorBounds = compose.onNodeWithTag("syncStatusBannerIndicator").fetchSemanticsNode().boundsInRoot
        val textBounds = compose.onNodeWithText("Syncing budget… Showing local data.").fetchSemanticsNode().boundsInRoot

        assertTrue(contentBounds.left >= hostBounds.left)
        assertTrue(contentBounds.right <= hostBounds.right)
        assertTrue(textBounds.left >= indicatorBounds.right)
        assertTrue(textBounds.right <= contentBounds.right)
    }
}
