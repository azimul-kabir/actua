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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
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
import com.azimulkabir.actua.ui.components.withCurrencySymbol
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
    var upcomingValue by remember(editorKey) { mutableStateOf(schedule?.customUpcomingLength) }
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
            customUpcomingLength = upcomingValue,
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

    val currencySymbol = currencyInputPrefix()
    val sign = if (income) "+" else "−"
    val kindColor = if (income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val caption = stringResource(if (income) R.string.fs_income else R.string.fs_expense)
    val shownAmount = sign + withCurrencySymbol(heroAmountText(amountLow), currencySymbol)
    val upperAmount = sign + withCurrencySymbol(heroAmountText(amountHigh), currencySymbol)

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().imePadding()) {
        ActuaScreenHeader(
            title = stringResource(if (schedule == null) R.string.fs_new_schedule else R.string.fs_edit_schedule_title),
            onBack = onBack,
        ) {
            if (onDelete != null) {
                IconButton(onClick = { showDelete = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.fs_delete_schedule),
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
                        stringResource(R.string.fs_unreadable_repeat),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            } else if (schedule?.isCustom == true) {
                Text(
                    stringResource(R.string.fs_custom_rule_preserved),
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
                    ScheduleAmountOp.EXACT -> stringResource(R.string.fs_exact_amount)
                    ScheduleAmountOp.APPROXIMATE -> stringResource(R.string.fs_approximate_amount)
                    ScheduleAmountOp.BETWEEN -> stringResource(R.string.fs_up_to_amount, upperAmount)
                },
                contentDescription = if (amountOp == ScheduleAmountOp.BETWEEN) {
                    stringResource(R.string.fs_amount_between_cd, caption, shownAmount, upperAmount)
                } else stringResource(R.string.fs_amount_cd, caption, shownAmount),
                onClickLabel = stringResource(R.string.fs_edit_amount),
                onClick = { calculatorTarget = 0 },
                action = {
                    AssistChip(
                        onClick = { income = !income },
                        label = { Text(stringResource(if (income) R.string.fs_switch_to_expense else R.string.fs_switch_to_income)) },
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
                                ScheduleAmountOp.EXACT -> stringResource(R.string.fs_exact)
                                ScheduleAmountOp.APPROXIMATE -> stringResource(R.string.fs_approximate)
                                ScheduleAmountOp.BETWEEN -> stringResource(R.string.fs_between)
                            },
                        )
                    }
                }
            }
            if (amountOp == ScheduleAmountOp.BETWEEN) {
                ActuaFormCard {
                    ActuaFormRow(
                        icon = Icons.Outlined.Calculate,
                        label = stringResource(R.string.fs_from),
                        value = withCurrencySymbol(centsToInput(amountLow), currencySymbol),
                        onClick = { calculatorTarget = 0 },
                    )
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Calculate,
                        label = stringResource(R.string.fs_to),
                        value = withCurrencySymbol(centsToInput(amountHigh), currencySymbol),
                        onClick = { calculatorTarget = 1 },
                    )
                }
            }

            ActuaFormCard {
                ActuaFormTextField(
                    icon = Icons.Outlined.Edit,
                    label = stringResource(R.string.fs_schedule_name),
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.fs_optional),
                )
                ActuaCardDivider()
                PickerTextField(
                    label = stringResource(R.string.fs_payee_optional),
                    value = payeeName,
                    options = payeeOptions,
                    onValueChange = { payeeName = it },
                    allowCustom = true,
                    rowIcon = Icons.Outlined.Storefront,
                    placeholder = stringResource(R.string.fs_none),
                )
                ActuaCardDivider()
                PickerTextField(
                    label = stringResource(R.string.fs_account),
                    value = accountName,
                    options = accounts.filterNot { it.closed }.map { it.name },
                    onValueChange = { accountName = it },
                    rowIcon = Icons.Outlined.AccountBalanceWallet,
                    placeholder = stringResource(R.string.fs_choose_account),
                )
            }

            ActuaFormCard {
                ActuaFormRow(
                    icon = Icons.Outlined.Repeat,
                    label = stringResource(R.string.fs_repeats),
                    value = null,
                    checked = repeats,
                    onClick = { repeats = !repeats },
                )
                ActuaCardDivider()
                if (repeats) {
                    ActuaFormRow(
                        icon = Icons.Outlined.EventAvailable,
                        label = stringResource(R.string.fs_repeat),
                        value = recurrenceSummary(recurrence),
                        caption = nextDateSummary(recurrence),
                        onClick = { showRepeatEditor = true },
                    )
                } else {
                    ActuaFormRow(
                        icon = Icons.Outlined.CalendarMonth,
                        label = stringResource(R.string.fs_date),
                        value = formatDate(oneOffDate.toLocalDate()),
                        onClick = { datePickerTarget = DateTarget.ONE_OFF },
                    )
                }
            }

            ActuaFormCard {
                ActuaFormRow(
                    icon = Icons.Outlined.Bolt,
                    label = stringResource(R.string.fs_automatically_add_transaction),
                    value = null,
                    caption = stringResource(R.string.fs_automatically_add_description),
                    checked = automaticallyAdd,
                    onClick = { automaticallyAdd = !automaticallyAdd },
                )
                ActuaCardDivider()
                val upcomingChoices = upcomingOptionValues.map { upcomingWindowLabel(it) to it }
                PickerTextField(
                    label = stringResource(R.string.fs_upcoming_window),
                    value = upcomingChoices.firstOrNull { it.second == upcomingValue }?.first
                        ?: stringResource(R.string.fs_budget_default),
                    options = upcomingChoices.map { it.first },
                    onValueChange = { label ->
                        upcomingValue = upcomingChoices.firstOrNull { it.first == label }?.second
                    },
                    rowIcon = Icons.Outlined.DateRange,
                )
            }

            if (schedule != null) {
                ActuaGroupLabel(stringResource(R.string.fs_linked_transactions))
                if (linkedTransactions.isEmpty()) {
                    Text(
                        stringResource(R.string.fs_no_linked_transactions),
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
                        stringResource(R.string.fs_custom_schedule_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                }
                ActuaSecondaryButton(
                    text = stringResource(R.string.fs_edit_as_rule),
                    onClick = { onEditAsRule(ruleId) },
                )
            }
        }

        ActuaPrimaryActionBar(
            text = stringResource(R.string.fs_save),
            onClick = { onSave(fields(), payeeName) },
            enabled = canSave,
            icon = Icons.Outlined.Check,
        )
    }

    calculatorTarget?.let { target ->
        CalculatorAmountSheet(
            title = stringResource(if (target == 0) R.string.fs_schedule_amount else R.string.fs_upper_amount),
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
                }) { Text(stringResource(R.string.fs_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) { Text(stringResource(R.string.fs_cancel)) }
            },
        ) { DatePicker(picker) }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.fs_delete_schedule_question)) },
            text = { Text(stringResource(R.string.fs_delete_schedule_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    onDelete?.invoke()
                }) { Text(stringResource(R.string.fs_delete_schedule), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.fs_cancel)) }
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
            TextButton(onClick = onUnlink) { Text(stringResource(R.string.fs_unlink)) }
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
        ActuaScreenHeader(title = stringResource(R.string.fs_repeat), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ActuaFormCard {
                ActuaMenuRow(
                    icon = Icons.Outlined.Repeat,
                    label = stringResource(R.string.fs_frequency),
                    value = frequencyLabel(recurrence.frequency),
                    choices = RecurConfig.Frequency.entries.map { frequencyLabel(it) to it },
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
                    label = stringResource(R.string.fs_every),
                    value = recurrence.interval,
                    valueLabel = intervalLabel(recurrence),
                    range = 1..365,
                ) { onChange(recurrence.copy(interval = it)) }
                ActuaCardDivider()
                ActuaFormRow(
                    icon = Icons.Outlined.CalendarMonth,
                    label = stringResource(R.string.fs_starting),
                    value = formatDate(recurrence.start.toLocalDate()),
                    onClick = { dateTarget = RepeatDateTarget.START },
                )
            }

            if (recurrence.frequency == RecurConfig.Frequency.MONTHLY) {
                ActuaGroupLabel(stringResource(R.string.fs_on_these_days))
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
                    text = stringResource(R.string.fs_add_day),
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

            ActuaGroupLabel(stringResource(R.string.fs_ends))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                repeatEndTokens.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = recurrence.endMode == option,
                        onClick = {
                            onChange(recurrence.copy(
                                endMode = option,
                                endOccurrences = if (option == "after_n_occurrences") {
                                    recurrence.endOccurrences ?: 1
                                } else null,
                                endDate = if (option == "on_date") {
                                    recurrence.endDate ?: recurrence.start
                                } else null,
                            ))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, repeatEndTokens.size),
                    ) { Text(repeatEndLabel(option)) }
                }
            }
            if (recurrence.endMode == "after_n_occurrences") {
                ActuaFormCard {
                    StepperRow(
                        icon = Icons.Outlined.Tune,
                        label = stringResource(R.string.fs_occurrences),
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
                        label = stringResource(R.string.fs_end_date),
                        value = formatDate((recurrence.endDate ?: recurrence.start).toLocalDate()),
                        onClick = { dateTarget = RepeatDateTarget.END },
                    )
                }
            }

            ActuaGroupLabel(stringResource(R.string.fs_weekend_handling))
            ActuaFormCard {
                ActuaFormRow(
                    icon = Icons.Outlined.SkipNext,
                    label = stringResource(R.string.fs_skip_weekends),
                    value = null,
                    caption = stringResource(R.string.fs_skip_weekends_description),
                    checked = recurrence.skipWeekend,
                    onClick = { onChange(recurrence.copy(skipWeekend = !recurrence.skipWeekend)) },
                )
                if (recurrence.skipWeekend) {
                    ActuaCardDivider()
                    ActuaMenuRow(
                        icon = Icons.Outlined.SwapHoriz,
                        label = stringResource(R.string.fs_move_to),
                        value = if (recurrence.weekendSolveMode == "before") stringResource(R.string.fs_friday_before)
                        else stringResource(R.string.fs_monday_after),
                        choices = listOf(
                            stringResource(R.string.fs_friday_before) to "before",
                            stringResource(R.string.fs_monday_after) to "after",
                        ),
                    ) { onChange(recurrence.copy(weekendSolveMode = it)) }
                }
            }

            ActuaGroupLabel(stringResource(R.string.fs_next_dates))
            val preview = ScheduleRecurrence.upcomingDates(recurrence, 4, DayDate.today())
            if (preview.isEmpty()) {
                Text(
                    stringResource(R.string.fs_no_upcoming_pattern_dates),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            } else ActuaFormCard {
                preview.forEachIndexed { index, day ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    ) {
                        Text(formatDate(day.toLocalDate()), modifier = Modifier.weight(1f))
                        Text(weekdayName(day.weekday), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                }) { Text(stringResource(R.string.fs_ok)) }
            },
            dismissButton = { TextButton(onClick = { dateTarget = null }) { Text(stringResource(R.string.fs_cancel)) } },
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
            label = stringResource(R.string.fs_which),
            value = ordinalLabel(boundedValue),
            choices = listOf(stringResource(R.string.fs_last) to -1) + (1..max).map { ordinalLabel(it) to it },
            modifier = Modifier.weight(1f),
        ) { onChange(pattern.copy(value = it)) }
        ChoiceField(
            label = stringResource(R.string.fs_day),
            value = patternTypeLabel(pattern.type),
            choices = patternTypeChoices(),
            modifier = Modifier.weight(1.35f),
        ) { type ->
            onChange(pattern.copy(type = type, value = if (type != "day" && pattern.value > 5) 5 else pattern.value))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.fs_remove_pattern))
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
    val decreaseLabel = stringResource(R.string.fs_decrease_step, label)
    val increaseLabel = stringResource(R.string.fs_increase_step, label)
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
            modifier = Modifier.semantics { contentDescription = decreaseLabel },
        ) { Text("−") }
        Text(value.toString(), modifier = Modifier.padding(horizontal = Spacing.md))
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = increaseLabel },
        ) { Text("+") }
    }
}

