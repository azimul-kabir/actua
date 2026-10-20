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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.azimulkabir.actua.R
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
    // Resolved here so the non-composable click and activity-result callbacks never hold literals.
    val reportSaved = stringResource(R.string.diagnostics_report_saved)
    val reportSaveFailed = stringResource(R.string.diagnostics_report_save_failed)
    val reportCopied = stringResource(R.string.diagnostics_report_copied)
    val noEmailApp = stringResource(R.string.diagnostics_no_email_app)
    val diagnosticsCleared = stringResource(R.string.diagnostics_cleared)
    val clipboardLabel = stringResource(R.string.diagnostics_clipboard_label)
    val emailSubject = stringResource(R.string.diagnostics_email_subject)
    val emailChooserTitle = stringResource(R.string.diagnostics_email_chooser)

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = report()
        scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(text.toByteArray()) }
                }
            }.fold(onSuccess = { reportSaved }, onFailure = { reportSaveFailed })
        }
    }

    Scaffold(modifier = modifier, topBar = { ActuaScreenHeader(title = stringResource(R.string.settings_page_diagnostics), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.xl),
        ) {
            item {
                Text(
                    stringResource(R.string.diagnostics_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
                )
            }
            item {
                ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm)) {
                    ActuaFormRow(icon = Icons.Outlined.ContentCopy, label = stringResource(R.string.diagnostics_copy_report), value = null, onClick = {
                        copyToClipboard(context, clipboardLabel, report())
                        message = reportCopied
                    })
                    ActuaCardDivider()
                    ActuaFormRow(icon = Icons.Outlined.SaveAlt, label = stringResource(R.string.diagnostics_export_report), value = null, onClick = {
                        export.launch(DiagnosticsReport.fileName())
                    })
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Email, label = stringResource(R.string.diagnostics_email_report),
                        value = null, caption = DiagnosticsReport.SUPPORT_EMAIL,
                        onClick = {
                            message = if (emailReport(context, emailSubject, emailChooserTitle, report())) null
                                else noEmailApp
                        },
                    )
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.DeleteOutline, label = stringResource(R.string.diagnostics_clear), value = null,
                        enabled = events.isNotEmpty(),
                        onClick = {
                            DiagnosticsLog.clear()
                            events = DiagnosticsLog.events()
                            message = diagnosticsCleared
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
                    stringResource(R.string.diagnostics_recent_events, events.size),
                    Modifier.padding(horizontal = Spacing.screenHorizontal).padding(top = Spacing.md),
                )
            }
            if (events.isEmpty()) item {
                Text(stringResource(R.string.diagnostics_no_events),
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

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Opens the user's email app addressed to Actua with the report as the body. False when none is installed. */
private fun emailReport(context: Context, subject: String, chooserTitle: String, text: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(DiagnosticsReport.SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
        // Only email apps: they handle mailto:, and the selector keeps chat apps out of the chooser.
        selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
    }
    return try {
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
