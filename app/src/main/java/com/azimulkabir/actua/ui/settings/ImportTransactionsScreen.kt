package com.azimulkabir.actua.ui.settings

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.content.Intent
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.importing.AutomationIntent
import com.azimulkabir.actua.data.importing.CsvTransactionCandidateSource
import com.azimulkabir.actua.data.importing.ImportCandidate
import com.azimulkabir.actua.data.importing.ImportColumnMapping
import com.azimulkabir.actua.data.importing.ImportColumnRole
import com.azimulkabir.actua.data.importing.ImportDuplicateDetector
import com.azimulkabir.actua.data.importing.ImportMatch
import com.azimulkabir.actua.data.importing.ImportHistoryEntry
import com.azimulkabir.actua.data.importing.ImportPreferences
import com.azimulkabir.actua.data.importing.ImportProblem
import com.azimulkabir.actua.data.importing.ImportTable
import com.azimulkabir.actua.data.importing.ImportConfidence
import com.azimulkabir.actua.data.importing.FinancialMessageParser
import com.azimulkabir.actua.data.importing.NotificationImportPreferences
import com.azimulkabir.actua.data.importing.FinancialMessageProfile
import com.azimulkabir.actua.data.importing.StatementFormat
import com.azimulkabir.actua.data.importing.StatementDocumentReader
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaFormTextField
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.components.ActuaMenuRow
import com.azimulkabir.actua.ui.components.ActuaPrimaryActionBar
import com.azimulkabir.actua.ui.components.ActuaSecondaryButton
import com.azimulkabir.actua.ui.theme.Spacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

private data class ReviewRow(
    val sourceRow: Int,
    val date: String,
    val payee: String,
    val amount: String,
    val notes: String,
    val reference: String?,
    val selected: Boolean,
    val confidence: ImportConfidence = ImportConfidence.HIGH,
    val sourceLabel: String = "Statement",
    /** Add the row even though it matches an existing transaction (Actual's `forceAddTransaction`). */
    val addAsNew: Boolean = false,
)

