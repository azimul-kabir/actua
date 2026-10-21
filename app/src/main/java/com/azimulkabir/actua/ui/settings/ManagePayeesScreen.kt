package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.model.ActualManagedPayee
import com.azimulkabir.actua.ui.components.ActuaGroupedItem
import com.azimulkabir.actua.ui.components.ActuaListRow
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSectionHeader
import com.azimulkabir.actua.ui.components.GroupPosition
import com.azimulkabir.actua.ui.theme.Spacing

/** Ordinary payees, then transfer payees, each filtered by name (case-insensitive substring). */
internal data class ManagedPayeeSections(
    val ordinary: List<ActualManagedPayee>,
    val transfers: List<ActualManagedPayee>,
)

internal fun managedPayeeSections(payees: List<ActualManagedPayee>, query: String): ManagedPayeeSections {
    val term = query.trim()
    val matching = payees.filter { term.isEmpty() || it.name.contains(term, ignoreCase = true) }
    return ManagedPayeeSections(
        ordinary = matching.filter { it.transferAccountId == null },
        transfers = matching.filter { it.transferAccountId != null },
    )
}

/**
 * Actual's Payees page on Android: rename, favorite, per-payee category learning, delete, and merge
 * or delete several payees after a long press. Transfer payees belong to their account and are
 * listed read-only.
 */
