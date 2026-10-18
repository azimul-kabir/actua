package com.azimulkabir.actua.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NoBudgetScreen(
    onConnect: () -> Unit,
    onBudgetInstalled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newBudgetName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            errorMessage = null
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BudgetFileManager(context).importLocalBudget(stream)
                        } ?: error("Could not open selected archive")
                    }
                }.onSuccess { imported ->
                    ActiveBudgetStore(context).budgetId = imported.id
                    onBudgetInstalled()
                }.onFailure { error ->
                    errorMessage = error.message ?: "Could not import budget archive."
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create local budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This budget stays on this device and works offline without a server.")
                    OutlinedTextField(
                        value = newBudgetName,
                        onValueChange = { newBudgetName = it },
                        label = { Text("Budget name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newBudgetName.isNotBlank(),
                    onClick = {
                        val name = newBudgetName.trim()
                        val files = BudgetFileManager(context)
                        val existing = files.listLocalBudgets().mapNotNull { it.budgetName?.lowercase() }
                        if (name.lowercase() in existing) {
                            errorMessage = "“$name” already exists."
                            return@TextButton
                        }
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    files.createBudget(name)
                                }
                            }.onSuccess { created ->
                                showCreateDialog = false
                                newBudgetName = ""
                                ActiveBudgetStore(context).budgetId = created.id
                                onBudgetInstalled()
                            }.onFailure { error ->
                                errorMessage = error.message ?: "Could not create local budget."
                            }
                        }
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No budget open", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Connect to your Actual server, or create a standalone budget on this device.",
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) { Text("Connect to Actual") }
        FilledTonalButton(
            onClick = {
                errorMessage = null
                newBudgetName = ""
                showCreateDialog = true
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) { Text("Create local budget") }
        OutlinedButton(
            onClick = {
                errorMessage = null
                importLauncher.launch(
                    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import budget archive (.zip)") }
        errorMessage?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
