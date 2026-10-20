package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** relativeTime became @Composable in the pt-BR strings phase, so its labels are verified on-device. */
@RunWith(AndroidJUnit4::class)
class RelativeTimeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun formatsSyncAndBackupAgeCompactly() {
        val now = 1_000_000L
        compose.setContent {
            MaterialTheme {
                SyncAgeLabels(
                    justNow = relativeTime(now - 5_000, now),
                    minutes = relativeTime(now - 120_000, now),
                    hours = relativeTime(now - 7_200_000, now),
                )
            }
        }
        compose.onNodeWithText("just now").assertExists()
        compose.onNodeWithText("2 min ago").assertExists()
        compose.onNodeWithText("2 hr ago").assertExists()
    }
}

@Composable
private fun SyncAgeLabels(justNow: String, minutes: String, hours: String) {
    Column {
        Text(justNow)
        Text(minutes)
        Text(hours)
    }
}
