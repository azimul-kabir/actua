package com.azimulkabir.actua.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportsEntryIsVisibleAndOpensReports() {
        val reportsOpened = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                HomeScreen(onReportsClick = { reportsOpened.value = true })
            }
        }

        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText("Reports").performClick()
        compose.runOnIdle { assertTrue(reportsOpened.value) }
    }
}
