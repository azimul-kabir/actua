package com.azimulkabir.actua.ui.budget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.BudgetDisplayOptions
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaSheetCard
import com.azimulkabir.actua.ui.components.ActuaSheetContent
import com.azimulkabir.actua.ui.components.ActuaSheetTitle
import com.azimulkabir.actua.ui.theme.LocalCategoryStatusColors
import com.azimulkabir.actua.ui.theme.Spacing

/** The collapsible groups in the Budget display options sheet. */
internal enum class BudgetOptionsSection { PAGE, ROWS, CATEGORIES }

/** Saves which sections are open across rotation and process recreation. */
internal val BudgetOptionsSectionsSaver = listSaver<Set<BudgetOptionsSection>, String>(
    save = { open -> open.map { it.name } },
    restore = { names -> names.map(BudgetOptionsSection::valueOf).toSet() },
)

/**
 * The Budget tab's device-local display switches, in collapsible sections so the long list stays
 * scannable. Each switch applies immediately; [expanded] is owned by the caller so the open
 * sections survive closing and reopening the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BudgetDisplayOptionsSheet(
    options: BudgetDisplayOptions,
    onOptionsChange: (BudgetDisplayOptions) -> Unit,
    planView: Boolean,
    expanded: Set<BudgetOptionsSection>,
    onExpandedChange: (Set<BudgetOptionsSection>) -> Unit,
    onDismiss: () -> Unit,
) {
    val statusColors = LocalCategoryStatusColors.current
    val statusDots = statusColors?.showDots ?: true
    fun toggle(section: BudgetOptionsSection) =
        onExpandedChange(if (section in expanded) expanded - section else expanded + section)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle(stringResource(R.string.budget_display_options_title))
            OptionsSection(
                title = stringResource(R.string.budget_options_section_page),
                enabledCount = listOf(options.showOverview, options.showBudgetStatusBanners, options.showCategoryFilters).count { it },
                totalCount = 3,
                expanded = BudgetOptionsSection.PAGE in expanded,
                onToggle = { toggle(BudgetOptionsSection.PAGE) },
            ) {
                OptionSwitchRow(stringResource(R.string.budget_show_overview), options.showOverview) {
                    onOptionsChange(options.copy(showOverview = it))
                }
                ActuaCardDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_warnings), options.showBudgetStatusBanners) {
                    onOptionsChange(options.copy(showBudgetStatusBanners = it))
                }
                ActuaCardDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_filters), options.showCategoryFilters) {
                    onOptionsChange(options.copy(showCategoryFilters = it))
                }
            }
            val rowSwitches = listOf(
                options.showSpent, options.showProgressBars, statusDots, options.showBalancePills,
                options.showGoalIcons, options.showGroupTotals,
            )
            OptionsSection(
                title = stringResource(R.string.budget_options_section_rows),
                enabledCount = rowSwitches.count { it },
                totalCount = rowSwitches.size,
                expanded = BudgetOptionsSection.ROWS in expanded,
                onToggle = { toggle(BudgetOptionsSection.ROWS) },
            ) {
                OptionSwitchRow(
                    if (planView) stringResource(R.string.budget_show_spending_details)
                    else stringResource(R.string.budget_show_spent_column),
                    options.showSpent,
                ) { onOptionsChange(options.copy(showSpent = it)) }
                ActuaCardDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_progress_bars), options.showProgressBars) {
                    onOptionsChange(options.copy(showProgressBars = it))
                }
                ActuaCardDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_status_dots), statusDots) {
                    statusColors?.updateShowDots(it)
                }
                ActuaCardDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_balance_pills), options.showBalancePills) {
                    onOptionsChange(options.copy(showBalancePills = it))
                }
                ActuaCardDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_goal_icons), options.showGoalIcons) {
                    onOptionsChange(options.copy(showGoalIcons = it))
                }
                ActuaCardDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_group_totals), options.showGroupTotals) {
                    onOptionsChange(options.copy(showGroupTotals = it))
                }
            }
            OptionsSection(
                title = stringResource(R.string.budget_options_section_categories),
                enabledCount = listOf(options.hideFullySpent, options.showHidden).count { it },
                totalCount = 2,
                expanded = BudgetOptionsSection.CATEGORIES in expanded,
                onToggle = { toggle(BudgetOptionsSection.CATEGORIES) },
            ) {
                OptionSwitchRow(stringResource(R.string.budget_hide_fully_spent), options.hideFullySpent) {
                    onOptionsChange(options.copy(hideFullySpent = it))
                }
                ActuaCardDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_hidden), options.showHidden) {
                    onOptionsChange(options.copy(showHidden = it))
                }
            }
        }
    }
}

/** One card: a header row that expands and collapses [content], with how many of its switches are on. */
@Composable
private fun OptionsSection(
    title: String,
    enabledCount: Int,
    totalCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "optionsChevron")
    val stateText = stringResource(
        if (expanded) R.string.budget_options_section_expanded else R.string.budget_options_section_collapsed,
    )
    ActuaSheetCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = stateText }
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.budget_options_section_summary, enabledCount, totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                ActuaCardDivider()
                content()
            }
        }
    }
}

/** A labelled switch row that toggles as a whole and is announced as a single switch. */
@Composable
private fun OptionSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
