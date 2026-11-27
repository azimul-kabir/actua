package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.importing.FinancialCapturePattern
import com.azimulkabir.actua.data.importing.FinancialMessageProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapturePatternSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun canSaveAndPreviewAPatternScopedToAnAllowedApp() {
        val apps = mapOf("example.bank" to "Example Bank")
        var patterns by mutableStateOf(emptyList<FinancialCapturePattern>())
        var source by mutableStateOf<String?>(null)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (patterns.isNotEmpty()) {
                        CapturePatternTest(
                            "purchase CNY 25.00 at Example Shop",
                            apps, FinancialMessageProfile(patterns = patterns), source, { source = it },
                        )
                    }
                    CapturePatternSettings(patterns, apps) { patterns = it }
                }
            }
        }

        compose.onNodeWithText("Add pattern").performScrollTo().performClick()
        compose.onNode(hasText("Pattern name") and hasSetTextAction()).performTextInput("Example rule")
        compose.onNode(hasText("Regular expression") and hasSetTextAction())
            .performTextInput("""CNY (?<amount>[\d.]+) at (?<payee>.+)""")
        compose.onNodeWithText("Source app: Any source").performScrollTo().performClick()
        compose.onNode(hasText("Example Bank") and hasAnyAncestor(isPopup()), useUnmergedTree = true).performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals("example.bank", patterns.single().sourcePackage) }

        compose.onNodeWithText("Test source: Any source").performScrollTo().performClick()
        compose.onNode(hasText("Example Bank") and hasAnyAncestor(isPopup()), useUnmergedTree = true).performClick()
        compose.onNodeWithText("Test capture patterns").performScrollTo().performClick()
        compose.onNodeWithText("Matched: Example rule").assertExists()
        compose.onNodeWithText("Amount: -25.00").assertExists()
        compose.onNodeWithText("Payee: Example Shop").assertExists()
    }
}
