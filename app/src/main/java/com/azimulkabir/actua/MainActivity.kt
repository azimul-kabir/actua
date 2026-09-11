package com.azimulkabir.actua

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.azimulkabir.actua.ui.navigation.AppNavigation
import com.azimulkabir.actua.ui.theme.ActuaTheme
import com.azimulkabir.actua.data.sync.ActualSyncScheduler
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.notifications.CreditCardDueNotificationScheduler
import com.azimulkabir.actua.widget.WidgetActions
import com.azimulkabir.actua.widget.WidgetUpdater

data class AppLaunchRequest(val action: String, val target: String?, val nonce: Long = System.nanoTime())

class MainActivity : ComponentActivity() {
    private var foregroundGeneration by mutableIntStateOf(0)
    private var launchRequest by mutableStateOf<AppLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActualSyncScheduler.schedulePeriodic(this)
        CreditCardDueNotificationScheduler.refresh(this)
        launchRequest = intent.toLaunchRequest()
        enableEdgeToEdge()
        setContent {
            var appearance by remember { mutableStateOf(DisplayPreferences(this).appearance) }
            ActuaTheme(appearance = appearance) {
                AppNavigation(
                    modifier = Modifier.fillMaxSize(),
                    foregroundGeneration = foregroundGeneration,
                    launchRequest = launchRequest,
                    onLaunchRequestConsumed = { launchRequest = null },
                    onAppearanceChange = { appearance = it },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        foregroundGeneration += 1
        WidgetUpdater.requestAll(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = intent.toLaunchRequest()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) ActualSyncScheduler.scheduleLocalBackup(this)
    }

    private fun Intent.toLaunchRequest(): AppLaunchRequest? = action?.takeIf {
        it.startsWith("com.azimulkabir.actua.widget.")
    }?.let { AppLaunchRequest(it, getStringExtra(WidgetActions.EXTRA_TARGET)) }
}
