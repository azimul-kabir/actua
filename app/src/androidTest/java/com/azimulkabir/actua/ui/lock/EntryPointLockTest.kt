package com.azimulkabir.actua.ui.lock

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.security.AppLockPreferences
import com.azimulkabir.actua.widget.ScheduleWidgetConfigurationActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An activity opened from a widget's setup shows the lock before any of its content (#1341). The system
 * prompt itself is not driven here; the lock screen it opens over is what is checked.
 */
@RunWith(AndroidJUnit4::class)
class EntryPointLockTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val lockPreferences = context.getSharedPreferences("app_lock_preferences", Context.MODE_PRIVATE)

    @Before fun start() { lockPreferences.edit().clear().commit() }
    @After fun finish() { lockPreferences.edit().clear().commit() }

    private fun launch(): ActivityScenario<ScheduleWidgetConfigurationActivity> {
        val intent = Intent(context, ScheduleWidgetConfigurationActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1341)
        return ActivityScenario.launch(intent)
    }

    @Test fun theWidgetSetupScreenShowsTheLockInsteadOfItsContentWhenLocked() {
        AppLockPreferences(context).enabled = true
        // Leaving the app with "Immediately" locks it again on the next start.
        AppLock.onBackground()
        launch().use {
            compose.onNodeWithText(context.getString(R.string.app_lock_title)).assertIsDisplayed()
            assertEquals(
                0,
                compose.onAllNodes(hasText(context.getString(R.string.widget_upcoming_schedules))).fetchSemanticsNodes().size,
            )
        }
    }

    @Test fun theWidgetSetupScreenOpensDirectlyWhenTheLockIsOff() {
        AppLock.onSettingChanged()
        launch().use {
            compose.onNodeWithText(context.getString(R.string.widget_upcoming_schedules)).assertIsDisplayed()
        }
    }
}
