package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.BudgetProgressState
import com.azimulkabir.actua.ui.theme.categoryStatusColor

/**
 * A small dot reflecting a category's [BudgetProgressState], colored through the shared
 * [categoryStatusColor] resolver so it always matches the category's progress bar. The state's
 * label is exposed via semantics so status is never conveyed by color alone.
 */
@Composable
fun CategoryStatusDot(
    status: BudgetProgressState,
    modifier: Modifier = Modifier,
    color: Color = categoryStatusColor(status),
    size: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = status.label },
    )
}
