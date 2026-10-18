package com.azimulkabir.actua.ui.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup

/**
 * Confirms deleting a category group. Actual Budget requires choosing a destination expense group
 * to receive child categories before deleting the source group.
 */
@Composable
internal fun CategoryGroupDeleteDialog(
    group: ActualCategoryGroup,
    targetGroups: List<ActualCategoryGroup>,
    onDismiss: () -> Unit,
    onDelete: (targetGroupId: String) -> Unit,
) {
    val initialTarget = remember(targetGroups, group) {
        targetGroups.firstOrNull { target ->
            !target.categories.any { tc ->
                group.categories.any { gc -> gc.name.equals(tc.name, ignoreCase = true) }
            }
        }?.id ?: targetGroups.firstOrNull()?.id
    }
    var selectedTargetId by remember(targetGroups) { mutableStateOf(initialTarget) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${group.name}?") },
        text = {
            Column {
                if (group.categories.isEmpty()) {
                    Text("Delete \"${group.name}\"?")
                } else {
                    val count = group.categories.size
                    Text(
                        "\"${group.name}\" contains $count ${if (count == 1) "category" else "categories"}. " +
                            "To delete this group, choose another expense group to move them to.",
                    )
                }

                if (targetGroups.isEmpty()) {
                    Text(
                        "There is no other expense group to move categories to.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    Text(
                        "Move categories to:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .selectableGroup(),
                    ) {
                        items(targetGroups, key = { it.id }) { target ->
                            val hasDuplicateName = target.categories.any { tc ->
                                group.categories.any { gc -> gc.name.equals(tc.name, ignoreCase = true) }
                            }
                            val isSelected = target.id == selectedTargetId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        enabled = !hasDuplicateName,
                                        role = Role.RadioButton,
                                        onClick = { selectedTargetId = target.id },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    enabled = !hasDuplicateName,
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        target.name,
                                        color = if (hasDuplicateName) {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    if (hasDuplicateName) {
                                        Text(
                                            "Contains category with same name",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val validSelection = targetGroups.any { target ->
                target.id == selectedTargetId && !target.categories.any { tc ->
                    group.categories.any { gc -> gc.name.equals(tc.name, ignoreCase = true) }
                }
            }
            val canDelete = targetGroups.isNotEmpty() && selectedTargetId != null && validSelection
            TextButton(
                enabled = canDelete,
                onClick = { selectedTargetId?.let(onDelete) },
            ) {
                Text(
                    "Delete",
                    color = if (canDelete) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
