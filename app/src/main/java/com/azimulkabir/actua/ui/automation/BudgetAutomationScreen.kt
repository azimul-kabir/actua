package com.azimulkabir.actua.ui.automation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.BudgetAutomationDocument
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetScheduleFunding
import com.azimulkabir.actua.model.BudgetTarget
import com.azimulkabir.actua.ui.components.formatMoneyCents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Percent
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormTextField
import com.azimulkabir.actua.ui.components.ActuaHeroAmount
import com.azimulkabir.actua.ui.components.ActuaMenuRow
import com.azimulkabir.actua.ui.components.ActuaNoteEditorSheet
import com.azimulkabir.actua.ui.components.CalculatorAmountSheet
import com.azimulkabir.actua.ui.components.centsToInput
import com.azimulkabir.actua.ui.components.currencyInputPrefix
import com.azimulkabir.actua.ui.components.parseInputCents
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.theme.Sizes
import com.azimulkabir.actua.ui.theme.Spacing
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Dedicated full-page Budget Automation editor (issue #265).
 *
 * Structure matches upstream Actual Budget's mobile editor exactly: an "Automations" list
 * (the 7 contribution types from [contributionTypes]) plus a separate "Options" section
 * (Balance cap / Long-term goal - see [BudgetTarget.Type.isOption]), each with at most one
 * instance per category ([BudgetTarget.Type.isSingleton]). The underlying model/JSON
 * encoding lives in [BudgetTarget] and is unchanged by this screen.
 */
@Composable
fun BudgetAutomationScreen(
    group: BudgetGroup,
    category: BudgetCategory,
    month: String,
    hideDecimalPlaces: Boolean,
    scheduleFunding: List<BudgetScheduleFunding> = emptyList(),
    incomeCategories: List<String> = emptyList(),
    conventionalAmountEntry: Boolean = false,
    onBack: () -> Unit,
    onSave: (List<BudgetTarget>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (category.hasUnsupportedTarget) {
        UnsupportedAutomationNotice(category = category, onBack = onBack, modifier = modifier)
        return
    }

    var entries by remember(category) { mutableStateOf(category.automations) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var addingType by remember { mutableStateOf<BudgetTarget.Type?>(null) }

    if (editingIndex != null || addingType != null) {
        val index = editingIndex
        val existing = index?.let(entries::get)
        val usedSingletons = entries.withIndex()
            .filter { (i, e) -> i != index && e.type.isSingleton }
            .map { it.value.type }
            .toSet()
        AutomationEntryEditor(
            initial = existing,
            initialType = existing?.type ?: addingType!!,
            month = month,
            hideDecimalPlaces = hideDecimalPlaces,
            scheduleFunding = scheduleFunding,
            incomeCategories = incomeCategories,
            usedSingletonTypes = usedSingletons,
            conventionalAmountEntry = conventionalAmountEntry,
            onBack = { editingIndex = null; addingType = null },
            onDelete = if (existing != null) {
                {
                    entries = entries.toMutableList().also { it.removeAt(requireNotNull(index)) }
                    editingIndex = null; addingType = null
                }
            } else null,
            onSave = { target ->
                entries = when (index) {
                    null -> entries + target
                    else -> entries.toMutableList().also { it[index] = target }
                }
                editingIndex = null; addingType = null
            },
            modifier = modifier,
        )
        return
    }

    val contributionEntries = entries.withIndex().filterNot { it.value.type.isOption }
    val optionEntries = entries.withIndex().filter { it.value.type.isOption }
    val hasLimit = entries.any { it.type == BudgetTarget.Type.LIMIT }
    val hasGoal = entries.any { it.type == BudgetTarget.Type.GOAL }
    val errors = BudgetAutomationDocument.validate(entries)
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Budget Automation", onBack = onBack)
        Text(
            "${group.name} · ${category.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal + Spacing.xs),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionHeader("Automations")
            if (contributionEntries.isEmpty()) EmptyAutomationsNote()
            contributionEntries.forEach { (index, target) ->
                AutomationSummaryCard(target = target, hideDecimalPlaces = hideDecimalPlaces, onClick = { editingIndex = index })
            }
            AddButton(enabled = entries.size < 20, onClick = { addingType = BudgetTarget.Type.FIXED }) { Text("+ Add an automation") }

            SectionHeader("Options", modifier = Modifier.padding(top = Spacing.md))
            optionEntries.forEach { (index, target) ->
                AutomationSummaryCard(target = target, hideDecimalPlaces = hideDecimalPlaces, onClick = { editingIndex = index })
            }
            if (!hasLimit) AddButton(onClick = { addingType = BudgetTarget.Type.LIMIT }) { Text("+ Add balance cap") }
            if (!hasGoal) AddButton(onClick = { addingType = BudgetTarget.Type.GOAL }) { Text("+ Add long-term goal") }

            errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            SaveButton(
                text = if (entries.isEmpty()) "Save (remove automations)" else "Save automations",
                enabled = errors.isEmpty(),
                onClick = { onSave(entries); onBack() },
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EmptyAutomationsNote() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No automations yet", fontWeight = FontWeight.SemiBold)
            Text(
                "Add an automation to have Actua suggest a budgeted amount for this category each month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnsupportedAutomationNotice(category: BudgetCategory, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val types = category.unsupportedAutomationTypes.ifEmpty {
        if (category.automationReadOnly) listOf("notes-managed") else listOf("advanced")
    }.joinToString()
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = category.name, onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("Automations are read-only", style = MaterialTheme.typography.titleMedium)
            Text(
                "This category contains $types automation settings that Actua cannot safely edit yet. Nothing has been changed. Continue managing this category in Actual Budget.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AutomationSummaryCard(target: BudgetTarget, hideDecimalPlaces: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(target.type.label, fontWeight = FontWeight.SemiBold)
                Text(
                    automationSummary(target, hideDecimalPlaces),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (target.type.hasPriority) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        "P${target.priority}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun automationSummary(target: BudgetTarget, hideDecimalPlaces: Boolean): String = when (target.type) {
    BudgetTarget.Type.FIXED -> {
        val cadence = if (target.everyCount > 1) "every ${target.everyCount} ${target.period.jsonValue}s" else "every ${target.period.jsonValue}"
        "${formatMoneyCents(target.amountCents, hideDecimalPlaces)} $cadence"
    }
    BudgetTarget.Type.BY_DATE -> "${formatMoneyCents(target.amountCents, hideDecimalPlaces)} by ${target.targetMonth}"
    BudgetTarget.Type.SCHEDULE -> (target.scheduleName ?: "Linked schedule") + adjustmentSuffix(target)
    BudgetTarget.Type.PERCENTAGE -> "${target.percentage}% of ${if (target.percentagePrevious) "last" else "this"} month's ${target.percentageSource}"
    BudgetTarget.Type.HISTORICAL -> when (target.historicalMode) {
        BudgetTarget.HistoricalMode.AVERAGE -> "Average of ${target.historicalMonths} recent months" + adjustmentSuffix(target)
        BudgetTarget.HistoricalMode.COPY -> "Copy from ${target.historicalMonths} months ago"
    }
    BudgetTarget.Type.REFILL -> "Refills to the balance cap"
    BudgetTarget.Type.REMAINDER -> "Weight ${target.weight}"
    BudgetTarget.Type.LIMIT -> {
        val cadence = (target.limitPeriod ?: BudgetTarget.LimitPeriod.MONTHLY).jsonValue
        "${formatMoneyCents(target.amountCents, hideDecimalPlaces)} · $cadence" +
            if (target.limitHold) " · retain excess" else " · release excess"
    }
    BudgetTarget.Type.GOAL -> formatMoneyCents(target.amountCents, hideDecimalPlaces)
}

/** The 7 automation types offered by "+ Add an automation", in upstream `displayTemplateTypes` order. */
private val contributionTypes = listOf(
    BudgetTarget.Type.FIXED, BudgetTarget.Type.SCHEDULE, BudgetTarget.Type.BY_DATE,
    BudgetTarget.Type.PERCENTAGE, BudgetTarget.Type.HISTORICAL, BudgetTarget.Type.REFILL,
    BudgetTarget.Type.REMAINDER,
)

@Composable
private fun AutomationEntryEditor(
    initial: BudgetTarget?,
    initialType: BudgetTarget.Type,
    month: String,
    hideDecimalPlaces: Boolean,
    scheduleFunding: List<BudgetScheduleFunding>,
    incomeCategories: List<String>,
    usedSingletonTypes: Set<BudgetTarget.Type>,
    conventionalAmountEntry: Boolean,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (BudgetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (initialType) {
        BudgetTarget.Type.LIMIT -> LimitEditor(initial, conventionalAmountEntry, onBack, onDelete, onSave, modifier)
        BudgetTarget.Type.GOAL -> GoalEditor(initial, conventionalAmountEntry, onBack, onDelete, onSave, modifier)
        else -> ContributionEditor(
            initial, initialType, month, hideDecimalPlaces, scheduleFunding, incomeCategories,
            usedSingletonTypes, conventionalAmountEntry, onBack, onDelete, onSave, modifier,
        )
    }
}

@Composable
private fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = title, onBack = onBack) {
            if (onDelete != null) {
                IconButton(onClick = { confirmingDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove automation", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
    if (confirmingDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Remove this automation?") },
            text = { Text("\"$title\" will be removed once you save.") },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

/** The automation's note as a form row that opens the shared note editor sheet. */
@Composable
private fun NoteField(note: String, onChange: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    ActuaFormCard {
        ActuaFormRow(
            icon = Icons.AutoMirrored.Outlined.Notes,
            label = "Note",
            value = note.ifBlank { "Add note" },
            valueIsPlaceholder = note.isBlank(),
            onClick = { editing = true },
        )
    }
    if (editing) {
        ActuaNoteEditorSheet(
            initialNote = note,
            placeholder = "Automation note",
            onDismiss = { editing = false },
            onSave = { onChange(it); editing = false },
        )
    }
}

/** The editor's main amount as a hero that opens the calculator, like Add transaction. */
@Composable
private fun AmountHero(caption: String, cents: Long, conventionalAmountEntry: Boolean, onChange: (Long) -> Unit) {
    var calculatorOpen by remember { mutableStateOf(false) }
    val shown = currencyInputPrefix() + amountText(cents)
    ActuaHeroAmount(
        amount = shown,
        caption = caption,
        amountColor = if (cents == 0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        captionColor = MaterialTheme.colorScheme.onSurfaceVariant,
        contentDescription = "$caption, $shown",
        onClickLabel = "Edit $caption",
        onClick = { calculatorOpen = true },
    )
    if (calculatorOpen) {
        CalculatorAmountSheet(
            title = caption,
            initialCents = cents,
            conventionalAmountEntry = conventionalAmountEntry,
            onDismiss = { calculatorOpen = false },
            onApply = { onChange(kotlin.math.abs(it)) },
        )
    }
}

/** A secondary amount as a form row that opens the calculator. */
@Composable
private fun AmountRow(label: String, cents: Long, conventionalAmountEntry: Boolean, onChange: (Long) -> Unit) {
    var calculatorOpen by remember { mutableStateOf(false) }
    ActuaFormRow(
        icon = Icons.Outlined.Calculate,
        label = label,
        value = currencyInputPrefix() + amountText(cents),
        valueIsPlaceholder = cents == 0L,
        onClick = { calculatorOpen = true },
    )
    if (calculatorOpen) {
        CalculatorAmountSheet(
            title = label,
            initialCents = cents,
            conventionalAmountEntry = conventionalAmountEntry,
            onDismiss = { calculatorOpen = false },
            onApply = { onChange(kotlin.math.abs(it)) },
        )
    }
}

private fun amountText(cents: Long): String = if (cents == 0L) "0" else centsToInput(cents)

@Composable
private fun LimitEditor(
    initial: BudgetTarget?,
    conventionalAmountEntry: Boolean,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (BudgetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amountCents by remember { mutableStateOf(initial?.amountCents ?: 0L) }
    var period by remember { mutableStateOf(initial?.limitPeriod ?: BudgetTarget.LimitPeriod.MONTHLY) }
    var startDate by remember { mutableStateOf(initial?.limitStartDate ?: LocalDate.now().toString()) }
    var hold by remember { mutableStateOf(initial?.limitHold ?: false) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var datePickerOpen by remember { mutableStateOf(false) }
    val validStart = period != BudgetTarget.LimitPeriod.WEEKLY || runCatching { LocalDate.parse(startDate) }.isSuccess
    val canSave = amountCents > 0L && validStart

    EditorScaffold(title = BudgetTarget.Type.LIMIT.label, onBack = onBack, onDelete = onDelete, modifier = modifier) {
        AmountHero("Amount", amountCents, conventionalAmountEntry) { amountCents = it }
        Text(BudgetTarget.Type.LIMIT.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("Configuration")
        Text("Every", style = MaterialTheme.typography.titleSmall)
        SegmentedChoice(
            options = BudgetTarget.LimitPeriod.entries.map { it to it.jsonValue.replaceFirstChar(Char::uppercase) },
            selected = period,
        ) { period = it }
        Text(
            "A weekly or daily cap is scaled by the number of weeks/days in the month.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (period == BudgetTarget.LimitPeriod.WEEKLY) {
            DateField("Weekly start date", startDate) { datePickerOpen = true }
        }
        SwitchCard(
            icon = Icons.Outlined.Savings,
            label = "Retain existing funds over the cap",
            caption = if (hold) "Excess carryover stays in the category." else "Excess carryover is released to Ready to Budget.",
            checked = hold,
        ) { hold = it }
        NoteField(note) { note = it }
        SaveButton(
            text = if (initial == null) "Add balance cap" else "Update balance cap",
            enabled = canSave,
            onClick = {
                onSave(
                    BudgetTarget(
                        BudgetTarget.Type.LIMIT, amountCents = amountCents,
                        limitPeriod = period, limitStartDate = if (period == BudgetTarget.LimitPeriod.WEEKLY) startDate else null,
                        limitHold = hold, note = note.trim().ifBlank { null },
                    ),
                )
            },
        )
        Spacer(Modifier.height(20.dp))
    }
    simpleDatePicker(datePickerOpen, startDate, { datePickerOpen = false }) { startDate = it }
}

@Composable
private fun GoalEditor(
    initial: BudgetTarget?,
    conventionalAmountEntry: Boolean,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (BudgetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amountCents by remember { mutableStateOf(initial?.amountCents ?: 0L) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    EditorScaffold(title = BudgetTarget.Type.GOAL.label, onBack = onBack, onDelete = onDelete, modifier = modifier) {
        AmountHero("Target amount", amountCents, conventionalAmountEntry) { amountCents = it }
        Text(BudgetTarget.Type.GOAL.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NoteField(note) { note = it }
        SaveButton(
            text = if (initial == null) "Add long-term goal" else "Update long-term goal",
            enabled = amountCents > 0L,
            onClick = { onSave(BudgetTarget(BudgetTarget.Type.GOAL, amountCents = amountCents, note = note.trim().ifBlank { null })) },
        )
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContributionEditor(
    initial: BudgetTarget?,
    initialType: BudgetTarget.Type,
    month: String,
    hideDecimalPlaces: Boolean,
    scheduleFunding: List<BudgetScheduleFunding>,
    incomeCategories: List<String>,
    usedSingletonTypes: Set<BudgetTarget.Type>,
    conventionalAmountEntry: Boolean,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (BudgetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by remember { mutableStateOf(initialType) }
    var amountCents by remember { mutableStateOf(initial?.amountCents ?: 0L) }
    var priority by remember { mutableStateOf(initial?.priority?.takeIf { it > 0 } ?: 1) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    // FIXED
    var period by remember { mutableStateOf(initial?.period ?: BudgetTarget.Period.MONTH) }
    var everyCount by remember { mutableStateOf(initial?.everyCount ?: 1) }
    var startingDate by remember { mutableStateOf(initial?.startingDate ?: "$month-01") }

    // BY_DATE
    var targetMonth by remember { mutableStateOf(initial?.targetMonth ?: month) }
    var repeats by remember { mutableStateOf(initial?.repeats ?: false) }
    var repeatEvery by remember { mutableStateOf(initial?.repeatEvery ?: 1) }
    var repeatAnnual by remember { mutableStateOf(initial?.repeatAnnual ?: false) }
    var allowEarlySpending by remember { mutableStateOf(initial?.allowEarlySpending ?: false) }
    var spendFromMonth by remember { mutableStateOf(initial?.spendFromMonth ?: month) }

    // SCHEDULE
    var scheduleId by remember { mutableStateOf(initial?.scheduleId) }
    var scheduleName by remember { mutableStateOf(initial?.scheduleName) }
    var scheduleFull by remember { mutableStateOf(initial?.scheduleFull ?: false) }
    var scheduleMenu by remember { mutableStateOf(false) }
    var savingsModeMenu by remember { mutableStateOf(false) }

    // HISTORICAL
    var historicalMode by remember { mutableStateOf(initial?.historicalMode ?: BudgetTarget.HistoricalMode.AVERAGE) }
    var historicalMonths by remember { mutableStateOf(initial?.historicalMonths ?: 3) }
    var historicalModeMenu by remember { mutableStateOf(false) }

    // PERCENTAGE
    var percentage by remember { mutableStateOf(initial?.percentage ?: 10) }
    var percentageSource by remember { mutableStateOf(initial?.percentageSource ?: "available funds") }
    var percentagePrevious by remember { mutableStateOf(initial?.percentagePrevious ?: false) }
    var percentageOfMenu by remember { mutableStateOf(false) }
    var percentageSourceMenu by remember { mutableStateOf(false) }

    // Adjustment ("increase"/"decrease" modifier) - SCHEDULE and HISTORICAL(AVERAGE) only
    var adjustmentEnabled by remember { mutableStateOf(initial?.adjustmentType != null) }
    var adjustmentType by remember { mutableStateOf(initial?.adjustmentType ?: BudgetTarget.AdjustmentType.PERCENT) }
    var adjustmentIncrease by remember {
        mutableStateOf(
            when (initial?.adjustmentType) {
                BudgetTarget.AdjustmentType.PERCENT -> (initial.adjustmentPercent ?: 0.0) >= 0.0
                BudgetTarget.AdjustmentType.FIXED -> (initial.adjustmentAmountCents ?: 0L) >= 0L
                null -> true
            },
        )
    }
    var adjustmentMagnitude by remember {
        mutableStateOf(
            when (initial?.adjustmentType) {
                BudgetTarget.AdjustmentType.PERCENT -> initial.adjustmentPercent?.let { kotlin.math.abs(it) }
                    ?.let { java.math.BigDecimal(it).stripTrailingZeros().toPlainString() } ?: ""
                BudgetTarget.AdjustmentType.FIXED -> initial.adjustmentAmountCents?.let { plainAmount(kotlin.math.abs(it)) } ?: ""
                null -> ""
            },
        )
    }

    // REMAINDER
    var weight by remember { mutableStateOf(initial?.weight ?: 1) }
    var limitPeriod by remember { mutableStateOf(initial?.limitPeriod) }
    var limitAmountCents by remember { mutableStateOf(initial?.limitAmountCents ?: 0L) }
    var limitStartDate by remember { mutableStateOf(initial?.limitStartDate ?: "$month-01") }
    var limitHold by remember { mutableStateOf(initial?.limitHold ?: false) }

    var datePickerFor by remember { mutableStateOf<DateTarget?>(null) }
    var periodMenu by remember { mutableStateOf(false) }

    val needsStartingDate = type == BudgetTarget.Type.FIXED && (period == BudgetTarget.Period.WEEK || period == BudgetTarget.Period.DAY)
    val validStartingDate = !needsStartingDate || runCatching { LocalDate.parse(startingDate) }.isSuccess
    val validLimitStartDate = limitPeriod != BudgetTarget.LimitPeriod.WEEKLY ||
        runCatching { LocalDate.parse(limitStartDate) }.isSuccess
    val canSave = when (type) {
        BudgetTarget.Type.FIXED -> amountCents > 0L
        BudgetTarget.Type.BY_DATE -> amountCents > 0L &&
            runCatching { java.time.YearMonth.parse(targetMonth.take(7)) }.isSuccess
        BudgetTarget.Type.SCHEDULE -> !scheduleId.isNullOrBlank() || !scheduleName.isNullOrBlank()
        BudgetTarget.Type.PERCENTAGE -> percentage in 1..100
        BudgetTarget.Type.HISTORICAL -> historicalMonths in 1..24
        BudgetTarget.Type.REFILL -> true
        BudgetTarget.Type.REMAINDER -> weight >= 1 &&
            (limitPeriod == null || limitAmountCents > 0L)
        else -> false
    } && validStartingDate && validLimitStartDate

    val adjustmentApplicable = type == BudgetTarget.Type.SCHEDULE ||
        (type == BudgetTarget.Type.HISTORICAL && historicalMode == BudgetTarget.HistoricalMode.AVERAGE)
    val adjustmentMagnitudeValue = adjustmentMagnitude.toDoubleOrNull()?.takeIf { it > 0.0 }
    val adjustmentActive = adjustmentApplicable && adjustmentEnabled && adjustmentMagnitudeValue != null

    fun buildTarget(): BudgetTarget = BudgetTarget(
        type = type,
        amountCents = amountCents,
        priority = priority.coerceAtLeast(1),
        note = note.trim().ifBlank { null },
        period = period, everyCount = everyCount.coerceAtLeast(1),
        startingDate = if (type == BudgetTarget.Type.FIXED) startingDate else null,
        targetMonth = if (type == BudgetTarget.Type.BY_DATE) targetMonth.take(7) else null,
        repeats = repeats, repeatEvery = repeatEvery.coerceAtLeast(1), repeatAnnual = repeatAnnual,
        allowEarlySpending = allowEarlySpending,
        spendFromMonth = if (allowEarlySpending) spendFromMonth.take(7) else null,
        scheduleId = if (type == BudgetTarget.Type.SCHEDULE) scheduleId else null,
        scheduleName = if (type == BudgetTarget.Type.SCHEDULE) scheduleName else null,
        scheduleFull = scheduleFull,
        historicalMode = historicalMode, historicalMonths = historicalMonths.coerceIn(1, 24),
        percentage = percentage.coerceIn(1, 100), percentageSource = percentageSource, percentagePrevious = percentagePrevious,
        adjustmentType = if (adjustmentActive) adjustmentType else null,
        adjustmentPercent = if (adjustmentActive && adjustmentType == BudgetTarget.AdjustmentType.PERCENT) {
            (if (adjustmentIncrease) 1.0 else -1.0) * (adjustmentMagnitudeValue ?: 0.0)
        } else null,
        adjustmentAmountCents = if (adjustmentActive && adjustmentType == BudgetTarget.AdjustmentType.FIXED) {
            val magnitudeCents = runCatching {
                java.math.BigDecimal(adjustmentMagnitude).movePointRight(2).longValueExact()
            }.getOrDefault(0L)
            (if (adjustmentIncrease) 1L else -1L) * magnitudeCents
        } else null,
        weight = weight.coerceAtLeast(1),
        limitPeriod = if (type == BudgetTarget.Type.REMAINDER) limitPeriod else null,
        limitAmountCents = if (type == BudgetTarget.Type.REMAINDER) limitAmountCents.takeIf { it > 0L } else null,
        limitStartDate = if (type == BudgetTarget.Type.REMAINDER && limitPeriod == BudgetTarget.LimitPeriod.WEEKLY) limitStartDate else null,
        limitHold = if (type == BudgetTarget.Type.REMAINDER) limitHold else false,
    )

    EditorScaffold(title = type.label, onBack = onBack, onDelete = onDelete, modifier = modifier) {
        SectionTitle("Automation type")
        ContributionTypeGrid(selected = type, disabled = usedSingletonTypes) { type = it }

        SectionTitle("Configuration")
        when (type) {
            BudgetTarget.Type.FIXED -> {
                AmountHero("Amount", amountCents, conventionalAmountEntry) { amountCents = it }
                NumberStepper("Every", everyCount, 1..365) { everyCount = it }
                Box {
                    ChoiceField("Period", period.jsonValue.replaceFirstChar(Char::uppercase) + "s") { periodMenu = true }
                    DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                        BudgetTarget.Period.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.jsonValue.replaceFirstChar(Char::uppercase) + "s") },
                                onClick = { period = option; periodMenu = false },
                            )
                        }
                    }
                }
                DateField("Starting", startingDate) { datePickerFor = DateTarget.STARTING_DATE }
            }
            BudgetTarget.Type.BY_DATE -> {
                AmountHero("Total amount", amountCents, conventionalAmountEntry) { amountCents = it }
                DateField("Target month", targetMonth.take(7) + "-01") { datePickerFor = DateTarget.TARGET_MONTH }
                SwitchCard(icon = Icons.Outlined.Repeat, label = "Repeats", checked = repeats) { repeats = it }
                if (repeats) {
                    NumberStepper("Repeat every", repeatEvery, 1..50) { repeatEvery = it }
                    SegmentedChoice(
                        options = listOf(false to "Months", true to "Years"),
                        selected = repeatAnnual,
                    ) { repeatAnnual = it }
                }
                SwitchCard(
                    icon = Icons.Outlined.Bolt,
                    label = "Allow early spending",
                    caption = "Spend from the category before the target month without the automation recalculating.",
                    checked = allowEarlySpending,
                ) { allowEarlySpending = it }
                if (allowEarlySpending) {
                    DateField("Start spending in", spendFromMonth.take(7) + "-01") { datePickerFor = DateTarget.SPEND_FROM }
                }
            }
            BudgetTarget.Type.SCHEDULE -> {
                if (scheduleFunding.isEmpty()) {
                    Text(
                        "No schedules found. Create one from Schedules first.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box {
                        ChoiceField("Schedule", scheduleName ?: "Select a schedule") { scheduleMenu = true }
                        DropdownMenu(expanded = scheduleMenu, onDismissRequest = { scheduleMenu = false }) {
                            scheduleFunding.forEach { schedule ->
                                DropdownMenuItem(text = {
                                    Column {
                                        Text(schedule.name ?: "Unnamed schedule")
                                        Text(formatMoneyCents(schedule.amountCents, hideDecimalPlaces),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }, onClick = { scheduleId = schedule.id; scheduleName = schedule.name; scheduleMenu = false })
                            }
                        }
                    }
                }
                Box {
                    ChoiceField(
                        "Savings mode",
                        if (scheduleFull) "Cover each occurrence when it occurs" else "Save for the next occurrence",
                    ) { savingsModeMenu = true }
                    DropdownMenu(expanded = savingsModeMenu, onDismissRequest = { savingsModeMenu = false }) {
                        DropdownMenuItem(text = { Text("Save for the next occurrence") }, onClick = { scheduleFull = false; savingsModeMenu = false })
                        DropdownMenuItem(text = { Text("Cover each occurrence when it occurs") }, onClick = { scheduleFull = true; savingsModeMenu = false })
                    }
                }
            }
            BudgetTarget.Type.PERCENTAGE -> {
                NumberStepper("Percentage", percentage, 1..100) { percentage = it }
                Box {
                    ChoiceField("Percentage of", if (percentagePrevious) "Last month" else "This month") { percentageOfMenu = true }
                    DropdownMenu(expanded = percentageOfMenu, onDismissRequest = { percentageOfMenu = false }) {
                        DropdownMenuItem(text = { Text("This month") }, onClick = { percentagePrevious = false; percentageOfMenu = false })
                        DropdownMenuItem(text = { Text("Last month") }, onClick = { percentagePrevious = true; percentageOfMenu = false })
                    }
                }
                Box {
                    ChoiceField("Income source", percentageSourceLabel(percentageSource)) { percentageSourceMenu = true }
                    DropdownMenu(expanded = percentageSourceMenu, onDismissRequest = { percentageSourceMenu = false }) {
                        (listOf("available funds", "all income") + incomeCategories).distinct().forEach { source ->
                            DropdownMenuItem(
                                text = { Text(percentageSourceLabel(source)) },
                                onClick = { percentageSource = source; percentageSourceMenu = false },
                            )
                        }
                    }
                }
            }
            BudgetTarget.Type.HISTORICAL -> {
                Box {
                    ChoiceField(
                        "Mode",
                        if (historicalMode == BudgetTarget.HistoricalMode.COPY) "Copy a previous month" else "Average of previous months",
                    ) { historicalModeMenu = true }
                    DropdownMenu(expanded = historicalModeMenu, onDismissRequest = { historicalModeMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Average of previous months") },
                            onClick = { historicalMode = BudgetTarget.HistoricalMode.AVERAGE; historicalModeMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy a previous month") },
                            onClick = { historicalMode = BudgetTarget.HistoricalMode.COPY; historicalModeMenu = false },
                        )
                    }
                }
                NumberStepper("Months back", historicalMonths, 1..24) { historicalMonths = it }
            }
            BudgetTarget.Type.REFILL -> {
                if (BudgetTarget.Type.LIMIT !in usedSingletonTypes) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                "Add a balance cap automation to set the refill target.",
                                modifier = Modifier.padding(start = 10.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                } else {
                    Text(
                        "Tops the category back up to its balance cap each month. No amount of its own.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BudgetTarget.Type.REMAINDER -> {
                NumberStepper("Weight", weight, 1..20) { weight = it }
                Text(
                    "Categories with higher weights get a bigger share of the leftover To Budget.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionTitle("Optional cap")
                ActuaFormCard {
                    ActuaMenuRow(
                        icon = Icons.Outlined.Savings,
                        label = "Cap",
                        value = limitPeriod?.jsonValue?.replaceFirstChar(Char::uppercase) ?: "No cap",
                        choices = listOf<Pair<String, BudgetTarget.LimitPeriod?>>("No cap" to null) +
                            BudgetTarget.LimitPeriod.entries.map { it.jsonValue.replaceFirstChar(Char::uppercase) to it },
                    ) { limitPeriod = it }
                    if (limitPeriod != null) {
                        ActuaCardDivider()
                        AmountRow("Limit amount", limitAmountCents, conventionalAmountEntry) { limitAmountCents = it }
                    }
                }
                if (limitPeriod == BudgetTarget.LimitPeriod.WEEKLY) {
                    DateField("Weekly start date", limitStartDate) { datePickerFor = DateTarget.LIMIT_START }
                }
                if (limitPeriod != null) {
                    SwitchCard(icon = Icons.Outlined.Savings, label = "Hold excess carryover", checked = limitHold) { limitHold = it }
                }
            }
            else -> Unit
        }

        if (adjustmentApplicable) {
            SectionTitle("Adjustment")
            SwitchCard(
                icon = Icons.Outlined.Tune,
                label = "Increase or decrease the computed amount",
                caption = "Matches Actual's \"increase\"/\"decrease\" modifier.",
                checked = adjustmentEnabled,
            ) { adjustmentEnabled = it }
            if (adjustmentEnabled) {
                SegmentedChoice(
                    options = listOf(true to "Increase", false to "Decrease"),
                    selected = adjustmentIncrease,
                ) { adjustmentIncrease = it }
                ActuaFormCard {
                    ActuaMenuRow(
                        icon = Icons.Outlined.Tune,
                        label = "Adjust by",
                        value = if (adjustmentType == BudgetTarget.AdjustmentType.PERCENT) "Percent" else "Fixed amount",
                        choices = listOf(
                            "Percent" to BudgetTarget.AdjustmentType.PERCENT,
                            "Fixed amount" to BudgetTarget.AdjustmentType.FIXED,
                        ),
                    ) { adjustmentType = it }
                    ActuaCardDivider()
                    if (adjustmentType == BudgetTarget.AdjustmentType.PERCENT) {
                        ActuaFormTextField(
                            icon = Icons.Outlined.Percent,
                            label = "Percent",
                            value = adjustmentMagnitude,
                            onValueChange = { adjustmentMagnitude = it },
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    } else {
                        AmountRow("Amount", parseInputCents(adjustmentMagnitude) ?: 0L, conventionalAmountEntry) {
                            adjustmentMagnitude = plainAmount(it)
                        }
                    }
                }
            }
        }

        if (type.hasPriority) NumberStepper("Priority", priority, 1..30) { priority = it }
        NoteField(note) { note = it }
        Text(type.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SaveButton(
            text = if (initial == null) "Add automation" else "Update automation",
            enabled = canSave,
            onClick = { onSave(buildTarget()) },
        )
        Spacer(Modifier.height(20.dp))
    }

    datePickerFor?.let { target ->
        val initialDate = when (target) {
            DateTarget.STARTING_DATE -> runCatching { LocalDate.parse(startingDate) }.getOrDefault(LocalDate.now())
            DateTarget.TARGET_MONTH -> runCatching { LocalDate.parse(targetMonth.take(7) + "-01") }.getOrDefault(LocalDate.now())
            DateTarget.SPEND_FROM -> runCatching { LocalDate.parse(spendFromMonth.take(7) + "-01") }.getOrDefault(LocalDate.now())
            DateTarget.LIMIT_START -> runCatching { LocalDate.parse(limitStartDate) }.getOrDefault(LocalDate.now())
        }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        when (target) {
                            DateTarget.STARTING_DATE -> startingDate = picked.toString()
                            DateTarget.TARGET_MONTH -> targetMonth = picked.toString().take(7)
                            DateTarget.SPEND_FROM -> spendFromMonth = picked.toString().take(7)
                            DateTarget.LIMIT_START -> limitStartDate = picked.toString()
                        }
                    }
                    datePickerFor = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePickerFor = null }) { Text("Cancel") } },
        ) { DatePicker(picker) }
    }
}

private enum class DateTarget { STARTING_DATE, TARGET_MONTH, SPEND_FROM, LIMIT_START }

@Composable
private fun ContributionTypeGrid(selected: BudgetTarget.Type, disabled: Set<BudgetTarget.Type>, onSelect: (BudgetTarget.Type) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        contributionTypes.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option ->
                    TypeCard(
                        type = option, selected = option == selected, enabled = option !in disabled,
                        onClick = { onSelect(option) }, modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TypeCard(type: BudgetTarget.Type, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(104.dp),
        shape = MaterialTheme.shapes.large,
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    type.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (selected) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.height(18.dp).width(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (!enabled) "Only one per category" else type.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun AddButton(enabled: Boolean = true, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilledTonalButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(Sizes.secondaryButtonHeight),
        shape = MaterialTheme.shapes.large,
    ) { label() }
}

/** The editor's main action; it stays in the scrolling column after the fields it saves. */
@Composable
private fun SaveButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(Sizes.primaryButtonHeight),
        shape = MaterialTheme.shapes.large,
    ) {
        Icon(Icons.Outlined.Check, contentDescription = null)
        Text(text, modifier = Modifier.padding(start = Spacing.sm))
    }
}

/** A labelled −/+ stepper as a form-card row. */
@Composable
private fun NumberStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    ActuaFormCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Sizes.formRowMinHeight)
                .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = { onChange((value - 1).coerceIn(range)) },
                enabled = value > range.first,
                modifier = Modifier.semantics { contentDescription = "Decrease $label" },
            ) { Text("−") }
            Text(value.toString(), modifier = Modifier.padding(horizontal = 14.dp))
            FilledTonalIconButton(
                onClick = { onChange((value + 1).coerceIn(range)) },
                enabled = value < range.last,
                modifier = Modifier.semantics { contentDescription = "Increase $label" },
            ) { Text("+") }
        }
    }
}

@Composable
private fun ChoiceField(label: String, value: String, onClick: () -> Unit) {
    ActuaFormCard {
        ActuaFormRow(
            icon = Icons.Outlined.Tune,
            label = label,
            value = value,
            trailing = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose $label") },
            onClick = onClick,
        )
    }
}

@Composable
private fun DateField(label: String, isoDate: String, onClick: () -> Unit) {
    ActuaFormCard {
        ActuaFormRow(
            icon = Icons.Outlined.CalendarMonth,
            label = label,
            value = isoDate,
            trailing = { Icon(Icons.Outlined.DateRange, contentDescription = "Choose $label") },
            onClick = onClick,
        )
    }
}

/** A switch row with an optional [caption], in its own card. */
@Composable
private fun SwitchCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    caption: String? = null,
    onChange: (Boolean) -> Unit,
) {
    ActuaFormCard {
        ActuaFormRow(icon = icon, label = label, value = null, caption = caption, checked = checked,
            onClick = { onChange(!checked) })
    }
}

/** One choice out of [options] (value to label) as full-width segmented buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs),
    )
}

@Composable
private fun SectionHeader(value: String, modifier: Modifier = Modifier) {
    Text(
        value, modifier = modifier.padding(start = Spacing.xs, top = Spacing.xs),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun simpleDatePicker(open: Boolean, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    if (!open) return
    val initialDate = runCatching { LocalDate.parse(current) }.getOrDefault(LocalDate.now())
    val picker = rememberDatePickerState(initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { millis ->
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(picker) }
}

private fun plainAmount(cents: Long): String = java.math.BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()

private fun adjustmentSuffix(target: BudgetTarget): String = when (target.adjustmentType) {
    BudgetTarget.AdjustmentType.PERCENT -> target.adjustmentPercent?.let {
        " · ${if (it >= 0) "+" else ""}${java.math.BigDecimal(it).stripTrailingZeros().toPlainString()}%"
    } ?: ""
    BudgetTarget.AdjustmentType.FIXED -> target.adjustmentAmountCents?.let {
        " · ${if (it >= 0) "+" else ""}${plainAmount(it)}"
    } ?: ""
    null -> ""
}

private fun percentageSourceLabel(source: String): String = when (source.lowercase()) {
    "available funds" -> "Available funds"
    "all income" -> "All income"
    else -> source
}
