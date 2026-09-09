package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
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
    item: ScheduleListItem,
    accounts: List<Account>,
    payeeOptions: List<String>,
    hideDecimalPlaces: Boolean,
    conventionalAmountEntry: Boolean,
    onBack: () -> Unit,
    onSave: (ScheduleFormFields, String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val schedule = item.schedule
    val originalRecurrence = (schedule.dateCondition as? ScheduleDateCondition.Recurring)?.config
    val originalDate = (schedule.dateCondition as? ScheduleDateCondition.Fixed)?.day
        ?: schedule.nextDate ?: DayDate.today()
    var name by remember(schedule.id) { mutableStateOf(schedule.name.orEmpty()) }
    var payeeName by remember(schedule.id) { mutableStateOf(item.payeeName.orEmpty()) }
    var accountName by remember(schedule.id) { mutableStateOf(item.accountName.orEmpty()) }
    var income by remember(schedule.id) { mutableStateOf(schedule.postAmount > 0) }
    var amountOp by remember(schedule.id) { mutableStateOf(schedule.amountOp) }
    val originalRange = schedule.amount as? ScheduledAmount.Range
    var amountLow by remember(schedule.id) {
        mutableStateOf(minOf(abs(originalRange?.first ?: schedule.postAmount), abs(originalRange?.second ?: schedule.postAmount)))
    }
    var amountHigh by remember(schedule.id) {
        mutableStateOf(maxOf(abs(originalRange?.first ?: schedule.postAmount), abs(originalRange?.second ?: schedule.postAmount)))
    }
    var calculatorTarget by remember { mutableStateOf<Int?>(null) }
    var repeats by remember(schedule.id) { mutableStateOf(originalRecurrence != null) }
    var oneOffDate by remember(schedule.id) { mutableStateOf(originalDate) }
    var recurrence by remember(schedule.id) {
        mutableStateOf(
            originalRecurrence ?: RecurConfig(
                frequency = RecurConfig.Frequency.MONTHLY,
                interval = 1,
                start = schedule.nextDate ?: DayDate.today(),
            ),
        )
    }
    var datePickerTarget by remember { mutableStateOf<DateTarget?>(null) }
    var automaticallyAdd by remember(schedule.id) { mutableStateOf(schedule.postsTransaction) }
    var upcomingLabel by remember(schedule.id) {
        mutableStateOf(upcomingOptions.entries.firstOrNull {
            it.value == schedule.customUpcomingLength
        }?.key ?: "Budget default")
    }
    var showDelete by remember { mutableStateOf(false) }
    val unreadableDate = schedule.dateCondition == ScheduleDateCondition.Unsupported && schedule.dateOp != null
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
                "Edit Schedule",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
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
            } else if (schedule.isCustom) {
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
                RecurrenceFields(
                    recurrence = recurrence,
                    onChange = { recurrence = it },
                    onPickStart = { datePickerTarget = DateTarget.START },
                    onPickEnd = { datePickerTarget = DateTarget.END },
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

            Button(
                enabled = canSave,
                onClick = { onSave(fields(), payeeName) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Check, null)
                Text("Save", Modifier.padding(start = 8.dp))
            }
            TextButton(
                onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete Schedule", color = MaterialTheme.colorScheme.error)
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
            DateTarget.START -> recurrence.start
            DateTarget.END -> recurrence.endDate ?: recurrence.start
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
                            DateTarget.START -> recurrence = recurrence.copy(start = day)
                            DateTarget.END -> recurrence = recurrence.copy(endDate = day)
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
                    onDelete()
                }) { Text("Delete schedule", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RecurrenceFields(
    recurrence: RecurConfig,
    onChange: (RecurConfig) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    Text("Frequency", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RecurConfig.Frequency.entries.forEach { frequency ->
            FilterChip(
                selected = recurrence.frequency == frequency,
                onClick = {
                    onChange(
                        recurrence.copy(
                            frequency = frequency,
                            patterns = if (frequency == RecurConfig.Frequency.MONTHLY) {
                                recurrence.patterns
                            } else emptyList(),
                        ),
                    )
                },
                label = { Text(frequency.name.lowercase().replaceFirstChar(Char::uppercase)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    OutlinedTextField(
        value = recurrence.interval.toString(),
        onValueChange = { value ->
            value.toIntOrNull()?.takeIf { it in 1..365 }?.let {
                onChange(recurrence.copy(interval = it))
            }
        },
        label = { Text("Every") },
        supportingText = {
            Text(recurrence.frequency.name.lowercase().removeSuffix("ly") + "(s)")
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    DateField("Starting", recurrence.start, onPickStart)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Skip weekends", modifier = Modifier.weight(1f))
        Switch(
            recurrence.skipWeekend,
            { onChange(recurrence.copy(skipWeekend = it)) },
        )
    }
    if (recurrence.skipWeekend) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = recurrence.weekendSolveMode == "before",
                onClick = { onChange(recurrence.copy(weekendSolveMode = "before")) },
                label = { Text("Friday before") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = recurrence.weekendSolveMode == "after",
                onClick = { onChange(recurrence.copy(weekendSolveMode = "after")) },
                label = { Text("Monday after") },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Text("Ends", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("never" to "Never", "after_n_occurrences" to "After", "on_date" to "On date")
            .forEach { option ->
                FilterChip(
                    selected = recurrence.endMode == option.first,
                    onClick = {
                        onChange(
                            recurrence.copy(
                                endMode = option.first,
                                endOccurrences = if (option.first == "after_n_occurrences") {
                                    recurrence.endOccurrences ?: 1
                                } else null,
                                endDate = if (option.first == "on_date") {
                                    recurrence.endDate ?: recurrence.start
                                } else null,
                            ),
                        )
                    },
                    label = { Text(option.second) },
                    modifier = Modifier.weight(1f),
                )
            }
    }
    if (recurrence.endMode == "after_n_occurrences") {
        OutlinedTextField(
            value = (recurrence.endOccurrences ?: 1).toString(),
            onValueChange = { value ->
                value.toIntOrNull()?.takeIf { it in 1..999 }?.let {
                    onChange(recurrence.copy(endOccurrences = it))
                }
            },
            label = { Text("Occurrences") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (recurrence.endMode == "on_date") {
        DateField("End date", recurrence.endDate ?: recurrence.start, onPickEnd)
    }
    val preview = ScheduleRecurrence.upcomingDates(recurrence, 4, DayDate.today())
    Text("Next dates", style = MaterialTheme.typography.labelLarge)
    Text(
        if (preview.isEmpty()) "This pattern has no upcoming dates."
        else preview.joinToString("  •  ") { it.iso },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
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

private enum class DateTarget { ONE_OFF, START, END }

private val upcomingOptions = linkedMapOf(
    "Budget default" to null,
    "1 day" to "1",
    "1 week" to "7",
    "2 weeks" to "14",
    "1 month" to "oneMonth",
    "Rest of month" to "currentMonth",
)

private fun DayDate.toLocalDate(): LocalDate = LocalDate.of(year, month, day)
