package com.azimulkabir.actua.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.content.Intent
import android.provider.OpenableColumns
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.azimulkabir.actua.data.notifications.CapturedTransactionNotifications
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.importing.accountForImportHint
import com.azimulkabir.actua.data.importing.importAccountMemoryKey
import com.azimulkabir.actua.data.importing.prefilledImportAccount
import com.azimulkabir.actua.data.importing.AutomationIntent
import com.azimulkabir.actua.data.importing.CsvTransactionCandidateSource
import com.azimulkabir.actua.data.importing.ImportCandidate
import com.azimulkabir.actua.data.importing.ImportGroup
import com.azimulkabir.actua.data.importing.ReviewedCandidate
import com.azimulkabir.actua.data.importing.groupImportRows
import com.azimulkabir.actua.data.importing.ImportColumnMapping
import com.azimulkabir.actua.data.importing.ImportColumnRole
import com.azimulkabir.actua.data.importing.ImportDuplicateDetector
import com.azimulkabir.actua.data.importing.ImportMatch
import com.azimulkabir.actua.data.importing.ImportHistoryEntry
import com.azimulkabir.actua.data.importing.ImportPreferences
import com.azimulkabir.actua.data.importing.ImportProblem
import com.azimulkabir.actua.data.importing.ImportProblemCode
import com.azimulkabir.actua.data.importing.ImportStatementException
import com.azimulkabir.actua.data.importing.ImportTable
import com.azimulkabir.actua.data.importing.ImportConfidence
import com.azimulkabir.actua.data.importing.FinancialMessageParser
import com.azimulkabir.actua.data.importing.NotificationImportPreferences
import com.azimulkabir.actua.data.importing.FinancialMessageProfile
import com.azimulkabir.actua.data.importing.StatementFormat
import com.azimulkabir.actua.data.importing.StatementDocumentReader
import com.azimulkabir.actua.data.importing.StatementFormatDetector
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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

/** The selected statement document could not be opened at all; localized at display. */
private class SelectedStatementUnreadableException : Exception()
private class UnsupportedStatementTypeException : Exception()

/** Statements above the size limit are refused before parsing; localized at display. */
private class StatementTooLargeException : Exception()

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
    /** This row's own account when queued rows are reviewed (#1037); null until one is chosen. */
    val accountId: String? = null,
    val accountHint: String? = null,
    /** The captured row's queue id, so its notification can be dismissed once handled (#1046). */
    val queueId: String? = null,
    val keepPayeeCase: Boolean = false,
    val financialId: String? = null,
)

