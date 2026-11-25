package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * A chart's breakdown list (every slice or node with its share and amount), collapsed under the chart
 * behind a "Show breakdown" control so a long list doesn't stretch its card. Styled like the custom
 * report's "Show summary" control.
 */
@Composable
internal fun CollapsibleBreakdown(content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // A quiet control rather than the theme's accent colour, so it doesn't compete with the chart.
    TextButton(
        onClick = { expanded = !expanded },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        // No side padding, so the label lines up with the card's text.
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        modifier = Modifier.testTag("chartBreakdownToggle"),
    ) {
        Text(if (expanded) "Hide breakdown" else "Show breakdown")
    }
    // Slides down like a Budget group's rows (same fades, slides and timings), while the card grows
    // from the top to make room; growing sideways too would show the rows cut off at the left.
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(tween(180)) + expandVertically(tween(220), expandFrom = Alignment.Top) +
            slideInVertically(tween(220)) { -it / 3 },
        exit = fadeOut(tween(120)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Top) +
            slideOutVertically(tween(180)) { -it / 3 },
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}
