package com.azimulkabir.actua.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.AccountGroupReorderPlanner
import com.azimulkabir.actua.data.budget.model.ActualAccountGroup
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaSheetCard
import com.azimulkabir.actua.ui.components.ActuaSheetContent
import com.azimulkabir.actua.ui.components.ActuaSheetTitle
import com.azimulkabir.actua.ui.components.RenameDialog
import com.azimulkabir.actua.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccountGroupsSheet(
    groups: List<ActualAccountGroup>,
    onDismiss: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onMoveGroup: (AccountGroupReorderPlanner.GroupMove) -> Boolean,
) {
    val sortedGroups = remember(groups) { groups.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
    var creatingGroup by remember { mutableStateOf(false) }
    var renamingGroup by remember { mutableStateOf<ActualAccountGroup?>(null) }
    var deletingGroup by remember { mutableStateOf<ActualAccountGroup?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActuaSheetTitle("Manage account groups")
                IconButton(onClick = { creatingGroup = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "New account group")
                }
            }

            if (sortedGroups.isEmpty()) {
                ActuaSheetCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xl, horizontal = Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "No custom account groups yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { creatingGroup = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text("New account group", modifier = Modifier.padding(start = Spacing.xs))
                        }
                    }
                }
            } else {
                ActuaSheetCard {
                    sortedGroups.forEachIndexed { index, group ->
                        if (index > 0) ActuaCardDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = Spacing.sm),
                            )
                            Text(
                                group.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    AccountGroupReorderPlanner.moveGroupUp(sortedGroups, group.id)?.let {
                                        onMoveGroup(it.second)
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.semantics {
                                    contentDescription = "Move ${group.name} group up"
                                },
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null)
                            }
                            IconButton(
                                onClick = {
                                    AccountGroupReorderPlanner.moveGroupDown(sortedGroups, group.id)?.let {
                                        onMoveGroup(it.second)
                                    }
                                },
                                enabled = index < sortedGroups.size - 1,
                                modifier = Modifier.semantics {
                                    contentDescription = "Move ${group.name} group down"
                                },
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
                            }
                            IconButton(
                                onClick = { renamingGroup = group },
                                modifier = Modifier.semantics {
                                    contentDescription = "Rename ${group.name} group"
                                },
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                            }
                            IconButton(
                                onClick = { deletingGroup = group },
                                modifier = Modifier.semantics {
                                    contentDescription = "Delete ${group.name} group"
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creatingGroup) {
        RenameDialog(
            title = "New account group",
            currentName = "",
            onDismiss = { creatingGroup = false },
            onSave = { name ->
                onCreateGroup(name)
                creatingGroup = false
            },
        )
    }

    renamingGroup?.let { group ->
        RenameDialog(
            title = "Rename account group",
            currentName = group.name,
            onDismiss = { renamingGroup = null },
            onSave = { name ->
                onRenameGroup(group.id, name)
                renamingGroup = null
            },
        )
    }

    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text("Delete account group") },
            text = {
                Text("Delete \"${group.name}\"? Accounts in this group will become ungrouped.")
            },
            dismissButton = {
                TextButton(onClick = { deletingGroup = null }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGroup(group.id)
                    deletingGroup = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}
