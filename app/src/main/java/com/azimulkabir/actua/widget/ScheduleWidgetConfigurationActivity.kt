package com.azimulkabir.actua.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.azimulkabir.actua.data.schedules.ScheduleWidgetPeriod
import com.azimulkabir.actua.ui.theme.ActuaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduleWidgetConfigurationActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        enableEdgeToEdge()
        setContent {
            ActuaTheme {
                ScheduleWidgetConfigurationScreen(widgetId = widgetId, onCancel = ::finish, onSave = ::save)
            }
        }
    }

    private fun save(days: Int) {
        ScheduleWidgetPreferences(this).save(widgetId, days)
        lifecycleScope.launch(Dispatchers.IO) {
            val manager = AppWidgetManager.getInstance(this@ScheduleWidgetConfigurationActivity)
            WidgetUpdater.update(
                this@ScheduleWidgetConfigurationActivity, manager, widgetId, ScheduledTransactionsWidgetProvider(),
            )
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleWidgetConfigurationScreen(widgetId: Int, onCancel: () -> Unit, onSave: (Int) -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(ScheduleWidgetPreferences(context).periodDays(widgetId)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upcoming schedules") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, contentDescription = "Cancel") }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { onSave(selected) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Add widget") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Show scheduled transactions due within:", style = MaterialTheme.typography.bodyMedium)
            ScheduleWidgetPeriod.entries.forEach { period ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { selected = period.days }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == period.days, onClick = { selected = period.days })
                    Text("${period.days} days", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