private enum class DateTarget { ONE_OFF }
private enum class RepeatDateTarget { START, END }

/** Persisted end-mode tokens; labels are resolved per locale by [repeatEndLabel]. */
private val repeatEndTokens = listOf("never", "after_n_occurrences", "on_date")

/** Persisted pattern type tokens (day-of-month or weekday codes); labels via [patternTypeLabel]. */
private val patternTypeTokens = listOf("day", "SU", "MO", "TU", "WE", "TH", "FR", "SA")

@Composable
private fun repeatEndLabel(token: String): String = when (token) {
    "after_n_occurrences" -> stringResource(R.string.fs_after)
    "on_date" -> stringResource(R.string.fs_on_date)
    else -> stringResource(R.string.fs_never)
}

@Composable
internal fun weekdayName(code: String): String = when (code) {
    "SU" -> stringResource(R.string.fs_sunday)
    "MO" -> stringResource(R.string.fs_monday)
    "TU" -> stringResource(R.string.fs_tuesday)
    "WE" -> stringResource(R.string.fs_wednesday)
    "TH" -> stringResource(R.string.fs_thursday)
    "FR" -> stringResource(R.string.fs_friday)
    "SA" -> stringResource(R.string.fs_saturday)
    else -> code
}

@Composable
private fun weekdayName(weekday: Int): String = weekdayName(
    when (weekday) {
        1 -> "SU"; 2 -> "MO"; 3 -> "TU"; 4 -> "WE"; 5 -> "TH"; 6 -> "FR"; else -> "SA"
    },
)

