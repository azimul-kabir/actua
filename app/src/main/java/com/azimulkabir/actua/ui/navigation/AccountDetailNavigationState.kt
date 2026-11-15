package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

/** Flow state retains its original navigation-composition lifetime and saveability. */
internal class AccountDetailNavigationState(
    val creditCardsReturnToBills: MutableState<Boolean>,
    val statementsAccountId: MutableState<String?>,
    val statementsReturnToTransactions: MutableState<Boolean>,
    val selectedStatement: MutableState<com.azimulkabir.actua.model.CreditCardCycle.StatementRecord?>,
)

@Composable
internal fun rememberAccountDetailNavigationState(): AccountDetailNavigationState =
    AccountDetailNavigationState(
        creditCardsReturnToBills = rememberSaveable { mutableStateOf(false) },
        statementsAccountId = rememberSaveable { mutableStateOf<String?>(null) },
        statementsReturnToTransactions = rememberSaveable { mutableStateOf(false) },
        selectedStatement = remember { mutableStateOf<com.azimulkabir.actua.model.CreditCardCycle.StatementRecord?>(null) },
    )
