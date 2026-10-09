package com.azimulkabir.actua.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.azimulkabir.actua.data.diagnostics.DiagnosticsLog
import com.azimulkabir.actua.data.diagnostics.DiagnosticsReport
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recent app activity for bug reports (#225): crashes, failed actions, screens, syncs and server
 * requests. The report can be copied, saved as a text file or emailed to Actua; it never contains
 * server addresses, credentials or budget data.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf(DiagnosticsLog.events()) }
    var message by remember { mutableStateOf<String?>(null) }
    fun report(): String = DiagnosticsReport.build(DiagnosticsLog.events().also { events = it })

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = report()
        scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(text.toByteArray()) }
                }
            }.fold(onSuccess = { "Report saved." }, onFailure = { "Could not save the report." })
        }
    }

    Scaffold(modifier = modifier, topBar = { ActuaScreenHeader(title = "Diagnostics", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.xl),
        ) {
            item {
                Text(
                    "Recent app activity, kept on this device for 7 days: crashes, failed actions, screens " +
                        "opened, syncs and server requests. Share it with Actua when reporting a problem. It " +
                        "lists error types, code locations, request paths, status codes, timings and counts " +
                        "only, with no server address, passwords, tokens, keys or budget data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
                )
            }
            item {
                ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm)) {
                    ActuaFormRow(icon = Icons.Outlined.ContentCopy, label = "Copy report", value = null, onClick = {
                        copyToClipboard(context, report())
                        message = "Report copied."
                    })
                    ActuaCardDivider()
                    ActuaFormRow(icon = Icons.Outlined.SaveAlt, label = "Export report", value = null, onClick = {
                        export.launch(DiagnosticsReport.fileName())
                    })
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Email, label = "Email to Actua",
                        value = null, caption = DiagnosticsReport.SUPPORT_EMAIL,
                        onClick = {
                            message = if (emailReport(context, report())) null
                                else "No email app found. Copy or export the report instead."
                        },
                    )
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.DeleteOutline, label = "Clear diagnostics", value = null,
                        enabled = events.isNotEmpty(),
                        onClick = {
                            DiagnosticsLog.clear()
                            events = DiagnosticsLog.events()
                            message = "Diagnostics cleared."
                        },
                    )
                }
            }
            message?.let { text ->
                item {
                    Text(text, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs))
                }
            }
            item {
                ActuaGroupLabel(
                    "Recent events (${events.size})",
                    Modifier.padding(horizontal = Spacing.screenHorizontal).padding(top = Spacing.md),
                )
            }
            if (events.isEmpty()) item {
                Text("No events yet. They appear as you use the app.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm))
            }
            items(events.asReversed()) { event ->
                Text(
                    event.line(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Actua diagnostics", text))
}

/** Opens the user's email app addressed to Actua with the report as the body. False when none is installed. */
private fun emailReport(context: Context, text: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(DiagnosticsReport.SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "Actua diagnostics report")
        putExtra(Intent.EXTRA_TEXT, text)
        // Only email apps: they handle mailto:, and the selector keeps chat apps out of the chooser.
        selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
    }
    return try {
        context.startActivity(Intent.createChooser(send, "Email diagnostics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
