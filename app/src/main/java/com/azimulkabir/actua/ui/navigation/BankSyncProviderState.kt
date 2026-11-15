package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

/** Flow state retains its original navigation-composition lifetime and saveability. */
internal class BankSyncProviderState(
    val simpleFinConfigured: MutableState<Boolean>,
    val goCardlessConfigured: MutableState<Boolean>,
    val enableBankingConfigured: MutableState<Boolean>,
    val pluggyAiConfigured: MutableState<Boolean>,
    val akahuConfigured: MutableState<Boolean>,
    val simpleFinDiscovery: MutableState<com.azimulkabir.actua.ui.banksync.DiscoveryState>,
    val goCardlessDiscovery: MutableState<com.azimulkabir.actua.ui.banksync.DiscoveryState>,
    val pluggyAiDiscovery: MutableState<com.azimulkabir.actua.ui.banksync.DiscoveryState>,
    val akahuDiscovery: MutableState<com.azimulkabir.actua.ui.banksync.DiscoveryState>,
    val goCardlessInstitutions: MutableState<List<com.azimulkabir.actua.ui.banksync.GoCardlessInstitutionUi>>,
    val goCardlessInstitutionsLoading: MutableState<Boolean>,
    val goCardlessRequisitionId: MutableState<String?>,
)

@Composable
internal fun rememberBankSyncProviderState(): BankSyncProviderState =
    BankSyncProviderState(
        simpleFinConfigured = remember { mutableStateOf(false) },
        goCardlessConfigured = remember { mutableStateOf(false) },
        enableBankingConfigured = remember { mutableStateOf(false) },
        pluggyAiConfigured = remember { mutableStateOf(false) },
        akahuConfigured = remember { mutableStateOf(false) },
        simpleFinDiscovery = remember { mutableStateOf<com.azimulkabir.actua.ui.banksync.DiscoveryState>(com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle) },
        goCardlessDiscovery = remember { mutableStateOf<com.azimulkabir.actua.ui.banksync.DiscoveryState>(com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle) },
        pluggyAiDiscovery = remember { mutableStateOf<com.azimulkabir.actua.ui.banksync.DiscoveryState>(com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle) },
        akahuDiscovery = remember { mutableStateOf<com.azimulkabir.actua.ui.banksync.DiscoveryState>(com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle) },
        goCardlessInstitutions = remember { mutableStateOf<List<com.azimulkabir.actua.ui.banksync.GoCardlessInstitutionUi>>(emptyList()) },
        goCardlessInstitutionsLoading = remember { mutableStateOf(false) },
        goCardlessRequisitionId = remember { mutableStateOf<String?>(null) },
    )
