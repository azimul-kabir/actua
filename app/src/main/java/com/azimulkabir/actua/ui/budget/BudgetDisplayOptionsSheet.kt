package com.azimulkabir.actua.ui.budget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.BudgetDisplayOptions
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaListRow
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
    val colorBalances = statusColors?.colorBudgetedAmounts ?: false
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
                OptionDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_warnings), options.showBudgetStatusBanners) {
                    onOptionsChange(options.copy(showBudgetStatusBanners = it))
                }
                OptionDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_filters), options.showCategoryFilters) {
                    onOptionsChange(options.copy(showCategoryFilters = it))
                }
            }
            val rowSwitches = listOf(
                options.showSpent, options.showProgressBars, statusDots, colorBalances, options.showBalancePills,
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
                OptionDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_progress_bars), options.showProgressBars) {
                    onOptionsChange(options.copy(showProgressBars = it))
                }
                OptionDivider()
                OptionSwitchRow(
                    stringResource(R.string.budget_show_status_dots),
                    statusDots,
                    stringResource(R.string.settings_category_status_dot_description),
                ) { statusColors?.updateShowDots(it) }
                OptionDivider()
                OptionSwitchRow(
                    stringResource(R.string.settings_color_balances),
                    colorBalances,
                    stringResource(R.string.settings_color_balances_description),
                ) { statusColors?.updateColorBudgetedAmounts(it) }
                OptionDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_balance_pills), options.showBalancePills) {
                    onOptionsChange(options.copy(showBalancePills = it))
                }
                OptionDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_goal_icons), options.showGoalIcons) {
                    onOptionsChange(options.copy(showGoalIcons = it))
                }
                OptionDivider()
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
                OptionDivider()
                OptionSwitchRow(stringResource(R.string.budget_show_hidden), options.showHidden) {
                    onOptionsChange(options.copy(showHidden = it))
                }
            }
        }
    }
}

/**
 * A collapsible group styled like a settings section: a primary label (with how many of its switches
 * are on) over a rounded card of rows that expands and collapses.
 */
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = stateText }
            .padding(
                start = Spacing.screenHorizontal + Spacing.xs,
                end = Spacing.screenHorizontal,
                top = Spacing.sm,
                bottom = Spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.budget_options_section_summary, enabledCount, totalCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.xs))
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation),
        )
    }
    AnimatedVisibility(visible = expanded) {
        ActuaSheetCard { content() }
    }
}

/** Divider between the rows of an options card, inset like the Settings cards. */
@Composable
private fun OptionDivider() = ActuaCardDivider(inset = Spacing.screenHorizontal)

/**
 * A switch row built like the Settings toggles: a title with an optional grey [detail] and a trailing
 * switch, toggling as a whole and announced as a single switch.
 */
@Composable
private fun OptionSwitchRow(
    label: String,
    checked: Boolean,
    detail: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    ActuaListRow(
        title = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        subtitle = detail?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
    )
}