@Composable
private fun patternTypeChoices(): List<Pair<String, String>> =
    patternTypeTokens.map { patternTypeLabel(it) to it }

@Composable
internal fun patternTypeLabel(type: String): String =
    if (type == "day") stringResource(R.string.fs_day) else weekdayName(type)

@Composable
internal fun frequencyLabel(frequency: RecurConfig.Frequency): String = stringResource(
    when (frequency) {
        RecurConfig.Frequency.DAILY -> R.string.fs_frequency_daily
        RecurConfig.Frequency.WEEKLY -> R.string.fs_frequency_weekly
        RecurConfig.Frequency.MONTHLY -> R.string.fs_frequency_monthly
        RecurConfig.Frequency.YEARLY -> R.string.fs_frequency_yearly
    },
)

@Composable
internal fun ordinalLabel(value: Int): String {
    if (value == -1) return stringResource(R.string.fs_last)
    if (value == 1) return stringResource(R.string.fs_ordinal_first)
    return stringResource(
        if (value % 100 in 11..13) R.string.fs_ordinal_other else when (value % 10) {
            1 -> R.string.fs_ordinal_st
            2 -> R.string.fs_ordinal_nd
            3 -> R.string.fs_ordinal_rd
            else -> R.string.fs_ordinal_other
        },
        value,
    )
}

