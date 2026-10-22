package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.home.HomeSummaryPeriod
import com.azimulkabir.actua.ui.theme.Spacing

/**
 * Stepper for the day (1–31) the month-based summaries and report presets start on; 1 keeps the
 * calendar month. Device-local, so nothing here is synced.
 */
@Composable
fun MonthStartDayRow(startDay: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
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
                onClick = { onChange(startDay - 1) },
                enabled = startDay > HomeSummaryPeriod.CALENDAR_MONTH_START_DAY,
                modifier = Modifier.semantics { contentDescription = startDayEarlierLabel },
            ) { Icon(Icons.Outlined.Remove, null) }
            Text(
                startDay.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("summaryStartDay"),
            )
            IconButton(
                onClick = { onChange(startDay + 1) },
                enabled = startDay < 31,
                modifier = Modifier.semantics { contentDescription = startDayLaterLabel },
            ) { Icon(Icons.Outlined.Add, null) }
        }
        Text(
            stringResource(R.string.home_summary_start_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
