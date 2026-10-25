package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.home.MonthStartRule
import com.azimulkabir.actua.data.home.StartDayAdjustment
import com.azimulkabir.actua.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle

/**
 * Stepper for the day (1–31) the month-based summaries and report presets start on; 1 keeps the
 * calendar month. Can move a start that lands on a weekend day to the previous or next working day,
 * with the weekend days configurable. Device-local, so nothing here is synced.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthStartDayRow(rule: MonthStartRule, onChange: (MonthStartRule) -> Unit, modifier: Modifier = Modifier) {
    val startDay = rule.day
    val locale = LocalConfiguration.current.locales[0]
    // Resolved once: semantics lambdas are not composable, so they take the ready strings.
    val startDayEarlierLabel = stringResource(R.string.home_summary_start_earlier)
    val startDayLaterLabel = stringResource(R.string.home_summary_start_later)
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_summary_start_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onChange(rule.copy(day = startDay - 1)) },
                enabled = startDay > MonthStartRule.CALENDAR_MONTH_START_DAY,
                modifier = Modifier.semantics { contentDescription = startDayEarlierLabel },
            ) { Icon(Icons.Outlined.Remove, null) }
            Text(
                startDay.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("summaryStartDay"),
            )
            IconButton(
                onClick = { onChange(rule.copy(day = startDay + 1)) },
                enabled = startDay < 31,
                modifier = Modifier.semantics { contentDescription = startDayLaterLabel },
            ) { Icon(Icons.Outlined.Add, null) }
        }
        Text(
            stringResource(R.string.home_summary_start_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.home_summary_start_adjustment_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StartDayAdjustment.values().forEach { option ->
                FilterChip(
                    selected = rule.adjustment == option,
                    onClick = { onChange(rule.copy(adjustment = option)) },
                    label = { Text(stringResource(option.labelRes())) },
                    modifier = Modifier.testTag("startDayAdjustment${option.name}"),
                )
            }
        }
        if (rule.adjustment != StartDayAdjustment.NONE) {
            Text(
                stringResource(R.string.home_summary_weekend_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.values().forEach { day ->
                    FilterChip(
                        selected = day in rule.weekendDays,
                        onClick = { onChange(rule.copy(weekendDays = rule.weekendDays.toggled(day))) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                        modifier = Modifier.testTag("weekendDay${day.name}"),
                    )
                }
            }
            Text(
                stringResource(R.string.home_summary_weekend_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Set<DayOfWeek>.toggled(day: DayOfWeek): Set<DayOfWeek> = if (day in this) this - day else this + day

private fun StartDayAdjustment.labelRes(): Int = when (this) {
    StartDayAdjustment.NONE -> R.string.home_summary_adjustment_none
    StartDayAdjustment.PREVIOUS_WORKING_DAY -> R.string.home_summary_adjustment_previous
    StartDayAdjustment.NEXT_WORKING_DAY -> R.string.home_summary_adjustment_next
}
