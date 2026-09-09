package com.azimulkabir.actua.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.SplitLine
import com.azimulkabir.actua.model.Type
import com.azimulkabir.actua.ui.components.centsToInput
import com.azimulkabir.actua.ui.components.CalculatorAmountSheet
import com.azimulkabir.actua.ui.components.formatDate
import com.azimulkabir.actua.ui.components.parseStoredDate
import com.azimulkabir.actua.ui.components.storageDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    editing: Transaction?,
    onBack: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit = {},
    modifier: Modifier = Modifier,
    accountOptions: List<String> = listOf("Everyday account", "Cash", "Credit card"),
    offBudgetAccountOptions: Set<String> = emptySet(),
    categoryOptions: List<String> = listOf("Groceries", "Dining", "Transport", "Rent"),
    payeeOptions: List<String> = emptyList(),
    accountBalanceLabels: Map<String, String> = emptyMap(),
    defaultAccount: String? = null,
    defaultCategory: String? = null,
    hideDecimalPlaces: Boolean = false,
    conventionalAmountEntry: Boolean = false,
    onResolveRuleCategory: (Transaction) -> String? = { null },
) {
    var amountCents by remember(editing) { mutableStateOf(abs(editing?.amountCents ?: 0L)) }
    var showCalculator by remember { mutableStateOf(false) }
    var amountExpression by remember(editing) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(editing) { mutableStateOf(false) }
    var payee by remember(editing) { mutableStateOf(editing?.payee ?: "") }
    var category by remember(editing, defaultCategory) {
        mutableStateOf(editing?.category ?: defaultCategory?.takeIf(categoryOptions::contains).orEmpty())
    }
    var account by remember(editing, accountOptions) {
        mutableStateOf(
            if (editing?.type == Type.TRANSFER && editing.amountCents >= 0) {
                editing.transferAccount ?: editing.account
            } else editing?.account ?: defaultAccount?.takeIf(accountOptions::contains)
                ?: accountOptions.firstOrNull().orEmpty()
        )
    }
    var transferAccount by remember(editing) {
        mutableStateOf(
            if (editing?.type == Type.TRANSFER && editing.amountCents >= 0) editing.account
            else editing?.transferAccount.orEmpty()
        )
    }
    var date by remember(editing) { mutableStateOf(editing?.date?.let(::parseStoredDate) ?: LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var notes by remember(editing) { mutableStateOf(editing?.notes ?: "") }
    var cleared by remember(editing) { mutableStateOf(editing?.cleared ?: false) }
    var transactionType by remember(editing) {
        mutableStateOf((editing?.type ?: Type.EXPENSE).displayName)
    }
    var splitLines by remember(editing) { mutableStateOf(editing?.splits.orEmpty()) }
    var splitCalculatorIndex by remember { mutableStateOf<Int?>(null) }
    var splitAmountExpression by remember(editing) { mutableStateOf<String?>(null) }
    val isOffBudget = account in offBudgetAccountOptions
    LaunchedEffect(isOffBudget) {
        if (isOffBudget) {
            category = ""
            splitLines = splitLines.map { it.copy(category = "") }
        }
    }
    val isSplit = splitLines.isNotEmpty()
    val splitTotal = splitLines.sumOf { if (it.isOpposite) -it.amountCents else it.amountCents }
    val splitIsValid = !isSplit || (splitLines.size >= 2 && splitLines.all {
        (isOffBudget || it.category.isNotBlank()) && it.amountCents > 0
    } && splitTotal == amountCents)
    val canSave = amountCents > 0 && account.isNotBlank() &&
        (transactionType != Type.TRANSFER.displayName || transferAccount.isNotBlank()) && splitIsValid
    val cursorTransition = rememberInfiniteTransition(label = "Amount cursor")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(520), repeatMode = RepeatMode.Reverse),
        label = "Amount cursor alpha",
    )
    val blinkingCursor = if (cursorAlpha > 0.5f) " │" else ""
    val saveTransaction = {
        if (canSave) {
            onSave(
                Transaction(
                    id = editing?.id.orEmpty(),
                    date = storageDate(date),
                    payee = payee,
                    category = if (transactionType == "Transfer" || isOffBudget) ""
                    else category.ifBlank { "Uncategorized" },
                    account = account,
                    amount = (amountCents / 100L).toInt() * if (transactionType == "Income") 1 else -1,
                    cleared = cleared,
                    amountCents = amountCents * if (transactionType == "Income") 1 else -1,
                    type = Type.entries.first { it.displayName == transactionType },
                    transferAccount = transferAccount.takeIf { transactionType == "Transfer" },
                    notes = notes,
                    splits = if (isOffBudget) splitLines.map { it.copy(category = "") } else splitLines,
                ),
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.Close, contentDescription = "Cancel")
            }
            Text(if (editing == null) "Add transaction" else "Edit transaction",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            if (editing != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete transaction",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Type.entries.forEach { type ->
                    FilterChip(
                        selected = transactionType == type.displayName,
                        onClick = {
                            transactionType = type.displayName
                            if (type == Type.TRANSFER) splitLines = emptyList()
                        },
                        label = {
                            Text(
                                type.displayName,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = "৳${when {
                        showCalculator && amountExpression != null -> amountExpression.orEmpty()
                        showCalculator && amountCents == 0L -> ""
                        else -> centsToInput(amountCents)
                    }}" +
                        if (showCalculator) blinkingCursor else "",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Amount") }, singleLine = true,
                    trailingIcon = { Icon(Icons.Outlined.Calculate, contentDescription = null) },
                    supportingText = { if (hideDecimalPlaces) Text("Decimal places are hidden in lists") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    Modifier.matchParentSize().pointerInput(Unit) {
                        detectTapGestures {
                            amountExpression = null
                            showCalculator = true
                        }
                    },
                )
            }
            if (transactionType != Type.TRANSFER.displayName) {
                PickerTextField(
                    label = "Payee", value = payee, options = payeeOptions,
                    supportingValues = accountBalanceLabels.mapKeys { "Transfer: ${it.key}" },
                    onValueChange = { value ->
                        val transferTarget = value.takeIf { it.startsWith("Transfer: ") }
                            ?.removePrefix("Transfer: ")?.takeIf(accountOptions::contains)
                        if (transferTarget != null) {
                            if (transferTarget != account) {
                                payee = ""
                                transferAccount = transferTarget
                                transactionType = Type.TRANSFER.displayName
                                category = ""
                                splitLines = emptyList()
                            }
                            return@PickerTextField
                        }
                        payee = value
                        if (!isOffBudget) onResolveRuleCategory(
                            Transaction(
                                id = "",
                                account = account,
                                payee = value,
                                category = category,
                                amount = (amountCents / 100).toInt(),
                                amountCents = amountCents,
                                type = Type.entries.first { it.displayName == transactionType },
                                date = storageDate(date),
                                notes = notes,
                                cleared = cleared,
                            ),
                        )?.let { category = it }
                    }, allowCustom = true,
                )
            }
            if (transactionType != Type.TRANSFER.displayName && !isSplit && !isOffBudget) {
                PickerTextField(
                    label = "Category", value = category, options = categoryOptions,
                    onValueChange = { category = it },
                )
            }
            PickerTextField(
                label = if (transactionType == Type.TRANSFER.displayName) "From" else "Account",
                value = account, options = accountOptions,
                supportingValues = accountBalanceLabels,
                onValueChange = {
                    account = it
                    if (it in offBudgetAccountOptions) {
                        category = ""
                        splitLines = splitLines.map { line -> line.copy(category = "") }
                    }
                    if (transferAccount == it) transferAccount = ""
                },
            )
            if (transactionType == Type.TRANSFER.displayName) {
                PickerTextField(
                    label = "To", value = transferAccount,
                    options = accountOptions.filterNot { it == account },
                    supportingValues = accountBalanceLabels,
                    onValueChange = { transferAccount = it },
                )
            } else {
                if (!isSplit) {
                    FilledTonalButton(
                        onClick = {
                            splitLines = if (editing == null) listOf(SplitLine(), SplitLine())
                            else listOf(
                                SplitLine(category = category, amountCents = amountCents), SplitLine(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(if (isOffBudget) "Split transaction" else "Split into multiple categories") }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (isOffBudget) "Split transaction" else "Split categories",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                category = if (isOffBudget) "" else splitLines.firstOrNull()?.category.orEmpty()
                                splitLines = emptyList()
                            }) { Text("Remove split") }
                        }
                        splitLines.forEachIndexed { index, line ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Split ${index + 1}", style = MaterialTheme.typography.labelLarge)
                                if (!isOffBudget) PickerTextField(
                                    label = "Category",
                                    value = line.category,
                                    options = categoryOptions,
                                    onValueChange = { value ->
                                        splitLines = splitLines.toMutableList().also {
                                            it[index] = line.copy(category = value)
                                        }
                                    },
                                )
                                Box(Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = "৳${when {
                                            splitCalculatorIndex == index && splitAmountExpression != null ->
                                                splitAmountExpression.orEmpty()
                                            splitCalculatorIndex == index && line.amountCents == 0L -> ""
                                            else -> centsToInput(line.amountCents)
                                        }}" +
                                            if (splitCalculatorIndex == index) blinkingCursor else "",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Amount") },
                                        singleLine = true,
                                        trailingIcon = { Icon(Icons.Outlined.Calculate, contentDescription = null) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Box(Modifier.matchParentSize().clickable {
                                        splitAmountExpression = null
                                        splitCalculatorIndex = index
                                    })
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        if (line.isOpposite) "Opposite direction" else "Same direction",
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = line.isOpposite,
                                        onCheckedChange = { value ->
                                            splitLines = splitLines.toMutableList().also {
                                                it[index] = line.copy(isOpposite = value)
                                            }
                                        },
                                    )
                                }
                                if (line.amountCents == 0L && amountCents - splitTotal > 0) {
                                    TextButton(onClick = {
                                        splitLines = splitLines.toMutableList().also {
                                            it[index] = line.copy(amountCents = amountCents - splitTotal)
                                        }
                                    }) { Text("Use remaining ৳${centsToInput(amountCents - splitTotal)}") }
                                }
                                PickerTextField(
                                    label = "Payee (optional)",
                                    value = line.payee,
                                    options = payeeOptions,
                                    supportingValues = accountBalanceLabels.mapKeys { "Transfer: ${it.key}" },
                                    onValueChange = { value ->
                                        splitLines = splitLines.toMutableList().also {
                                            it[index] = line.copy(payee = value)
                                        }
                                    },
                                    allowCustom = true,
                                )
                                OutlinedTextField(
                                    value = line.notes,
                                    onValueChange = { value ->
                                        splitLines = splitLines.toMutableList().also {
                                            it[index] = line.copy(notes = value)
                                        }
                                    },
                                    label = { Text("Split note") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (splitLines.size > 2) {
                                    TextButton(onClick = {
                                        splitLines = splitLines.toMutableList().also { it.removeAt(index) }
                                    }) { Text("Remove this split", color = MaterialTheme.colorScheme.error) }
                                }
                                if (index < splitLines.lastIndex) HorizontalDivider()
                            }
                        }
                        FilledTonalButton(
                            onClick = { splitLines = splitLines + SplitLine() },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Add another split") }
                        Text(
                            if (splitTotal == amountCents) "Split total matches the transaction amount"
                            else "Remaining: ৳${centsToInput(amountCents - splitTotal)}",
                            color = if (splitTotal == amountCents) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formatDate(date), onValueChange = {}, readOnly = true,
                    label = { Text("Date") }, singleLine = true,
                    trailingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.matchParentSize().clickable { showDatePicker = true })
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Cleared", modifier = Modifier.weight(1f))
                Switch(checked = cleared, onCheckedChange = { cleared = it })
            }
            TransactionSaveButton(
                canSave = canSave,
                onClick = saveTransaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        }
    }
    if (showCalculator) CalculatorAmountSheet(
        title = if (editing == null) "Transaction amount" else "Edit amount",
        initialCents = amountCents,
        conventionalAmountEntry = conventionalAmountEntry,
        onDismiss = {
            showCalculator = false
            amountExpression = null
        },
        onApply = { amountCents = it },
        onExpressionChange = { amountExpression = it },
    )
    splitCalculatorIndex?.let { index ->
        val line = splitLines.getOrNull(index)
        if (line != null) CalculatorAmountSheet(
            title = "Split ${index + 1} amount",
            initialCents = line.amountCents,
            conventionalAmountEntry = conventionalAmountEntry,
            onDismiss = {
                splitCalculatorIndex = null
                splitAmountExpression = null
            },
            onApply = { value ->
                splitLines = splitLines.toMutableList().also { it[index] = line.copy(amountCents = value) }
            },
            onExpressionChange = { splitAmountExpression = it },
        ) else splitCalculatorIndex = null
    }
    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = { Text("This transaction will be removed from your budget.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(editing)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

private val Type.displayName: String get() = name.lowercase().replaceFirstChar(Char::uppercase)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionSaveButton(
    canSave: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = canSave,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(Icons.Outlined.Check, contentDescription = null)
        Text("Save", modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun PickerTextField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    allowCustom: Boolean = false,
    supportingValues: Map<String, String> = emptyMap(),
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            singleLine = true,
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { showPicker = true })
    }
    if (showPicker) SearchableTransactionPicker(
        title = label.removeSuffix(" (optional)"),
        selected = value,
        options = options,
        allowCustom = allowCustom,
        supportingValues = supportingValues,
        onDismiss = { showPicker = false },
        onSelect = {
            onValueChange(it)
            showPicker = false
        },
    )
}

@Composable
private fun SearchableTransactionPicker(
    title: String,
    selected: String,
    options: List<String>,
    allowCustom: Boolean,
    supportingValues: Map<String, String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val uniqueOptions = remember(options) { options.distinct() }
    val searchResults = remember(query, uniqueOptions) { filterPickerOptions(uniqueOptions, query) }
    val transferOptions = alphabetizePickerOptions(
        uniqueOptions.filter { it.startsWith("Transfer: ") },
    )
    val regularOptions = uniqueOptions.filterNot { it.startsWith("Transfer: ") }
    val grouped = regularOptions
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .groupBy { it.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetterOrDigit)?.toString() ?: "#" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(if (allowCustom) "Find or add a ${title.lowercase()}" else "Search ${title.lowercase()}")
                    },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp,
                    ),
                ) {
                    if (selected.isNotBlank() && query.isBlank() && selected in uniqueOptions) {
                        item { PickerSectionLabel("Selected") }
                        item {
                            PickerGroup(
                                listOf(selected), selected, supportingValues = supportingValues,
                                onSelect = onSelect,
                            )
                        }
                    }
                    if (query.isNotBlank() && searchResults.isNotEmpty()) {
                        item { PickerSectionLabel("Search results") }
                        item {
                            PickerGroup(
                                options = searchResults,
                                selected = selected,
                                displayText = { it.removePrefix("Transfer: ") },
                                supportingValues = supportingValues,
                                onSelect = onSelect,
                            )
                        }
                    }
                    if (query.isBlank() && transferOptions.isNotEmpty()) {
                        item { PickerSectionLabel("Payments and transfers") }
                        item {
                            PickerGroup(
                                options = transferOptions,
                                selected = selected,
                                displayText = { it.removePrefix("Transfer: ") },
                                supportingValues = supportingValues,
                                onSelect = onSelect,
                            )
                        }
                    }
                    if (allowCustom && query.isNotBlank() && uniqueOptions.none {
                            it.equals(query.trim(), ignoreCase = true)
                        }
                    ) {
                        item { PickerSectionLabel("New ${title.lowercase()}") }
                        item {
                            PickerGroup(
                                options = listOf(query.trim()),
                                selected = "",
                                displayText = { "Add “$it”" },
                                onSelect = onSelect,
                            )
                        }
                    }
                    if (query.isBlank()) {
                        grouped.forEach { (letter, entries) ->
                            item(key = "heading-$letter") { PickerSectionLabel(letter) }
                            item(key = "group-$letter") {
                                PickerGroup(
                                    entries, selected, supportingValues = supportingValues,
                                    onSelect = onSelect,
                                )
                            }
                        }
                    }
                    if (query.isNotBlank() && searchResults.isEmpty() && !allowCustom) {
                        item {
                            Text(
                                "No matches",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun filterPickerOptions(options: List<String>, query: String): List<String> {
    val term = query.trim()
    if (term.isEmpty()) return alphabetizePickerOptions(options)
    return alphabetizePickerOptions(
        options.filter { it.removePrefix("Transfer: ").contains(term, ignoreCase = true) },
    )
}

internal fun alphabetizePickerOptions(options: List<String>): List<String> = options.distinct()
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.removePrefix("Transfer: ") })

@Composable
private fun PickerSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp, start = 4.dp),
    )
}

@Composable
private fun PickerGroup(
    options: List<String>,
    selected: String,
    displayText: (String) -> String = { it },
    supportingValues: Map<String, String> = emptyMap(),
    onSelect: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            options.forEachIndexed { index, option ->
                PickerRow(
                    text = displayText(option),
                    supportingText = supportingValues[option],
                    selected = option == selected,
                ) { onSelect(option) }
                if (index < options.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    text: String,
    supportingText: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        supportingText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