@Composable
fun ManagePayeesScreen(
    payees: List<ActualManagedPayee>,
    learnCategoriesEnabled: Boolean,
    onBack: () -> Unit,
    onSetLearnCategoriesEnabled: (Boolean) -> Unit,
    onRename: (ActualManagedPayee, String) -> Unit,
    onSetFavorite: (ActualManagedPayee, Boolean) -> Unit,
    onSetLearnCategories: (ActualManagedPayee, Boolean) -> Unit,
    onDelete: (List<ActualManagedPayee>) -> Unit,
    onMerge: (target: ActualManagedPayee, merged: List<ActualManagedPayee>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var actionsFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<ActualManagedPayee?>(null) }
    var deleting by remember { mutableStateOf<List<ActualManagedPayee>?>(null) }
    var merging by remember { mutableStateOf<List<ActualManagedPayee>?>(null) }
    val sections = remember(payees, query) { managedPayeeSections(payees, query) }
    val byId = remember(payees) { payees.associateBy(ActualManagedPayee::id) }
    // Drop selections that disappeared after a sync or a write.
    val selected = selectedIds.mapNotNull(byId::get).filter { it.transferAccountId == null }
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = selecting) { selectedIds = emptyList() }

    fun toggle(payee: ActualManagedPayee) {
        selectedIds = if (payee.id in selectedIds) selectedIds - payee.id else selectedIds + payee.id
    }

    Column(modifier.fillMaxSize().imePadding()) {
        if (selecting) {
            ActuaScreenHeader(
                title = pluralStringResource(R.plurals.fs_selected_count, selected.size, selected.size),
                onBack = { selectedIds = emptyList() },
                actions = {
                    TextButton(onClick = { merging = selected }, enabled = selected.size >= 2) { Text(stringResource(R.string.fs_merge)) }
                    TextButton(onClick = { deleting = selected }) { Text(stringResource(R.string.fs_delete)) }
                },
            )
        } else {
            ActuaScreenHeader(title = stringResource(R.string.fs_payees_title), onBack = onBack)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.fs_search_payees)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
            item(key = "learning") {
                ActuaGroupedItem(GroupPosition.of(0, 1)) {
                    val categoryLearning = stringResource(R.string.fs_payee_category_learning)
                    ActuaListRow(
                        title = { Text(categoryLearning, style = MaterialTheme.typography.bodyLarge) },
                        subtitle = {
                            Text(
                                stringResource(R.string.fs_payee_category_learning_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailing = {
                            Switch(
                                checked = learnCategoriesEnabled,
                                onCheckedChange = onSetLearnCategoriesEnabled,
                                modifier = Modifier.semantics { contentDescription = categoryLearning },
                            )
                        },
                    )
                }
            }
            item(key = "payees-header") { ActuaSectionHeader(stringResource(R.string.fs_payees_title)) }
            if (sections.ordinary.isEmpty()) {
                item(key = "payees-empty") {
                    Text(
                        stringResource(
                            if (payees.none { it.transferAccountId == null }) R.string.fs_no_payees_yet
                            else R.string.fs_no_payees_match,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal + Spacing.xs, vertical = Spacing.sm),
                    )
                }
            }
            itemsIndexed(sections.ordinary, key = { _, payee -> payee.id }) { index, payee ->
                val isSelected = payee.id in selectedIds
                val selectionDescription = if (selecting) stringResource(
                    if (isSelected) R.string.fs_selected_payee else R.string.fs_not_selected_payee,
                    payee.name,
                ) else null
                ActuaGroupedItem(GroupPosition.of(index, sections.ordinary.size)) {
                    ActuaListRow(
                        title = {
                            Text(
                                payee.name.ifBlank { stringResource(R.string.fs_unnamed_payee) }, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        subtitle = if (payee.learnCategories) null else {
                            {
                                Text(stringResource(R.string.fs_payee_category_learning_off), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leading = if (selecting) {
                            { Checkbox(checked = isSelected, onCheckedChange = null) }
                        } else if (payee.favorite) {
                            { Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.fs_favorite), tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        trailing = if (selecting) null else {
                            {
                                Box {
                                    IconButton(onClick = { actionsFor = payee.id }) {
                                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.fs_payee_actions, payee.name))
                                    }
                                    DropdownMenu(expanded = actionsFor == payee.id, onDismissRequest = { actionsFor = null }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.fs_rename)) }, onClick = { actionsFor = null; renaming = payee })
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(
                                                        if (payee.favorite) R.string.fs_remove_from_favorites
                                                        else R.string.fs_add_to_favorites,
                                                    ),
                                                )
                                            },
                                            leadingIcon = { Icon(if (payee.favorite) Icons.Outlined.StarBorder else Icons.Filled.Star, null) },
                                            onClick = { actionsFor = null; onSetFavorite(payee, !payee.favorite) },
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(
                                                        if (payee.learnCategories) R.string.fs_turn_off_category_learning
                                                        else R.string.fs_turn_on_category_learning,
                                                    ),
                                                )
                                            },
                                            onClick = { actionsFor = null; onSetLearnCategories(payee, !payee.learnCategories) },
                                        )
                                        DropdownMenuItem(text = { Text(stringResource(R.string.fs_select)) }, onClick = { actionsFor = null; toggle(payee) })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.fs_delete)) }, onClick = { actionsFor = null; deleting = listOf(payee) })
                                    }
                                }
                            }
                        },
                        onClick = if (selecting) ({ toggle(payee) }) else null,
                        onLongClick = { toggle(payee) },
                        modifier = if (selectionDescription != null) Modifier.semantics {
                            contentDescription = selectionDescription
                        } else Modifier,
                    )
                }
            }
            if (sections.transfers.isNotEmpty()) {
                item(key = "transfers-header") { ActuaSectionHeader(stringResource(R.string.fs_transfer_payees)) }
                item(key = "transfers-note") {
                    Text(
                        stringResource(R.string.fs_transfer_payees_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal + Spacing.xs, vertical = Spacing.xs),
                    )
                }
                itemsIndexed(sections.transfers, key = { _, payee -> payee.id }) { index, payee ->
                    ActuaGroupedItem(GroupPosition.of(index, sections.transfers.size)) {
                        ActuaListRow(title = { Text(payee.name, style = MaterialTheme.typography.bodyLarge) })
                    }
                }
            }
        }
    }

    renaming?.let { payee ->
        var name by remember(payee.id) { mutableStateOf(payee.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.fs_rename_payee)) },
            text = {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.fs_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { onRename(payee, name.trim()); renaming = null }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.fs_save))
                }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.fs_cancel)) } },
        )
    }

    deleting?.let { targets ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = {
                Text(
                    if (targets.size == 1) stringResource(R.string.fs_delete_payee_question, targets.single().name)
                    else pluralStringResource(R.plurals.fs_delete_payees_question, targets.size, targets.size),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (targets.size == 1) R.string.fs_delete_payees_message_this
                        else R.string.fs_delete_payees_message_these,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(targets); deleting = null; selectedIds = emptyList() }) { Text(stringResource(R.string.fs_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.fs_cancel)) } },
        )
    }

    merging?.let { targets ->
        var targetId by remember(targets) { mutableStateOf(targets.first().id) }
        AlertDialog(
            onDismissRequest = { merging = null },
            title = { Text(stringResource(R.string.fs_merge_payees_title, targets.size)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.fs_merge_payees_keep))
                    LazyColumn(Modifier.heightIn(max = 320.dp).selectableGroup()) {
                        items(targets, key = ActualManagedPayee::id) { payee ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(selected = payee.id == targetId, role = Role.RadioButton) { targetId = payee.id }
                                    .padding(vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = payee.id == targetId, onClick = null)
                                Text(payee.name, modifier = Modifier.padding(start = Spacing.sm))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = targets.first { it.id == targetId }
                    onMerge(target, targets.filter { it.id != targetId })
                    merging = null
                    selectedIds = emptyList()
                }) { Text(stringResource(R.string.fs_merge_payees)) }
            },
            dismissButton = { TextButton(onClick = { merging = null }) { Text(stringResource(R.string.fs_cancel)) } },
        )
    }
}
