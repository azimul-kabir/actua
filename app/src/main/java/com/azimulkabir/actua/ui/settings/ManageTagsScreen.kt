package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.ui.navigation.floatingBarClearance
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.model.ActualTag
import com.azimulkabir.actua.ui.components.ActuaGroupedItem
import com.azimulkabir.actua.ui.components.ActuaListRow
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.GroupPosition
import com.azimulkabir.actua.ui.theme.Spacing

// Mirrors Actual's web tag color picker (packages/component-library/src/ColorPicker.tsx DEFAULT_COLOR_SET)
// so tag colors created/edited on Android match the swatches shown in the web app.
private val tagPalette = listOf(
    "#690CB0", "#D32F2F", "#C2185B", "#7B1FA2", "#512DA8", "#303F9F", "#1976D2", "#0288D1", "#0097A7", "#00796B",
    "#388E3C", "#689F38", "#AFB42B", "#FBC02D", "#FFA000", "#F57C00", "#E64A19", "#5D4037", "#616161", "#455A64",
    "#FF6666", "#FF99FF", "#C39DDF", "#6666FF", "#B2FFFF", "#99cb99", "#FFFF7F", "#FFAB66", "#D4B89C", "#BFBFBF",
    "#FFAEAE", "#FFCCFF", "#E4D4FF", "#B0B0FF", "#D8FFFF", "#CFE5CF", "#FFFFB2", "#FFD5B3", "#E4D3C3", "#DADADA",
)
internal fun isValidManagedTagName(name: String): Boolean = name.isNotBlank() && name.none { it == '#' || it.isWhitespace() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTagsScreen(
    tags: List<ActualTag>, hiddenSupported: Boolean, onBack: () -> Unit,
    onCreate: (String, String?, String?, Boolean) -> Unit,
    onUpdate: (ActualTag, String, String?, String?, Boolean) -> Unit,
    onDelete: (ActualTag) -> Unit,
    onDeleteAll: (List<ActualTag>) -> Unit = {},
    onSetHidden: (List<ActualTag>, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }; var editing by remember { mutableStateOf<ActualTag?>(null) }
    var creating by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf<ActualTag?>(null) }
    var viewing by remember { mutableStateOf<ActualTag?>(null) }; var actionsFor by remember { mutableStateOf<ActualTag?>(null) }
    val filtered = remember(tags, query) { tags.filter { query.isBlank() || it.tag.contains(query, true) || it.description.orEmpty().contains(query, true) } }
    // Multi-select for the bulk actions (Actual's tags-delete-all / tags-hide-all / tags-unhide-all). Ids survive
    // rotation; tags that disappear after a sync or a write drop out of the selection.
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var deletingAll by remember { mutableStateOf<List<ActualTag>?>(null) }
    val selected = remember(tags, selectedIds) { tags.filter { it.id in selectedIds } }
    val selecting = selected.isNotEmpty()
    fun toggle(tag: ActualTag) { selectedIds = if (tag.id in selectedIds) selectedIds - tag.id else selectedIds + tag.id }
    // Back leaves selection mode before it leaves the screen.
    BackHandler(enabled = selecting && viewing == null) { selectedIds = emptyList() }

    viewing?.let { tag ->
        ManagedTagTransactionsScreen(tag = tag, onBack = { viewing = null }, modifier = modifier)
        return
    }

    Scaffold(modifier = modifier, topBar = {
        if (selecting) {
            ActuaScreenHeader(
                title = pluralStringResource(R.plurals.fs_selected_count, selected.size, selected.size),
                onBack = { selectedIds = emptyList() },
                actions = {
                    if (hiddenSupported) {
                        TextButton(onClick = { onSetHidden(selected, true); selectedIds = emptyList() }, enabled = selected.any { !it.hidden }) {
                            Text(stringResource(R.string.fs_hide_tags))
                        }
                        TextButton(onClick = { onSetHidden(selected, false); selectedIds = emptyList() }, enabled = selected.any { it.hidden }) {
                            Text(stringResource(R.string.fs_show_tags))
                        }
                    }
                    TextButton(onClick = { deletingAll = selected }) { Text(stringResource(R.string.fs_delete)) }
                },
            )
        } else {
            ActuaScreenHeader(title = stringResource(R.string.fs_manage_tags), onBack = onBack)
        }
    }, floatingActionButton = {
        if (!selecting) FloatingActionButton(onClick = { creating = true }, modifier = Modifier.padding(bottom = floatingBarClearance())) { Icon(Icons.Outlined.Add, stringResource(R.string.fs_create_tag)) }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.fs_search_tags)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm))
            if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (tags.isEmpty()) R.string.fs_no_managed_tags else R.string.fs_no_matching_tags),
                )
            }
            else LazyColumn(contentPadding = PaddingValues(top = Spacing.sm, bottom = floatingBarClearance() + 88.dp)) { itemsIndexed(filtered, key = { _, tag -> tag.id }) { index, tag ->
                val isSelected = tag.id in selectedIds
                val selectionDescription = if (selecting) stringResource(
                    if (isSelected) R.string.fs_selected_tag else R.string.fs_not_selected_tag, tag.tag,
                ) else null
                ActuaGroupedItem(GroupPosition.of(index, filtered.size), dividerInset = Spacing.lg + 18.dp + Spacing.md) {
                ActuaListRow(
                    title = { Text("#${tag.tag}", style = MaterialTheme.typography.bodyLarge) },
                    subtitle = { val detail = listOfNotNull(tag.description?.takeIf(String::isNotBlank), if (tag.hidden) stringResource(R.string.fs_hidden) else null); if (detail.isNotEmpty()) Text(detail.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leading = if (selecting) {
                        { Checkbox(checked = isSelected, onCheckedChange = null) }
                    } else {
                        { TagColorDot(tag.color) }
                    },
                    trailing = if (selecting) null else {
                        { Box {
                            IconButton(onClick = { actionsFor = tag }) { Icon(Icons.Outlined.Edit, stringResource(R.string.fs_tag_actions, tag.tag)) }
                            DropdownMenu(expanded = actionsFor?.id == tag.id, onDismissRequest = { actionsFor = null }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.fs_view_transactions)) }, leadingIcon = { Icon(Icons.Outlined.ReceiptLong, null) }, onClick = { actionsFor = null; viewing = tag })
                                DropdownMenuItem(text = { Text(stringResource(R.string.fs_edit)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { actionsFor = null; editing = tag })
                                DropdownMenuItem(text = { Text(stringResource(R.string.fs_select)) }, leadingIcon = { Icon(Icons.Outlined.CheckBox, null) }, onClick = { actionsFor = null; toggle(tag) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.fs_delete)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { actionsFor = null; deleting = tag })
                            }
                        } }
                    },
                    onClick = if (selecting) ({ toggle(tag) }) else ({ viewing = tag }),
                    onLongClick = { toggle(tag) },
                    modifier = if (selectionDescription != null) Modifier.semantics {
                        contentDescription = selectionDescription
                        this.selected = isSelected
                    } else Modifier,
                )
                }
            } }
        }
    }
    if (creating) TagEditorDialog(stringResource(R.string.fs_create_tag_title), null, hiddenSupported, { creating = false }) { n,c,d,h -> onCreate(n,c,d,h); creating = false }
    editing?.let { tag -> TagEditorDialog(stringResource(R.string.fs_edit_tag_title, tag.tag), tag, hiddenSupported, { editing = null }) { n,c,d,h -> onUpdate(tag,n,c,d,h); editing = null } }
    deletingAll?.let { targets ->
        AlertDialog(
            onDismissRequest = { deletingAll = null },
            title = {
                Text(
                    if (targets.size == 1) stringResource(R.string.fs_delete_tag_title, targets.single().tag)
                    else pluralStringResource(R.plurals.fs_delete_tags_question, targets.size, targets.size),
                )
            },
            text = {
                Text(
                    if (targets.size == 1) stringResource(R.string.fs_delete_tag_message, targets.single().tag)
                    else stringResource(R.string.fs_delete_tags_message),
                )
            },
            confirmButton = { TextButton(onClick = { onDeleteAll(targets); deletingAll = null; selectedIds = emptyList() }) { Text(stringResource(R.string.fs_delete)) } },
            dismissButton = { TextButton(onClick = { deletingAll = null }) { Text(stringResource(R.string.fs_cancel)) } },
        )
    }
    deleting?.let { tag -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.fs_delete_tag_title, tag.tag)) }, text = { Text(stringResource(R.string.fs_delete_tag_message, tag.tag)) }, confirmButton = { TextButton(onClick = { onDelete(tag); deleting = null }) { Text(stringResource(R.string.fs_delete)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.fs_cancel)) } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TagEditorDialog(title: String, existing: ActualTag?, hiddenSupported: Boolean, onDismiss: () -> Unit, onSave: (String, String?, String?, Boolean) -> Unit) {
    var name by remember(existing) { mutableStateOf(existing?.tag.orEmpty()) }; var color by remember(existing) { mutableStateOf(existing?.color ?: tagPalette.first()) }
    var description by remember(existing) { mutableStateOf(existing?.description.orEmpty()) }; var hidden by remember(existing) { mutableStateOf(existing?.hidden ?: false) }
    val valid = isValidManagedTagName(name)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.fs_name)) }, singleLine = true, isError = name.isNotEmpty() && !valid)
        OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.fs_description)) }); Text(stringResource(R.string.fs_color), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tagPalette.forEach { option -> Box(Modifier.size(if (color == option) 36.dp else 32.dp).background(parseTagColor(option), CircleShape).clickable { color = option }) }
        }
        if (hiddenSupported) Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.fs_hidden), modifier = Modifier.weight(1f)); Switch(hidden, { hidden = it }) }
    } }, confirmButton = { Button(onClick = { onSave(name.trim(), color, description.trim().ifBlank { null }, hidden) }, enabled = valid) { Text(stringResource(R.string.fs_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fs_cancel)) } })
}

@Composable private fun TagColorDot(value: String?) { Box(Modifier.size(18.dp).background(parseTagColor(value), CircleShape)) }
internal fun parseManagedTagColor(value: String?): Color = runCatching { Color(android.graphics.Color.parseColor(value ?: "#808080")) }.getOrDefault(Color.Gray)
private fun parseTagColor(value: String?): Color = parseManagedTagColor(value)
