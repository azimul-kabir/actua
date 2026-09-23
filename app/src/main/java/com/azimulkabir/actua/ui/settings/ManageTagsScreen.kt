package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.model.ActualTag

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
    onDelete: (ActualTag) -> Unit, modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }; var editing by remember { mutableStateOf<ActualTag?>(null) }
    var creating by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf<ActualTag?>(null) }
    var viewing by remember { mutableStateOf<ActualTag?>(null) }; var actionsFor by remember { mutableStateOf<ActualTag?>(null) }
    val filtered = remember(tags, query) { tags.filter { query.isBlank() || it.tag.contains(query, true) || it.description.orEmpty().contains(query, true) } }

    viewing?.let { tag ->
        ManagedTagTransactionsScreen(tag = tag, onBack = { viewing = null }, modifier = modifier)
        return
    }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("Manage Tags") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }) }, floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, "Create tag") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(query, { query = it }, label = { Text("Search tags") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (tags.isEmpty()) "No managed tags yet" else "No tags match your search") }
            else LazyColumn { items(filtered, key = { it.id }) { tag ->
                ListItem(
                    headlineContent = { Text("#${tag.tag}") },
                    supportingContent = { val detail = listOfNotNull(tag.description?.takeIf(String::isNotBlank), if (tag.hidden) "Hidden" else null); if (detail.isNotEmpty()) Text(detail.joinToString(" · ")) },
                    leadingContent = { TagColorDot(tag.color) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { actionsFor = tag }) { Icon(Icons.Outlined.Edit, "Actions for #${tag.tag}") }
                            DropdownMenu(expanded = actionsFor?.id == tag.id, onDismissRequest = { actionsFor = null }) {
                                DropdownMenuItem(text = { Text("View transactions") }, leadingIcon = { Icon(Icons.Outlined.ReceiptLong, null) }, onClick = { actionsFor = null; viewing = tag })
                                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { actionsFor = null; editing = tag })
                                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { actionsFor = null; deleting = tag })
                            }
                        }
                    },
                    modifier = Modifier.clickable { viewing = tag },
                )
            } }
        }
    }
    if (creating) TagEditorDialog("Create Tag", null, hiddenSupported, { creating = false }) { n,c,d,h -> onCreate(n,c,d,h); creating = false }
    editing?.let { tag -> TagEditorDialog("Edit #${tag.tag}", tag, hiddenSupported, { editing = null }) { n,c,d,h -> onUpdate(tag,n,c,d,h); editing = null } }
    deleting?.let { tag -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete #${tag.tag}?") }, text = { Text("This deletes the managed tag metadata. Existing #${tag.tag} text in historical transaction notes will remain as an unmanaged hashtag.") }, confirmButton = { TextButton(onClick = { onDelete(tag); deleting = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TagEditorDialog(title: String, existing: ActualTag?, hiddenSupported: Boolean, onDismiss: () -> Unit, onSave: (String, String?, String?, Boolean) -> Unit) {
    var name by remember(existing) { mutableStateOf(existing?.tag.orEmpty()) }; var color by remember(existing) { mutableStateOf(existing?.color ?: tagPalette.first()) }
    var description by remember(existing) { mutableStateOf(existing?.description.orEmpty()) }; var hidden by remember(existing) { mutableStateOf(existing?.hidden ?: false) }
    val valid = isValidManagedTagName(name)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, isError = name.isNotEmpty() && !valid)
        OutlinedTextField(description, { description = it }, label = { Text("Description") }); Text("Color", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tagPalette.forEach { option -> Box(Modifier.size(if (color == option) 36.dp else 32.dp).background(parseTagColor(option), CircleShape).clickable { color = option }) }
        }
        if (hiddenSupported) Row(verticalAlignment = Alignment.CenterVertically) { Text("Hidden", modifier = Modifier.weight(1f)); Switch(hidden, { hidden = it }) }
    } }, confirmButton = { Button(onClick = { onSave(name.trim(), color, description.trim().ifBlank { null }, hidden) }, enabled = valid) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun TagColorDot(value: String?) { Box(Modifier.size(18.dp).background(parseTagColor(value), CircleShape)) }
internal fun parseManagedTagColor(value: String?): Color = runCatching { Color(android.graphics.Color.parseColor(value ?: "#808080")) }.getOrDefault(Color.Gray)
private fun parseTagColor(value: String?): Color = parseManagedTagColor(value)
