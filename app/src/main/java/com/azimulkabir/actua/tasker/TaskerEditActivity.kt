package com.azimulkabir.actua.tasker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.importing.AutomationIntent
import com.azimulkabir.actua.data.importing.importHintAccountNames
import com.azimulkabir.actua.data.importing.NotificationImportPreferences
import com.azimulkabir.actua.data.importing.TaskerPlugin
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaMenuRow
import com.azimulkabir.actua.ui.components.ActuaPrimaryActionBar
import com.azimulkabir.actua.ui.components.ActuaSecondaryButton
import com.azimulkabir.actua.ui.theme.ActuaTheme
import com.azimulkabir.actua.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tasker's edit screen for the "Queue transaction" plugin action (#1040). Saving returns the
 * fields and the current automation token to the app that opened it, which is named on screen.
 */
class TaskerEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a host that asked for a result (Tasker) can receive the bundle and its token.
        val caller = callingPackage ?: run { finish(); return }
        setResult(Activity.RESULT_CANCELED)
        val stored = intent.getBundleExtra(TaskerPlugin.EXTRA_BUNDLE)
        val initial = TaskerPlugin.fieldsOf(stored?.keySet().orEmpty().associateWith { stored?.getString(it) })
        val synchronous = (intent.getIntExtra(TaskerPlugin.EXTRA_HOST_CAPABILITIES, 0) and
            TaskerPlugin.CAPABILITY_SYNCHRONOUS) != 0
        val callerLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(caller, 0)).toString()
        }.getOrDefault(caller)
        val preferences = NotificationImportPreferences(this)
        var accountNames by mutableStateOf(emptyList<String>())
        lifecycleScope.launch {
            accountNames = withContext(Dispatchers.IO) {
                runCatching {
                    val repository = ActuaRepository(this@TaskerEditActivity)
                    try { importHintAccountNames(repository.accounts()) } finally { repository.close() }
                }.getOrDefault(emptyList())
            }
        }
        enableEdgeToEdge()
        setContent {
            ActuaTheme {
                TaskerQueueTransactionScreen(
                    initial = initial,
                    callerLabel = callerLabel,
                    accountNames = accountNames,
                    integrationOn = { preferences.automationEnabled && preferences.automationToken != null },
                    onTurnOn = { preferences.automationEnabled = true },
                    onCancel = ::finish,
                    onSave = { fields ->
                        val token = preferences.automationToken ?: return@TaskerQueueTransactionScreen
                        val values = TaskerPlugin.bundleValues(fields, token)
                        val bundle = Bundle().apply {
                            values.forEach { (key, value) -> putString(key, value) }
                            putString(TaskerPlugin.KEY_VARIABLE_REPLACE, TaskerPlugin.replaceKeys(values))
                        }
                        val result = Intent()
                            .putExtra(TaskerPlugin.EXTRA_BUNDLE, bundle)
                            .putExtra(TaskerPlugin.EXTRA_BLURB, TaskerPlugin.blurb(fields))
                        if (synchronous) result.putExtra(TaskerPlugin.EXTRA_REQUESTED_TIMEOUT, TaskerPlugin.REQUESTED_TIMEOUT_MS)
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    },
                )
            }
        }
    }
}

private val typeChoices = listOf(
    "From the amount's sign" to "",
    "Debit (money out)" to "debit",
    "Credit (money in)" to "credit",
)

private data class FieldSpec(val extra: String, val label: String, val hint: String? = null)

private val textFields = listOf(
    FieldSpec(AutomationIntent.EXTRA_AMOUNT, "Amount", "e.g. %amount. For card charges, set Type to Debit, or use -%amount so a negative refund becomes money in. Currency symbols are ignored."),
    FieldSpec(AutomationIntent.EXTRA_PAYEE, "Payee"),
    FieldSpec(AutomationIntent.EXTRA_DATE, "Date", "YYYY-MM-DD. Empty means today."),
    FieldSpec(AutomationIntent.EXTRA_NOTES, "Notes"),
    FieldSpec(AutomationIntent.EXTRA_REFERENCE, "Reference"),
    FieldSpec(AutomationIntent.EXTRA_ACCOUNT, "Account", "Choose an account, or type its exact name, digits from it, or a variable."),
    FieldSpec(AutomationIntent.EXTRA_SOURCE, "Source", "Shown during review. Empty means Tasker."),
    FieldSpec(AutomationIntent.EXTRA_TEXT, "Alert text", "Instead of an amount: the raw alert, read by Actua's own parser."),
)

/** Lists the open accounts whose names select themselves on review (#1042); typing still works. */
@Composable
private fun AccountPicker(names: List<String>, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Choose account")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            names.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onPick(name) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskerQueueTransactionScreen(
    initial: Map<String, String>,
    callerLabel: String,
    accountNames: List<String>,
    integrationOn: () -> Boolean,
    onTurnOn: () -> Unit,
    onCancel: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    val fields = remember { mutableStateMapOf<String, String>().apply { putAll(initial) } }
    var enabled by remember { mutableStateOf(integrationOn()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue transaction in Actua") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, contentDescription = "Cancel") }
                },
            )
        },
        bottomBar = {
            ActuaPrimaryActionBar(
                text = "Save",
                onClick = { onSave(fields.toMap()) },
                enabled = enabled && TaskerPlugin.canSave(fields),
                modifier = Modifier.imePadding(),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal),
        ) {
            Text(
                "Adds a transaction to Actua's review queue. Nothing reaches your budget until you import it. " +
                    "Every field accepts Tasker variables such as %amount.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
            if (!enabled) {
                Text(
                    "Accept transactions from Tasker is off, so there is no token to save.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                ActuaSecondaryButton(
                    text = "Turn on",
                    onClick = { onTurnOn(); enabled = integrationOn() },
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
            }
            ActuaFormCard(Modifier.padding(vertical = Spacing.sm)) {
                ActuaMenuRow(
                    icon = Icons.Outlined.SwapVert,
                    label = "Type",
                    value = typeChoices.firstOrNull { it.second == fields[AutomationIntent.EXTRA_TYPE].orEmpty() }?.first
                        ?: fields[AutomationIntent.EXTRA_TYPE].orEmpty(),
                    choices = typeChoices,
                ) { fields[AutomationIntent.EXTRA_TYPE] = it }
            }
            textFields.forEach { spec ->
                OutlinedTextField(
                    value = fields[spec.extra].orEmpty(),
                    onValueChange = { fields[spec.extra] = it },
                    label = { Text(spec.label) },
                    supportingText = spec.hint?.let { { Text(it) } },
                    singleLine = spec.extra != AutomationIntent.EXTRA_TEXT && spec.extra != AutomationIntent.EXTRA_NOTES,
                    trailingIcon = if (spec.extra == AutomationIntent.EXTRA_ACCOUNT && accountNames.isNotEmpty()) {
                        { AccountPicker(accountNames) { fields[AutomationIntent.EXTRA_ACCOUNT] = it } }
                    } else null,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                )
            }
            Text(
                "Saving gives $callerLabel your Actua automation token so it can run this action. " +
                    "If you replace the token in Actua, open this action and save it again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.md),
            )
        }
    }
}