@Composable
internal fun intervalLabel(config: RecurConfig): String = pluralStringResource(
    when (config.frequency) {
        RecurConfig.Frequency.DAILY -> R.plurals.fs_interval_days
        RecurConfig.Frequency.WEEKLY -> R.plurals.fs_interval_weeks
        RecurConfig.Frequency.MONTHLY -> R.plurals.fs_interval_months
        RecurConfig.Frequency.YEARLY -> R.plurals.fs_interval_years
    },
    config.interval,
    config.interval,
)

@Composable
private fun recurrenceSummary(config: RecurConfig): String = when {
    config.interval == 1 -> frequencyLabel(config.frequency)
    else -> stringResource(R.string.fs_every_interval, intervalLabel(config))
}

@Composable
private fun monthlyPatternSummary(config: RecurConfig): String {
    if (config.patterns.isEmpty()) {
        return stringResource(R.string.fs_monthly_default_pattern, config.start.day)
    }
    return stringResource(
        R.string.fs_repeats_on,
        config.patterns.map { pattern ->
            if (pattern.type == "day") stringResource(R.string.fs_pattern_day, ordinalLabel(pattern.value))
            else stringResource(R.string.fs_pattern_weekday, ordinalLabel(pattern.value), patternTypeLabel(pattern.type))
        }.joinToString(", "),
    )
}

@Composable
private fun nextDateSummary(config: RecurConfig): String {
    val next = ScheduleRecurrence.upcomingDates(config, 1, DayDate.today()).firstOrNull()
    return if (next == null) stringResource(R.string.fs_no_upcoming_dates)
    else stringResource(R.string.fs_next_date, formatDate(next.toLocalDate()))
}

/** Persisted upcoming-window tokens (Actual's customUpcomingLength values); null is the budget default. */
private val upcomingOptionValues = listOf(null, "1", "7", "14", "oneMonth", "currentMonth")

@Composable
internal fun upcomingWindowLabel(value: String?): String = when (value) {
    "1" -> stringResource(R.string.fs_one_day)
    "7" -> stringResource(R.string.fs_one_week)
    "14" -> stringResource(R.string.fs_two_weeks)
    "oneMonth" -> stringResource(R.string.fs_one_month)
    "currentMonth" -> stringResource(R.string.fs_rest_of_month)
    else -> stringResource(R.string.fs_budget_default)
}

private fun DayDate.toLocalDate(): LocalDate = LocalDate.of(year, month, day)

private fun heroAmountText(cents: Long): String = if (cents == 0L) "0" else centsToInput(cents)
