package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    schedules: List<ScheduleListItem>,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onPost: (String, Boolean) -> Unit,
    onSkip: (String) -> Unit,
    onSetCompleted: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<ScheduleListItem?>(null) }
    var deleteItem by remember { mutableStateOf<ScheduleListItem?>(null) }
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
                    ScheduleRow(
                        item = item,
                        hideDecimals = hideDecimalPlaces,
                        onClick = { onEdit(item.schedule.id) },
                        onLongClick = { actionItem = item },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
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

    actionItem?.let { item ->
        ScheduleActionsSheet(
            item = item,
            onDismiss = { actionItem = null },
            onPost = { today ->
                actionItem = null
                onPost(item.schedule.id, today)
            },
            onSkip = {
                actionItem = null
                onSkip(item.schedule.id)
            },
            onSetCompleted = {
                actionItem = null
                onSetCompleted(item.schedule.id, !item.schedule.completed)
            },
            onDelete = {
                actionItem = null
                deleteItem = item
            },
        )
    }
    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("Delete this schedule?") },
            text = { Text("Transactions this schedule already created will be kept.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteItem = null
                    onDelete(item.schedule.id)
                }) {
                    Text("Delete schedule", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduleRow(
    item: ScheduleListItem,
    hideDecimals: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val schedule = item.schedule
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            }
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "Edit schedule",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleActionsSheet(
    item: ScheduleListItem,
    onDismiss: () -> Unit,
    onPost: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onSetCompleted: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (!item.schedule.completed) {
            ScheduleAction("Post Transaction", Icons.Outlined.AddCircleOutline) {
                onPost(false)
            }
            ScheduleAction("Post Transaction Today", Icons.Outlined.EventAvailable) {
                onPost(true)
            }
            if (item.schedule.isRecurring) {
                ScheduleAction("Skip Next Date", Icons.Outlined.SkipNext, onClick = onSkip)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        ScheduleAction(
            if (item.schedule.completed) "Restart" else "Mark Completed",
            if (item.schedule.completed) Icons.Outlined.RestartAlt
            else Icons.Outlined.CheckCircleOutline,
            onClick = onSetCompleted,
        )
        ScheduleAction(
            "Delete",
            Icons.Outlined.DeleteOutline,
            destructive = true,
            onClick = onDelete,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ScheduleAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(label, color = color) },
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
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
