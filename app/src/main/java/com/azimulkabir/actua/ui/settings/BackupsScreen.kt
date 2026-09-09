package com.azimulkabir.actua.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.BackupDestinationManager
import com.azimulkabir.actua.data.budget.BackupItem
import com.azimulkabir.actua.data.budget.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupsScreen(
    budgetId: String,
    onBack: () -> Unit,
    onBeforeRestore: () -> Unit,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val service = remember { BackupService(context) }
    val destinations = remember { BackupDestinationManager(context) }
    val scope = rememberCoroutineScope()
    var backups by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
    var destination by remember { mutableStateOf(destinations.read()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupItem?>(null) }
    var pendingExport by remember { mutableStateOf<BackupItem.Archive?>(null) }
    var confirmBackup by remember { mutableStateOf(false) }

    fun refresh() { backups = runCatching { service.availableBackups(budgetId) }.getOrDefault(emptyList()) }
    fun makeBackup() {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { service.makeBackup(budgetId) } }
                .onSuccess { message = "Backup created." }
                .onFailure { message = it.message ?: "Could not create backup." }
            destination = destinations.read(); busy = false; refresh()
        }
    }

    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                destinations.select(uri, null)
                service.mirrorExisting(budgetId)
            } }.onSuccess { message = "Existing backups mirrored." }
                .onFailure { message = it.message ?: "Could not use this folder." }
            destination = destinations.read(); busy = false
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val backup = pendingExport
        pendingExport = null
        if (uri != null && backup != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                    service.archiveFile(budgetId, backup.id).inputStream().use { it.copyTo(output) }
                }
            } }.onSuccess { message = "Backup exported." }
                .onFailure { message = it.message ?: "Could not export backup." }
            busy = false
        }
    }

    LaunchedEffect(budgetId) { refresh() }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestore = null },
            title = { Text(if (backup is BackupItem.Latest) "Revert budget?" else "Restore backup?") },
            text = { Text(if (backup is BackupItem.Latest) {
                "Replace this restored budget with the version that was active immediately before the restore?"
            } else {
                "Your current budget will be preserved for one-tap revert. Restoring disconnects this copy from server sync."
            }) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; onBeforeRestore()
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) {
                        service.restore(budgetId, when (backup) {
                            BackupItem.Latest -> BackupService.LATEST_ID
                            is BackupItem.Archive -> backup.id
                        })
                    } }.onSuccess { message = "Backup restored." }
                        .onFailure { message = it.message ?: "Could not restore backup." }
                    pendingRestore = null; busy = false; refresh(); onRestored()
                }
            }) { Text(if (backup is BackupItem.Latest) "Revert" else "Restore") } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
    if (confirmBackup) AlertDialog(
        onDismissRequest = { confirmBackup = false },
        title = { Text("Create a new backup?") },
        text = { Text("This replaces the one-tap pre-restore version. The restored budget will remain available as a normal backup.") },
        confirmButton = { TextButton(onClick = { confirmBackup = false; makeBackup() }) { Text("Back up") } },
        dismissButton = { TextButton(onClick = { confirmBackup = false }) { Text("Cancel") } },
    )

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Backups", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Destination", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Backup location", fontWeight = FontWeight.Medium)
                            Text(destination.name ?: "Private app storage", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (destination.lastMirroredMillis > 0) Text(
                                "Mirrored ${relativeTime(destination.lastMirroredMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(enabled = !busy, onClick = { chooseFolder.launch(null) }) { Text("Change") }
                    }
                    if (destination.uri != null) {
                        HorizontalDivider()
                        TextButton(enabled = !busy, onClick = {
                            destinations.reset(); destination = destinations.read(); message = "Using private app storage only."
                        }) { Text("Reset to default", color = MaterialTheme.colorScheme.error) }
                    }
                    destination.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            Text(
                "Backups stay in private app storage and are automatically mirrored under Actua/$budgetId/ when a folder is selected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                if (backups.any { it is BackupItem.Latest }) confirmBackup = true else makeBackup()
            }) {
                if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                Text("Back up now")
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text("Available backups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            backups.forEachIndexed { index, backup ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) { pendingRestore = backup }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(when (backup) {
                            BackupItem.Latest -> "Pre-restore version"
                            is BackupItem.Archive -> DateFormat.getDateTimeInstance().format(Date.from(backup.modifiedAt))
                        })
                        Text("Tap to ${if (backup is BackupItem.Latest) "revert" else "restore"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (backup is BackupItem.Archive) OutlinedButton(enabled = !busy, onClick = {
                        pendingExport = backup; export.launch(backup.id)
                    }) { Text("Export") }
                }
                if (index != backups.lastIndex) HorizontalDivider()
            }
            if (backups.isEmpty()) Text("No backups yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - timestamp).coerceAtLeast(0) / 1000)
    return when {
        seconds < 10 -> "just now"
        seconds < 60 -> "$seconds sec ago"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86400 -> "${seconds / 3600} hr ago"
        else -> "${seconds / 86400} days ago"
    }
}
