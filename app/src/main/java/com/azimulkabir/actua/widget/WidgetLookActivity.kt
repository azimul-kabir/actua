package com.azimulkabir.actua.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.billing.supporterPerksAvailable
import com.azimulkabir.actua.data.preferences.SupporterPerks
import com.azimulkabir.actua.ui.settings.WidgetLookSection

/** Whether this user can change a widget's look: a supporter on a build with supporter extras. */
internal fun widgetLookIsSupporter(context: Context): Boolean = supporterPerksAvailable() && SupporterPerks(context).isSupporter

/**
 * The setup screen of the widgets that have nothing else to set up (Budget snapshot, Transaction, Spending trend,
 * Income and spending): just their own look. It is optional and reconfigurable, so adding a widget never waits on it.
 * Before Android 12 a launcher opens it whenever such a widget is added, and a look needs Android 12, so it finishes
 * straight away there and the widget is added as before.
 */
class WidgetLookActivity : WidgetConfigurationBaseActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val done = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        if (!WidgetStyles.accentTintedAvailable()) {
            setResult(Activity.RESULT_OK, done)
            finish()
            return
        }
        // Closing without saving keeps the widget as it is, so it is never removed for it.
        setResult(Activity.RESULT_OK, done)
        enableEdgeToEdge()
        setContent {
            WidgetConfigurationTheme(this) {
                WidgetLookScreen(widgetId, onCancel = ::finish, onSave = ::save)
            }
        }
    }

    private fun save(look: WidgetLookOverride) {
        if (widgetLookIsSupporter(this)) WidgetLookPreferences(this).save(widgetId, look)
        WidgetUpdater.requestAll(this)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetLookScreen(widgetId: Int, onCancel: () -> Unit, onSave: (WidgetLookOverride) -> Unit) {
    val context = LocalContext.current
    var look by remember(widgetId) { mutableStateOf(WidgetLookPreferences(context).override(widgetId)) }
    val isSupporter = remember { widgetLookIsSupporter(context) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_look_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.widget_action_cancel))
                    }
                },
            )
        },
        bottomBar = {
            Button(onClick = { onSave(look) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.widget_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            WidgetLookSection(isSupporter, look, onLookChange = { look = it })
        }
    }
}
