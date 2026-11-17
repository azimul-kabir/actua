package com.azimulkabir.actua.billing

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class SandboxActivationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun requiresFullCredentialBeforeVerification() {
        compose.setContent { MaterialTheme { WebsiteActivation() } }
        compose.onNodeWithText("Activate sandbox extras").assertIsNotEnabled()
        compose.onNodeWithText("Purchase recovery code").performTextInput("synthetic")
        compose.onNodeWithText("Activate sandbox extras").assertIsNotEnabled()
    }
}
