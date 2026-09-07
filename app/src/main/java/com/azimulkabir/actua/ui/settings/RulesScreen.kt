package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleChoice
import com.azimulkabir.actua.data.rules.RuleEditorData
import com.azimulkabir.actua.data.rules.RuleFieldType
import com.azimulkabir.actua.data.rules.RuleSchema
import com.azimulkabir.actua.data.rules.RuleValue
import java.time.LocalDate

@Composable
fun RulesScreen(
    rules: List<Rule>,
    supported: Boolean,
    scheduleOwnedRuleIds: Set<String>,
    editorData: RuleEditorData,
    onBack: () -> Unit,
    onSave: (Rule) -> Boolean,
    onDelete: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Rule?>(null) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Rules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
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
        RuleEditor(rule, editorData, rule.id in scheduleOwnedRuleIds, onDismiss = { editing = null },
            onSave = { if (onSave(it)) editing = null },
            onDelete = { if (onDelete(rule.id)) editing = null })
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RuleEditor(rule: Rule, data: RuleEditorData, scheduleOwned: Boolean,
    onDismiss: () -> Unit, onSave: (Rule) -> Unit, onDelete: () -> Unit) {
    var draft by remember(rule.id) { mutableStateOf(rule) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (rule.conditions.isEmpty() && rule.actions.isEmpty()) "New rule" else "Edit rule",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Stage", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Rule.Stage.entries.forEach { stage -> FilterChip(selected = draft.stage == stage,
                    onClick = { draft = draft.copy(stage = stage) }, label = { Text(stage.name.lowercase().replaceFirstChar(Char::uppercase)) }) }
            }
            Text("Match", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(draft.conditionsOp == Rule.ConditionsOp.AND,
                    { draft = draft.copy(conditionsOp = Rule.ConditionsOp.AND) }, { Text("All conditions") })
                FilterChip(draft.conditionsOp == Rule.ConditionsOp.OR,
                    { draft = draft.copy(conditionsOp = Rule.ConditionsOp.OR) }, { Text("Any condition") })
            }
            Text("If", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            draft.conditions.forEachIndexed { index, condition ->
                ConditionEditor(condition, data, onChange = { changed ->
                    draft = draft.copy(conditions = draft.conditions.toMutableList().also { it[index] = changed })
                }, onRemove = { draft = draft.copy(conditions = draft.conditions.toMutableList().also { it.removeAt(index) }) })
            }
            TextButton(onClick = { draft = draft.copy(conditions = draft.conditions +
                Rule.Condition("is", "imported_payee", RuleValue.Text(""))) }) {
                Icon(Icons.Outlined.Add, null); Text("Add condition")
            }
            Text("Then", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            draft.actions.forEachIndexed { index, action ->
                ActionEditor(action, data, onChange = { changed ->
                    draft = draft.copy(actions = draft.actions.toMutableList().also { it[index] = changed })
                }, onRemove = { draft = draft.copy(actions = draft.actions.toMutableList().also { it.removeAt(index) }) })
            }
            TextButton(onClick = { draft = draft.copy(actions = draft.actions +
                Rule.Action("set", "category", RuleValue.Null)) }) {
                Icon(Icons.Outlined.Add, null); Text("Add action")
            }
            HorizontalDivider()
            Button(onClick = { onSave(draft) }, enabled = draft.conditions.isNotEmpty() && draft.actions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()) { Text("Save rule") }
            if (!scheduleOwned && rule.conditions.isNotEmpty()) TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Delete, null); Text("Delete rule")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConditionEditor(condition: Rule.Condition, data: RuleEditorData,
    onChange: (Rule.Condition) -> Unit, onRemove: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectField(RuleSchema.fieldLabel(condition.field), RuleSchema.conditionFields.map { it to RuleSchema.fieldLabel(it) }) { field ->
                    val op = RuleSchema.validOps(field).firstOrNull() ?: "is"
                    onChange(condition.copy(field = field, op = op, value = defaultValue(field, op), options = emptyMap()))
                }
                Spacer(Modifier.weight(1f)); IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, "Remove condition") }
            }
            SelectField(RuleSchema.opLabel(condition.op), RuleSchema.validOps(condition.field).map { it to RuleSchema.opLabel(it) }) { op ->
                onChange(condition.copy(op = op, value = defaultValue(condition.field, op)))
            }
            if (condition.op !in setOf("onBudget", "offBudget")) RuleValueEditor(condition.field, condition.op,
                condition.value, condition.options, data) { value, options -> onChange(condition.copy(value = value, options = options)) }
        }
    }
}

