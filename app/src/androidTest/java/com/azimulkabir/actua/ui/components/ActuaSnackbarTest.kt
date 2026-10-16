package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.ui.theme.ActuaTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #1127: snackbars use the app's tonal surface, rounded corners and accent, not Material's near-black default. */
@RunWith(AndroidJUnit4::class)
class ActuaSnackbarTest {
    @get:Rule val compose = createComposeRule()

    private var result: SnackbarResult? = null
    private var expectedContainer = Color.Unspecified
    private var inverse = Color.Unspecified
    private var density = 1f

    private fun show(appearance: String, message: String, action: String? = null) {
        compose.setContent {
            ActuaTheme(appearance = appearance) {
                val host = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                density = androidx.compose.ui.platform.LocalDensity.current.density
                expectedContainer = MaterialTheme.colorScheme.surfaceContainerHighest
                inverse = MaterialTheme.colorScheme.inverseSurface
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.BottomCenter) {
                    ActuaSnackbarHost(host)
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    scope.launch {
                        result = host.showSnackbar(message, action, duration = SnackbarDuration.Indefinite)
                    }
                }
            }
        }
    }

    private fun containerPixel(): Int {
        val image = compose.onNodeWithTag(ActuaSnackbarTag).captureToImage()
        // Inside the container's left padding (past the 12dp outer padding, border and corner), mid-height, clear of the text.
        return image.toPixelMap()[(20 * density).toInt(), image.height / 2].toArgb()
    }

    @Test fun messageAndActionAreShown() {
        show("Light", "No network", "Retry")
        compose.onNodeWithText("No network").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(SnackbarResult.ActionPerformed, result)
    }

    @Test fun lightSnackbarUsesTheAppsTonalSurfaceNotTheInverseSurface() {
        show("Light", "Merged transactions")
        compose.onNodeWithText("Merged transactions").assertIsDisplayed()
        assertEquals(expectedContainer.toArgb(), containerPixel())
        assertNotEquals(inverse.toArgb(), containerPixel())
    }

    @Test fun darkSnackbarUsesTheAppsTonalSurfaceNotTheInverseSurface() {
        show("Dark", "Merged transactions")
        compose.onNodeWithText("Merged transactions").assertIsDisplayed()
        assertEquals(expectedContainer.toArgb(), containerPixel())
        assertNotEquals(inverse.toArgb(), containerPixel())
    }
}
