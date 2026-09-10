package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.schedules.RecurConfig
import com.azimulkabir.actua.data.schedules.ScheduleDiscovery
import com.azimulkabir.actua.ui.components.formatMoneyCents

@Composable
fun FindSchedulesScreen(
    proposals: List<ScheduleDiscovery.DisplayProposal>?,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onCreate: (List<ScheduleDiscovery.Proposal>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedIds by remember(proposals) { mutableStateOf(emptySet<String>()) }
    val selected = proposals.orEmpty().filter { it.proposal.id in selectedIds }.map { it.proposal }
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Text(
                "Find Schedules",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onCreate(selected) }, enabled = selected.isNotEmpty()) {
                Text("Create")
            }
        }
        if (proposals == null) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text("Looking for repeating transactions…", modifier = Modifier.padding(top = 16.dp))
            }
        } else if (proposals.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Nothing found", style = MaterialTheme.typography.titleMedium)
                Text(
                    "No repeating transactions were found in your history.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                "Select repeating transactions to turn into schedules.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(proposals, key = { it.proposal.id }) { item ->
                    val checked = item.proposal.id in selectedIds
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedIds = if (checked) selectedIds - item.proposal.id
                            else selectedIds + item.proposal.id
                        }.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(item.payeeName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(formatMoneyCents(item.proposal.amount, hideDecimalPlaces))
                            }
                            Text(
                                recurrenceLabel(item.proposal.config),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                item.accountName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}

private fun recurrenceLabel(config: RecurConfig): String {
    val unit = when (config.frequency) {
        RecurConfig.Frequency.DAILY -> "day"
        RecurConfig.Frequency.WEEKLY -> "week"
        RecurConfig.Frequency.MONTHLY -> "month"
        RecurConfig.Frequency.YEARLY -> "year"
    }
    val base = if (config.interval == 1) "Every $unit" else "Every ${config.interval} ${unit}s"
    return if (config.exactPatternLabel().isEmpty()) base else "$base · ${config.exactPatternLabel()}"
}

private fun RecurConfig.exactPatternLabel(): String = patterns.joinToString(", ") { pattern ->
    when {
        pattern.type == "day" && pattern.value == -1 -> "last day"
        pattern.type == "day" -> "day ${pattern.value}"
        pattern.value == -1 -> "last ${weekdayLabel(pattern.type)}"
        else -> "${ordinal(pattern.value)} ${weekdayLabel(pattern.type)}"
    }
}

private fun weekdayLabel(code: String) = mapOf(
    "SU" to "Sunday", "MO" to "Monday", "TU" to "Tuesday", "WE" to "Wednesday",
    "TH" to "Thursday", "FR" to "Friday", "SA" to "Saturday",
)[code].orEmpty()

private fun ordinal(value: Int): String {
    val suffix = if (value % 100 in 11..13) "th" else when (value % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$value$suffix"
}
