package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.PayeeLocationSummary
import com.azimulkabir.actua.ui.components.ActuaGroupedItem
import com.azimulkabir.actua.ui.components.ActuaListRow
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.GroupPosition
import com.azimulkabir.actua.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

private sealed interface LocationDeletion {
    data class One(val location: PayeeLocationSummary) : LocationDeletion
    data class All(val payeeId: String, val payeeName: String) : LocationDeletion
}

@Composable
fun PayeeLocationsScreen(
    locations: List<PayeeLocationSummary>,
    writesSupported: Boolean,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onClearPayee: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeletion by remember { mutableStateOf<LocationDeletion?>(null) }
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Payee Locations", onBack = onBack)
        Text(
            "Saved coordinates stay in your Actual budget and synchronize with it. Actua never tracks location in the background.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
        )
        if (!writesSupported) {
            Text(
                "This budget cannot safely synchronize payee-location changes. Existing locations are read-only.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            )
        }
        if (locations.isEmpty()) {
            Text(
                "No saved payee locations",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(Spacing.screenHorizontal),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
                locations.groupBy { it.payeeId to it.payeeName }.forEach { (payee, entries) ->
                    item(key = "heading-" + payee.first) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(
                                start = Spacing.screenHorizontal + Spacing.xs, end = Spacing.sm, top = Spacing.md,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                payee.second,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { pendingDeletion = LocationDeletion.All(payee.first, payee.second) },
                                enabled = writesSupported,
                            ) { Text("Clear all") }
                        }
                    }
                    itemsIndexed(entries, key = { _, location -> location.id }) { index, location ->
                        ActuaGroupedItem(GroupPosition.of(index, entries.size), dividerInset = Spacing.lg) {
                        ActuaListRow(
                            title = {
                                Text("%.5f, %.5f".format(location.latitude, location.longitude),
                                    style = MaterialTheme.typography.bodyLarge)
                            },
                            subtitle = {
                                Text(DateFormat.getDateTimeInstance().format(Date(location.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailing = {
                                IconButton(
                                    onClick = { pendingDeletion = LocationDeletion.One(location) },
                                    enabled = writesSupported,
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete location")
                                }
                            },
                        )
                        }
                    }
                }
            }
        }
    }

    pendingDeletion?.let { deletion ->
        val description = when (deletion) {
            is LocationDeletion.One -> "Delete this saved location for " + deletion.location.payeeName + "?"
            is LocationDeletion.All -> "Delete all saved locations for " + deletion.payeeName + "?"
        }
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Delete payee location") },
            text = { Text(description) },
            confirmButton = {
                TextButton(onClick = {
                    when (deletion) {
                        is LocationDeletion.One -> onDelete(deletion.location.id)
                        is LocationDeletion.All -> onClearPayee(deletion.payeeId)
                    }
                    pendingDeletion = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) { Text("Cancel") }
            },
        )
    }
}
