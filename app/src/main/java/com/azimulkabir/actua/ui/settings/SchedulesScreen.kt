package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import com.azimulkabir.actua.ui.components.formatMoneyCents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SchedulesScreen(
    schedules: List<ScheduleListItem>,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onSetCompleted: (String, Boolean) -> Unit,
    onSkip: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ScheduleListItem?>(null) }
    val completedCount = schedules.count { it.schedule.completed }
    val visible = remember(schedules, search, showCompleted) {
        schedules.filter { showCompleted || !it.schedule.completed }.filter { item ->
            search.isBlank() || listOfNotNull(
                item.title, item.accountName, item.payeeName, item.schedule.nextDate?.iso,
            ).any { it.contains(search.trim(), ignoreCase = true) }
        }
    }

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Text("Scheduled Transactions", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { showSearch = !showSearch }) {
                Icon(Icons.Outlined.Search, "Search schedules")
            }
            Box {
                IconButton(onClick = { optionsOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, "Schedule options")
                }
                DropdownMenu(optionsOpen, { optionsOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Show completed") },
                        trailingIcon = { Checkbox(showCompleted, onCheckedChange = null) },
                        onClick = { showCompleted = !showCompleted; optionsOpen = false },
                    )
                }
            }
        }
        if (showSearch) OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text("Search schedules") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )
        when {
            visible.isEmpty() -> Text(
                if (search.isNotBlank()) "No matching schedules"
                else if (!showCompleted && completedCount > 0) "No active schedules"
                else "No scheduled transactions",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.schedule.id }) { item ->
                    ScheduleRow(item, hideDecimalPlaces, onSetCompleted, onSkip) {
                        pendingDelete = item
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }
                if (!showCompleted && completedCount > 0) item("completed-footer") {
                    Text("$completedCount completed ${if (completedCount == 1) "schedule" else "schedules"} hidden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp))
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this schedule?") },
            text = { Text("Transactions already created by this schedule will be kept.") },
            confirmButton = { TextButton(onClick = {
                onDelete(item.schedule.id); pendingDelete = null
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ScheduleRow(
    item: ScheduleListItem,
    hideDecimals: Boolean,
    onSetCompleted: (String, Boolean) -> Unit,
    onSkip: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val schedule = item.schedule
    Column(
        Modifier.fillMaxWidth().clickable { menuOpen = true }
            .padding(start = 20.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    item.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(item.status)
            }
            Text(
                formatScheduleAmount(item, hideDecimals),
                fontWeight = FontWeight.SemiBold,
                color = if (schedule.postAmount > 0) Color(0xFF2E7D32)
                    else MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.accountName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            val recurring = schedule.dateCondition is ScheduleDateCondition.Recurring
            Text(
                (if (recurring) "Repeats · " else "") + formatDate(schedule.nextDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, "Schedule actions")
                }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    if (!schedule.completed && schedule.isRecurring) DropdownMenuItem(
                        text = { Text("Skip next date") }, onClick = {
                            menuOpen = false; onSkip(schedule.id)
                        })
                    DropdownMenuItem(
                        text = { Text(if (schedule.completed) "Restart" else "Mark completed") },
                        onClick = { menuOpen = false; onSetCompleted(schedule.id, !schedule.completed) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ScheduleStatus) {
    val color = when (status) {
        ScheduleStatus.MISSED -> MaterialTheme.colorScheme.error
        ScheduleStatus.DUE -> Color(0xFFF57C00)
        ScheduleStatus.UPCOMING -> MaterialTheme.colorScheme.primary
        ScheduleStatus.PAID -> Color(0xFF2E7D32)
        ScheduleStatus.COMPLETED, ScheduleStatus.SCHEDULED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    androidx.compose.material3.Surface(
        color = color.copy(alpha = 0.14f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
    ) {
        Text(status.name.lowercase().replaceFirstChar(Char::uppercase), color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

private fun formatScheduleAmount(item: ScheduleListItem, hideDecimals: Boolean): String {
    val schedule = item.schedule
    return when (val amount = schedule.amount) {
        is ScheduledAmount.Range -> {
            val low = minOf(amount.first, amount.second)
            val high = maxOf(amount.first, amount.second)
            "${formatMoneyCents(low, hideDecimals)} – ${formatMoneyCents(high, hideDecimals)}"
        }
        else -> (if (schedule.amountOp == ScheduleAmountOp.APPROXIMATE) "~ " else "") +
            formatMoneyCents(schedule.postAmount, hideDecimals)
    }
}

private fun formatDate(day: DayDate?): String = day?.let {
    LocalDate.of(it.year, it.month, it.day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
} ?: "No next date"
