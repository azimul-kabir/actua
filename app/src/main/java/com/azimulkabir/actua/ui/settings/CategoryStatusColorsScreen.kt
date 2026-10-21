package com.azimulkabir.actua.ui.settings

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
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
    R.string.settings_swatch_grey to 0xFF9AA0A6, R.string.settings_swatch_graphite to 0xFF616161,
    R.string.settings_swatch_slate to 0xFF64748B, R.string.settings_swatch_brown to 0xFF8D6E63,
    R.string.settings_swatch_red to 0xFFE0201A, R.string.settings_swatch_coral to 0xFFEF4444,
    R.string.settings_swatch_rose to 0xFFF43F5E, R.string.settings_swatch_pink to 0xFFEC4899,
    R.string.settings_swatch_magenta to 0xFFC026D3, R.string.settings_swatch_lavender to 0xFFA78BFA,
    R.string.settings_swatch_violet to 0xFF6E11FF, R.string.settings_swatch_indigo to 0xFF6366F1,
    R.string.settings_swatch_blue to 0xFF3B82F6, R.string.settings_swatch_sky to 0xFF0EA5E9,
    R.string.settings_swatch_cyan to 0xFF06B6D4, R.string.settings_swatch_teal to 0xFF14B8A6,
    R.string.settings_swatch_emerald to 0xFF10B981, R.string.settings_swatch_green to 0xFF16A34A,
    R.string.settings_swatch_mint to 0xFF4ADE80, R.string.settings_swatch_lime to 0xFF84CC16,
    R.string.settings_swatch_yellow to 0xFFFACC15, R.string.settings_swatch_amber to 0xFFFFA726,
    R.string.settings_swatch_orange to 0xFFF97316, R.string.settings_swatch_burnt_orange to 0xFFC2410C,
).map { (nameRes, argb) -> nameRes to Color(argb or 0xFF000000) }

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
                val statusLabel = stringResource(status.labelRes)
                val currentColorDescription = stringResource(R.string.settings_status_color_current, statusLabel)
                ActuaListRow(
                    title = { Text(statusLabel) },
                    subtitle = { Text(stringResource(statusSubtitle(status))) },
                    leading = {
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(color)
                                .semantics { contentDescription = currentColorDescription },
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
        ) { Text(stringResource(R.string.settings_reset_to_defaults)) }
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
        title = { Text(stringResource(R.string.settings_status_color_title, stringResource(status.labelRes))) },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                swatchPalette.forEach { (nameRes, swatch) ->
                    val selected = swatch == currentColor
                    val swatchName = stringResource(nameRes)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .clickable { onSelect(swatch) }
                            .semantics { contentDescription = swatchName },
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = stringResource(R.string.settings_swatch_selected),
                                tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
        },
    )
}

@StringRes
private fun statusSubtitle(status: BudgetProgressState): Int = when (status) {
    BudgetProgressState.UNASSIGNED -> R.string.settings_status_unassigned_subtitle
    BudgetProgressState.FUNDED -> R.string.settings_status_funded_subtitle
    BudgetProgressState.SPENDING -> R.string.settings_status_spending_subtitle
    BudgetProgressState.SPENT -> R.string.settings_status_spent_subtitle
    BudgetProgressState.OVERSPENT -> R.string.settings_status_overspent_subtitle
    BudgetProgressState.GOAL_IN_PROGRESS -> R.string.settings_status_goal_in_progress_subtitle
    BudgetProgressState.GOAL_REACHED -> R.string.settings_status_goal_reached_subtitle
    BudgetProgressState.GOAL_OVERFUNDED -> R.string.settings_status_goal_overfunded_subtitle
}
