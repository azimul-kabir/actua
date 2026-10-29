package com.azimulkabir.actua.ui.lock

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.ui.theme.ActuaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The lock screen and gate (#1340). The system prompt itself is not driven here. */
@RunWith(AndroidJUnit4::class)
class AppLockScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun string(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test
    fun lockScreenShowsTitleAndUnlockButtonAndReportsClicks() {
        var unlocks = 0
        compose.setContent {
            ActuaTheme {
                AppLockScreen(message = null, onUnlock = { unlocks++ }, onOpenSecuritySettings = null)
            }
        }

        compose.onNodeWithText(string(R.string.app_lock_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.app_lock_unlock)).assertIsDisplayed().performClick()
        assertEquals(1, unlocks)
    }

    @Test
    fun lockScreenShowsAnErrorAndTheSecuritySettingsShortcutWhenOffered() {
        var opened = 0
        compose.setContent {
            ActuaTheme {
                AppLockScreen(message = "Too many attempts", onUnlock = {}, onOpenSecuritySettings = { opened++ })
            }
        }

        compose.onNodeWithText("Too many attempts").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.app_lock_open_security_settings)).assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theGateDoesNotComposeItsContentWhileLocked() {
        compose.setContent {
            ActuaTheme {
                AppLockGate(locked = true, onUnlocked = {}) { Text("secret balance") }
            }
        }

        compose.onNodeWithText(string(R.string.app_lock_title)).assertIsDisplayed()
        compose.onAllNodesWithTextCount("secret balance").let { assertEquals(0, it) }
    }

    @Test
    fun theGateShowsItsContentWhenUnlocked() {
        compose.setContent {
            ActuaTheme {
                AppLockGate(locked = false, onUnlocked = {}) { Text("secret balance") }
            }
        }

        compose.onNodeWithText("secret balance").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