@Composable
private fun ActionEditor(action: Rule.Action, data: RuleEditorData,
    onChange: (Rule.Action) -> Unit, onRemove: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectField(RuleSchema.opLabel(action.op), listOf("set", "prepend-notes", "append-notes", "delete-transaction")
                    .map { it to RuleSchema.opLabel(it) }) { op ->
                    onChange(when (op) {
                        "set" -> Rule.Action(op, "category", RuleValue.Null)
                        "delete-transaction" -> Rule.Action(op, null, RuleValue.Null)
                        else -> Rule.Action(op, "notes", RuleValue.Text(""))
                    })
                }
                Spacer(Modifier.weight(1f)); IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, "Remove action") }
            }
            if (action.op == "set") {
                val field = action.field ?: "category"
                SelectField(RuleSchema.fieldLabel(field), RuleSchema.actionFields.map { it to RuleSchema.fieldLabel(it) }) {
                    onChange(action.copy(field = it, value = defaultValue(it, "is"), options = emptyMap()))
                }
                RuleValueEditor(field, "is", action.value, action.options, data) { value, options ->
                    onChange(action.copy(value = value, options = options))
                }
            } else if (action.op != "delete-transaction") {
                OutlinedTextField(action.value.text.orEmpty(), { onChange(action.copy(value = RuleValue.Text(it))) },
                    label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RuleValueEditor(field: String, op: String, value: RuleValue, options: Map<String, RuleValue>,
    data: RuleEditorData, onChange: (RuleValue, Map<String, RuleValue>) -> Unit) {
    val choices = when (field) { "account" -> data.accounts; "payee" -> data.payees; "category" -> data.categories;
        "category_group" -> data.categoryGroups; else -> emptyList() }
    when (RuleSchema.type(field)) {
        RuleFieldType.ID -> {
            if (op in setOf("oneOf", "notOneOf")) MultiChoice(value.list.orEmpty(), choices) { onChange(RuleValue.ListValue(it), options) }
            else SelectField(data.names[value.text] ?: choices.firstOrNull { it.id == value.text }?.name ?: "Select value",
                choices.map { it.id to it.name }) { onChange(RuleValue.Text(it), options) }
        }
        RuleFieldType.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Value", Modifier.weight(1f)); Switch(value.flag == true, { onChange(RuleValue.Flag(it), options) })
        }
        RuleFieldType.NUMBER -> {
            if (op == "isbetween") {
                val map = (value as? RuleValue.ObjectValue)?.value.orEmpty()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput("From", map["num1"]?.number, Modifier.weight(1f)) { a -> onChange(RuleValue.ObjectValue(map + ("num1" to RuleValue.Number(a))), options) }
                    NumberInput("To", map["num2"]?.number, Modifier.weight(1f)) { b -> onChange(RuleValue.ObjectValue(map + ("num2" to RuleValue.Number(b))), options) }
                }
            } else NumberInput("Amount", value.number, Modifier.fillMaxWidth()) { onChange(RuleValue.Number(it), options) }
            if (field == "amount" && op != "isbetween") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Any" to null, "Outflow" to "outflow", "Inflow" to "inflow").forEach { (label, key) ->
                    val selected = if (key == null) options["outflow"]?.flag != true && options["inflow"]?.flag != true else options[key]?.flag == true
                    FilterChip(selected, { onChange(value, key?.let { mapOf(it to RuleValue.Flag(true)) }.orEmpty()) }, { Text(label) })
                }
            }
        }
        else -> OutlinedTextField(
            if (op in setOf("oneOf", "notOneOf")) value.list.orEmpty().mapNotNull { it.text }.joinToString(", ")
            else value.text.orEmpty(), { text ->
            onChange(if (op in setOf("oneOf", "notOneOf")) RuleValue.ListValue(text.split(',').map { RuleValue.Text(it.trim()) }.filter { it.value.isNotEmpty() })
                else RuleValue.Text(text), options)
        }, label = { Text(if (field == "date") "Date (YYYY-MM-DD)" else if (op == "matches") "Regular expression" else "Value") },
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NumberInput(label: String, cents: Double?, modifier: Modifier, onChange: (Double) -> Unit) {
    OutlinedTextField(if (cents == null) "" else "%.2f".format(cents / 100.0), { text ->
        text.toDoubleOrNull()?.let { onChange(it * 100.0) }
    }, label = { Text(label) }, modifier = modifier, singleLine = true)
}

@Composable
private fun MultiChoice(selected: List<RuleValue>, choices: List<RuleChoice>, onChange: (List<RuleValue>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val ids = selected.mapNotNull { it.text }.toSet()
    Box {
        TextButton(onClick = { open = true }) { Text(if (ids.isEmpty()) "Select values" else "${ids.size} selected") }
        DropdownMenu(open, { open = false }) { choices.forEach { choice ->
            DropdownMenuItem(text = { Text(choice.name) }, leadingIcon = { Checkbox(choice.id in ids, null) }, onClick = {
                val updated = if (choice.id in ids) ids - choice.id else ids + choice.id
                onChange(updated.map { RuleValue.Text(it) })
            })
        } }
    }
}

@Composable
private fun SelectField(label: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        DropdownMenu(open, { open = false }) { options.forEach { (value, title) ->
            DropdownMenuItem(text = { Text(title) }, onClick = { open = false; onSelect(value) })
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
