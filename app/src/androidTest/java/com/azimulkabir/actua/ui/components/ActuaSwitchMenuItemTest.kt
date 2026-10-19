package com.azimulkabir.actua.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActuaSwitchMenuItemTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exposesSwitchRoleAndStateAndReportsTheToggledValue() {
        var checked by mutableStateOf(false)
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            MaterialTheme {
                ActuaSwitchMenuItem("Show overview", checked, { changes += it; checked = it })
            }
        }

        val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        compose.onNodeWithText("Show overview")
            .assert(isSwitch)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()

        assertEquals(listOf(true), changes)
        compose.onNodeWithText("Show overview")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }
}
