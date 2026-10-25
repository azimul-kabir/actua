package com.azimulkabir.actua.ui.settings

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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.home.Holiday
import com.azimulkabir.actua.data.home.HolidayCodec
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.formatDate
import com.azimulkabir.actua.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The dates the user treats as non-working days besides the weekend. Device-local, per budget, and
 * display-only: the month start day and weekend-skipping schedules step past them like weekend days.
 */
@Composable
internal fun HolidaysSettings(holidays: List<Holiday>, onChange: (List<Holiday>) -> Unit, modifier: Modifier = Modifier) {
    var adding by remember { mutableStateOf(false) }
    Column(modifier.padding(top = Spacing.md)) {
        ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
            if (holidays.isEmpty()) {
                Text(
                    stringResource(R.string.settings_holidays_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = Spacing.lg).testTag("holidaysEmpty"),
                )
                ActuaCardDivider(inset = Spacing.screenHorizontal)
            }
            holidays.forEach { holiday ->
                HolidayRow(holiday) { onChange(holidays - holiday) }
                ActuaCardDivider(inset = Spacing.screenHorizontal)
            }
            TextButton(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("addHoliday"),
            ) {
                Icon(Icons.Outlined.Add, null)
                Text(stringResource(R.string.settings_holidays_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            stringResource(R.string.settings_holidays_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal + Spacing.xs, vertical = Spacing.sm),
        )
    }
    if (adding) {
        AddHolidayDialog(
            onDismiss = { adding = false },
            onAdd = { holiday ->
                onChange(HolidayCodec.normalize(holidays + holiday))
                adding = false
            },
        )
    }
}

@Composable
private fun HolidayRow(holiday: Holiday, onRemove: () -> Unit) {
    val date = formatDate(holiday.date)
    val removeLabel = stringResource(R.string.settings_holidays_remove, date)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.md)) {
            Text(date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (holiday.label.isNotBlank()) {
                Text(
                    holiday.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = removeLabel }) {
            Icon(Icons.Outlined.Delete, null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddHolidayDialog(onDismiss: () -> Unit, onAdd: (Holiday) -> Unit) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    var label by remember { mutableStateOf("") }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = picker.selectedDateMillis != null,
                onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        onAdd(Holiday(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(), label.trim()))
                    }
                },
            ) { Text(stringResource(R.string.fs_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DatePicker(picker)
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(MAX_LABEL_LENGTH) },
                label = { Text(stringResource(R.string.settings_holidays_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

private const val MAX_LABEL_LENGTH = 60
