package com.azimulkabir.actua

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

class MainActivity : ComponentActivity() {
    private var foregroundGeneration by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActualSyncScheduler.schedulePeriodic(this)
        enableEdgeToEdge()
        setContent {
            var appearance by remember { mutableStateOf(DisplayPreferences(this).appearance) }
            ActuaTheme(appearance = appearance) {
                AppNavigation(
                    modifier = Modifier.fillMaxSize(),
                    foregroundGeneration = foregroundGeneration,
                    onAppearanceChange = { appearance = it },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        foregroundGeneration += 1
    }
}
