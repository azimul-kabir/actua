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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.CategoryDeletePlan

/**
 * Confirms deleting [categoryName]. When Actual would require it ([CategoryDeletePlan.requiresTransfer]),
 * the user must pick a same-type category to move its transactions and budget amounts to, as
 * Actual's `confirm-category-delete` does; [onDelete] receives that category's id, else null.
 */
@Composable
internal fun CategoryDeleteDialog(
    categoryName: String,
    loadPlan: suspend () -> CategoryDeletePlan?,
    onDismiss: () -> Unit,
    onDelete: (transferToId: String?) -> Unit,
) {
    var loaded by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<CategoryDeletePlan?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        plan = loadPlan()
        loaded = true
    }
    val transfer = plan?.takeIf { it.requiresTransfer }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $categoryName?") },
        text = {
            when {
                !loaded -> CircularProgressIndicator()
                transfer == null -> Text("Delete \"$categoryName\"? This cannot be undone.")
                else -> Column {
                    Text(
                        if (transfer.isIncome) {
                            "\"$categoryName\" is used by existing transactions. To delete it, choose another " +
                                "income category to move them to."
                        } else {
                            "\"$categoryName\" is used by existing transactions or has budgeted amounts. To delete " +
                                "it, choose another category to move them to."
                        },
                    )
                    if (transfer.targets.isEmpty()) {
                        Text(
                            "There's no other visible ${if (transfer.isIncome) "income" else "expense"} category to move them to.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 8.dp).selectableGroup(),
                        ) {
                            transfer.targets.forEach { group ->
                                item(key = "group-${group.id}") {
                                    Text(
                                        group.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                    )
                                }
                                items(group.categories, key = { it.id }) { category ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .selectable(
                                                selected = category.id == selectedId,
                                                role = Role.RadioButton,
                                                onClick = { selectedId = category.id },
                                            )
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = category.id == selectedId, onClick = null)
                                        Text(category.name, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canDelete = loaded && (transfer == null || selectedId != null)
            TextButton(enabled = canDelete, onClick = { onDelete(if (transfer == null) null else selectedId) }) {
                Text(
                    "Delete",
                    color = if (canDelete) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
