package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.BudgetProgressState
import com.azimulkabir.actua.ui.components.ActuaListRow
import com.azimulkabir.actua.ui.theme.LocalCategoryStatusColors
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.ui.theme.categoryStatusColor

// A curated, theme-adjacent swatch set rather than a full HSV picker: it keeps every choice
// legible against both light and dark surfaces and matches the five status meanings we're
// letting users retint (neutral, success, info/primary, warning, error) plus a few alternates.
private val swatchPalette = listOf(
    0xFF9AA0A6, 0xFF616161, 0xFF16A34A, 0xFF4ADE80, 0xFF0EA5E9,
    0xFF6E11FF, 0xFFC026D3, 0xFFF97316, 0xFFFFA726, 0xFFE0201A,
    0xFFEF4444, 0xFFFACC15,
).map { Color(it or 0xFF000000) }

/**
 * Settings → Budget → Category status colors. One picker per [BudgetProgressState], a live
 * preview swatch, and a reset action — all backed by [LocalCategoryStatusColors] so a change is
 * immediately reflected on the status dot and progress bar wherever a category is shown.
 */
@Composable
fun CategoryStatusColorSettings(modifier: Modifier = Modifier) {
    val state = LocalCategoryStatusColors.current
    var pickerFor by remember { mutableStateOf<BudgetProgressState?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        BudgetProgressState.entries.forEach { status ->
            val color = categoryStatusColor(status)
            ActuaListRow(
                title = { Text(status.label) },
                subtitle = { Text(statusSubtitle(status)) },
                leading = {
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(color)
                            .semantics { contentDescription = "${status.label} color: current" },
                    )
                },
                enabled = state != null,
                onClick = { pickerFor = status },
            )
        }
        TextButton(
            onClick = { state?.resetToDefaults() },
            enabled = state != null,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
        ) { Text("Reset to defaults") }
    }

    val editingStatus = pickerFor
    if (editingStatus != null && state != null) {
        ColorPickerDialog(
            status = editingStatus,
            currentColor = categoryStatusColor(editingStatus),
            onSelect = { color ->
                state.setOverride(editingStatus, color)
                pickerFor = null
            },
            onDismiss = { pickerFor = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerDialog(
    status: BudgetProgressState,
    currentColor: Color,
    onSelect: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${status.label} color") },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                swatchPalette.forEach { swatch ->
                    val selected = swatch == currentColor
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .clickable { onSelect(swatch) }
                            .semantics { contentDescription = "${status.label} swatch" },
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun statusSubtitle(status: BudgetProgressState): String = when (status) {
    BudgetProgressState.UNASSIGNED -> "No money assigned to this category"
    BudgetProgressState.FUNDED -> "Fully funded, nothing spent yet"
    BudgetProgressState.SPENDING -> "Funded and partially spent"
    BudgetProgressState.SPENT -> "Fully spent, balance at zero"
    BudgetProgressState.OVERSPENT -> "Spent more than available"
    BudgetProgressState.GOAL_IN_PROGRESS -> "Goal, by-date or schedule target still being funded"
    BudgetProgressState.GOAL_REACHED -> "Goal, by-date or schedule target fully funded"
}
