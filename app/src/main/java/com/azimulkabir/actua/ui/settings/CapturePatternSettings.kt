package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.importing.CaptureDirection
import com.azimulkabir.actua.data.importing.CapturePatternJson
import com.azimulkabir.actua.data.importing.CapturePatternProblem
import com.azimulkabir.actua.data.importing.FinancialCapturePattern
import com.azimulkabir.actua.data.importing.FinancialMessageParser
import com.azimulkabir.actua.data.importing.FinancialMessageProfile
import com.azimulkabir.actua.data.importing.ImportParseResult
import com.azimulkabir.actua.data.importing.InvalidCapturePattern
import com.azimulkabir.actua.data.importing.MAX_CAPTURE_PATTERNS
import com.azimulkabir.actua.data.importing.MAX_CAPTURE_PATTERN_FILE_BYTES
import com.azimulkabir.actua.data.importing.capturePatternProblem
import com.azimulkabir.actua.data.schedules.formatIsoDay
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.util.suspendRunCatching
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CapturePatternTest(
    message: String,
    apps: Map<String, String>,
    profile: FinancialMessageProfile,
    sourcePackage: String?,
    onSourceChange: (String?) -> Unit,
) {
    var result by remember(message, profile, sourcePackage) { mutableStateOf<ImportParseResult?>(null) }
    val sharedLabel = stringResource(R.string.pasted_message)
    CaptureChoice(
        stringResource(R.string.capture_pattern_test_source),
        sourcePackage,
        listOf(null to stringResource(R.string.capture_pattern_global)) + apps.toList(),
        onSourceChange,
    )
    TextButton(enabled = message.isNotBlank(), onClick = {
        result = FinancialMessageParser.parse(message, apps[sourcePackage] ?: sharedLabel, profile = profile, sourcePackage = sourcePackage)
    }) { Text(stringResource(R.string.capture_pattern_test)) }
    result?.let { parsed ->
        val candidate = parsed.candidates.firstOrNull()
        if (candidate == null) {
            Text(stringResource(R.string.no_valid_transactions_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val parser = parsed.matchedPattern ?: stringResource(R.string.capture_pattern_builtin)
            Text(stringResource(R.string.capture_pattern_matched, parser))
            Text(stringResource(R.string.capture_pattern_preview_amount, BigDecimal.valueOf(candidate.amountCents, 2).toPlainString()))
            Text(stringResource(R.string.capture_pattern_preview_date, formatIsoDay(candidate.date)))
            if (candidate.payee.isNotEmpty()) Text(stringResource(R.string.capture_pattern_preview_payee, candidate.payee))
            candidate.accountHint?.let { Text(stringResource(R.string.capture_pattern_preview_account, it)) }
            candidate.reference?.let { Text(stringResource(R.string.capture_pattern_preview_reference, it)) }
        }
    }
}

@Composable
internal fun CapturePatternSettings(
    patterns: List<FinancialCapturePattern>,
    apps: Map<String, String>,
    onChange: (List<FinancialCapturePattern>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPatterns by rememberUpdatedState(patterns)
    val update by rememberUpdatedState(onChange)
    var editor by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var transferring by remember { mutableStateOf(false) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        transferring = true
        scope.launch {
            val result = suspendRunCatching {
                withContext(Dispatchers.IO) {
                    val json = context.contentResolver.openInputStream(uri)?.use {
                        it.readLimitedStatement(MAX_CAPTURE_PATTERN_FILE_BYTES).toString(Charsets.UTF_8)
                    } ?: error("Cannot read capture patterns")
                    CapturePatternJson.decode(json)
                }
            }
            transferring = false
            result.onSuccess { imported ->
                val combined = (currentPatterns + imported).distinct()
                if (combined.size > MAX_CAPTURE_PATTERNS) {
                    notice = context.getString(R.string.capture_pattern_limit, MAX_CAPTURE_PATTERNS)
                } else {
                    update(combined)
                    notice = context.getString(R.string.capture_patterns_imported)
                }
            }.onFailure { error ->
                notice = if (error is InvalidCapturePattern) {
                    context.getString(
                        R.string.capture_pattern_import_rule_error,
                        error.index + 1, context.getString(error.problem.labelRes),
                    )
                } else context.getString(R.string.capture_pattern_import_error)
            }
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapshot = currentPatterns
        transferring = true
        scope.launch {
            val result = suspendRunCatching {
                withContext(Dispatchers.IO) {
                    val bytes = CapturePatternJson.encode(snapshot).toByteArray(Charsets.UTF_8)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("Cannot write capture patterns")
                }
            }
            notice = context.getString(
                if (result.isSuccess) R.string.capture_patterns_exported else R.string.capture_pattern_export_error,
            )
            transferring = false
        }
    }

    ActuaGroupLabel(stringResource(R.string.capture_patterns_title))
    Text(stringResource(R.string.capture_patterns_help), style = MaterialTheme.typography.bodySmall)
    if (patterns.isNotEmpty()) {
        ActuaFormCard {
            patterns.forEachIndexed { index, pattern ->
                if (index > 0) ActuaCardDivider()
                ActuaFormRow(
                    icon = Icons.Outlined.Edit,
                    label = pattern.name,
                    value = pattern.sourcePackage?.let { apps[it] ?: it } ?: stringResource(R.string.capture_pattern_global),
                    onClick = { editor = index },
                )
            }
        }
    }
    TextButton(enabled = !transferring && patterns.size < MAX_CAPTURE_PATTERNS, onClick = { editor = patterns.size }) {
        Text(stringResource(R.string.capture_pattern_add))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TextButton(enabled = !transferring, onClick = { importFile.launch(arrayOf("application/json", "text/plain")) }) {
            Text(stringResource(R.string.capture_patterns_import))
        }
        TextButton(
            enabled = !transferring && patterns.isNotEmpty(),
            onClick = { exportFile.launch("actua-capture-patterns.json") },
        ) {
            Text(stringResource(R.string.capture_patterns_export))
        }
    }
    notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    editor?.let { index ->
        CapturePatternEditor(
            pattern = patterns.getOrNull(index),
            apps = apps,
            onDismiss = { editor = null },
            onSave = { rule ->
                onChange(patterns.toMutableList().apply {
                    if (index < size) set(index, rule) else add(rule)
                })
                editor = null
            },
            onDelete = patterns.getOrNull(index)?.let {
                { onChange(patterns.toMutableList().apply { removeAt(index) }); editor = null }
            },
            onMoveEarlier = if (index in 1 until patterns.size) {
                { onChange(patterns.toMutableList().apply { add(index - 1, removeAt(index)) }); editor = null }
            } else null,
        )
    }
}

@Composable
private fun CapturePatternEditor(
    pattern: FinancialCapturePattern?,
    apps: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (FinancialCapturePattern) -> Unit,
    onDelete: (() -> Unit)?,
    onMoveEarlier: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(pattern?.name.orEmpty()) }
    var expression by remember { mutableStateOf(pattern?.expression.orEmpty()) }
    var selectedApp by remember { mutableStateOf(pattern?.sourcePackage) }
    var direction by remember { mutableStateOf(pattern?.direction ?: CaptureDirection.KEYWORDS) }
    var dateFormat by remember { mutableStateOf(pattern?.dateFormat.orEmpty()) }
    var decimal by remember { mutableStateOf((pattern?.decimalSeparator ?: '.').toString()) }
    var grouping by remember { mutableStateOf(if (pattern == null) "," else pattern.groupingSeparator?.toString().orEmpty()) }
    var problem by remember { mutableStateOf<CapturePatternProblem?>(null) }
    val unavailableApp = pattern?.sourcePackage?.takeIf { it !in apps }
    val appChoices = listOf(null to stringResource(R.string.capture_pattern_global)) +
        apps.toList() + listOfNotNull(unavailableApp?.let { it to it })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (pattern == null) R.string.capture_pattern_add else R.string.capture_pattern_edit)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PatternField(name, { name = it }, R.string.capture_pattern_name)
                PatternField(expression, { expression = it }, R.string.capture_pattern_expression, singleLine = false)
                Text(stringResource(R.string.capture_pattern_expression_help), style = MaterialTheme.typography.bodySmall)
                CaptureChoice(stringResource(R.string.capture_pattern_source), selectedApp, appChoices) { selectedApp = it }
                CaptureChoice(stringResource(R.string.capture_pattern_direction), direction, CaptureDirection.entries.map {
                    it to stringResource(it.labelRes)
                }) { direction = it }
                PatternField(dateFormat, { dateFormat = it }, R.string.capture_pattern_date_format)
                PatternField(decimal, { decimal = it }, R.string.capture_pattern_decimal)
                PatternField(grouping, { grouping = it }, R.string.capture_pattern_grouping)
                problem?.let { Text(stringResource(it.labelRes), color = MaterialTheme.colorScheme.error) }
                onMoveEarlier?.let { TextButton(onClick = it) { Text(stringResource(R.string.capture_pattern_move_earlier)) } }
                onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.delete)) } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val decimalSeparator = decimal.singleOrNull()
                if (decimalSeparator == null || grouping.length > 1) {
                    problem = CapturePatternProblem.SEPARATORS
                } else {
                    val rule = FinancialCapturePattern(
                        name.trim(), expression, selectedApp, direction, dateFormat.trim(),
                        decimalSeparator, grouping.singleOrNull(),
                    )
                    problem = capturePatternProblem(rule)
                    if (problem == null) onSave(rule)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PatternField(value: String, onChange: (String) -> Unit, label: Int, singleLine: Boolean = true) {
    OutlinedTextField(
        value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        singleLine = singleLine, minLines = if (singleLine) 1 else 2, maxLines = if (singleLine) 1 else 4,
    )
}

@Composable
private fun <T> CaptureChoice(label: String, selected: T, choices: List<Pair<T, String>>, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.first == selected }?.second.orEmpty()
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.capture_pattern_choice, label, selectedLabel)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (value, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onChange(value) })
            }
        }
    }
}

private val CaptureDirection.labelRes: Int
    get() = when (this) {
        CaptureDirection.KEYWORDS -> R.string.capture_pattern_keywords
        CaptureDirection.EXPENSE -> R.string.capture_pattern_expense
        CaptureDirection.INCOME -> R.string.capture_pattern_income
    }

private val CapturePatternProblem.labelRes: Int
    get() = when (this) {
        CapturePatternProblem.NAME -> R.string.capture_pattern_error_name
        CapturePatternProblem.EXPRESSION -> R.string.capture_pattern_error_expression
        CapturePatternProblem.AMOUNT_GROUP -> R.string.capture_pattern_error_amount
        CapturePatternProblem.DATE_FORMAT -> R.string.capture_pattern_error_date
        CapturePatternProblem.SEPARATORS -> R.string.capture_pattern_error_separators
        CapturePatternProblem.PACKAGE -> R.string.capture_pattern_error_package
    }
