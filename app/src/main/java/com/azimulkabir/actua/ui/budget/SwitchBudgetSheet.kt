package com.azimulkabir.actua.ui.budget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.azimulkabir.actua.R
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaSheetAction
import com.azimulkabir.actua.ui.components.ActuaSheetCard
import com.azimulkabir.actua.ui.components.ActuaSheetContent
import com.azimulkabir.actua.ui.components.ActuaSheetTitle
import com.azimulkabir.actua.ui.settings.BudgetSelectionItem

/**
 * Picks another local budget. Choosing one hands its id to [onSelect], the same callback Settings'
 * active budget picker uses, so switching behaves identically; the active budget is marked and does nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwitchBudgetSheet(
    options: List<BudgetSelectionItem>,
    activeBudgetId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle(stringResource(R.string.budget_switch_budget))
            ActuaSheetCard {
                options.forEachIndexed { index, option ->
                    if (index > 0) ActuaCardDivider()
                    val active = option.id == activeBudgetId
                    ActuaSheetAction(
                        label = option.name,
                        icon = if (active) Icons.Outlined.Check else Icons.Outlined.FolderOpen,
                        onClick = {
                            onDismiss()
                            if (!active) onSelect(option.id)
                        },
                    )
                }
            }
        }
    }
}
