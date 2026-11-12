package com.azimulkabir.actua.ui.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.widget.WidgetStyle
import com.azimulkabir.actua.widget.WidgetStyles
import org.junit.After
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
        compose.setContent { MaterialTheme { WidgetStyleSettings() } }
        val styles = 4 + if (WidgetStyles.accentTintedAvailable()) 1 else 0

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertCountEquals(1)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(styles)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertCountEquals(1)
    }

    @Test fun previewSampleTextIsDecorationLeftOutOfTheAccessibilityTree() {
        compose.setContent { MaterialTheme { WidgetStyleSettings() } }

        compose.onAllNodesWithText("\$1,240.00").assertCountEquals(0)
        compose.onAllNodesWithText("Budget").assertCountEquals(0)
    }
}
