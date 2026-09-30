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
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaListRow
import com.azimulkabir.actua.ui.theme.LocalCategoryStatusColors
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.ui.theme.categoryStatusColor

// A curated, theme-adjacent swatch set rather than a full HSV picker: it keeps every choice
// legible against both light and dark surfaces while covering the status meanings users retint
// (neutral, success, info/primary, warning, error) with a range of alternates in each hue.
// Ordered by hue; earlier values are kept so previously saved choices still show as selected.
private val swatchPalette = listOf(
    "Grey" to 0xFF9AA0A6, "Graphite" to 0xFF616161, "Slate" to 0xFF64748B, "Brown" to 0xFF8D6E63,
    "Red" to 0xFFE0201A, "Coral" to 0xFFEF4444, "Rose" to 0xFFF43F5E, "Pink" to 0xFFEC4899,
    "Magenta" to 0xFFC026D3, "Lavender" to 0xFFA78BFA, "Violet" to 0xFF6E11FF, "Indigo" to 0xFF6366F1,
    "Blue" to 0xFF3B82F6, "Sky" to 0xFF0EA5E9, "Cyan" to 0xFF06B6D4, "Teal" to 0xFF14B8A6,
    "Emerald" to 0xFF10B981, "Green" to 0xFF16A34A, "Mint" to 0xFF4ADE80, "Lime" to 0xFF84CC16,
    "Yellow" to 0xFFFACC15, "Amber" to 0xFFFFA726, "Orange" to 0xFFF97316, "Burnt orange" to 0xFFC2410C,
).map { (name, argb) -> name to Color(argb or 0xFF000000) }

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
        ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
            BudgetProgressState.entries.forEachIndexed { index, status ->
                if (index > 0) ActuaCardDivider()
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
                swatchPalette.forEach { (name, swatch) ->
                    val selected = swatch == currentColor
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .clickable { onSelect(swatch) }
                            .semantics { contentDescription = "$name" },
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