@Composable
fun ImportTransactionsScreen(
    accounts: List<Account>,
    /** For an account and candidates, the existing transaction each would update (null: added). Blocking. */
    findMatches: (String, List<ImportCandidate>) -> List<ImportMatch?>,
    /**
     * The reviewed candidates grouped by account, whether to mark them cleared, and a callback once
     * they are all imported in one batch.
     */
    onImport: (List<ImportGroup>, Boolean, onImported: () -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialSharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
    /** A statement another app shared or opened with Actua (#1333); reviewed like a picked file. */
    initialSharedFile: Uri? = null,
    onSharedFileConsumed: () -> Unit = {},
    /**
     * Shows only the capture review queue on its own page (#1050), opened from the Accounts tab, a
     * captured-transaction notification (#1046) or [onOpenCapturedReview].
     */
    capturedReviewOnly: Boolean = false,
    onOpenCapturedReview: () -> Unit = {},
) {
    val context = LocalContext.current
    // Configuration-aware resource access; re-captured on every recomposition (#424 pattern).
    val resources = LocalResources.current
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
    val budgetId = remember { ActiveBudgetStore(context).budgetId ?: "no-budget" }
    var captureEnabled by remember { mutableStateOf(notificationPreferences.enabled) }
    var queued by remember { mutableStateOf(notificationPreferences.queued()) }
    var automationEnabled by remember { mutableStateOf(notificationPreferences.automationEnabled) }
    var automationToken by remember { mutableStateOf(notificationPreferences.automationToken) }
    var automationRejection by remember { mutableStateOf(notificationPreferences.automationRejection) }
    DisposableEffect(notificationPreferences) {
        val stop = notificationPreferences.observeAutomationRejection {
            automationRejection = notificationPreferences.automationRejection
        }
        onDispose(stop)
    }
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
    // Queued notifications and broadcasts can belong to different accounts, so each row picks its
    // own (#1037); statement files and pasted text import into the one chosen account.
    // Read from state on every call, so handlers that switch the source see the new value.
    fun perRowAccounts() = sourceFormat == StatementFormat.NOTIFICATION
    fun ReviewRow.targetAccountId(): String? = if (perRowAccounts()) accountId else account?.id
    // Selected, valid rows with their candidates; matching follows Actual's file import (#1009).
    val readyRows = rows.withIndex().filter { it.value.selected }
        .mapNotNull { (index, row) -> row.toCandidateOrNull()?.let { Triple(index, it, row.targetAccountId()) } }
    val matchesByRow by produceState(emptyMap<Int, ImportMatch>(), readyRows) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                readyRows.filter { it.third != null }.groupBy { requireNotNull(it.third) }
                    .flatMap { (accountId, inAccount) ->
                        inAccount.map { it.first }.zip(findMatches(accountId, inAccount.map { it.second }))
                    }
            }
        }.getOrDefault(emptyList()).mapNotNull { (index, match) -> match?.let { index to it } }.toMap()
    }

    fun prefilledAccountId(hint: String?, source: String): String? =
        prefilledImportAccount(hint, source, accounts) { notificationPreferences.rememberedAccount(budgetId, it) }?.id

    fun assignUnassignedRows(accountId: String) = rows.indices.forEach { index ->
        if (rows[index].accountId == null) rows[index] = rows[index].copy(accountId = accountId)
    }

    val openAccounts = accounts.filterNot(Account::closed)
    var defaultAccountId by remember { mutableStateOf(notificationPreferences.defaultAccount(budgetId)) }
    val defaultAccount = openAccounts.firstOrNull { it.id == defaultAccountId }

    @Composable
    fun DefaultAccountRow() = ActuaMenuRow(
        icon = Icons.Outlined.AccountBalanceWallet,
        label = stringResource(R.string.default_account),
        value = defaultAccount?.name ?: stringResource(R.string.none),
        valueIsPlaceholder = defaultAccount == null,
        choices = listOf(stringResource(R.string.none) to null) + openAccounts.map { it.name to it.id },
    ) { id ->
        // Rows still without an account take the new default, as new captures will (#1064).
        notificationPreferences.setDefaultAccount(budgetId, id); defaultAccountId = id
        if (id != null && perRowAccounts()) assignUnassignedRows(id)
    }

    fun reviewCandidates(candidates: List<ImportCandidate>, parseProblems: List<ImportProblem>) {
        if (!perRowAccounts()) candidates.mapNotNull(ImportCandidate::accountHint).distinct().singleOrNull()?.let { hint ->
            accountForImportHint(hint, accounts)?.let { account = it }
        }
        rows.clear()
        // Rows the account already holds are matched and updated instead (#1009); only repeats
        // inside this file start unchecked.
        val knownKeys = mutableSetOf<String>()
        rows += candidates.map { candidate ->
            val key = candidate.financialId?.let { "id:$it" }
                ?: ImportDuplicateDetector.key(candidate.date, candidate.amountCents, candidate.payee)
            val duplicate = !knownKeys.add(key)
            ReviewRow(candidate.sourceRow, formatDate(candidate.date), candidate.payee,
                BigDecimal.valueOf(candidate.amountCents, 2).toPlainString(), candidate.notes,
                candidate.reference, selected = !duplicate, candidate.confidence, candidate.sourceLabel,
                accountId = if (perRowAccounts()) prefilledAccountId(candidate.accountHint, candidate.sourceLabel) else null,
                accountHint = candidate.accountHint, queueId = candidate.queueId, keepPayeeCase = candidate.keepPayeeCase,
                financialId = candidate.financialId)
        }
        problems = parseProblems
        message = if (rows.isEmpty()) resources.getString(R.string.no_valid_transactions_found) else null
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

    fun reviewCaptured() {
        queued = notificationPreferences.queued()
        sourceName = resources.getString(R.string.captured_notifications); sourceFormat = StatementFormat.NOTIFICATION
        table = null; mapping = null; reviewCandidates(queued, emptyList())
    }

    LaunchedEffect(capturedReviewOnly) {
        if (capturedReviewOnly) {
            reviewCaptured()
            if (rows.isEmpty()) message = resources.getString(R.string.no_captured_waiting)
        }
    }

    var confirmNotifications by remember { mutableStateOf(notificationPreferences.confirmNotificationsEnabled) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        confirmNotifications = granted
        notificationPreferences.confirmNotificationsEnabled = granted
        if (!granted) message = resources.getString(R.string.allow_notifications_message)
    }

    LaunchedEffect(initialSharedText) {
        initialSharedText?.takeIf(String::isNotBlank)?.let {
            reviewText(it, resources.getString(R.string.shared_message), StatementFormat.SHARED_TEXT)
            onSharedTextConsumed()
        }
    }

    fun load(uri: Uri, requireKnownFormat: Boolean = false) {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: resources.getString(R.string.default_statement_filename)
                val detected = StatementFormatDetector.detect(name, context.contentResolver.getType(uri))
                // The picker has always treated an unrecognised file as CSV; a file another app pushed
                // at us is rejected instead, so an unrelated share doesn't open a nonsense review.
                if (detected == null && requireKnownFormat) throw UnsupportedStatementTypeException()
                val format = detected ?: StatementFormat.CSV
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readLimitedStatement() }
                    ?: throw SelectedStatementUnreadableException()
                Triple(name, format, StatementDocumentReader.read(context, bytes, format))
            } }.onSuccess { (name, format, readResult) ->
                    sourceName = name; sourceFormat = format
                    when (readResult) {
                        is com.azimulkabir.actua.data.importing.StatementReadResult.Table -> {
                            table = readResult.table
                            val suggested = CsvTransactionCandidateSource.suggestedMapping(readResult.table.headers)
                            profileName = ""
                            review(readResult.table, suggested)
                        }
                        is com.azimulkabir.actua.data.importing.StatementReadResult.Candidates -> {
                            table = null; mapping = null; profileName = ""
                            reviewCandidates(readResult.parseResult.candidates, readResult.parseResult.problems)
                        }
                    }
                }.onFailure {
                    message = when (it) {
                        is SelectedStatementUnreadableException -> resources.getString(R.string.could_not_read_selected_file)
                        is UnsupportedStatementTypeException -> resources.getString(R.string.import_shared_file_unsupported)
                        is StatementTooLargeException -> resources.getString(R.string.statement_too_large)
                        is ImportStatementException -> resources.getString(
                            when (it.code) {
                                ImportProblemCode.XLSX_NO_SHEET -> R.string.import_problem_xlsx_no_sheet
                                ImportProblemCode.PDF_NO_TABLE -> R.string.import_problem_pdf_no_table
                                else -> R.string.could_not_parse_statement
                            }
                        )
                        else -> it.message ?: resources.getString(R.string.could_not_parse_statement)
                    }
                }
            busy = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { load(it) }
    }
    LaunchedEffect(initialSharedFile) {
        initialSharedFile?.let {
            load(it, requireKnownFormat = true)
            onSharedFileConsumed()
        }
    }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(
            title = stringResource(
                if (capturedReviewOnly) R.string.review_captured_title else R.string.import_transactions_title
            ),
            onBack = onBack,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal)) {
            if (!capturedReviewOnly) {
                Text(stringResource(R.string.import_transactions_privacy),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActuaGroupLabel(stringResource(R.string.message_group))
                OutlinedTextField(pastedText, { pastedText = it }, label = { Text(stringResource(R.string.paste_financial_alert)) },
                    minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = pastedText.isNotBlank(), onClick = {
                        reviewText(pastedText, resources.getString(R.string.pasted_message), StatementFormat.SHARED_TEXT)
                    }) { Text(stringResource(R.string.review_text)) }
                    if (queued.isNotEmpty()) TextButton(onClick = onOpenCapturedReview) {
                        Text(pluralStringResource(R.plurals.review_captured, queued.size, queued.size))
                    }
                }
                ActuaGroupLabel(stringResource(R.string.bank_notifications))
                ActuaFormCard {
                    ActuaFormRow(
                        icon = Icons.Outlined.NotificationsActive,
                        label = stringResource(R.string.capture_bank_notifications),
                        value = null,
                        checked = captureEnabled,
                        onClick = {
                            val enabled = !captureEnabled
                            if (enabled && allowedPackages.isEmpty()) {
                                message = resources.getString(R.string.select_app_before_capture)
                                appMenu = true
                            } else {
                                captureEnabled = enabled; notificationPreferences.enabled = enabled
                                if (enabled) context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        },
                    )
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Notifications,
                        label = stringResource(R.string.notify_about_captures),
                        value = null,
                        caption = stringResource(R.string.notify_captures_caption),
                        checked = confirmNotifications,
                        onClick = {
                            val enabled = !confirmNotifications
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                confirmNotifications = enabled
                                notificationPreferences.confirmNotificationsEnabled = enabled
                            }
                        },
                    )
                    ActuaCardDivider()
                    Box(Modifier.fillMaxWidth()) {
                        ActuaFormRow(
                            icon = Icons.Outlined.Apps,
                            label = stringResource(R.string.notification_apps),
                            value = if (allowedPackages.isEmpty()) stringResource(R.string.select_notification_apps)
                            else pluralStringResource(R.plurals.selected_apps, allowedPackages.size, allowedPackages.size),
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
                    DefaultAccountRow()
                    ActuaCardDivider()
                    ActuaFormTextField(icon = Icons.AutoMirrored.Outlined.TrendingDown, label = stringResource(R.string.debit_keywords),
                        value = debitKeywords, onValueChange = { debitKeywords = it })
                    ActuaCardDivider()
                    ActuaFormTextField(icon = Icons.AutoMirrored.Outlined.TrendingUp, label = stringResource(R.string.credit_keywords),
                        value = creditKeywords, onValueChange = { creditKeywords = it })
                }
                Text(stringResource(R.string.notification_access_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        val debit = debitKeywords.split(',').map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
                        val credit = creditKeywords.split(',').map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
                        if (debit.isEmpty() || credit.isEmpty()) message = resources.getString(R.string.keep_debit_credit_keyword)
                        else {
                            notificationPreferences.saveProfile(FinancialMessageProfile(debit, credit))
                            message = resources.getString(R.string.saved_parser_keywords)
                        }
                    }) { Text(stringResource(R.string.save_parser_words)) }
                    TextButton(onClick = {
                        CapturedTransactionNotifications.cancel(context, notificationPreferences.queued().mapNotNull { it.queueId })
                        notificationPreferences.clearAll(); captureEnabled = false; confirmNotifications = false; queued = emptyList()
                        automationEnabled = false; automationToken = null
                        message = resources.getString(R.string.deleted_notification_data)
                    }) { Text(stringResource(R.string.delete_notification_data)) }
                }
                ActuaGroupLabel(stringResource(R.string.automation_apps))
                ActuaFormCard {
                    ActuaFormRow(
                        icon = Icons.Outlined.Bolt,
                        label = stringResource(R.string.accept_from_tasker),
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
                            label = stringResource(R.string.token_label),
                            value = token,
                            caption = stringResource(R.string.tap_to_copy),
                            onClick = {
                                copySensitiveText(context, resources.getString(R.string.automation_token_clipboard_label), token)
                                message = resources.getString(R.string.copied_automation_token)
                            },
                        )
                    }
                    automationRejection?.let { rejection ->
                        ActuaCardDivider()
                        ActuaFormRow(
                            icon = Icons.Outlined.ErrorOutline,
                            label = stringResource(R.string.last_broadcast_ignored),
                            value = null,
                            caption = rejection.reason,
                            supportingValue = relativeTime(rejection.atMillis),
                        )
                    }
                }
                Text(
                    stringResource(
                        R.string.automation_help_text,
                        AutomationIntent.ACTION_QUEUE_TRANSACTION, context.packageName,
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm))
                if (automationToken != null) TextButton(onClick = {
                    automationToken = notificationPreferences.regenerateAutomationToken()
                    message = resources.getString(R.string.token_regenerated)
                }) { Text(stringResource(R.string.new_token)) }
                ActuaGroupLabel(stringResource(R.string.statement_file))
                ActuaFormCard {
                    ActuaMenuRow(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        label = stringResource(R.string.import_into),
                        value = account?.name ?: stringResource(R.string.no_open_account),
                        valueIsPlaceholder = account == null,
                        choices = accounts.map { it.name to it },
                    ) { account = it }
                }
                ActuaSecondaryButton(
                    text = stringResource(if (busy) R.string.reading else R.string.choose_statement_file),
                    icon = Icons.Outlined.UploadFile,
                    enabled = !busy && account != null,
                    onClick = { picker.launch(arrayOf(
                        "text/csv", "text/comma-separated-values", "text/plain",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/pdf",
                        "application/x-ofx", "application/ofx", "application/qfx", "application/x-qfx",
                        "application/qif", "text/qif", "application/xml", "text/xml", "*/*",
                    )) },
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
                val activeTable = table
                val activeMapping = mapping
                if (activeTable != null && activeMapping != null) {
                    ActuaGroupLabel(stringResource(R.string.column_mapping))
                    ActuaFormCard {
                        activeTable.headers.forEachIndexed { index, header ->
                            if (index > 0) ActuaCardDivider()
                            ActuaMenuRow(
                                icon = Icons.Outlined.ViewColumn,
                                label = header.ifBlank { stringResource(R.string.column_number, index + 1) },
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
                            label = stringResource(R.string.expenses_are_positive),
                            value = null,
                            checked = activeMapping.expensesArePositive,
                            onClick = { review(activeTable, activeMapping.copy(expensesArePositive = !activeMapping.expensesArePositive)) },
                        )
                        ActuaCardDivider()
                        ActuaMenuRow(
                            icon = Icons.Outlined.CalendarMonth,
                            label = stringResource(R.string.date_format_label),
                            value = activeMapping.datePattern,
                            choices = listOf("Auto", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "dd MMM yyyy")
                                .map { it to it },
                        ) { pattern -> review(activeTable, activeMapping.copy(datePattern = pattern)) }
                        ActuaCardDivider()
                        ActuaFormTextField(icon = Icons.Outlined.Edit, label = stringResource(R.string.mapping_profile_name),
                            value = profileName, onValueChange = { profileName = it })
                    }
                    Row {
                        Box {
                            TextButton(enabled = importPreferences.profileNames().isNotEmpty(), onClick = { profileMenu = true }) {
                                Text(stringResource(R.string.load_profile))
                            }
                            DropdownMenu(profileMenu, { profileMenu = false }) {
                                importPreferences.profileNames().forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = {
                                    val saved = importPreferences.profile(name)
                                    profileMenu = false
                                    if (saved?.roles?.size == activeTable.headers.size) {
                                        profileName = name; review(activeTable, saved)
                                    } else message = resources.getString(R.string.profile_column_count_mismatch)
                                }) }
                            }
                        }
                        TextButton(enabled = profileName.isNotBlank(), onClick = {
                            importPreferences.saveProfile(profileName.trim(), activeMapping)
                            message = resources.getString(R.string.saved_mapping_profile, profileName.trim())
                        }) { Text(stringResource(R.string.save_profile)) }
                    }
                }
            }
            if (problems.isNotEmpty()) Text(
                pluralStringResource(R.plurals.malformed_rows_excluded, problems.size, problems.size) + " " +
                    problems.take(3).map {
                        stringResource(R.string.row_problem, it.sourceRow, importProblemMessage(it))
                    }.joinToString(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            message?.let { Text(it, modifier = Modifier.padding(bottom = 8.dp)) }
            if (!capturedReviewOnly) {
                if (history.isNotEmpty()) {
                    ActuaGroupLabel(stringResource(R.string.recent_imports))
                    history.take(3).forEach { entry ->
                        Text(stringResource(
                            R.string.import_history_entry,
                            entry.sourceName, entry.imported, entry.skipped, entry.accountName,
                        ),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (rows.isNotEmpty()) ActuaFormCard(Modifier.padding(top = Spacing.md)) {
                if (perRowAccounts()) {
                    // Account choices come first, ahead of the rows they apply to (#1064).
                    if (rows.any { it.accountId == null }) {
                        ActuaMenuRow(
                            icon = Icons.Outlined.AccountBalanceWallet,
                            label = stringResource(R.string.account_for_all),
                            value = stringResource(R.string.choose_an_account),
                            valueIsPlaceholder = true,
                            choices = openAccounts.map { it.name to it.id },
                        ) { assignUnassignedRows(it) }
                        ActuaCardDivider()
                    }
                    DefaultAccountRow()
                    ActuaCardDivider()
                }
                ActuaFormRow(
                    icon = Icons.Outlined.Check,
                    label = stringResource(R.string.mark_as_cleared),
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
                val duplicateReason = if (duplicate) stringResource(R.string.repeated_in_file) else null
                val match = matchesByRow[index]
                ActuaFormCard(Modifier.padding(top = Spacing.md)) {
                    Column(Modifier.padding(start = Spacing.xs, end = Spacing.md, bottom = Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = row.selected, onCheckedChange = { rows[index] = row.copy(selected = it) })
                        Text(stringResource(R.string.format_row, sourceFormat.displayName(), row.sourceRow),
                            style = MaterialTheme.typography.labelLarge)
                        if (duplicateReason != null) Text(
                            stringResource(R.string.duplicate_reason, duplicateReason),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (perRowAccounts()) {
                        val rowAccount = accounts.firstOrNull { it.id == row.accountId }
                        ActuaMenuRow(
                            icon = Icons.Outlined.AccountBalanceWallet,
                            label = stringResource(R.string.account_label),
                            value = rowAccount?.name ?: stringResource(R.string.choose_an_account),
                            valueIsPlaceholder = rowAccount == null,
                            choices = accounts.map { it.name to it.id },
                        ) { rows[index] = row.copy(accountId = it) }
                    }
                    Text(stringResource(
                        R.string.confidence_source,
                        row.confidence.displayName(),
                        if (row.sourceLabel == "Statement") stringResource(R.string.statement_source) else row.sourceLabel,
                    ),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (match != null) {
                        val existing = stringResource(
                            R.string.match_on_date,
                            match.payeeName ?: stringResource(R.string.a_transaction),
                            formatDate(match.date),
                        )
                        Text(
                            when {
                                row.addAsNew -> stringResource(R.string.match_add_as_new, existing)
                                match.reconciled -> stringResource(R.string.match_reconciled, existing)
                                else -> stringResource(R.string.match_updates, existing)
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = row.addAsNew, onCheckedChange = { rows[index] = row.copy(addAsNew = it) })
                            Text(stringResource(R.string.add_as_new), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(row.date, { rows[index] = row.copy(date = it) }, label = { Text(stringResource(R.string.date_iso_label)) },
                        isError = invalid, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(row.payee, { rows[index] = row.copy(payee = it) }, label = { Text(stringResource(R.string.payee)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(row.amount, { rows[index] = row.copy(amount = it) }, label = { Text(stringResource(R.string.signed_amount)) },
                        isError = invalid, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(row.notes, { rows[index] = row.copy(notes = it) }, label = { Text(stringResource(R.string.notes)) },
                        modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            rows.removeAt(index)
                            // A rejected capture leaves the queue now, not only this page (#1076).
                            row.queueId?.let { id ->
                                CapturedTransactionNotifications.reject(context, notificationPreferences, id)
                                queued = notificationPreferences.queued()
                            }
                        }) { Text(stringResource(R.string.reject)) }
                    }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
        val selected = rows.filter(ReviewRow::selected)
        val reviewed = selected.mapNotNull { row ->
            row.toCandidateOrNull()?.let { ReviewedCandidate(it, row.targetAccountId(), row.addAsNew) }
        }
        val importPlan = groupImportRows(reviewed)
        val waiting = importPlan.unassigned.size
        ActuaPrimaryActionBar(
            text = if (waiting > 0) stringResource(R.string.import_waiting_accounts, importPlan.importCount, waiting)
                else pluralStringResource(R.plurals.approve_and_import, importPlan.importCount, importPlan.importCount),
            onClick = {
                val imported = importPlan.importCount
                val accountNames = importPlan.groups.mapNotNull { group -> accounts.firstOrNull { it.id == group.accountId }?.name }
                // Ticked rows still without an account stay for review, and stay queued (#1037).
                var leftover = rows.filter { it.selected && it.toCandidateOrNull() != null && it.targetAccountId() == null }
                onImport(importPlan.groups, markCleared) {
                    importPreferences.addHistory(ImportHistoryEntry(sourceName, sourceFormat, accountNames.joinToString(", "),
                        imported, rows.size - imported, System.currentTimeMillis()))
                    history = importPreferences.history()
                    if (sourceFormat == StatementFormat.NOTIFICATION) {
                        // Rows imported or dropped here no longer need their notifications (#1046).
                        val keptIds = leftover.mapNotNull(ReviewRow::queueId).toSet()
                        CapturedTransactionNotifications.cancel(context, rows.mapNotNull(ReviewRow::queueId).filterNot(keptIds::contains))
                        // The next row from the same card or app starts with the account chosen now (#1045).
                        notificationPreferences.rememberAccounts(budgetId, selected.mapNotNull { row ->
                            row.accountId?.let { importAccountMemoryKey(row.accountHint, row.sourceLabel) to it }
                        }.toMap())
                        // Rows left waiting pick up the choices just remembered, as new captures do.
                        leftover = leftover.map { row ->
                            row.copy(accountId = prefilledAccountId(row.accountHint, row.sourceLabel))
                        }
                        val remaining = leftover.mapNotNull(ReviewRow::toQueuedCandidateOrNull)
                        notificationPreferences.replaceQueue(remaining); queued = remaining
                    }
                    val waitingForAccount = leftover.count { it.targetAccountId() == null }
                    message = resources.getQuantityString(R.plurals.imported_transactions, imported, imported) +
                        if (waitingForAccount > 0) {
                            " " + resources.getQuantityString(
                                R.plurals.still_need_account, waitingForAccount, waitingForAccount,
                            )
                        } else ""
                    rows.clear(); rows += leftover; problems = emptyList()
                }
            },
            enabled = selected.isNotEmpty() && reviewed.size == selected.size && importPlan.importCount > 0,
            icon = Icons.Outlined.Check,
        )
        if (!capturedReviewOnly) {
            if (history.isNotEmpty()) {
                TextButton(onClick = { importPreferences.clearHistory(); history = emptyList() },
                    modifier = Modifier.align(Alignment.End)) {
                    Text(pluralStringResource(R.plurals.clear_import_history, history.size, history.size))
                }
            }
        }
    }
}

@Composable
private fun ImportColumnRole.displayName() = stringResource(when (this) {
    ImportColumnRole.IGNORE -> R.string.column_role_ignore
    ImportColumnRole.DATE -> R.string.column_role_date
    ImportColumnRole.PAYEE -> R.string.column_role_payee
    ImportColumnRole.NOTES -> R.string.column_role_notes
    ImportColumnRole.REFERENCE -> R.string.column_role_reference
    ImportColumnRole.AMOUNT -> R.string.column_role_amount
    ImportColumnRole.DEBIT -> R.string.column_role_debit
    ImportColumnRole.CREDIT -> R.string.column_role_credit
})

@Composable
private fun ImportConfidence.displayName() = stringResource(when (this) {
    ImportConfidence.HIGH -> R.string.confidence_high
    ImportConfidence.MEDIUM -> R.string.confidence_medium
    ImportConfidence.LOW -> R.string.confidence_low
})

@Composable
private fun StatementFormat.displayName() = stringResource(when (this) {
    StatementFormat.CSV -> R.string.statement_format_csv
    StatementFormat.XLSX -> R.string.statement_format_xlsx
    StatementFormat.PDF -> R.string.statement_format_pdf
    StatementFormat.OFX -> R.string.statement_format_ofx
    StatementFormat.QFX -> R.string.statement_format_qfx
    StatementFormat.QIF -> R.string.statement_format_qif
    StatementFormat.CAMT053 -> R.string.statement_format_camt053
    StatementFormat.SHARED_TEXT -> R.string.statement_format_shared_text
    StatementFormat.NOTIFICATION -> R.string.statement_format_notification
})

/** Localized text for one parse problem; [ImportProblem.detail] only ever carries raw data. */
@Composable
internal fun importProblemMessage(problem: ImportProblem): String = when (problem.code) {
    ImportProblemCode.FILE_EMPTY -> stringResource(R.string.import_problem_file_empty)
    ImportProblemCode.MISSING_REQUIRED_COLUMNS -> {
        val columns = problem.detail.orEmpty().split(',').filter(String::isNotBlank).map { column ->
            stringResource(when (column) {
                "date" -> R.string.import_column_date
                "description/payee" -> R.string.import_column_payee
                else -> R.string.import_column_amount
            })
        }.joinToString()
        stringResource(R.string.import_problem_missing_columns, columns)
    }
    ImportProblemCode.PAYEE_BLANK -> stringResource(R.string.import_problem_payee_blank)
    ImportProblemCode.AMOUNT_BLANK -> stringResource(R.string.import_problem_amount_blank)
    ImportProblemCode.AMOUNT_BLANK_OR_ZERO -> stringResource(R.string.import_problem_amount_blank_or_zero)
    ImportProblemCode.INVALID_AMOUNT -> stringResource(R.string.import_problem_invalid_amount)
    ImportProblemCode.UNSUPPORTED_DATE -> stringResource(R.string.import_problem_unsupported_date, problem.detail.orEmpty())
    ImportProblemCode.UNCLOSED_QUOTED_FIELD -> stringResource(R.string.import_problem_unclosed_quote)
    ImportProblemCode.DEBIT_OR_CREDIT_NOT_RECOGNIZED ->
        stringResource(R.string.import_problem_direction_not_recognized)
    ImportProblemCode.TRANSACTION_AMOUNT_NOT_RECOGNIZED ->
        stringResource(R.string.import_problem_transaction_amount_not_recognized)
    ImportProblemCode.ROW_COULD_NOT_BE_PARSED -> stringResource(R.string.import_problem_row_parse)
    ImportProblemCode.NO_TRANSACTIONS_OFX -> stringResource(R.string.import_problem_no_transactions_ofx)
    ImportProblemCode.INVALID_QIF -> stringResource(R.string.import_problem_invalid_qif, problem.detail.orEmpty())
    ImportProblemCode.CAMT_PARSE_FAILED -> stringResource(R.string.import_problem_camt_parse, problem.detail.orEmpty())
    ImportProblemCode.CAMT_NO_ENTRIES -> stringResource(R.string.import_problem_camt_no_entries)
    ImportProblemCode.XLSX_NO_SHEET -> stringResource(R.string.import_problem_xlsx_no_sheet)
    ImportProblemCode.PDF_NO_TABLE -> stringResource(R.string.import_problem_pdf_no_table)
    ImportProblemCode.NO_AMOUNT_OR_TEXT -> stringResource(R.string.import_problem_no_amount_or_text)
    ImportProblemCode.AMOUNT_TOO_LARGE -> stringResource(R.string.import_problem_amount_too_large)
    ImportProblemCode.INVALID_TYPE -> stringResource(R.string.import_problem_type)
    ImportProblemCode.INVALID_DATE -> stringResource(R.string.import_problem_date_format)
}

private fun ReviewRow.toCandidateOrNull(): ImportCandidate? = runCatching {
    val parsedDate = LocalDate.parse(date.trim())
    val cents = BigDecimal(amount.trim()).setScale(2).movePointRight(2).longValueExact()
    require(payee.isNotBlank() && cents != 0L)
    ImportCandidate(sourceRow, parsedDate.year * 10_000 + parsedDate.monthValue * 100 + parsedDate.dayOfMonth,
        payee.trim(), notes.trim(), cents, reference, keepPayeeCase = keepPayeeCase, financialId = financialId)
}.getOrNull()

/** The row as it goes back to the notification queue, keeping its source and account hint. */
private fun ReviewRow.toQueuedCandidateOrNull(): ImportCandidate? =
    toCandidateOrNull()?.copy(confidence = confidence, sourceLabel = sourceLabel, accountHint = accountHint, queueId = queueId)

private fun formatDate(value: Int): String = "%04d-%02d-%02d".format(value / 10_000, value / 100 % 100, value % 100)

private fun InputStream.readLimitedStatement(maxBytes: Int = 25 * 1024 * 1024): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > maxBytes) throw StatementTooLargeException()
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
