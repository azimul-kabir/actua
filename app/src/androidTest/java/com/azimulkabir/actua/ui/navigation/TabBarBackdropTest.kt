package com.azimulkabir.actua.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Transparent form backgrounds must not leave sharp content visible through the blurred copy. */
@SdkSuppress(minSdkVersion = 31)
class TabBarBackdropTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun transparentContentIsBlurredOverAnOpaqueRecordedBackground() {
        var dark by mutableStateOf(true)
        compose.setContent {
            val backdrop = rememberTabBarBackdrop()
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Canvas(Modifier.size(300.dp).recordsBackdrop(backdrop, Color.White)) {
                    // Like text in Connection/Backups: dark marks with otherwise transparent pixels.
                    if (dark) {
                        val step = 8.dp.toPx()
                        var x = 0f
                        while (x < size.width) {
                            drawRect(Color.Black, Offset(x, 0f), Size(2.dp.toPx(), size.height))
                            x += step
                        }
                    }
                }
                Box(
                    Modifier.offset(20.dp, 120.dp).size(260.dp, 60.dp).graphicsLayer()
                        .blurredBackdrop(backdrop, Color.Transparent)
                        .testTag("blur"),
                )
            }
        }
        compose.waitForIdle()
        val blurred = compose.onNodeWithTag("blur").captureToImage().toPixelMap()
        val row = blurred.height / 2
        val darkest = (20 until blurred.width - 20).minOf { blurred[it, row].red }
        assertTrue("Sharp black stripes must be replaced by blurred grey: $darkest", darkest > 0.5f)
        assertTrue("The backdrop must contain the stripes, not just a flat fill: $darkest", darkest < 0.9f)

        compose.runOnIdle { dark = false }
        compose.waitForIdle()
        val cleared = compose.onNodeWithTag("blur").captureToImage().toPixelMap()
        assertTrue("Backdrop must update when content changes", cleared[cleared.width / 2, cleared.height / 2].red > 0.95f)
    }
}
