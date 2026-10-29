package com.azimulkabir.actua

import com.azimulkabir.actua.data.preferences.ScreenPrivacyPreferences
import com.azimulkabir.actua.data.importing.SharedStatementIntent
import com.azimulkabir.actua.ui.lock.AppLock
import com.azimulkabir.actua.data.security.AppLockPreferences
import com.azimulkabir.actua.ui.lock.AppLockGate
import com.azimulkabir.actua.data.diagnostics.DiagnosticsLog
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.azimulkabir.actua.ui.navigation.AppNavigation
import com.azimulkabir.actua.ui.theme.ActuaTheme
import com.azimulkabir.actua.ui.theme.CategoryStatusColorState
import com.azimulkabir.actua.ui.theme.LocalCategoryStatusColors
import com.azimulkabir.actua.data.sync.ActualSyncScheduler
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.notifications.CapturedTransactionNotifications
import com.azimulkabir.actua.data.notifications.CreditCardDueLaunch
import com.azimulkabir.actua.data.notifications.CreditCardDueNotificationScheduler
import com.azimulkabir.actua.data.notifications.ScheduleReminderLaunch
import com.azimulkabir.actua.widget.WidgetActions
import com.azimulkabir.actua.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class AppLaunchRequest(val action: String, val target: String?, val nonce: Long = System.nanoTime())
const val SHARED_IMPORT_ACTION = "com.azimulkabir.actua.IMPORT_SHARED_TEXT"
const val SHARED_IMPORT_FILE_ACTION = "com.azimulkabir.actua.IMPORT_SHARED_FILE"

class MainActivity : AppCompatActivity() {
    private var foregroundGeneration by mutableIntStateOf(0)
    private var launchRequest by mutableStateOf<AppLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenPrivacyPreferences.apply(this)
        DiagnosticsLog.attach(this)
        if (savedInstanceState == null) DiagnosticsLog.appLaunched()
        // A recreated activity (rotation, theme change) still holds the original share intent; only
        // a fresh launch may start a file import, or the same statement would open again.
        launchRequest = intent.toLaunchRequest(allowSharedFile = savedInstanceState == null)
        // Decide before the first frame so a locked app never draws budget content, not even briefly.
        refreshAppLock()
        enableEdgeToEdge()
        setContent {
            var appearance by remember { mutableStateOf(DisplayPreferences(this).appearance) }
            var useDynamicColor by remember { mutableStateOf(DisplayPreferences(this).useDynamicColor) }
            val categoryStatusColorState = remember { CategoryStatusColorState(this) }
            ActuaTheme(appearance = appearance, dynamicColor = useDynamicColor) {
                AppLockGate(locked = AppLock.locked, onUnlocked = AppLock::unlock) {
                    CompositionLocalProvider(LocalCategoryStatusColors provides categoryStatusColorState) {
                        AppNavigation(
                            modifier = Modifier.fillMaxSize(),
                            foregroundGeneration = foregroundGeneration,
                            launchRequest = launchRequest,
                            onLaunchRequestConsumed = { launchRequest = null },
                            onAppearanceChange = { appearance = it },
                            onUseDynamicColorChange = { useDynamicColor = it },
                        )
                    }
                }
            }
        }
        // Both operations can initialise WorkManager, and refreshing card reminders opens the
        // selected SQLite budget. They maintain background work only, so never make the first
        // Compose frame wait for them. The app-open sync in AppNavigation refreshes reminders
        // again after it has incorporated server changes.
        lifecycleScope.launch(Dispatchers.IO) {
            ActualSyncScheduler.schedulePeriodic(applicationContext)
            CreditCardDueNotificationScheduler.refresh(applicationContext)
        }
    }

    override fun onStart() {
        super.onStart()
        refreshAppLock()
        foregroundGeneration += 1
        // Looking up widget instances crosses Binder. Keep it off the input/render thread just
        // like the per-widget database reads triggered by the resulting broadcast.
        lifecycleScope.launch(Dispatchers.IO) {
            WidgetUpdater.requestAll(applicationContext)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = intent.toLaunchRequest()
    }

    override fun onStop() {
        super.onStop()
        // A rotation or theme change stops the activity too, but the user hasn't left the app.
        if (!isChangingConfigurations) AppLock.onBackground()
        if (!isChangingConfigurations) ActualSyncScheduler.scheduleLocalBackup(this)
    }

    private fun refreshAppLock() {
        val preferences = AppLockPreferences(this)
        AppLock.onForeground(preferences.enabled, preferences.timeout)
    }

    private fun Intent.toLaunchRequest(allowSharedFile: Boolean = true): AppLaunchRequest? = action?.takeIf {
        it.startsWith("com.azimulkabir.actua.widget.")
    }?.let { AppLaunchRequest(it, getStringExtra(WidgetActions.EXTRA_TARGET)) }
        ?: takeIf { action == CreditCardDueLaunch.ACTION }
            ?.let { AppLaunchRequest(CreditCardDueLaunch.ACTION, getStringExtra(CreditCardDueLaunch.EXTRA_ACCOUNT_ID)) }
        ?: takeIf { action == ScheduleReminderLaunch.ACTION }
            ?.let { AppLaunchRequest(ScheduleReminderLaunch.ACTION, getStringExtra(ScheduleReminderLaunch.EXTRA_SCHEDULE_ID)) }
        ?: takeIf { action == CapturedTransactionNotifications.ACTION_REVIEW }
            ?.let { AppLaunchRequest(CapturedTransactionNotifications.ACTION_REVIEW, null) }
        ?: takeIf { action == Intent.ACTION_SEND && type?.startsWith("text/") == true }
            ?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf(String::isNotBlank)
            ?.let { AppLaunchRequest(SHARED_IMPORT_ACTION, it) }
        ?: takeIf { allowSharedFile }?.let(SharedStatementIntent::fileUri)
            ?.let { AppLaunchRequest(SHARED_IMPORT_FILE_ACTION, it.toString()) }
}
