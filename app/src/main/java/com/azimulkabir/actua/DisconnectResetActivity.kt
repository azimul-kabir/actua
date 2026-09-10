package com.azimulkabir.actua

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.security.DisconnectResetManager
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import com.azimulkabir.actua.data.sync.SyncRunResult
import com.azimulkabir.actua.ui.theme.ActuaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Confirmation surface for a deliberate server disconnect.
 *
 * It lives outside ConnectionScreen so the existing repository can be torn down by
 * recreating the application task after the reset. A final sync protects normal
 * Actual data plus Actua-specific CRDT preferences such as credit-card metadata.
 */
class DisconnectResetActivity : ComponentActivity() {
    private enum class Stage { Confirm, Syncing, SyncFailed }

    private var stage by mutableStateOf(Stage.Confirm)
    private var syncFailure by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ActuaTheme(appearance = DisplayPreferences(this).appearance) {
                when (stage) {
                    Stage.Confirm -> AlertDialog(
                        onDismissRequest = ::cancelAndRestore,
                        title = { Text("Disconnect & reset?") },
                        text = {
                            Text(
                                "Actua will sync pending changes, then remove downloaded server budgets, local backups, encryption keys, and connection data from this device. Your budgets on the Actual server will not be deleted. Device display settings and the local demo budget will be kept."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = ::syncThenReset) { Text("Disconnect & reset") }
                        },
                        dismissButton = {
                            TextButton(onClick = ::cancelAndRestore) { Text("Cancel") }
                        },
                    )

                    Stage.Syncing -> AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Syncing before disconnect") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator()
                                Text("Saving your latest Actual and Actua budget changes to the server before local data is removed.")
                            }
                        },
                        confirmButton = {},
                    )

                    Stage.SyncFailed -> AlertDialog(
                        onDismissRequest = ::cancelAndRestore,
                        title = { Text("Couldn’t sync latest changes") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Actua did not remove any local budget data. Disconnecting anyway may permanently discard changes that have not reached your Actual server.")
                                syncFailure?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = ::forceReset) {
                                Text("Disconnect anyway", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = ::syncThenReset) { Text("Try again") }
                        },
                    )
                }
            }
        }
    }

    private fun syncThenReset() {
        if (stage == Stage.Syncing) return
        stage = Stage.Syncing
        syncFailure = null
        lifecycleScope.launch {
            val activeId = ActiveBudgetStore(this@DisconnectResetActivity).budgetId
            val needsServerSync = activeId != null && !DemoBudgetManager.isDemoBudget(activeId)

            val result = if (!needsServerSync) {
                Result.success(Unit)
            } else {
                runCatching {
                    when (val sync = withContext(Dispatchers.IO) {
                        ActualSyncRunner.run(this@DisconnectResetActivity)
                    }) {
                        is SyncRunResult.Success -> Unit
                        SyncRunResult.NotConfigured -> error("The active budget is not configured for server sync.")
                        SyncRunResult.EncryptionKeyUnavailable -> error("The active encrypted budget is locked. Unlock it before disconnecting, or disconnect anyway.")
                    }
                }
            }

            result.onSuccess { resetAndRestart() }
                .onFailure { error ->
                    syncFailure = error.message ?: "Sync failed."
                    stage = Stage.SyncFailed
                }
        }
    }

    private fun forceReset() {
        resetAndRestart()
    }

    private fun resetAndRestart() {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    DisconnectResetManager(this@DisconnectResetActivity).resetLocalServerData()
                }
            }.onSuccess {
                DisconnectResetManager(this@DisconnectResetActivity).restartIntoFreshConnectionState()
                finish()
            }.onFailure { error ->
                syncFailure = error.message ?: "Could not reset local Actua data."
                stage = Stage.SyncFailed
            }
        }
    }

    private fun cancelAndRestore() {
        DisconnectResetManager(this).restartIntoFreshConnectionState()
        finish()
    }
}
