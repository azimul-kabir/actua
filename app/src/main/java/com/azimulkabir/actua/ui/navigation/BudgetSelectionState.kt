package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

/** Flow state retains its original navigation-composition lifetime and saveability. */
internal class BudgetSelectionState(
    val budgetMonth: MutableState<String>,
    val activeBudgetCategory: MutableState<String?>,
    val reopenBudgetCategory: MutableState<String?>,
    val transactionsReturnCategory: MutableState<String?>,
    val editingAutomationCategory: MutableState<String?>,
)

@Composable
internal fun rememberBudgetSelectionState(): BudgetSelectionState =
    BudgetSelectionState(
        budgetMonth = rememberSaveable {
            mutableStateOf(java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date()))
        },
        activeBudgetCategory = rememberSaveable { mutableStateOf<String?>(null) },
        reopenBudgetCategory = rememberSaveable { mutableStateOf<String?>(null) },
        transactionsReturnCategory = rememberSaveable { mutableStateOf<String?>(null) },
        editingAutomationCategory = rememberSaveable { mutableStateOf<String?>(null) },
    )
