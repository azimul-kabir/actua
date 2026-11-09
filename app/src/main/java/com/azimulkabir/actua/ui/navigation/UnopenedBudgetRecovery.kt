package com.azimulkabir.actua.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.BackupItem
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetMetadata
import com.azimulkabir.actua.data.budget.UnopenedBudgetRecovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Shown while a local budget couldn't be opened; it stays on disk until the user decides (#1509). */
@Composable
internal fun UnopenedBudgetBanner(budgetName: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        Text(
            stringResource(R.string.navigation_unopened_budget_banner, budgetName),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .testTag("unopenedBudgetBanner"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private enum class RecoveryStep { Options, Redownload, Restore, Delete }

/**
 * Options for a budget that couldn't be opened: try again, re-download, restore from a local
 * backup, or delete. Each change is confirmed first. [onReopen] reopens the app's budget, selecting
 * [budget] when [selectBudget] is true; [onMessage] reports the outcome.
 */
@Composable
internal fun UnopenedBudgetRecoveryDialog(
    budget: BudgetMetadata,
    onDismiss: () -> Unit,
    onReopen: (selectBudget: Boolean) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val recovery = remember { UnopenedBudgetRecovery(context) }
    val name = budget.budgetName ?: budget.id

    var step by remember { mutableStateOf(RecoveryStep.Options) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var unsynced by remember { mutableStateOf<Int?>(null) }
    var backups by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
    var canRedownload by remember { mutableStateOf(false) }
    var selectedBackup by remember { mutableStateOf<BackupItem?>(null) }

    LaunchedEffect(budget.id) {
        withContext(Dispatchers.IO) {
            unsynced = recovery.unsyncedChanges(budget.id)
            backups = recovery.backups(budget.id)
            canRedownload = recovery.canRedownload(budget)
        }
        loaded = true
    }

    fun run(action: () -> String, failure: String, selectBudget: Boolean) {
        busy = true
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) { action() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                null
            }
            busy = false
            onMessage(result ?: failure)
            if (result != null) onReopen(selectBudget)
            onDismiss()
        }
    }

    val unsyncedCount = unsynced
    val unsyncedText: String? = when {
        budget.isUserLocalOnly -> stringResource(R.string.navigation_unopened_budget_local_only)
        unsyncedCount == null -> stringResource(R.string.navigation_unopened_budget_unsynced_unknown)
        unsyncedCount > 0 -> pluralStringResource(R.plurals.navigation_unopened_budget_unsynced, unsyncedCount, unsyncedCount)
        else -> null
    }
    val dismiss = { if (!busy) onDismiss() }

    when (step) {
        RecoveryStep.Options -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.navigation_unopened_budget_title, name)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.navigation_unopened_budget_message))
                    if (loaded) unsyncedText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!loaded || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    val enabled = loaded && !busy
                    OutlinedButton(
                        enabled = enabled,
                        onClick = { onReopen(true); onDismiss() },
                        modifier = Modifier.fillMaxWidth().testTag("unopenedBudgetTryAgain"),
                    ) { Text(stringResource(R.string.navigation_unopened_budget_try_again)) }
                    if (canRedownload) OutlinedButton(
                        enabled = enabled,
                        onClick = { step = RecoveryStep.Redownload },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.navigation_unopened_budget_redownload)) }
                    OutlinedButton(
                        enabled = enabled,
                        onClick = { step = RecoveryStep.Restore },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.navigation_unopened_budget_restore)) }
                    OutlinedButton(
                        enabled = enabled,
                        onClick = { step = RecoveryStep.Delete },
                        modifier = Modifier.fillMaxWidth().testTag("unopenedBudgetDelete"),
                    ) {
                        Text(
                            stringResource(R.string.navigation_unopened_budget_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = onDismiss) {
                    Text(stringResource(R.string.navigation_unopened_budget_not_now))
                }
            },
        )

        RecoveryStep.Redownload -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.navigation_unopened_budget_redownload_title, name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.navigation_unopened_budget_redownload_message))
                    unsyncedText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    run(
                        action = {
                            val result = recovery.redownload(budget.id)
                            resources.getString(
                                R.string.navigation_unopened_budget_redownload_done,
                                result.metadata.budgetName ?: name,
                            )
                        },
                        failure = resources.getString(R.string.navigation_unopened_budget_redownload_failed),
                        selectBudget = true,
                    )
                }) { Text(stringResource(R.string.navigation_unopened_budget_redownload_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { step = RecoveryStep.Options }) {
                    Text(stringResource(R.string.navigation_unopened_budget_cancel))
                }
            },
        )

        RecoveryStep.Restore -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.navigation_unopened_budget_restore_title, name)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (backups.isEmpty()) Text(stringResource(R.string.navigation_unopened_budget_restore_none))
                    backups.forEach { backup ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = backup == selectedBackup,
                                    enabled = !busy,
                                    role = Role.RadioButton,
                                    onClick = { selectedBackup = backup },
                                ),
                        ) {
                            RadioButton(selected = backup == selectedBackup, onClick = null)
                            Text(
                                when (backup) {
                                    BackupItem.Latest -> stringResource(R.string.navigation_unopened_budget_restore_previous)
                                    is BackupItem.Archive -> DateFormat.getDateTimeInstance().format(Date.from(backup.modifiedAt))
                                },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && selectedBackup != null, onClick = {
                    val backup = selectedBackup ?: return@TextButton
                    run(
                        action = {
                            recovery.restore(
                                budget.id,
                                when (backup) {
                                    BackupItem.Latest -> BackupService.LATEST_ID
                                    is BackupItem.Archive -> backup.id
                                },
                            )
                            resources.getString(R.string.navigation_unopened_budget_restore_done, name)
                        },
                        failure = resources.getString(R.string.navigation_unopened_budget_restore_failed),
                        selectBudget = true,
                    )
                }) { Text(stringResource(R.string.navigation_unopened_budget_restore_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { step = RecoveryStep.Options }) {
                    Text(stringResource(R.string.navigation_unopened_budget_cancel))
                }
            },
        )

        RecoveryStep.Delete -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.navigation_unopened_budget_delete_title, name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!budget.isUserLocalOnly) unsyncedText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(stringResource(R.string.navigation_unopened_budget_delete_message))
                    if (budget.isServerBacked) Text(stringResource(R.string.navigation_unopened_budget_delete_server_note))
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    modifier = Modifier.testTag("unopenedBudgetDeleteConfirm"),
                    onClick = {
                        run(
                            action = {
                                recovery.delete(budget.id)
                                resources.getString(R.string.navigation_unopened_budget_delete_done, name)
                            },
                            failure = resources.getString(R.string.navigation_unopened_budget_delete_failed),
                            selectBudget = false,
                        )
                    },
                ) {
                    Text(
                        stringResource(R.string.navigation_unopened_budget_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { step = RecoveryStep.Options }) {
                    Text(stringResource(R.string.navigation_unopened_budget_cancel))
                }
            },
        )
    }
}
