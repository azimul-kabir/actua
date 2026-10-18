package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleChoice
import com.azimulkabir.actua.data.rules.RuleEditorData
import com.azimulkabir.actua.data.rules.RuleFieldType
import com.azimulkabir.actua.data.rules.RuleSchema
import com.azimulkabir.actua.data.rules.RuleValue
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaFormTextField
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.components.ActuaMenuRow
import com.azimulkabir.actua.ui.components.ActuaPrimaryActionBar
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSecondaryButton
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.minorUnitsPerWhole
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.ui.theme.Spacing
import java.time.LocalDate

@Composable
fun RulesScreen(
    rules: List<Rule>,
    supported: Boolean,
    scheduleOwnedRuleIds: Set<String>,
    editorData: RuleEditorData,
    onBack: () -> Unit,
    onSave: (Rule, onSaved: () -> Unit) -> Unit,
    onDelete: (String, onDeleted: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    initialRuleId: String? = null,
    onInitialRuleClosed: () -> Unit = {},
    onApplyRule: ((Rule, onApplied: (count: Int) -> Unit) -> Unit)? = null,
    onPreviewRuleMatches: ((Rule) -> List<ActualTransaction>)? = null,
) {
    var search by remember { mutableStateOf("") }
    var editing by remember(initialRuleId) {
        mutableStateOf(initialRuleId?.let { id -> rules.firstOrNull { it.id == id } })
    }
    var ruleToApply by remember { mutableStateOf<Rule?>(null) }
    var applyResultCount by remember { mutableStateOf<Int?>(null) }

    // Opened straight into one rule (e.g. a schedule's "Edit as rule"): closing it returns to the caller.
    fun closeEditor() {
        val closedInitial = initialRuleId != null && editing?.id == initialRuleId
        editing = null
        if (closedInitial) onInitialRuleClosed()
    }

    ruleToApply?.let { rule ->
        val matches = remember(rule) { onPreviewRuleMatches?.invoke(rule) ?: emptyList() }
        val reconciledCount = remember(matches) { matches.count { it.reconciled } }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ruleToApply = null },
            title = { Text("Apply rule now?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (matches.isEmpty()) {
                        Text("No existing transactions match the conditions of this rule.")
                    } else {
                        Text("This will apply the rule actions to ${matches.size} matching transaction${if (matches.size == 1) "" else "s"}.")
                        if (reconciledCount > 0) {
                            Text(
                                "Warning: $reconciledCount reconciled transaction${if (reconciledCount == 1) "" else "s"} will be modified. Reconciled transactions are normally locked.",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (matches.isNotEmpty()) {
                    TextButton(onClick = {
                        val currentRule = rule
                        ruleToApply = null
                        onApplyRule?.invoke(currentRule) { count ->
                            applyResultCount = count
                        }
                    }) {
                        Text("Apply")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToApply = null }) {
                    Text(if (matches.isEmpty()) "Close" else "Cancel")
                }
            },
        )
    }

    applyResultCount?.let { count ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { applyResultCount = null },
            title = { Text("Rule applied") },
            text = { Text("Applied rule to $count transaction${if (count == 1) "" else "s"}.") },
            confirmButton = {
                TextButton(onClick = { applyResultCount = null }) { Text("OK") }
            },
        )
    }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Rules", onBack = onBack) {
            if (supported) IconButton(onClick = { editing = Rule.empty() }) { Icon(Icons.Outlined.Add, "Add rule") }
        }
        if (!supported) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("Rules unavailable", style = MaterialTheme.typography.titleMedium)
                Text("This budget does not contain Actual's rules table.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        OutlinedTextField(search, { search = it }, label = { Text("Search rules") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        val names = editorData.names
        val filtered = rules.filter { ruleSummary(it, names).contains(search, true) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            filtered.forEach { rule ->
                Surface(onClick = { editing = rule }, color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rule.stage.name, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                if (rule.id in scheduleOwnedRuleIds) Text("  •  SCHEDULE", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (onApplyRule != null) {
                                TextButton(onClick = { ruleToApply = rule }) {
                                    Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                    Text("Apply now")
                                }
                            }
                        }
                        Text("IF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        rule.conditions.forEachIndexed { index, condition ->
                            Text((if (index > 0) "${rule.conditionsOp.name.lowercase()} " else "") + conditionSummary(condition, names),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("THEN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        rule.actions.forEach { Text(actionSummary(it, names), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (filtered.isEmpty()) Text(if (search.isBlank()) "No rules yet" else "No matching rules",
                modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    }
    editing?.let { rule ->
        RuleEditor(
            rule = rule,
            data = editorData,
            scheduleOwned = rule.id in scheduleOwnedRuleIds,
            onDismiss = ::closeEditor,
            onSave = { onSave(it, ::closeEditor) },
            onDelete = { onDelete(rule.id, ::closeEditor) },
            onApply = if (onApplyRule != null) { { ruleToApply = it } } else null,
            onPreviewMatches = onPreviewRuleMatches,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RuleEditor(
    rule: Rule,
    data: RuleEditorData,
    scheduleOwned: Boolean,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit,
    onDelete: () -> Unit,
    onApply: ((Rule) -> Unit)? = null,
    onPreviewMatches: ((Rule) -> List<ActualTransaction>)? = null,
) {
    var draft by remember(rule.id) { mutableStateOf(rule) }
    var showPreviewList by remember { mutableStateOf(false) }
    val previewMatches = remember(draft.conditions, draft.conditionsOp) {
        if (draft.conditions.isNotEmpty()) onPreviewMatches?.invoke(draft) ?: emptyList()
        else emptyList()
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(if (rule.conditions.isEmpty() && rule.actions.isEmpty()) "New rule" else "Edit rule",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ActuaGroupLabel("Stage")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Rule.Stage.entries.forEachIndexed { index, stage ->
                        SegmentedButton(
                            selected = draft.stage == stage,
                            onClick = { draft = draft.copy(stage = stage) },
                            shape = SegmentedButtonDefaults.itemShape(index, Rule.Stage.entries.size),
                        ) { Text(stage.name.lowercase().replaceFirstChar(Char::uppercase)) }
                    }
                }
                ActuaGroupLabel("Match")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(Rule.ConditionsOp.AND to "All conditions", Rule.ConditionsOp.OR to "Any condition")
                        .forEachIndexed { index, (op, label) ->
                            SegmentedButton(
                                selected = draft.conditionsOp == op,
                                onClick = { draft = draft.copy(conditionsOp = op) },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                            ) { Text(label) }
                        }
                }
                ActuaGroupLabel("If")
                draft.conditions.forEachIndexed { index, condition ->
                    ConditionEditor(index, condition, data, onChange = { changed ->
                        draft = draft.copy(conditions = draft.conditions.toMutableList().also { it[index] = changed })
                    }, onRemove = { draft = draft.copy(conditions = draft.conditions.toMutableList().also { it.removeAt(index) }) })
                }
                ActuaSecondaryButton(
                    text = "Add condition",
                    icon = Icons.Outlined.Add,
                    onClick = {
                        draft = draft.copy(conditions = draft.conditions +
                            Rule.Condition("is", "imported_payee", RuleValue.Text("")))
                    },
                )
                ActuaGroupLabel("Then")
                draft.actions.forEachIndexed { index, action ->
                    ActionEditor(index, action, data, onChange = { changed ->
                        draft = draft.copy(actions = draft.actions.toMutableList().also { it[index] = changed })
                    }, onRemove = { draft = draft.copy(actions = draft.actions.toMutableList().also { it.removeAt(index) }) })
                }
                ActuaSecondaryButton(
                    text = "Add action",
                    icon = Icons.Outlined.Add,
                    onClick = {
                        draft = draft.copy(actions = draft.actions + Rule.Action("set", "category", RuleValue.Null))
                    },
                )
                if (draft.conditions.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${previewMatches.size} matching transaction${if (previewMatches.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (previewMatches.isNotEmpty()) {
                                    TextButton(onClick = { showPreviewList = !showPreviewList }) {
                                        Text(if (showPreviewList) "Hide preview" else "Preview")
                                    }
                                }
                            }
                            if (showPreviewList && previewMatches.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    previewMatches.take(10).forEach { tx ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                "${tx.date}: ${tx.payeeName ?: "No payee"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                com.azimulkabir.actua.ui.components.centsToInput(kotlin.math.abs(tx.amountCents)),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                    if (previewMatches.size > 10) {
                                        Text(
                                            "And ${previewMatches.size - 10} more...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (onApply != null && draft.conditions.isNotEmpty() && draft.actions.isNotEmpty()) {
                    TextButton(onClick = { onApply(draft) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Apply rule now", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
                if (!scheduleOwned && rule.conditions.isNotEmpty()) TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Text("Delete rule", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = Spacing.sm))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
            ActuaPrimaryActionBar(
                text = "Save rule",
                onClick = { onSave(draft) },
                enabled = draft.conditions.isNotEmpty() && draft.actions.isNotEmpty(),
                icon = Icons.Outlined.Check,
            )
        }
    }
}

/** Title row of a condition or action card, with its remove button. */
@Composable
internal fun RulePartHeader(title: String, removeDescription: String, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, removeDescription) }
    }
}

@Composable
internal fun ConditionEditor(index: Int, condition: Rule.Condition, data: RuleEditorData,
    onChange: (Rule.Condition) -> Unit, onRemove: () -> Unit) {
    ActuaFormCard {
        RulePartHeader("Condition ${index + 1}", "Remove condition", onRemove)
        ActuaMenuRow(
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            label = "Field",
            value = RuleSchema.fieldLabel(condition.field),
            choices = RuleSchema.conditionFields.map { RuleSchema.fieldLabel(it) to it },
        ) { field ->
            val op = RuleSchema.validOps(field).firstOrNull() ?: "is"
            onChange(condition.copy(field = field, op = op, value = defaultValue(field, op), options = emptyMap()))
        }
        ActuaCardDivider()
        ActuaMenuRow(
            icon = Icons.Outlined.Tune,
            label = "Operator",
            value = RuleSchema.opLabel(condition.op),
            choices = RuleSchema.validOps(condition.field).map { RuleSchema.opLabel(it) to it },
        ) { op ->
            onChange(condition.copy(op = op, value = defaultValue(condition.field, op)))
        }
        if (condition.op !in setOf("onBudget", "offBudget")) {
            ActuaCardDivider()
            RuleValueEditor(condition.field, condition.op, condition.value, condition.options, data) { value, options ->
                onChange(condition.copy(value = value, options = options))
            }
        }
    }
}

@Composable
private fun ActionEditor(index: Int, action: Rule.Action, data: RuleEditorData,
    onChange: (Rule.Action) -> Unit, onRemove: () -> Unit) {
    ActuaFormCard {
        RulePartHeader("Action ${index + 1}", "Remove action", onRemove)
        ActuaMenuRow(
            icon = Icons.Outlined.Bolt,
            label = "Action",
            value = RuleSchema.opLabel(action.op),
            choices = listOf("set", "set-split-amount", "prepend-notes", "append-notes", "delete-transaction")
                .map { RuleSchema.opLabel(it) to it },
        ) { op ->
            onChange(when (op) {
                "set" -> Rule.Action(op, "category", RuleValue.Null)
                "set-split-amount" -> Rule.Action(op, null, RuleValue.Number(0.0), splitOptions(1.0, "fixed-amount"))
                "delete-transaction" -> Rule.Action(op, null, RuleValue.Null)
                else -> Rule.Action(op, "notes", RuleValue.Text(""))
            })
        }
        if (action.op == "set") {
            val field = action.field ?: "category"
            ActuaCardDivider()
            ActuaMenuRow(
                icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                label = "Field",
                value = RuleSchema.fieldLabel(field),
                choices = RuleSchema.actionFields.map { RuleSchema.fieldLabel(it) to it },
            ) {
                onChange(action.copy(field = it, value = defaultValue(it, "is"), options = action.options.filterKeys { key -> key == SPLIT_INDEX }))
            }
            ActuaCardDivider()
            RuleValueEditor(field, "is", action.value, action.options, data) { value, options ->
                onChange(action.copy(value = value, options = options))
            }
            ActuaCardDivider()
            SplitLineRow(action.options[SPLIT_INDEX]?.number, allowNone = true) { index ->
                onChange(action.copy(options = action.options.withSplitIndex(index)))
            }
        } else if (action.op == "set-split-amount") {
            SplitAmountEditor(action, onChange)
        } else if (action.op != "delete-transaction") {
            ActuaCardDivider()
            ActuaFormTextField(
                icon = Icons.Outlined.Edit,
                label = "Text",
                value = action.value.text.orEmpty(),
                onValueChange = { onChange(action.copy(value = RuleValue.Text(it))) },
            )
        }
    }
}

private const val SPLIT_INDEX = "splitIndex"
private const val SPLIT_METHOD = "method"
private const val MAX_SPLIT_LINES = 10

private fun splitOptions(index: Double, method: String) =
    mapOf(SPLIT_INDEX to RuleValue.Number(index), SPLIT_METHOD to RuleValue.Text(method))

private fun Map<String, RuleValue>.withSplitIndex(index: Double?): Map<String, RuleValue> =
    if (index == null) this - SPLIT_INDEX else this + (SPLIT_INDEX to RuleValue.Number(index))

/** Picks which split line an action targets; [allowNone] adds "Whole transaction" for `set` actions. */
@Composable
private fun SplitLineRow(index: Double?, allowNone: Boolean, onChange: (Double?) -> Unit) {
    val lines = (1..MAX_SPLIT_LINES).map { "Split $it" to it.toDouble() }
    ActuaMenuRow(
        icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
        label = "Applies to",
        value = index?.let { "Split ${it.toInt()}" } ?: "Whole transaction",
        choices = (if (allowNone) listOf("Whole transaction" to null) else emptyList()) + lines.map { it.first to it.second },
    ) { onChange(it) }
}

@Composable
private fun SplitAmountEditor(action: Rule.Action, onChange: (Rule.Action) -> Unit) {
    val method = action.options[SPLIT_METHOD]?.text ?: "fixed-amount"
    val methods = listOf("fixed-amount" to "Fixed amount", "fixed-percent" to "Percent of remainder", "remainder" to "Remainder")
    ActuaCardDivider()
    SplitLineRow(action.options[SPLIT_INDEX]?.number ?: 1.0, allowNone = false) { index ->
        onChange(action.copy(options = action.options.withSplitIndex(index)))
    }
    ActuaCardDivider()
    ActuaMenuRow(
        icon = Icons.Outlined.Tune,
        label = "Method",
        value = methods.firstOrNull { it.first == method }?.second ?: method,
        choices = (methods + if (method == "formula") listOf("formula" to "Formula") else emptyList())
            .map { it.second to it.first },
    ) { chosen ->
        onChange(action.copy(
            value = if (chosen == "remainder") RuleValue.Null else RuleValue.Number(0.0),
            options = action.options + (SPLIT_METHOD to RuleValue.Text(chosen)),
        ))
    }
    when (method) {
        "fixed-amount" -> {
            ActuaCardDivider()
            NumberInput("Amount", action.value.number) { onChange(action.copy(value = RuleValue.Number(it))) }
        }
        "fixed-percent" -> {
            ActuaCardDivider()
            ActuaFormTextField(
                icon = Icons.Outlined.Calculate,
                label = "Percent",
                value = action.value.number?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty(),
                onValueChange = { text -> text.toDoubleOrNull()?.let { onChange(action.copy(value = RuleValue.Number(it))) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleValueEditor(field: String, op: String, value: RuleValue, options: Map<String, RuleValue>,
    data: RuleEditorData, onChange: (RuleValue, Map<String, RuleValue>) -> Unit) {
    val choices = when (field) { "account" -> data.accounts; "payee" -> data.payees; "category" -> data.categories;
        "category_group" -> data.categoryGroups; else -> emptyList() }
    when (RuleSchema.type(field)) {
        RuleFieldType.ID -> {
            if (op in setOf("oneOf", "notOneOf")) MultiChoice(value.list.orEmpty(), choices) { onChange(RuleValue.ListValue(it), options) }
            else {
                val selectedName = data.names[value.text] ?: choices.firstOrNull { it.id == value.text }?.name
                ActuaMenuRow(
                    icon = Icons.Outlined.Edit,
                    label = "Value",
                    value = selectedName ?: "Select value",
                    valueIsPlaceholder = selectedName == null,
                    choices = choices.map { it.name to it.id },
                ) { onChange(RuleValue.Text(it), options) }
            }
        }
        RuleFieldType.BOOLEAN -> ActuaFormRow(
            icon = Icons.Outlined.Edit,
            label = "Value",
            value = null,
            checked = value.flag == true,
            onClick = { onChange(RuleValue.Flag(value.flag != true), options) },
        )
        RuleFieldType.NUMBER -> {
            if (op == "isbetween") {
                val map = (value as? RuleValue.ObjectValue)?.value.orEmpty()
                NumberInput("From", map["num1"]?.number) { a -> onChange(RuleValue.ObjectValue(map + ("num1" to RuleValue.Number(a))), options) }
                ActuaCardDivider()
                NumberInput("To", map["num2"]?.number) { b -> onChange(RuleValue.ObjectValue(map + ("num2" to RuleValue.Number(b))), options) }
            } else NumberInput("Amount", value.number) { onChange(RuleValue.Number(it), options) }
            if (field == "amount" && op != "isbetween") {
                val directions = listOf("Any" to null, "Outflow" to "outflow", "Inflow" to "inflow")
                SingleChoiceSegmentedButtonRow(
                    Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
                ) {
                    directions.forEachIndexed { index, (label, key) ->
                        val selected = if (key == null) options["outflow"]?.flag != true && options["inflow"]?.flag != true
                            else options[key]?.flag == true
                        SegmentedButton(
                            selected = selected,
                            onClick = { onChange(value, key?.let { mapOf(it to RuleValue.Flag(true)) }.orEmpty()) },
                            shape = SegmentedButtonDefaults.itemShape(index, directions.size),
                        ) { Text(label) }
                    }
                }
            }
        }
        else -> ActuaFormTextField(
            icon = Icons.Outlined.Edit,
            label = if (field == "date") "Date (YYYY-MM-DD)" else if (op == "matches") "Regular expression" else "Value",
            value = if (op in setOf("oneOf", "notOneOf")) value.list.orEmpty().mapNotNull { it.text }.joinToString(", ")
                else value.text.orEmpty(),
            onValueChange = { text ->
                onChange(if (op in setOf("oneOf", "notOneOf")) RuleValue.ListValue(text.split(',').map { RuleValue.Text(it.trim()) }.filter { it.value.isNotEmpty() })
                    else RuleValue.Text(text), options)
            },
        )
    }
}

@Composable
internal fun NumberInput(label: String, cents: Double?, onChange: (Double) -> Unit) {
    ActuaFormTextField(
        icon = Icons.Outlined.Calculate,
        label = label,
        value = if (cents == null) "" else ruleAmountText(cents),
        onValueChange = { text -> text.toDoubleOrNull()?.let { onChange(it * minorUnitsPerWhole()) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** A form row listing how many [choices] are selected; its menu toggles each one. */
@Composable
internal fun MultiChoice(selected: List<RuleValue>, choices: List<RuleChoice>, onChange: (List<RuleValue>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val ids = selected.mapNotNull { it.text }.toSet()
    Box {
        ActuaFormRow(
            icon = Icons.Outlined.Edit,
            label = "Values",
            value = if (ids.isEmpty()) "Select values" else "${ids.size} selected",
            valueIsPlaceholder = ids.isEmpty(),
            onClick = { open = true },
        )
        DropdownMenu(open, { open = false }) { choices.forEach { choice ->
            DropdownMenuItem(text = { Text(choice.name) }, leadingIcon = { Checkbox(choice.id in ids, null) }, onClick = {
                val updated = if (choice.id in ids) ids - choice.id else ids + choice.id
                onChange(updated.map { RuleValue.Text(it) })
            })
        } }
    }
}

internal fun defaultValue(field: String, op: String): RuleValue = when {
    op in setOf("oneOf", "notOneOf") -> RuleValue.ListValue(emptyList())
    op == "isbetween" -> RuleValue.ObjectValue(mapOf("num1" to RuleValue.Number(0.0), "num2" to RuleValue.Number(0.0)))
    RuleSchema.type(field) == RuleFieldType.NUMBER -> RuleValue.Number(0.0)
    RuleSchema.type(field) == RuleFieldType.BOOLEAN -> RuleValue.Flag(true)
    RuleSchema.type(field) in setOf(RuleFieldType.STRING, RuleFieldType.DATE) -> RuleValue.Text("")
    else -> RuleValue.Null
}

private fun ruleSummary(rule: Rule, names: Map<String, String>) =
    (rule.conditions.map { conditionSummary(it, names) } + rule.actions.map { actionSummary(it, names) }).joinToString(" ")

private fun conditionSummary(condition: Rule.Condition, names: Map<String, String>) =
    "${RuleSchema.fieldLabel(condition.field)} ${RuleSchema.opLabel(condition.op)} ${valueLabel(condition.value, names)}".trim()

private fun actionSummary(action: Rule.Action, names: Map<String, String>) = when (action.op) {
    "set" -> "Set ${action.field?.let(RuleSchema::fieldLabel).orEmpty()} to ${valueLabel(action.value, names)}" +
        (action.options[SPLIT_INDEX]?.number?.let { " (split ${it.toInt()})" } ?: "")
    "set-split-amount" -> {
        val line = action.options[SPLIT_INDEX]?.number?.toInt() ?: 1
        when (action.options[SPLIT_METHOD]?.text) {
            "fixed-percent" -> "Split $line: ${action.value.number?.let { if (it % 1.0 == 0.0) it.toLong() else it }}% of remainder"
            "remainder" -> "Split $line: remainder"
            "formula" -> "Split $line: formula"
            else -> "Split $line: ${valueLabel(action.value, names)}"
        }
    }
    "prepend-notes" -> "Prepend to notes ${action.value.text.orEmpty()}"
    "append-notes" -> "Append to notes ${action.value.text.orEmpty()}"
    "delete-transaction" -> "Delete transaction"
    else -> RuleSchema.opLabel(action.op)
}

/** A rule amount (stored in the budget currency's minor units) as a plain decimal with that currency's places. */
private fun ruleAmountText(minorUnits: Double): String {
    val places = CurrencyDisplay.decimalPlaces
    return "%.${places}f".format(minorUnits / minorUnitsPerWhole(places))
}

private fun valueLabel(value: RuleValue, names: Map<String, String>): String = when (value) {
    is RuleValue.Text -> names[value.value] ?: value.value
    is RuleValue.Number -> ruleAmountText(value.value)
    is RuleValue.Flag -> value.value.toString()
    is RuleValue.ListValue -> value.value.joinToString(", ") { valueLabel(it, names) }
    is RuleValue.ObjectValue -> value.value.values.joinToString(" – ") { valueLabel(it, names) }
    RuleValue.Null -> ""
}
