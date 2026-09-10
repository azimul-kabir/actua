package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.schedules.*
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.ui.components.CalculatorAmountSheet
import com.azimulkabir.actua.ui.components.centsToInput
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
    onBack: () -> Unit,
    onSave: (ScheduleFormFields, String) -> Unit,
    onDelete: (() -> Unit)? = null,
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

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Text(
                if (schedule == null) "New Schedule" else "Edit Schedule",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = canSave,
                onClick = { onSave(fields(), payeeName) },
            ) { Text("Save") }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (unreadableDate) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        "This schedule uses a repeat pattern Actua cannot read. Edit it in Actual to avoid replacing that pattern.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else if (schedule?.isCustom == true) {
                Text(
                    "Extra rule conditions created in Actual will be preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("Details")
            OutlinedTextField(
                name, { name = it }, label = { Text("Schedule name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            PickerTextField(
                label = "Payee (optional)",
                value = payeeName,
                options = payeeOptions,
                onValueChange = { payeeName = it },
                allowCustom = true,
            )
            PickerTextField(
                label = "Account",
                value = accountName,
                options = accounts.filterNot { it.closed }.map { it.name },
                onValueChange = { accountName = it },
            )

            SectionTitle("Amount")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !income,
                    onClick = { income = false },
                    label = { Text("Expense") },
                    leadingIcon = if (!income) {
                        { Icon(Icons.Outlined.Check, null) }
                    } else null,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = income,
                    onClick = { income = true },
                    label = { Text("Income") },
                    leadingIcon = if (income) {
                        { Icon(Icons.Outlined.Check, null) }
                    } else null,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleAmountOp.entries.forEach { op ->
                    FilterChip(
                        selected = amountOp == op,
                        onClick = { amountOp = op },
                        label = {
                            Text(
                                when (op) {
                                    ScheduleAmountOp.EXACT -> "Exact"
                                    ScheduleAmountOp.APPROXIMATE -> "Approx."
                                    ScheduleAmountOp.BETWEEN -> "Between"
                                },
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            AmountField(
                label = if (amountOp == ScheduleAmountOp.BETWEEN) "From" else "Amount",
                cents = amountLow,
            ) { calculatorTarget = 0 }
            if (amountOp == ScheduleAmountOp.BETWEEN) {
                AmountField("To", amountHigh) { calculatorTarget = 1 }
            }

            SectionTitle("Date")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Repeats", modifier = Modifier.weight(1f))
                Switch(repeats, { repeats = it })
            }
            if (repeats) {
                Row(
                    Modifier.fillMaxWidth().clickable { showRepeatEditor = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Repeat", modifier = Modifier.weight(1f))
                    Text(
                        recurrenceSummary(recurrence),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Edit repeat pattern",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    nextDateSummary(recurrence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DateField("Date", oneOffDate) { datePickerTarget = DateTarget.ONE_OFF }
            }

            SectionTitle("Options")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Automatically Add Transaction")
                    Text(
                        "Create it when Actua syncs on or after the scheduled date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(automaticallyAdd, { automaticallyAdd = it })
            }
            PickerTextField(
                label = "Upcoming window",
                value = upcomingLabel,
                options = upcomingOptions.keys.toList(),
                onValueChange = { upcomingLabel = it },
            )

            if (onDelete != null) {
                TextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete Schedule", color = MaterialTheme.colorScheme.error)
                }
            }
        }
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Text(
                "Repeat",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("Repeats")
            ChoiceField(
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
            NumberStepper(
                label = "Every",
                value = recurrence.interval,
                valueLabel = intervalLabel(recurrence),
                range = 1..365,
            ) { onChange(recurrence.copy(interval = it)) }
            DateField("Starting", recurrence.start) { dateTarget = RepeatDateTarget.START }

            if (recurrence.frequency == RecurConfig.Frequency.MONTHLY) {
                SectionTitle("On These Days")
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
                }
                TextButton(onClick = {
                    onChange(recurrence.copy(
                        patterns = recurrence.patterns + RecurConfig.Pattern("day", recurrence.start.day),
                    ))
                }) {
                    Icon(Icons.Outlined.AddCircleOutline, null)
                    Text("Add day", Modifier.padding(start = 8.dp))
                }
                Text(
                    monthlyPatternSummary(recurrence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("Ends")
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
                NumberStepper(
                    label = "Occurrences",
                    value = recurrence.endOccurrences ?: 1,
                    valueLabel = (recurrence.endOccurrences ?: 1).toString(),
                    range = 1..999,
                ) { onChange(recurrence.copy(endOccurrences = it)) }
            }
            if (recurrence.endMode == "on_date") {
                DateField("End date", recurrence.endDate ?: recurrence.start) {
                    dateTarget = RepeatDateTarget.END
                }
            }

            SectionTitle("Weekend Handling")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Skip weekends")
                    Text(
                        "Move weekend occurrences to a weekday.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(recurrence.skipWeekend, {
                    onChange(recurrence.copy(skipWeekend = it))
                })
            }
            if (recurrence.skipWeekend) {
                ChoiceField(
                    label = "Move to",
                    value = if (recurrence.weekendSolveMode == "before") "Friday before" else "Monday after",
                    choices = listOf("Friday before" to "before", "Monday after" to "after"),
                ) { onChange(recurrence.copy(weekendSolveMode = it)) }
            }

            SectionTitle("Next Dates")
            val preview = ScheduleRecurrence.upcomingDates(recurrence, 4, DayDate.today())
            if (preview.isEmpty()) {
                Text("This pattern has no upcoming dates.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else preview.forEach { day ->
                Row(Modifier.fillMaxWidth()) {
                    Text(day.iso, modifier = Modifier.weight(1f))
                    Text(weekdayNames[day.weekday].orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    valueLabel: String,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(valueLabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Text("−") }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 14.dp))
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Text("+") }
    }
}

@Composable
private fun AmountField(label: String, cents: Long, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = centsToInput(cents),
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.Calculate, null) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun DateField(label: String, date: DayDate, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date.iso,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.DateRange, null) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
    )
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
    return if (next == null) "No upcoming dates" else "Next: ${next.iso}"
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
