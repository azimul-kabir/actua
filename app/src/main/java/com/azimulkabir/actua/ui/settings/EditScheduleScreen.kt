package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.schedules.*
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaFormTextField
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.components.ActuaHeroAmount
import com.azimulkabir.actua.ui.components.ActuaMenuRow
import com.azimulkabir.actua.ui.components.ActuaPrimaryActionBar
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSecondaryButton
import com.azimulkabir.actua.ui.components.CalculatorAmountSheet
import com.azimulkabir.actua.ui.components.centsToInput
import com.azimulkabir.actua.ui.components.currencyInputPrefix
import com.azimulkabir.actua.ui.components.formatDate
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.Sizes
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.ui.transactions.PickerTextField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScheduleScreen(
    item: ScheduleListItem?,
    accounts: List<Account>,
    payeeOptions: List<String>,
    hideDecimalPlaces: Boolean,
    conventionalAmountEntry: Boolean,
    linkedTransactions: List<ScheduleLinkedTransaction> = emptyList(),
    onBack: () -> Unit,
    onSave: (ScheduleFormFields, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onUnlinkTransaction: ((String) -> Unit)? = null,
    onEditAsRule: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val schedule = item?.schedule
    val originalRecurrence = (schedule?.dateCondition as? ScheduleDateCondition.Recurring)?.config
    val originalDate = (schedule?.dateCondition as? ScheduleDateCondition.Fixed)?.day
        ?: schedule?.nextDate ?: DayDate.today()
    val editorKey = schedule?.id ?: "new"
    var name by remember(editorKey) { mutableStateOf(schedule?.name.orEmpty()) }
    var payeeName by remember(editorKey) { mutableStateOf(item?.payeeName.orEmpty()) }
    var accountName by remember(editorKey) {
        mutableStateOf(item?.accountName ?: accounts.firstOrNull { !it.closed }?.name.orEmpty())
    }
    var income by remember(editorKey) { mutableStateOf((schedule?.postAmount ?: -1L) > 0) }
    var amountOp by remember(editorKey) { mutableStateOf(schedule?.amountOp ?: ScheduleAmountOp.APPROXIMATE) }
    val originalRange = schedule?.amount as? ScheduledAmount.Range
    var amountLow by remember(editorKey) {
        mutableStateOf(minOf(abs(originalRange?.first ?: schedule?.postAmount ?: 0L), abs(originalRange?.second ?: schedule?.postAmount ?: 0L)))
    }
    var amountHigh by remember(editorKey) {
        mutableStateOf(maxOf(abs(originalRange?.first ?: schedule?.postAmount ?: 0L), abs(originalRange?.second ?: schedule?.postAmount ?: 0L)))
    }
    var calculatorTarget by remember { mutableStateOf<Int?>(null) }
    var repeats by remember(editorKey) { mutableStateOf(originalRecurrence != null) }
    var oneOffDate by remember(editorKey) { mutableStateOf(originalDate) }
    var recurrence by remember(editorKey) {
        mutableStateOf(
            originalRecurrence ?: RecurConfig(
                frequency = RecurConfig.Frequency.MONTHLY,
                interval = 1,
                start = schedule?.nextDate ?: DayDate.today(),
            ),
        )
    }
    var datePickerTarget by remember { mutableStateOf<DateTarget?>(null) }
    var showRepeatEditor by remember(editorKey) { mutableStateOf(false) }
    var automaticallyAdd by remember(editorKey) { mutableStateOf(schedule?.postsTransaction ?: false) }
    var upcomingLabel by remember(editorKey) {
        mutableStateOf(upcomingOptions.entries.firstOrNull {
            it.value == schedule?.customUpcomingLength
        }?.key ?: "Budget default")
    }
    var showDelete by remember { mutableStateOf(false) }
    val unreadableDate = schedule?.dateCondition == ScheduleDateCondition.Unsupported && schedule.dateOp != null
    val accountId = accounts.firstOrNull { it.name == accountName && !it.closed }?.id
    val amountValid = amountLow > 0 && (amountOp != ScheduleAmountOp.BETWEEN || amountHigh > 0)
    val canSave = accountId != null && amountValid && !unreadableDate

    fun fields(): ScheduleFormFields {
        val sign = if (income) 1L else -1L
        val amount = if (amountOp == ScheduleAmountOp.BETWEEN) {
            val first = sign * amountLow
            val second = sign * amountHigh
            ScheduledAmount.Range(minOf(first, second), maxOf(first, second))
        } else {
            ScheduledAmount.Fixed(sign * amountLow)
        }
        return ScheduleFormFields(
            name = name,
            accountId = accountId,
            amount = amount,
            amountOp = amountOp,
            date = if (repeats) ScheduleDateCondition.Recurring(recurrence)
                else ScheduleDateCondition.Fixed(oneOffDate),
            postsTransaction = automaticallyAdd,
            customUpcomingLength = upcomingOptions[upcomingLabel],
        )
    }

    if (showRepeatEditor) {
        RepeatEditorScreen(
            recurrence = recurrence,
            onChange = { recurrence = it },
            onBack = { showRepeatEditor = false },
            modifier = modifier,
        )
        return
    }

    val currencyPrefix = currencyInputPrefix()
    val sign = if (income) "+" else "−"
    val kindColor = if (income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val caption = if (income) "Income" else "Expense"
    val shownAmount = "$sign$currencyPrefix${heroAmountText(amountLow)}"

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().imePadding()) {
        ActuaScreenHeader(
            title = if (schedule == null) "New Schedule" else "Edit Schedule",
            onBack = onBack,
        ) {
            if (onDelete != null) {
                IconButton(onClick = { showDelete = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete schedule",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (unreadableDate) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        "This schedule uses a repeat pattern Actua cannot read. Edit it in Actual to avoid replacing that pattern.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            } else if (schedule?.isCustom == true) {
                Text(
                    "Extra rule conditions created in Actual will be preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }

            ActuaHeroAmount(
                amount = shownAmount,
                caption = caption,
                amountColor = if (amountLow == 0L) MaterialTheme.colorScheme.onSurfaceVariant else kindColor,
                captionColor = kindColor,
                supportingText = when (amountOp) {
                    ScheduleAmountOp.EXACT -> "Exact amount"
                    ScheduleAmountOp.APPROXIMATE -> "Approximate amount"
                    ScheduleAmountOp.BETWEEN -> "to $sign$currencyPrefix${heroAmountText(amountHigh)}"
                },
                contentDescription = if (amountOp == ScheduleAmountOp.BETWEEN) {
                    "Amount, $caption between $shownAmount and $sign$currencyPrefix${heroAmountText(amountHigh)}"
                } else "Amount, $caption $shownAmount",
                onClickLabel = "Edit amount",
                onClick = { calculatorTarget = 0 },
                action = {
                    AssistChip(
                        onClick = { income = !income },
                        label = { Text(if (income) "Switch to expense" else "Switch to income") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.SwapVert,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                },
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ScheduleAmountOp.entries.forEachIndexed { index, op ->
                    SegmentedButton(
                        selected = amountOp == op,
                        onClick = { amountOp = op },
                        shape = SegmentedButtonDefaults.itemShape(index, ScheduleAmountOp.entries.size),
                    ) {
                        Text(
                            when (op) {
                                ScheduleAmountOp.EXACT -> "Exact"
                                ScheduleAmountOp.APPROXIMATE -> "Approx."
                                ScheduleAmountOp.BETWEEN -> "Between"
                            },
                        )
                    }
                }
            }
            if (amountOp == ScheduleAmountOp.BETWEEN) {
                ActuaFormCard {
                    ActuaFormRow(
                        icon = Icons.Outlined.Calculate,
                        label = "From",
                        value = "$currencyPrefix${centsToInput(amountLow)}",
                        onClick = { calculatorTarget = 0 },
                    )
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Calculate,
                        label = "To",
                        value = "$currencyPrefix${centsToInput(amountHigh)}",
                        onClick = { calculatorTarget = 1 },
                    )
                }
            }

            ActuaFormCard {
                ActuaFormTextField(
                    icon = Icons.Outlined.Edit,
                    label = "Schedule name",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Optional",
                )
                ActuaCardDivider()
                PickerTextField(
                    label = "Payee",
                    value = payeeName,
                    options = payeeOptions,
                    onValueChange = { payeeName = it },
                    allowCustom = true,
                    rowIcon = Icons.Outlined.Storefront,
                    placeholder = "None",
                )
                ActuaCardDivider()
                PickerTextField(
                    label = "Account",
                    value = accountName,
                    options = accounts.filterNot { it.closed }.map { it.name },
                    onValueChange = { accountName = it },
                    rowIcon = Icons.Outlined.AccountBalanceWallet,
                    placeholder = "Choose an account",
                )
            }

            ActuaFormCard {
                ActuaFormRow(
                    icon = Icons.Outlined.Repeat,
                    label = "Repeats",
                    value = null,
                    checked = repeats,
                    onClick = { repeats = !repeats },
                )
                ActuaCardDivider()
                if (repeats) {
                    ActuaFormRow(
                        icon = Icons.Outlined.EventAvailable,
                        label = "Repeat",
                        value = recurrenceSummary(recurrence),
                        caption = nextDateSummary(recurrence),
                        onClick = { showRepeatEditor = true },
                    )
                } else {
                    ActuaFormRow(
                        icon = Icons.Outlined.CalendarMonth,
                        label = "Date",
                        value = formatDate(oneOffDate.toLocalDate()),
                        onClick = { datePickerTarget = DateTarget.ONE_OFF },
                    )
                }
            }

            ActuaFormCard {
                ActuaFormRow(
                    icon = Icons.Outlined.Bolt,
                    label = "Automatically add transaction",
                    value = null,
                    caption = "Create it when Actua syncs on or after the scheduled date.",
                    checked = automaticallyAdd,
                    onClick = { automaticallyAdd = !automaticallyAdd },
                )
                ActuaCardDivider()
                PickerTextField(
                    label = "Upcoming window",
                    value = upcomingLabel,
                    options = upcomingOptions.keys.toList(),
                    onValueChange = { upcomingLabel = it },
                    rowIcon = Icons.Outlined.DateRange,
                )
            }

            if (schedule != null) {
                ActuaGroupLabel("Linked transactions")
                if (linkedTransactions.isEmpty()) {
                    Text(
                        "No transactions linked yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                } else {
                    ActuaFormCard {
                        linkedTransactions.forEachIndexed { index, transaction ->
                            LinkedTransactionRow(
                                transaction = transaction,
                                hideDecimalPlaces = hideDecimalPlaces,
                                onUnlink = onUnlinkTransaction?.let { unlink ->
                                    { unlink(transaction.id) }
                                },
                            )
                            if (index != linkedTransactions.lastIndex) ActuaCardDivider(inset = Spacing.lg)
                        }
                    }
                }
            }

            val ruleId = schedule?.ruleId
            if (onEditAsRule != null && ruleId != null) {
                if (schedule?.isCustom == true) {
                    Text(
                        "This schedule has custom conditions and actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                }
                ActuaSecondaryButton(text = "Edit as rule", onClick = { onEditAsRule(ruleId) })
            }
        }

        ActuaPrimaryActionBar(
            text = "Save",
            onClick = { onSave(fields(), payeeName) },
            enabled = canSave,
            icon = Icons.Outlined.Check,
        )
    }

    calculatorTarget?.let { target ->
        CalculatorAmountSheet(
            title = if (target == 0) "Schedule amount" else "Upper amount",
            initialCents = if (target == 0) amountLow else amountHigh,
            conventionalAmountEntry = conventionalAmountEntry,
            onDismiss = { calculatorTarget = null },
            onApply = {
                if (target == 0) amountLow = it else amountHigh = it
            },
        )
    }

    datePickerTarget?.let { target ->
        val selected = when (target) {
            DateTarget.ONE_OFF -> oneOffDate
        }
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selected.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val day = DayDate.from(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                        when (target) {
                            DateTarget.ONE_OFF -> oneOffDate = day
                        }
                    }
                    datePickerTarget = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) { Text("Cancel") }
            },
        ) { DatePicker(picker) }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete this schedule?") },
            text = { Text("Transactions this schedule already created will be kept.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    onDelete?.invoke()
                }) { Text("Delete schedule", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LinkedTransactionRow(
    transaction: ScheduleLinkedTransaction,
    hideDecimalPlaces: Boolean,
    onUnlink: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(transaction.payeeName, maxLines = 1)
            Text(
                listOfNotNull(transaction.date?.let { formatDate(it.toLocalDate()) }, transaction.accountName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            formatMoneyCents(transaction.amountCents, hideDecimalPlaces),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (onUnlink != null) {
            TextButton(onClick = onUnlink) { Text("Unlink") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatEditorScreen(
    recurrence: RecurConfig,
    onChange: (RecurConfig) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dateTarget by remember { mutableStateOf<RepeatDateTarget?>(null) }
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Repeat", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ActuaFormCard {
                ActuaMenuRow(
                    icon = Icons.Outlined.Repeat,
                    label = "Frequency",
                    value = recurrence.frequency.name.lowercase().replaceFirstChar(Char::uppercase),
                    choices = RecurConfig.Frequency.entries.map {
                        it.name.lowercase().replaceFirstChar(Char::uppercase) to it
                    },
                ) { frequency ->
                    onChange(recurrence.copy(
                        frequency = frequency,
                        patterns = if (frequency == RecurConfig.Frequency.MONTHLY) {
                            recurrence.patterns
                        } else emptyList(),
                    ))
                }
                ActuaCardDivider()
                StepperRow(
                    icon = Icons.Outlined.History,
                    label = "Every",
                    value = recurrence.interval,
                    valueLabel = intervalLabel(recurrence),
                    range = 1..365,
                ) { onChange(recurrence.copy(interval = it)) }
                ActuaCardDivider()
                ActuaFormRow(
                    icon = Icons.Outlined.CalendarMonth,
                    label = "Starting",
                    value = formatDate(recurrence.start.toLocalDate()),
                    onClick = { dateTarget = RepeatDateTarget.START },
                )
            }

            if (recurrence.frequency == RecurConfig.Frequency.MONTHLY) {
                ActuaGroupLabel("On these days")
                if (recurrence.patterns.isNotEmpty()) {
                    ActuaFormCard {
                        recurrence.patterns.forEachIndexed { index, pattern ->
                            MonthlyPatternRow(
                                pattern = pattern,
                                onChange = { replacement ->
                                    onChange(recurrence.copy(patterns = recurrence.patterns.toMutableList().also {
                                        it[index] = replacement
                                    }))
                                },
                                onDelete = {
                                    onChange(recurrence.copy(patterns = recurrence.patterns.filterIndexed { i, _ -> i != index }))
                                },
                            )
                            if (index != recurrence.patterns.lastIndex) ActuaCardDivider(inset = Spacing.lg)
                        }
                    }
                }
                ActuaSecondaryButton(
                    text = "Add day",
                    icon = Icons.Outlined.AddCircleOutline,
                    onClick = {
                        onChange(recurrence.copy(
                            patterns = recurrence.patterns + RecurConfig.Pattern("day", recurrence.start.day),
                        ))
                    },
                )
                Text(
                    monthlyPatternSummary(recurrence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }

            ActuaGroupLabel("Ends")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                repeatEndOptions.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = recurrence.endMode == option.first,
                        onClick = {
                            onChange(recurrence.copy(
                                endMode = option.first,
                                endOccurrences = if (option.first == "after_n_occurrences") {
                                    recurrence.endOccurrences ?: 1
                                } else null,
                                endDate = if (option.first == "on_date") {
                                    recurrence.endDate ?: recurrence.start
                                } else null,
                            ))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, repeatEndOptions.size),
                    ) { Text(option.second) }
                }
            }
            if (recurrence.endMode == "after_n_occurrences") {
                ActuaFormCard {
                    StepperRow(
                        icon = Icons.Outlined.Tune,
                        label = "Occurrences",
                        value = recurrence.endOccurrences ?: 1,
                        valueLabel = (recurrence.endOccurrences ?: 1).toString(),
                        range = 1..999,
                    ) { onChange(recurrence.copy(endOccurrences = it)) }
                }
            }
            if (recurrence.endMode == "on_date") {
                ActuaFormCard {
                    ActuaFormRow(
                        icon = Icons.Outlined.CalendarMonth,
                        label = "End date",
                        value = formatDate((recurrence.endDate ?: recurrence.start).toLocalDate()),
                        onClick = { dateTarget = RepeatDateTarget.END },
                    )
                }
            }

            ActuaGroupLabel("Weekend handling")
            ActuaFormCard {
                ActuaFormRow(
                    icon = Icons.Outlined.SkipNext,
                    label = "Skip weekends",
                    value = null,
                    caption = "Move weekend occurrences to a weekday.",
                    checked = recurrence.skipWeekend,
                    onClick = { onChange(recurrence.copy(skipWeekend = !recurrence.skipWeekend)) },
                )
                if (recurrence.skipWeekend) {
                    ActuaCardDivider()
                    ActuaMenuRow(
                        icon = Icons.Outlined.SwapHoriz,
                        label = "Move to",
                        value = if (recurrence.weekendSolveMode == "before") "Friday before" else "Monday after",
                        choices = listOf("Friday before" to "before", "Monday after" to "after"),
                    ) { onChange(recurrence.copy(weekendSolveMode = it)) }
                }
            }

            ActuaGroupLabel("Next dates")
            val preview = ScheduleRecurrence.upcomingDates(recurrence, 4, DayDate.today())
            if (preview.isEmpty()) {
                Text(
                    "This pattern has no upcoming dates.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            } else ActuaFormCard {
                preview.forEachIndexed { index, day ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    ) {
                        Text(formatDate(day.toLocalDate()), modifier = Modifier.weight(1f))
                        Text(weekdayNames[day.weekday].orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (index != preview.lastIndex) ActuaCardDivider(inset = Spacing.lg)
                }
            }
        }
    }

    dateTarget?.let { target ->
        val selected = if (target == RepeatDateTarget.START) recurrence.start
            else recurrence.endDate ?: recurrence.start
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selected.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val day = DayDate.from(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                        onChange(if (target == RepeatDateTarget.START) recurrence.copy(start = day)
                            else recurrence.copy(endDate = day))
                    }
                    dateTarget = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { dateTarget = null }) { Text("Cancel") } },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun MonthlyPatternRow(
    pattern: RecurConfig.Pattern,
    onChange: (RecurConfig.Pattern) -> Unit,
    onDelete: () -> Unit,
) {
    val max = if (pattern.type == "day") 31 else 5
    val boundedValue = if (pattern.value == -1) -1 else pattern.value.coerceIn(1, max)
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ChoiceField(
            label = "Which",
            value = ordinal(boundedValue),
            choices = listOf("Last" to -1) + (1..max).map { ordinal(it) to it },
            modifier = Modifier.weight(1f),
        ) { onChange(pattern.copy(value = it)) }
        ChoiceField(
            label = "Day",
            value = patternTypeLabel(pattern.type),
            choices = patternTypes,
            modifier = Modifier.weight(1.35f),
        ) { type ->
            onChange(pattern.copy(type = type, value = if (type != "day" && pattern.value > 5) 5 else pattern.value))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, "Remove pattern")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceField(
    label: String,
    value: String,
    choices: List<Pair<String, T>>,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (text, choice) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { expanded = false; onSelect(choice) },
                )
            }
        }
    }
}

/** A form-card row with − and + buttons that step [value] within [range]. */
@Composable
private fun StepperRow(
    icon: ImageVector,
    label: String,
    value: Int,
    valueLabel: String,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.formRowMinHeight)
            .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = muted)
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
            Text(valueLabel, style = MaterialTheme.typography.bodyLarge)
        }
        FilledTonalIconButton(
            onClick = { onChange(value - 1) },
            enabled = value > range.first,
            modifier = Modifier.semantics { contentDescription = "Decrease $label" },
        ) { Text("−") }
        Text(value.toString(), modifier = Modifier.padding(horizontal = Spacing.md))
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = "Increase $label" },
        ) { Text("+") }
    }
}

private enum class DateTarget { ONE_OFF }
private enum class RepeatDateTarget { START, END }

private val repeatEndOptions = listOf(
    "never" to "Never",
    "after_n_occurrences" to "After",
    "on_date" to "On date",
)

private val patternTypes = listOf(
    "Day" to "day",
    "Sunday" to "SU",
    "Monday" to "MO",
    "Tuesday" to "TU",
    "Wednesday" to "WE",
    "Thursday" to "TH",
    "Friday" to "FR",
    "Saturday" to "SA",
)

private val weekdayNames = mapOf(
    1 to "Sunday", 2 to "Monday", 3 to "Tuesday", 4 to "Wednesday",
    5 to "Thursday", 6 to "Friday", 7 to "Saturday",
)

private fun patternTypeLabel(type: String) = patternTypes.firstOrNull { it.second == type }?.first ?: "Day"

private fun ordinal(value: Int): String {
    if (value == -1) return "Last"
    val suffix = if (value % 100 in 11..13) "th" else when (value % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$value$suffix"
}

private fun intervalLabel(config: RecurConfig): String {
    val unit = when (config.frequency) {
        RecurConfig.Frequency.DAILY -> "day"
        RecurConfig.Frequency.WEEKLY -> "week"
        RecurConfig.Frequency.MONTHLY -> "month"
        RecurConfig.Frequency.YEARLY -> "year"
    }
    return "${config.interval} $unit${if (config.interval == 1) "" else "s"}"
}

private fun recurrenceSummary(config: RecurConfig): String = when {
    config.interval == 1 -> config.frequency.name.lowercase().replaceFirstChar(Char::uppercase)
    else -> "Every ${intervalLabel(config)}"
}

private fun monthlyPatternSummary(config: RecurConfig): String {
    if (config.patterns.isEmpty()) {
        return "Repeats on day ${config.start.day} of the month. Add specific days to repeat more than once."
    }
    return "Repeats on " + config.patterns.joinToString(", ") { pattern ->
        if (pattern.type == "day") "the ${ordinal(pattern.value)} day"
        else "the ${ordinal(pattern.value)} ${patternTypeLabel(pattern.type)}"
    } + "."
}

private fun nextDateSummary(config: RecurConfig): String {
    val next = ScheduleRecurrence.upcomingDates(config, 1, DayDate.today()).firstOrNull()
    return if (next == null) "No upcoming dates" else "Next: ${formatDate(next.toLocalDate())}"
}

private val upcomingOptions = linkedMapOf(
    "Budget default" to null,
    "1 day" to "1",
    "1 week" to "7",
    "2 weeks" to "14",
    "1 month" to "oneMonth",
    "Rest of month" to "currentMonth",
)

private fun DayDate.toLocalDate(): LocalDate = LocalDate.of(year, month, day)

private fun heroAmountText(cents: Long): String = if (cents == 0L) "0" else centsToInput(cents)
