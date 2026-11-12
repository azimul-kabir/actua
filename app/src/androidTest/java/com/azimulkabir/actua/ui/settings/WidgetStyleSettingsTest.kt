package com.azimulkabir.actua.ui.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.widget.WidgetStyle
import com.azimulkabir.actua.widget.WidgetStyles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetStyleSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val preferences = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
    private var saved = WidgetStyle.Default

    @Before fun startFromDefault() {
        saved = preferences.widgetStyle
        preferences.widgetStyle = WidgetStyle.Default
    }

    @After fun restore() {
        preferences.widgetStyle = saved
    }

    @Test fun tilesAreOneRadioGroupAnnouncedByStyleNameAndSelection() {
        compose.setContent { MaterialTheme { WidgetStyleSettings(isSupporter = true, onOpenSupport = {}) } }
        val styles = 4 + if (WidgetStyles.accentTintedAvailable()) 1 else 0

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertCountEquals(1)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(styles)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertCountEquals(1)
    }

    @Test fun previewSampleTextIsDecorationLeftOutOfTheAccessibilityTree() {
        compose.setContent { MaterialTheme { WidgetStyleSettings(isSupporter = true, onOpenSupport = {}) } }

        compose.onAllNodesWithText("\$1,240.00").assertCountEquals(0)
        compose.onAllNodesWithText("Budget").assertCountEquals(0)
    }

    @Test fun aNonSupporterSeesDefaultSelectedAndEveryOtherStyleOpensSupport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences.widgetStyle = WidgetStyle.SolidDark // left over from before support lapsed
        var supportOpened = 0
        compose.setContent { MaterialTheme { WidgetStyleSettings(isSupporter = false, onOpenSupport = { supportOpened++ }) } }
        val styles = 4 + if (WidgetStyles.accentTintedAvailable()) 1 else 0

        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertCountEquals(1)
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true) and hasText(context.getString(R.string.settings_widget_style_default)),
        ).assertExists()
        compose.onAllNodesWithText(context.getString(R.string.settings_supporter_extras_marker)).assertCountEquals(styles - 1)

        compose.onNodeWithText(context.getString(R.string.settings_widget_style_translucent)).performClick()
        compose.runOnIdle {
            assertEquals("a locked style opens the Support page", 1, supportOpened)
            assertEquals("and is not chosen", WidgetStyle.SolidDark, preferences.widgetStyle)
        }
    }
}
