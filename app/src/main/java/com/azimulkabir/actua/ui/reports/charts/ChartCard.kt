package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.ui.theme.danger
import com.azimulkabir.actua.ui.theme.success

/**
 * Direction of a chart trend indicator.
 */
enum class ChartTrendDirection {
    UP,
    DOWN,
    NEUTRAL,
}

/**
 * Encapsulates trend data displayed alongside the headline value in a [ChartCard].
 */
data class ChartTrend(
    val direction: ChartTrendDirection,
    val label: String,
    val isPositiveGood: Boolean = true,
)

/**
 * A standard container card for report charts featuring a title, optional expand action,
 * headline value with trend indicator, caption, and chart content area.
 */
@Composable
fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    headlineValue: String? = null,
    trend: ChartTrend? = null,
    caption: String? = null,
    onExpand: (() -> Unit)? = null,
    chart: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { heading() },
                )
                if (onExpand != null) {
                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInFull,
                            contentDescription = "Expand chart",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (headlineValue != null || trend != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (headlineValue != null) {
                        Text(
                            text = headlineValue,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (trend != null) {
                        ChartTrendBadge(trend)
                    }
                }
            }

            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                chart()
            }
        }
    }
}

/**
 * Badge displaying an up/down/neutral trend with an icon and label text.
 */
@Composable
fun ChartTrendBadge(trend: ChartTrend, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tint = when (trend.direction) {
        ChartTrendDirection.UP -> if (trend.isPositiveGood) colors.success else colors.danger
        ChartTrendDirection.DOWN -> if (trend.isPositiveGood) colors.danger else colors.success
        ChartTrendDirection.NEUTRAL -> colors.onSurfaceVariant
    }
    Row(
        modifier = modifier.padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (trend.direction) {
            ChartTrendDirection.UP -> Icons.AutoMirrored.Outlined.TrendingUp
            ChartTrendDirection.DOWN -> Icons.AutoMirrored.Outlined.TrendingDown
            ChartTrendDirection.NEUTRAL -> null
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = trend.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}