@Composable
fun ImportTransactionsScreen(
    accounts: List<Account>,
    /** For an account and candidates, the existing transaction each would update (null: added). Blocking. */
    findMatches: (String, List<ImportCandidate>) -> List<ImportMatch?>,
    /**
     * Account id, reviewed candidates, whether to mark them cleared, the candidate indexes to add
     * even though they match, and a callback once imported.
     */
    onImport: (String, List<ImportCandidate>, Boolean, Set<Int>, onImported: () -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialSharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<ReviewRow>() }
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    var problems by remember { mutableStateOf<List<ImportProblem>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val importPreferences = remember { ImportPreferences(context) }
    var table by remember { mutableStateOf<ImportTable?>(null) }
    var mapping by remember { mutableStateOf<ImportColumnMapping?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var sourceFormat by remember { mutableStateOf(StatementFormat.CSV) }
    var profileName by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(importPreferences.history()) }
    // Actual's "Clear transactions on import", on by default (#1008).
    var markCleared by rememberSaveable { mutableStateOf(true) }
    var profileMenu by remember { mutableStateOf(false) }
    val notificationPreferences = remember { NotificationImportPreferences(context) }
    var captureEnabled by remember { mutableStateOf(notificationPreferences.enabled) }
    var queued by remember { mutableStateOf(notificationPreferences.queued()) }
    var automationEnabled by remember { mutableStateOf(notificationPreferences.automationEnabled) }
    var automationToken by remember { mutableStateOf(notificationPreferences.automationToken) }
    var pastedText by remember { mutableStateOf(initialSharedText.orEmpty()) }
    var debitKeywords by remember { mutableStateOf(notificationPreferences.profile().debitKeywords.joinToString(", ")) }
    var creditKeywords by remember { mutableStateOf(notificationPreferences.profile().creditKeywords.joinToString(", ")) }
    var allowedPackages by remember { mutableStateOf(notificationPreferences.allowedPackages) }
    var appMenu by remember { mutableStateOf(false) }
    val notificationApps = remember {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(launcher, 0).map { info ->
            info.activityInfo.packageName to info.loadLabel(context.packageManager).toString()
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    // Selected, valid rows with their candidates; matching follows Actual's file import (#1009).
    val readyRows = rows.withIndex().filter { it.value.selected }
        .mapNotNull { (index, row) -> row.toCandidateOrNull()?.let { index to it } }
    val matchesByRow by produceState(emptyMap<Int, ImportMatch>(), account?.id, readyRows) {
        val accountId = account?.id
        value = if (accountId == null || readyRows.isEmpty()) emptyMap() else runCatching {
            withContext(Dispatchers.IO) { findMatches(accountId, readyRows.map { it.second }) }
        }.getOrDefault(emptyList()).withIndex()
            .mapNotNull { (position, match) -> match?.let { readyRows[position].first to it } }.toMap()
    }

    fun reviewCandidates(candidates: List<ImportCandidate>, parseProblems: List<ImportProblem>) {
        candidates.mapNotNull(ImportCandidate::accountHint).distinct().singleOrNull()?.let { hint ->
            accounts.singleOrNull { hint in it.name.filter(Char::isDigit) }?.let { account = it }
        }
        rows.clear()
        // Rows the account already holds are matched and updated instead (#1009); only repeats
        // inside this file start unchecked.
        val knownKeys = mutableSetOf<String>()
        rows += candidates.map { candidate ->
            val key = ImportDuplicateDetector.key(candidate.date, candidate.amountCents, candidate.payee)
            val duplicate = !knownKeys.add(key)
            ReviewRow(candidate.sourceRow, formatDate(candidate.date), candidate.payee,
                BigDecimal.valueOf(candidate.amountCents, 2).toPlainString(), candidate.notes,
                candidate.reference, selected = !duplicate, candidate.confidence, candidate.sourceLabel)
        }
        problems = parseProblems
        message = if (rows.isEmpty()) "No valid transactions found." else null
    }

    fun review(parsedTable: ImportTable, selectedMapping: ImportColumnMapping) {
        mapping = selectedMapping
        val result = CsvTransactionCandidateSource.parse(parsedTable, selectedMapping)
        reviewCandidates(result.candidates, result.problems)
    }

    fun reviewText(value: String, source: String, format: StatementFormat) {
        val result = FinancialMessageParser.parse(value, source, profile = notificationPreferences.profile())
        sourceName = source; sourceFormat = format; table = null; mapping = null
        reviewCandidates(result.candidates, result.problems)
    }

    LaunchedEffect(initialSharedText) {
        initialSharedText?.takeIf(String::isNotBlank)?.let {
            reviewText(it, "Shared message", StatementFormat.SHARED_TEXT)
            onSharedTextConsumed()
        }
    }

    fun load(uri: Uri) {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "statement.csv"
                val format = when (name.substringAfterLast('.', "").lowercase()) {
                    "xlsx" -> StatementFormat.XLSX
                    "pdf" -> StatementFormat.PDF
                    else -> StatementFormat.CSV
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readLimitedStatement() }
                    ?: error("Could not read the selected file")
                Triple(name, format, StatementDocumentReader.read(context, bytes, format))
            } }.onSuccess { (name, format, parsedTable) ->
                    sourceName = name; sourceFormat = format; table = parsedTable
                    val suggested = CsvTransactionCandidateSource.suggestedMapping(parsedTable.headers)
                    profileName = ""
                    review(parsedTable, suggested)
                }.onFailure { message = it.message ?: "Could not parse this statement." }
            busy = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(::load)
    }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Import transactions", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal)) {
            Text("CSV, XLSX, and text-based PDF files stay on this device. Every valid row is shown for review before anything is saved.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActuaGroupLabel("Message")
            OutlinedTextField(pastedText, { pastedText = it }, label = { Text("Paste SMS or email alert") },
                minLines = 3, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = pastedText.isNotBlank(), onClick = {
                    reviewText(pastedText, "Pasted message", StatementFormat.SHARED_TEXT)
                }) { Text("Review text") }
                if (queued.isNotEmpty()) TextButton(onClick = {
                    sourceName = "Captured notifications"; sourceFormat = StatementFormat.NOTIFICATION
                    table = null; mapping = null; reviewCandidates(queued, emptyList())
                }) { Text("Review captured (${queued.size})") }
            }
            ActuaGroupLabel("Bank notifications")
            ActuaFormCard {
                ActuaFormRow(
                    icon = Icons.Outlined.NotificationsActive,
                    label = "Capture future bank notifications",
                    value = null,
                    checked = captureEnabled,
                    onClick = {
                        val enabled = !captureEnabled
                        if (enabled && allowedPackages.isEmpty()) {
                            message = "Select at least one app before enabling capture."
                            appMenu = true
                        } else {
                            captureEnabled = enabled; notificationPreferences.enabled = enabled
                            if (enabled) context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    },
                )
                ActuaCardDivider()
                Box(Modifier.fillMaxWidth()) {
                    ActuaFormRow(
                        icon = Icons.Outlined.Apps,
                        label = "Notification apps",
                        value = if (allowedPackages.isEmpty()) "Select notification apps" else "${allowedPackages.size} selected",
                        valueIsPlaceholder = allowedPackages.isEmpty(),
                        onClick = { appMenu = true },
                    )
                    DropdownMenu(appMenu, { appMenu = false }) {
                        notificationApps.forEach { (packageName, label) ->
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(packageName in allowedPackages, null)
                                    Text(label)
                                } },
                                onClick = {
                                    allowedPackages = allowedPackages.toMutableSet().apply {
                                        if (!add(packageName)) remove(packageName)
                                    }
                                    notificationPreferences.allowedPackages = allowedPackages
                                    if (allowedPackages.isEmpty()) {
                                        captureEnabled = false; notificationPreferences.enabled = false
                                    }
                                },
                            )
                        }
                    }
                }
                ActuaCardDivider()
                ActuaFormTextField(icon = Icons.AutoMirrored.Outlined.TrendingDown, label = "Debit keywords",
                    value = debitKeywords, onValueChange = { debitKeywords = it })
                ActuaCardDivider()
                ActuaFormTextField(icon = Icons.AutoMirrored.Outlined.TrendingUp, label = "Credit keywords",
                    value = creditKeywords, onValueChange = { creditKeywords = it })
            }
            Text("Optional notification access processes alerts on-device and stores only recognized candidates, not raw notifications.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    val debit = debitKeywords.split(',').map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
                    val credit = creditKeywords.split(',').map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
                    if (debit.isEmpty() || credit.isEmpty()) message = "Keep at least one debit and credit keyword."
                    else {
                        notificationPreferences.saveProfile(FinancialMessageProfile(debit, credit))
                        message = "Saved message parser keywords."
                    }
                }) { Text("Save parser words") }
                TextButton(onClick = {
                    notificationPreferences.clearAll(); captureEnabled = false; queued = emptyList()
                    automationEnabled = false; automationToken = null
                    message = "Deleted captured candidates and parser settings."
                }) { Text("Delete notification data") }
            }
            ActuaGroupLabel("Automation apps")
            ActuaFormCard {
                ActuaFormRow(
                    icon = Icons.Outlined.Bolt,
                    label = "Accept transactions from Tasker",
                    value = null,
                    checked = automationEnabled,
                    onClick = {
                        automationEnabled = !automationEnabled
                        notificationPreferences.automationEnabled = automationEnabled
                        automationToken = notificationPreferences.automationToken
                    },
                )
                automationToken?.let { token ->
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Key,
                        label = "Token",
                        value = token,
                        caption = "Tap to copy",
                        onClick = {
                            copySensitiveText(context, "Actua automation token", token)
                            message = "Copied the automation token."
                        },
                    )
                }
            }
            Text(
                "Send a broadcast intent with action ${AutomationIntent.ACTION_QUEUE_TRANSACTION} to package " +
                    "${context.packageName}. Extras: token, amount (for example -12.34), and optionally type " +
                    "(debit or credit), payee, date (YYYY-MM-DD), notes, reference, account and source. Send text " +
                    "instead of amount to use Actua's message parser. Transactions wait here for review.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm))
            if (automationToken != null) TextButton(onClick = {
                automationToken = notificationPreferences.regenerateAutomationToken()
                message = "Created a new token. Update it in Tasker."
            }) { Text("New token") }
            ActuaGroupLabel("Statement file")
            ActuaFormCard {
                ActuaMenuRow(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    label = "Import into",
                    value = account?.name ?: "No open account",
                    valueIsPlaceholder = account == null,
                    choices = accounts.map { it.name to it },
                ) { account = it }
            }
            ActuaSecondaryButton(
                text = if (busy) "Reading…" else "Choose statement file",
                icon = Icons.Outlined.UploadFile,
                enabled = !busy && account != null,
                onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/pdf")) },
                modifier = Modifier.padding(vertical = Spacing.md),
            )
            val activeTable = table
            val activeMapping = mapping
            if (activeTable != null && activeMapping != null) {
                ActuaGroupLabel("Column mapping")
                ActuaFormCard {
                    activeTable.headers.forEachIndexed { index, header ->
                        if (index > 0) ActuaCardDivider()
                        ActuaMenuRow(
                            icon = Icons.Outlined.ViewColumn,
                            label = header.ifBlank { "Column ${index + 1}" },
                            value = activeMapping.roles.getOrElse(index) { ImportColumnRole.IGNORE }.displayName(),
                            choices = ImportColumnRole.entries.map { it.displayName() to it },
                        ) { role ->
                            val roles = activeMapping.roles.toMutableList().apply { this[index] = role }
                            review(activeTable, activeMapping.copy(roles = roles))
                        }
                    }
                }
                ActuaFormCard(Modifier.padding(top = Spacing.md)) {
                    ActuaFormRow(
                        icon = Icons.Outlined.SwapVert,
                        label = "Expenses are positive",
                        value = null,
                        checked = activeMapping.expensesArePositive,
                        onClick = { review(activeTable, activeMapping.copy(expensesArePositive = !activeMapping.expensesArePositive)) },
                    )
                    ActuaCardDivider()
                    ActuaMenuRow(
                        icon = Icons.Outlined.CalendarMonth,
                        label = "Date format",
                        value = activeMapping.datePattern,
                        choices = listOf("Auto", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "dd MMM yyyy")
                            .map { it to it },
                    ) { pattern -> review(activeTable, activeMapping.copy(datePattern = pattern)) }
                    ActuaCardDivider()
                    ActuaFormTextField(icon = Icons.Outlined.Edit, label = "Mapping profile name",
                        value = profileName, onValueChange = { profileName = it })
                }
                Row {
                    Box {
                        TextButton(enabled = importPreferences.profileNames().isNotEmpty(), onClick = { profileMenu = true }) {
                            Text("Load profile")
                        }
                        DropdownMenu(profileMenu, { profileMenu = false }) {
                            importPreferences.profileNames().forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = {
                                val saved = importPreferences.profile(name)
                                profileMenu = false
                                if (saved?.roles?.size == activeTable.headers.size) {
                                    profileName = name; review(activeTable, saved)
                                } else message = "That profile has a different number of columns."
                            }) }
                        }
                    }
                    TextButton(enabled = profileName.isNotBlank(), onClick = {
                        importPreferences.saveProfile(profileName.trim(), activeMapping)
                        message = "Saved mapping profile ${profileName.trim()}."
                    }) { Text("Save profile") }
                }
            }
            if (problems.isNotEmpty()) Text(
                "${problems.size} malformed row${if (problems.size == 1) " was" else "s were"} excluded. " +
                    problems.take(3).joinToString { "Row ${it.sourceRow}: ${it.message}" },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            message?.let { Text(it, modifier = Modifier.padding(bottom = 8.dp)) }
            if (history.isNotEmpty()) {
                ActuaGroupLabel("Recent imports")
                history.take(3).forEach { entry ->
                    Text("${entry.sourceName}: ${entry.imported} imported, ${entry.skipped} skipped · ${entry.accountName}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (rows.isNotEmpty()) ActuaFormCard(Modifier.padding(top = Spacing.md)) {
                ActuaFormRow(
                    icon = Icons.Outlined.Check,
                    label = "Mark as cleared",
                    value = null,
                    checked = markCleared,
                    onClick = { markCleared = !markCleared },
                )
            }
            rows.forEachIndexed { index, row ->
                val candidate = row.toCandidateOrNull()
                val invalid = candidate == null
                val candidateKey = candidate?.let { ImportDuplicateDetector.key(it.date, it.amountCents, it.payee) }
                val duplicate = candidateKey != null && rows.take(index).any {
                    it.toCandidateOrNull()?.let { prior ->
                        ImportDuplicateDetector.key(prior.date, prior.amountCents, prior.payee) == candidateKey
                    } == true
                }
                val duplicateReason = if (duplicate) "Repeated in this file" else null
                val match = matchesByRow[index]
                ActuaFormCard(Modifier.padding(top = Spacing.md)) {
                    Column(Modifier.padding(start = Spacing.xs, end = Spacing.md, bottom = Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = row.selected, onCheckedChange = { rows[index] = row.copy(selected = it) })
                        Text("${sourceFormat.name} row ${row.sourceRow}", style = MaterialTheme.typography.labelLarge)
                        if (duplicateReason != null) Text("  $duplicateReason", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Text("${row.confidence.name.lowercase().replaceFirstChar(Char::uppercase)} confidence · ${row.sourceLabel}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (match != null) {
                        val existing = "${match.payeeName ?: "a transaction"} on ${formatDate(match.date)}"
                        Text(
                            when {
                                row.addAsNew -> "Matches $existing, but will be added as a new transaction."
                                match.reconciled -> "Matches $existing, which is reconciled, so nothing is imported."
                                else -> "Matches $existing. Importing updates it instead of adding a copy."
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = row.addAsNew, onCheckedChange = { rows[index] = row.copy(addAsNew = it) })
                            Text("Add as a new transaction", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(row.date, { rows[index] = row.copy(date = it) }, label = { Text("Date (YYYY-MM-DD)") },
                        isError = invalid, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(row.payee, { rows[index] = row.copy(payee = it) }, label = { Text("Payee") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(row.amount, { rows[index] = row.copy(amount = it) }, label = { Text("Signed amount") },
                        isError = invalid, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(row.notes, { rows[index] = row.copy(notes = it) }, label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { rows.removeAt(index) }) { Text("Reject") }
                    }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
        val selected = rows.filter(ReviewRow::selected)
        val ready = selected.mapNotNull(ReviewRow::toCandidateOrNull)
        val addAsNew = selected.mapNotNull { row -> row.toCandidateOrNull()?.let { row.addAsNew } }
            .withIndex().filter { it.value }.mapTo(mutableSetOf()) { it.index }
        ActuaPrimaryActionBar(
            text = "Approve and import ${ready.size}",
            onClick = {
                val target = account ?: return@ActuaPrimaryActionBar
                onImport(target.id, ready, markCleared, addAsNew) {
                    message = "Imported ${ready.size} transaction${if (ready.size == 1) "" else "s"}."
                    importPreferences.addHistory(ImportHistoryEntry(sourceName, sourceFormat, target.name,
                        ready.size, rows.size - ready.size, System.currentTimeMillis()))
                    history = importPreferences.history()
                    if (sourceFormat == StatementFormat.NOTIFICATION) {
                        notificationPreferences.clearQueue(); queued = emptyList()
                    }
                    rows.clear(); problems = emptyList()
                }
            },
            enabled = selected.isNotEmpty() && ready.size == selected.size,
            icon = Icons.Outlined.Check,
        )
        if (history.isNotEmpty()) {
            TextButton(onClick = { importPreferences.clearHistory(); history = emptyList() },
                modifier = Modifier.align(Alignment.End)) { Text("Clear import history (${history.size})") }
        }
    }
}

private fun ImportColumnRole.displayName() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun ReviewRow.toCandidateOrNull(): ImportCandidate? = runCatching {
    val parsedDate = LocalDate.parse(date.trim())
    val cents = BigDecimal(amount.trim()).setScale(2).movePointRight(2).longValueExact()
    require(payee.isNotBlank() && cents != 0L)
    ImportCandidate(sourceRow, parsedDate.year * 10_000 + parsedDate.monthValue * 100 + parsedDate.dayOfMonth,
        payee.trim(), notes.trim(), cents, reference)
}.getOrNull()

private fun formatDate(value: Int): String = "%04d-%02d-%02d".format(value / 10_000, value / 100 % 100, value % 100)

private fun InputStream.readLimitedStatement(maxBytes: Int = 25 * 1024 * 1024): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= maxBytes) { "Statements larger than 25 MB are not supported" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun copySensitiveText(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}
