package com.azimulkabir.actua.billing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.ui.settings.SupporterExtras
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class LockedSupporterExtrasTest {
    @get:Rule val compose = createComposeRule()
    @Test fun showsEveryExtraLockedWithoutGrantingAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var openedSupport = 0
        var openedIcon = 0
        var openedWidget = 0
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SupporterExtras(false, { openedSupport++ }, { openedIcon++ }, false, {}, { openedWidget++ })
                }
            }
        }
        for (label in listOf(R.string.settings_greeting, R.string.settings_greeting_nickname,
            R.string.settings_celebrations, R.string.settings_app_icon, R.string.settings_widget_style)) {
            compose.onNodeWithText(context.getString(label)).assertExists()
        }
        compose.onNodeWithText(context.getString(R.string.settings_support_actua)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, openedSupport) }
        compose.onNodeWithText(context.getString(R.string.settings_greeting)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, openedSupport) }
        // The icon and widget rows open their pickers as previews instead of going straight to Support.
        compose.onNodeWithText(context.getString(R.string.settings_app_icon)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.settings_widget_style)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, openedIcon)
            assertEquals(1, openedWidget)
            assertEquals(2, openedSupport)
        }
    }
}
