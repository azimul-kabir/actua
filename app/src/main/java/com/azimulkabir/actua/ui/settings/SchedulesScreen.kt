package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.notifications.ScheduleReminderPlanner
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaGroupedItem
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSheetAction
import com.azimulkabir.actua.ui.components.ActuaSheetCard
import com.azimulkabir.actua.ui.components.ActuaSheetContent
import com.azimulkabir.actua.ui.components.ActuaSheetTitle
import com.azimulkabir.actua.ui.components.GroupPosition
import com.azimulkabir.actua.ui.components.formatDate as formatDisplayDate
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.PillShape
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.ui.theme.success
import com.azimulkabir.actua.ui.theme.warning
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    schedules: List<ScheduleListItem>,
    hideDecimalPlaces: Boolean,
    canAdd: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onFind: () -> Unit,
    onCalendar: () -> Unit,
    onEdit: (String) -> Unit,
    onPost: (String, Boolean) -> Unit,
    onSkip: (String) -> Unit,
    onSetCompleted: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    remindersEnabled: Boolean = false,
    onRemindersEnabledChange: (Boolean) -> Unit = {},
    reminderLeadDays: Int = ScheduleReminderPlanner.DEFAULT_LEAD_DAYS,
    onReminderLeadDaysChange: (Int) -> Unit = {},
) {
    var search by remember { mutableStateOf("") }
    var remindersOpen by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<ScheduleListItem?>(null) }
    var deleteItem by remember { mutableStateOf<ScheduleListItem?>(null) }
    val completedCount = schedules.count { it.schedule.completed }
    val visible = remember(schedules, search, showCompleted) {
        schedules.filter { showCompleted || !it.schedule.completed }.filter { item ->
            search.isBlank() || listOfNotNull(
                item.title, item.accountName, item.payeeName, item.dueDate?.iso,
            ).any { it.contains(search.trim(), ignoreCase = true) }
        }
    }

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.fs_schedules_title), onBack = onBack) {
            IconButton(onClick = { showSearch = !showSearch }) {
                Icon(Icons.Outlined.Search, stringResource(R.string.fs_search_schedules))
            }
            IconButton(onClick = onCalendar) {
                Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.fs_bills_calendar))
            }
            IconButton(onClick = onAdd, enabled = canAdd) {
                Icon(Icons.Outlined.Add, stringResource(R.string.fs_add_schedule))
            }
            Box {
                IconButton(onClick = { optionsOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.fs_schedule_options))
                }
                DropdownMenu(optionsOpen, { optionsOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fs_show_completed)) },
                        trailingIcon = { Checkbox(showCompleted, onCheckedChange = null) },
                        onClick = { showCompleted = !showCompleted; optionsOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fs_find_schedules)) },
                        onClick = { optionsOpen = false; onFind() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fs_reminders)) },
                        leadingIcon = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
                        onClick = { optionsOpen = false; remindersOpen = true },
                    )
                }
            }
        }
        if (showSearch) OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text(stringResource(R.string.fs_search_schedules)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )
        when {
            visible.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(
                        when {
                            search.isNotBlank() -> R.string.fs_no_matching_schedules
                            !showCompleted && completedCount > 0 -> R.string.fs_no_active_schedules
                            else -> R.string.fs_no_schedules
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (search.isBlank() && completedCount == 0) {
                    Text(
                        stringResource(R.string.fs_create_schedule_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onAdd, enabled = canAdd) { Text(stringResource(R.string.fs_new_schedule)) }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xl)) {
                itemsIndexed(visible, key = { _, item -> item.schedule.id }) { index, item ->
                    ActuaGroupedItem(GroupPosition.of(index, visible.size), dividerInset = Spacing.lg) {
                        ScheduleRow(
                            item = item,
                            hideDecimals = hideDecimalPlaces,
                            onClick = { onEdit(item.schedule.id) },
                            onLongClick = { actionItem = item },
                        )
                    }
                }
                if (!showCompleted && completedCount > 0) item("completed-footer") {
                    Text(
                        pluralStringResource(R.plurals.fs_completed_schedules_hidden, completedCount, completedCount),
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
    if (remindersOpen) {
        ScheduleRemindersSheet(
            enabled = remindersEnabled,
            onEnabledChange = onRemindersEnabledChange,
            leadDays = reminderLeadDays,
            onLeadDaysChange = onReminderLeadDaysChange,
            onDismiss = { remindersOpen = false },
        )
    }
    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text(stringResource(R.string.fs_delete_schedule_question)) },
            text = { Text(stringResource(R.string.fs_delete_schedule_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteItem = null
                    onDelete(item.schedule.id)
                }) {
                    Text(stringResource(R.string.fs_delete_schedule), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) { Text(stringResource(R.string.fs_cancel)) }
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
            .padding(start = Spacing.lg, top = 14.dp, end = Spacing.md, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatScheduleAmount(item, hideDecimals),
                    fontWeight = FontWeight.SemiBold,
                    color = if (schedule.postAmount > 0) MaterialTheme.colorScheme.success
                        else MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(item.status)
                Text(
                    item.accountName.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val recurring = schedule.dateCondition is ScheduleDateCondition.Recurring
                val nextDateText = formatDate(item.dueDate) ?: stringResource(R.string.fs_no_next_date)
                Text(
                    if (recurring) stringResource(R.string.fs_repeats_date, nextDateText) else nextDateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = stringResource(R.string.fs_edit_schedule_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleRemindersSheet(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    leadDays: Int,
    onLeadDaysChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle(stringResource(R.string.fs_reminders))
            ActuaSheetCard {
                ActuaFormRow(
                    icon = Icons.Outlined.NotificationsActive,
                    label = stringResource(R.string.fs_schedule_reminders),
                    value = null,
                    caption = stringResource(R.string.fs_schedule_reminders_caption),
                    checked = enabled,
                    onClick = { onEnabledChange(!enabled) },
                )
            }
            if (enabled) {
                ActuaSheetCard(Modifier.selectableGroup()) {
                    ScheduleReminderPlanner.leadDayOptions.forEachIndexed { index, days ->
                        if (index > 0) ActuaCardDivider()
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = days == leadDays, role = Role.RadioButton) { onLeadDaysChange(days) }
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = days == leadDays, onClick = null)
                            Text(reminderLeadLabel(days), Modifier.padding(start = Spacing.md))
                        }
                    }
                }
                Text(
                    stringResource(R.string.fs_schedule_reminders_timing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
            }
        }
    }
}

@Composable
internal fun reminderLeadLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.fs_reminder_lead_same_day)
    1 -> stringResource(R.string.fs_reminder_lead_one_day)
    else -> pluralStringResource(R.plurals.fs_reminder_lead_days, days, days)
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
        ActuaSheetContent {
            ActuaSheetTitle(item.title)
            if (!item.schedule.completed) {
                ActuaSheetCard {
                    ActuaSheetAction(stringResource(R.string.fs_post_transaction), icon = Icons.Outlined.AddCircleOutline, onClick = { onPost(false) })
                    ActuaCardDivider()
                    ActuaSheetAction(stringResource(R.string.fs_post_transaction_today), icon = Icons.Outlined.EventAvailable,
                        onClick = { onPost(true) })
                    if (item.schedule.isRecurring) {
                        ActuaCardDivider()
                        ActuaSheetAction(stringResource(R.string.fs_skip_next_date), icon = Icons.Outlined.SkipNext, onClick = onSkip)
                    }
                }
            }
            ActuaSheetCard {
                ActuaSheetAction(
                    stringResource(if (item.schedule.completed) R.string.fs_restart else R.string.fs_mark_completed),
                    icon = if (item.schedule.completed) Icons.Outlined.RestartAlt
                    else Icons.Outlined.CheckCircleOutline,
                    onClick = onSetCompleted,
                )
                ActuaCardDivider()
                ActuaSheetAction(stringResource(R.string.fs_delete), icon = Icons.Outlined.DeleteOutline, destructive = true, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun StatusChip(status: ScheduleStatus) {
    val color = when (status) {
        ScheduleStatus.MISSED -> MaterialTheme.colorScheme.error
        ScheduleStatus.DUE -> MaterialTheme.colorScheme.warning
        ScheduleStatus.UPCOMING -> MaterialTheme.colorScheme.primary
        ScheduleStatus.PAID -> MaterialTheme.colorScheme.success
        ScheduleStatus.COMPLETED, ScheduleStatus.SCHEDULED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    androidx.compose.material3.Surface(
        color = color.copy(alpha = 0.14f),
        shape = PillShape,
    ) {
        Text(scheduleStatusLabel(status), color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
internal fun scheduleStatusLabel(status: ScheduleStatus): String = stringResource(
    when (status) {
        ScheduleStatus.MISSED -> R.string.fs_status_missed
        ScheduleStatus.DUE -> R.string.fs_status_due
        ScheduleStatus.UPCOMING -> R.string.fs_status_upcoming
        ScheduleStatus.PAID -> R.string.fs_status_paid
        ScheduleStatus.COMPLETED -> R.string.fs_status_completed
        ScheduleStatus.SCHEDULED -> R.string.fs_status_scheduled
    },
)

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

private fun formatDate(day: DayDate?): String? = day?.let {
    formatDisplayDate(LocalDate.of(it.year, it.month, it.day))
}
