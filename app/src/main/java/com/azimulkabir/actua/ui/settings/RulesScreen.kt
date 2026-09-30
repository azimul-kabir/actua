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
) {
    var search by remember { mutableStateOf("") }
    var editing by remember(initialRuleId) {
        mutableStateOf(initialRuleId?.let { id -> rules.firstOrNull { it.id == id } })
    }
    // Opened straight into one rule (e.g. a schedule's "Edit as rule"): closing it returns to the caller.
    fun closeEditor() {
        val closedInitial = initialRuleId != null && editing?.id == initialRuleId
        editing = null
        if (closedInitial) onInitialRuleClosed()
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rule.stage.name, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            if (rule.id in scheduleOwnedRuleIds) Text("  •  SCHEDULE", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        RuleEditor(rule, editorData, rule.id in scheduleOwnedRuleIds, onDismiss = ::closeEditor,
            onSave = { onSave(it, ::closeEditor) },
            onDelete = { onDelete(rule.id, ::closeEditor) })
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RuleEditor(rule: Rule, data: RuleEditorData, scheduleOwned: Boolean,
    onDismiss: () -> Unit, onSave: (Rule) -> Unit, onDelete: () -> Unit) {
    var draft by remember(rule.id) { mutableStateOf(rule) }
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
private fun RulePartHeader(title: String, removeDescription: String, onRemove: () -> Unit) {
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
private fun ConditionEditor(index: Int, condition: Rule.Condition, data: RuleEditorData,
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
            choices = listOf("set", "prepend-notes", "append-notes", "delete-transaction")
                .map { RuleSchema.opLabel(it) to it },
        ) { op ->
            onChange(when (op) {
                "set" -> Rule.Action(op, "category", RuleValue.Null)
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
                onChange(action.copy(field = it, value = defaultValue(it, "is"), options = emptyMap()))
            }
            ActuaCardDivider()
            RuleValueEditor(field, "is", action.value, action.options, data) { value, options ->
                onChange(action.copy(value = value, options = options))
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleValueEditor(field: String, op: String, value: RuleValue, options: Map<String, RuleValue>,
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
private fun NumberInput(label: String, cents: Double?, onChange: (Double) -> Unit) {
    ActuaFormTextField(
        icon = Icons.Outlined.Calculate,
        label = label,
        value = if (cents == null) "" else "%.2f".format(cents / 100.0),
        onValueChange = { text -> text.toDoubleOrNull()?.let { onChange(it * 100.0) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** A form row listing how many [choices] are selected; its menu toggles each one. */
@Composable
private fun MultiChoice(selected: List<RuleValue>, choices: List<RuleChoice>, onChange: (List<RuleValue>) -> Unit) {
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

private fun defaultValue(field: String, op: String): RuleValue = when {
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
    "set" -> "Set ${action.field?.let(RuleSchema::fieldLabel).orEmpty()} to ${valueLabel(action.value, names)}"
    "prepend-notes" -> "Prepend to notes ${action.value.text.orEmpty()}"
    "append-notes" -> "Append to notes ${action.value.text.orEmpty()}"
    "delete-transaction" -> "Delete transaction"
    else -> RuleSchema.opLabel(action.op)
}

private fun valueLabel(value: RuleValue, names: Map<String, String>): String = when (value) {
    is RuleValue.Text -> names[value.value] ?: value.value
    is RuleValue.Number -> "%.2f".format(value.value / 100.0)
    is RuleValue.Flag -> value.value.toString()
    is RuleValue.ListValue -> value.value.joinToString(", ") { valueLabel(it, names) }
    is RuleValue.ObjectValue -> value.value.values.joinToString(" – ") { valueLabel(it, names) }
    RuleValue.Null -> ""
}
