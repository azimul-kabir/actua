package com.azimulkabir.actua.ui.budget

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
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

/**
 * The Budget tab's device-local display switches. Each switch applies immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BudgetDisplayOptionsSheet(
    options: BudgetDisplayOptions,
    onOptionsChange: (BudgetDisplayOptions) -> Unit,
    planView: Boolean,
    onDismiss: () -> Unit,
) {
    val statusColors = LocalCategoryStatusColors.current
    val statusDots = statusColors?.showDots ?: true
    val colorBalances = statusColors?.colorBudgetedAmounts ?: false

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle(stringResource(R.string.budget_display_options_title))
            OptionsSection(stringResource(R.string.budget_options_section_page)) {
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
            OptionsSection(stringResource(R.string.budget_options_section_rows)) {
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
            OptionsSection(stringResource(R.string.budget_options_section_categories)) {
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
 * A section styled like the settings screens: a primary label over a rounded card of switch rows.
 */
@Composable
private fun OptionsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Spacing.screenHorizontal + Spacing.xs,
            end = Spacing.screenHorizontal,
            top = Spacing.sm,
        ),
    )
    ActuaSheetCard { content() }
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
