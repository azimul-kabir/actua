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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
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
    val addRuleDescription = stringResource(R.string.fs_add_rule)

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
            title = { Text(stringResource(R.string.fs_apply_rule_question)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (matches.isEmpty()) {
                        Text(stringResource(R.string.fs_apply_rule_empty))
                    } else {
                        Text(pluralStringResource(R.plurals.fs_apply_rule_matches, matches.size, matches.size))
                        if (reconciledCount > 0) {
                            Text(
                                pluralStringResource(
                                    R.plurals.fs_apply_rule_reconciled_warning, reconciledCount, reconciledCount,
                                ),
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
                        Text(stringResource(R.string.fs_apply))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToApply = null }) {
                    Text(stringResource(if (matches.isEmpty()) R.string.fs_close else R.string.fs_cancel))
                }
            },
        )
    }

    applyResultCount?.let { count ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { applyResultCount = null },
            title = { Text(stringResource(R.string.fs_rule_applied)) },
            text = { Text(pluralStringResource(R.plurals.fs_rule_applied_count, count, count)) },
            confirmButton = {
                TextButton(onClick = { applyResultCount = null }) { Text(stringResource(R.string.fs_ok)) }
            },
        )
    }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.fs_rules_title), onBack = onBack) {
            if (supported) IconButton(onClick = { editing = Rule.empty() }) { Icon(Icons.Outlined.Add, addRuleDescription) }
        }
        if (!supported) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.fs_rules_unavailable), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.fs_rules_unavailable_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.fs_search_rules)) }, singleLine = true,
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
                                Text(ruleStageLabel(rule.stage), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                if (rule.id in scheduleOwnedRuleIds) Text(
                                    "  •  " + stringResource(R.string.fs_schedule_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (onApplyRule != null) {
                                TextButton(onClick = { ruleToApply = rule }) {
                                    Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                    Text(stringResource(R.string.fs_apply_now))
                                }
                            }
                        }
                        Text(stringResource(R.string.fs_if), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        rule.conditions.forEachIndexed { index, condition ->
                            Text(
                                (if (index > 0) stringResource(
                                    if (rule.conditionsOp == Rule.ConditionsOp.AND) R.string.fs_and else R.string.fs_or,
                                ) + " " else "") + conditionSummary(condition, names),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(stringResource(R.string.fs_then), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        rule.actions.forEach { Text(actionSummary(it, names), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (filtered.isEmpty()) Text(
                stringResource(if (search.isBlank()) R.string.fs_no_rules else R.string.fs_no_matching_rules),
                modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                Text(if (rule.conditions.isEmpty() && rule.actions.isEmpty()) stringResource(R.string.fs_new_rule)
                    else stringResource(R.string.fs_edit_rule),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ActuaGroupLabel(stringResource(R.string.fs_stage))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Rule.Stage.entries.forEachIndexed { index, stage ->
                        SegmentedButton(
                            selected = draft.stage == stage,
                            onClick = { draft = draft.copy(stage = stage) },
                            shape = SegmentedButtonDefaults.itemShape(index, Rule.Stage.entries.size),
                        ) { Text(ruleStageLabel(stage)) }
                    }
                }
                ActuaGroupLabel(stringResource(R.string.fs_match))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(
                        Rule.ConditionsOp.AND to stringResource(R.string.fs_all_conditions),
                        Rule.ConditionsOp.OR to stringResource(R.string.fs_any_condition),
                    )
                        .forEachIndexed { index, (op, label) ->
                            SegmentedButton(
                                selected = draft.conditionsOp == op,
                                onClick = { draft = draft.copy(conditionsOp = op) },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                            ) { Text(label) }
                        }
                }
                ActuaGroupLabel(stringResource(R.string.fs_if_title))
                draft.conditions.forEachIndexed { index, condition ->
                    ConditionEditor(index, condition, data, onChange = { changed ->
                        draft = draft.copy(conditions = draft.conditions.toMutableList().also { it[index] = changed })
                    }, onRemove = { draft = draft.copy(conditions = draft.conditions.toMutableList().also { it.removeAt(index) }) })
                }
                ActuaSecondaryButton(
                    text = stringResource(R.string.fs_add_condition),
                    icon = Icons.Outlined.Add,
                    onClick = {
                        draft = draft.copy(conditions = draft.conditions +
                            Rule.Condition("is", "imported_payee", RuleValue.Text("")))
                    },
                )
                ActuaGroupLabel(stringResource(R.string.fs_then_title))
                draft.actions.forEachIndexed { index, action ->
                    ActionEditor(index, action, data, onChange = { changed ->
                        draft = draft.copy(actions = draft.actions.toMutableList().also { it[index] = changed })
                    }, onRemove = { draft = draft.copy(actions = draft.actions.toMutableList().also { it.removeAt(index) }) })
                }
                ActuaSecondaryButton(
                    text = stringResource(R.string.fs_add_action),
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
                                    pluralStringResource(
                                        R.plurals.fs_matching_transactions, previewMatches.size, previewMatches.size,
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (previewMatches.isNotEmpty()) {
                                    TextButton(onClick = { showPreviewList = !showPreviewList }) {
                                        Text(stringResource(if (showPreviewList) R.string.fs_hide_preview else R.string.fs_preview))
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
                                                "${tx.date}: ${tx.payeeName ?: stringResource(R.string.fs_no_payee)}",
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
                                            pluralStringResource(
                                                R.plurals.fs_more_matches,
                                                previewMatches.size - 10,
                                                previewMatches.size - 10,
                                            ),
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
                        Text(stringResource(R.string.fs_apply_rule_now), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
                if (!scheduleOwned && rule.conditions.isNotEmpty()) TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.fs_delete_rule), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = Spacing.sm))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fs_cancel)) }
            }
            ActuaPrimaryActionBar(
                text = stringResource(R.string.fs_save_rule),
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
        RulePartHeader(
            stringResource(R.string.fs_condition_number, index + 1),
            stringResource(R.string.fs_remove_condition),
            onRemove,
        )
        ActuaMenuRow(
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            label = stringResource(R.string.fs_field),
            value = ruleFieldLabel(condition.field),
            choices = RuleSchema.conditionFields.map { ruleFieldLabel(it) to it },
        ) { field ->
            val op = RuleSchema.validOps(field).firstOrNull() ?: "is"
            onChange(condition.copy(field = field, op = op, value = defaultValue(field, op), options = emptyMap()))
        }
        ActuaCardDivider()
        ActuaMenuRow(
            icon = Icons.Outlined.Tune,
            label = stringResource(R.string.fs_operator),
            value = ruleOpLabel(condition.op),
            choices = RuleSchema.validOps(condition.field).map { ruleOpLabel(it) to it },
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
        RulePartHeader(
            stringResource(R.string.fs_action_number, index + 1),
            stringResource(R.string.fs_remove_action),
            onRemove,
        )
        ActuaMenuRow(
            icon = Icons.Outlined.Bolt,
            label = stringResource(R.string.fs_action_label),
            value = ruleOpLabel(action.op),
            choices = listOf("set", "set-split-amount", "prepend-notes", "append-notes", "delete-transaction")
                .map { ruleOpLabel(it) to it },
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
                label = stringResource(R.string.fs_field),
                value = ruleFieldLabel(field),
                choices = RuleSchema.actionFields.map { ruleFieldLabel(it) to it },
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
                label = stringResource(R.string.fs_text),
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
    val lines = (1..MAX_SPLIT_LINES).map { stringResource(R.string.fs_split_number, it) to it.toDouble() }
    ActuaMenuRow(
        icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
        label = stringResource(R.string.fs_applies_to),
        value = index?.let { stringResource(R.string.fs_split_number, it.toInt()) }
            ?: stringResource(R.string.fs_whole_transaction),
        choices = (if (allowNone) listOf(stringResource(R.string.fs_whole_transaction) to null) else emptyList()) +
            lines.map { it.first to it.second },
    ) { onChange(it) }
}

@Composable
private fun SplitAmountEditor(action: Rule.Action, onChange: (Rule.Action) -> Unit) {
    val method = action.options[SPLIT_METHOD]?.text ?: "fixed-amount"
    val methods = listOf(
        "fixed-amount" to stringResource(R.string.fs_split_method_fixed_amount),
        "fixed-percent" to stringResource(R.string.fs_split_method_percent),
        "remainder" to stringResource(R.string.fs_split_method_remainder),
    )
    ActuaCardDivider()
    SplitLineRow(action.options[SPLIT_INDEX]?.number ?: 1.0, allowNone = false) { index ->
        onChange(action.copy(options = action.options.withSplitIndex(index)))
    }
    ActuaCardDivider()
    ActuaMenuRow(
        icon = Icons.Outlined.Tune,
        label = stringResource(R.string.fs_method),
        value = methods.firstOrNull { it.first == method }?.second ?: method,
        choices = (methods + if (method == "formula") listOf("formula" to stringResource(R.string.fs_split_method_formula)) else emptyList())
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
            NumberInput(stringResource(R.string.fs_amount), action.value.number) { onChange(action.copy(value = RuleValue.Number(it))) }
        }
        "fixed-percent" -> {
            ActuaCardDivider()
            ActuaFormTextField(
                icon = Icons.Outlined.Calculate,
                label = stringResource(R.string.fs_percent),
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
                    label = stringResource(R.string.fs_value),
                    value = selectedName ?: stringResource(R.string.fs_select_value),
                    valueIsPlaceholder = selectedName == null,
                    choices = choices.map { it.name to it.id },
                ) { onChange(RuleValue.Text(it), options) }
            }
        }
        RuleFieldType.BOOLEAN -> ActuaFormRow(
            icon = Icons.Outlined.Edit,
            label = stringResource(R.string.fs_value),
            value = null,
            checked = value.flag == true,
            onClick = { onChange(RuleValue.Flag(value.flag != true), options) },
        )
        RuleFieldType.NUMBER -> {
            if (op == "isbetween") {
                val map = (value as? RuleValue.ObjectValue)?.value.orEmpty()
                NumberInput(stringResource(R.string.fs_from), map["num1"]?.number) { a -> onChange(RuleValue.ObjectValue(map + ("num1" to RuleValue.Number(a))), options) }
                ActuaCardDivider()
                NumberInput(stringResource(R.string.fs_to), map["num2"]?.number) { b -> onChange(RuleValue.ObjectValue(map + ("num2" to RuleValue.Number(b))), options) }
            } else NumberInput(stringResource(R.string.fs_amount), value.number) { onChange(RuleValue.Number(it), options) }
            if (field == "amount" && op != "isbetween") {
                val directions = listOf(
                    stringResource(R.string.fs_any) to null,
                    stringResource(R.string.fs_outflow) to "outflow",
                    stringResource(R.string.fs_inflow) to "inflow",
                )
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
            label = if (field == "date") stringResource(R.string.fs_date_iso_hint)
                else if (op == "matches") stringResource(R.string.fs_regular_expression)
                else stringResource(R.string.fs_value),
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
            label = stringResource(R.string.fs_values),
            value = if (ids.isEmpty()) stringResource(R.string.fs_select_values)
                else pluralStringResource(R.plurals.fs_values_selected, ids.size, ids.size),
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

@Composable
private fun ruleSummary(rule: Rule, names: Map<String, String>) =
    (rule.conditions.map { conditionSummary(it, names) } + rule.actions.map { actionSummary(it, names) }).joinToString(" ")

private val ruleFieldLabelRes = mapOf(
    "imported_payee" to R.string.fs_rule_field_imported_payee,
    "account" to R.string.fs_rule_field_account,
    "category" to R.string.fs_rule_field_category,
    "category_group" to R.string.fs_rule_field_category_group,
    "date" to R.string.fs_rule_field_date,
    "payee" to R.string.fs_rule_field_payee,
    "payee_name" to R.string.fs_rule_field_payee_name,
    "notes" to R.string.fs_rule_field_notes,
    "amount" to R.string.fs_rule_field_amount,
    "cleared" to R.string.fs_rule_field_cleared,
)

private val ruleOpLabelRes = mapOf(
    "is" to R.string.fs_rule_op_is,
    "isNot" to R.string.fs_rule_op_is_not,
    "oneOf" to R.string.fs_rule_op_one_of,
    "notOneOf" to R.string.fs_rule_op_not_one_of,
    "isapprox" to R.string.fs_rule_op_is_approx,
    "isbetween" to R.string.fs_rule_op_is_between,
    "contains" to R.string.fs_rule_op_contains,
    "doesNotContain" to R.string.fs_rule_op_does_not_contain,
    "matches" to R.string.fs_rule_op_matches,
    "hasTags" to R.string.fs_rule_op_has_all_tags,
    "hasAnyTag" to R.string.fs_rule_op_has_any_tag,
    "onBudget" to R.string.fs_rule_op_on_budget,
    "offBudget" to R.string.fs_rule_op_off_budget,
    "gt" to R.string.fs_rule_op_greater,
    "gte" to R.string.fs_rule_op_greater_equal,
    "lt" to R.string.fs_rule_op_less,
    "lte" to R.string.fs_rule_op_less_equal,
    "set" to R.string.fs_rule_op_set,
    "set-split-amount" to R.string.fs_rule_op_set_split_amount,
    "prepend-notes" to R.string.fs_rule_op_prepend_notes,
    "append-notes" to R.string.fs_rule_op_append_notes,
    "delete-transaction" to R.string.fs_rule_op_delete_transaction,
    "link-schedule" to R.string.fs_rule_op_link_schedule,
)

/** Localized field label; unknown fields keep RuleSchema's English-derived fallback. */
@Composable
internal fun ruleFieldLabel(field: String): String =
    ruleFieldLabelRes[field]?.let { stringResource(it) } ?: RuleSchema.fieldLabel(field)

/** Localized operator label; unknown operators keep RuleSchema's English-derived fallback. */
@Composable
internal fun ruleOpLabel(op: String): String =
    ruleOpLabelRes[op]?.let { stringResource(it) } ?: RuleSchema.opLabel(op)

@Composable
internal fun ruleStageLabel(stage: Rule.Stage): String = when (stage) {
    Rule.Stage.PRE -> stringResource(R.string.fs_stage_pre)
    Rule.Stage.DEFAULT -> stringResource(R.string.fs_stage_default)
    Rule.Stage.POST -> stringResource(R.string.fs_stage_post)
}

@Composable
private fun conditionSummary(condition: Rule.Condition, names: Map<String, String>) = stringResource(
    R.string.fs_rule_condition_summary,
    ruleFieldLabel(condition.field),
    ruleOpLabel(condition.op),
    valueLabel(condition.value, names),
).trim()

@Composable
private fun actionSummary(action: Rule.Action, names: Map<String, String>) = when (action.op) {
    "set" -> stringResource(
        R.string.fs_rule_action_set,
        action.field?.let { ruleFieldLabel(it) }.orEmpty(),
        valueLabel(action.value, names),
    ) + (action.options[SPLIT_INDEX]?.number?.let {
        stringResource(R.string.fs_split_suffix, it.toInt())
    } ?: "")
    "set-split-amount" -> {
        val line = action.options[SPLIT_INDEX]?.number?.toInt() ?: 1
        val amountText = action.value.number?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
        when (action.options[SPLIT_METHOD]?.text) {
            "fixed-percent" -> stringResource(R.string.fs_split_amount_percent, line, amountText.orEmpty())
            "remainder" -> stringResource(R.string.fs_split_amount_remainder, line)
            "formula" -> stringResource(R.string.fs_split_amount_formula, line)
            else -> stringResource(R.string.fs_split_amount_value, line, valueLabel(action.value, names))
        }
    }
    "prepend-notes" -> stringResource(R.string.fs_rule_action_prepend, action.value.text.orEmpty())
    "append-notes" -> stringResource(R.string.fs_rule_action_append, action.value.text.orEmpty())
    "delete-transaction" -> stringResource(R.string.fs_rule_action_delete)
    else -> ruleOpLabel(action.op)
}

/** A rule amount (stored in the budget currency's minor units) as a plain decimal with that currency's places. */
private fun ruleAmountText(minorUnits: Double): String {
    val places = CurrencyDisplay.decimalPlaces
    return "%.${places}f".format(minorUnits / minorUnitsPerWhole(places))
}

@Composable
private fun valueLabel(value: RuleValue, names: Map<String, String>): String = when (value) {
    is RuleValue.Text -> names[value.value] ?: value.value
    is RuleValue.Number -> ruleAmountText(value.value)
    is RuleValue.Flag -> stringResource(if (value.value) R.string.fs_boolean_true else R.string.fs_boolean_false)
    is RuleValue.ListValue -> value.value.map { valueLabel(it, names) }.joinToString(", ")
    is RuleValue.ObjectValue -> value.value.values.map { valueLabel(it, names) }.joinToString(" – ")
    RuleValue.Null -> ""
}
