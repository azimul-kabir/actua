package com.azimulkabir.actua.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.SnackbarHostState
import com.azimulkabir.actua.util.suspendRunCatching
import com.azimulkabir.actua.R
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.diagnostics.DiagnosticsLog
import com.azimulkabir.actua.ui.banksync.EnableBankingFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class BankSyncNavigationActions(
    val enableBankingFlow: EnableBankingFlow,
    val loadBankSyncProviderStatus: () -> Unit,
    val saveEnableBankingCredentials: (String, String) -> Unit,
    val savePluggyAiCredentials: (String, String, String, Boolean) -> Unit,
    val saveAkahuCredentials: (String, String) -> Unit,
    val discoverPluggyAiAccounts: () -> Unit,
    val discoverAkahuAccounts: () -> Unit,
    val saveSimpleFinToken: (String) -> Unit,
    val saveGoCardlessCredentials: (String, String) -> Unit,
    val discoverSimpleFinAccounts: () -> Unit,
    val loadGoCardlessInstitutions: (String) -> Unit,
    val startGoCardlessAuthorization: (String) -> Unit,
    val checkGoCardlessAccounts: () -> Unit,
    val linkBankAccount: (com.azimulkabir.actua.ui.banksync.DiscoveredBankAccount, Account, String) -> Unit,
    val createAndLinkBankAccount: (com.azimulkabir.actua.ui.banksync.DiscoveredBankAccount, String, Boolean, String) -> Unit,
    val unlinkBankAccount: (Account) -> Unit,
)

/** Uses current repository/locale inputs and the original navigation-composition coroutine scope. */
@Composable
internal fun bankSyncNavigationActions(
    context: Context,
    repository: ActuaRepository,
    coroutineScope: CoroutineScope,
    providerState: BankSyncProviderState,
    feedback: NavigationMutationFeedback,
    snackbarHostState: SnackbarHostState,
    navStrings: NavigationStrings,
    onMutate: (Int, () -> Unit, () -> Boolean) -> Unit,
): BankSyncNavigationActions {
    var simpleFinConfigured by providerState.simpleFinConfigured
    var goCardlessConfigured by providerState.goCardlessConfigured
    var enableBankingConfigured by providerState.enableBankingConfigured
    var pluggyAiConfigured by providerState.pluggyAiConfigured
    var akahuConfigured by providerState.akahuConfigured
    var simpleFinDiscovery by providerState.simpleFinDiscovery
    var goCardlessDiscovery by providerState.goCardlessDiscovery
    var pluggyAiDiscovery by providerState.pluggyAiDiscovery
    var akahuDiscovery by providerState.akahuDiscovery
    var goCardlessInstitutions by providerState.goCardlessInstitutions
    var goCardlessInstitutionsLoading by providerState.goCardlessInstitutionsLoading
    var goCardlessRequisitionId by providerState.goCardlessRequisitionId
    var errorMessage by feedback.error
    fun mutate(label: Int, onChanged: () -> Unit = {}, action: () -> Boolean) = onMutate(label, onChanged, action)

    fun loadBankSyncProviderStatus() {
        coroutineScope.launch {
            simpleFinConfigured = withContext(Dispatchers.IO) {
                runCatching { repository.bankSyncProviderConfigured("simpleFin") }.getOrDefault(false)
            }
            goCardlessConfigured = withContext(Dispatchers.IO) {
                runCatching { repository.bankSyncProviderConfigured("goCardless") }.getOrDefault(false)
            }
            enableBankingConfigured = withContext(Dispatchers.IO) {
                runCatching { repository.bankSyncProviderConfigured("enableBanking") }.getOrDefault(false)
            }
            pluggyAiConfigured = withContext(Dispatchers.IO) {
                runCatching { repository.bankSyncProviderConfigured("pluggyai") }.getOrDefault(false)
            }
            akahuConfigured = withContext(Dispatchers.IO) {
                runCatching { repository.bankSyncProviderConfigured("akahu") }.getOrDefault(false)
            }
        }
    }

    val enableBankingMessages = com.azimulkabir.actua.ui.banksync.EnableBankingMessages(
        starting = stringResource(R.string.fs_eb_starting),
        wait = stringResource(R.string.fs_eb_wait),
        loadFailed = stringResource(R.string.fs_eb_load_failed),
        authorizeFailed = stringResource(R.string.fs_eb_authorize_failed),
    )
    val enableBankingFlow = remember(repository, enableBankingMessages) {
        com.azimulkabir.actua.ui.banksync.EnableBankingFlow(
            scope = coroutineScope,
            io = Dispatchers.IO,
            loadBanks = repository::discoverEnableBankingBanks,
            startAuthorization = repository::startEnableBankingAuthorization,
            awaitAuthorization = repository::awaitEnableBankingAuthorization,
            openUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            onError = { message -> errorMessage = message },
            messages = enableBankingMessages,
        )
    }

    fun saveEnableBankingCredentials(applicationId: String, secretKey: String) {
        coroutineScope.launch {
            suspendRunCatching { withContext(Dispatchers.IO) { repository.setEnableBankingCredentials(applicationId, secretKey) } }
                .onSuccess {
                    loadBankSyncProviderStatus()
                    snackbarHostState.showSnackbar(navStrings.enableBankingSaved)
                }
                .onFailure {
                    errorMessage = it.message?.takeIf(String::isNotBlank)
                        ?: navStrings.enableBankingSaveFailed
                }
        }
    }

    val pluggyAiSavedMessage = stringResource(R.string.navigation_pluggyai_credentials_saved)
    val pluggyAiSaveFailedMessage = stringResource(R.string.navigation_pluggyai_credentials_save_failed)
    val akahuSavedMessage = stringResource(R.string.navigation_akahu_credentials_saved)
    val akahuSaveFailedMessage = stringResource(R.string.navigation_akahu_credentials_save_failed)
    val pluggyAiLoadFailedMessage = stringResource(R.string.navigation_pluggyai_accounts_load_failed)
    val akahuLoadFailedMessage = stringResource(R.string.navigation_akahu_accounts_load_failed)

    fun savePluggyAiCredentials(clientId: String, clientSecret: String, itemIds: String, perBudget: Boolean) {
        coroutineScope.launch {
            suspendRunCatching { withContext(Dispatchers.IO) { repository.setPluggyAiCredentials(clientId, clientSecret, itemIds, perBudget) } }
                .onSuccess {
                    loadBankSyncProviderStatus()
                    snackbarHostState.showSnackbar(pluggyAiSavedMessage)
                }
                .onFailure { errorMessage = it.message?.takeIf(String::isNotBlank) ?: pluggyAiSaveFailedMessage }
        }
    }

    fun saveAkahuCredentials(userToken: String, appToken: String) {
        coroutineScope.launch {
            suspendRunCatching { withContext(Dispatchers.IO) { repository.setAkahuCredentials(userToken, appToken) } }
                .onSuccess {
                    loadBankSyncProviderStatus()
                    snackbarHostState.showSnackbar(akahuSavedMessage)
                }
                .onFailure { errorMessage = it.message?.takeIf(String::isNotBlank) ?: akahuSaveFailedMessage }
        }
    }

    fun providerDiscovery(
        result: com.azimulkabir.actua.data.network.ProviderAccountsResult,
    ): com.azimulkabir.actua.ui.banksync.DiscoveryState = when (result) {
        is com.azimulkabir.actua.data.network.ProviderAccountsResult.Available -> com.azimulkabir.actua.ui.banksync.DiscoveryState.Available(
            result.accounts.map {
                com.azimulkabir.actua.ui.banksync.DiscoveredBankAccount(
                    it.id, it.name, it.institution, bankId = it.bankId, bankName = it.institution,
                )
            },
        )
        is com.azimulkabir.actua.data.network.ProviderAccountsResult.Error -> com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(result.reason)
    }

    fun discoverPluggyAiAccounts() {
        pluggyAiDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Loading
        coroutineScope.launch {
            pluggyAiDiscovery = suspendRunCatching { withContext(Dispatchers.IO) { repository.discoverPluggyAiAccounts() } }.fold(
                onSuccess = ::providerDiscovery,
                onFailure = { error ->
                    DiagnosticsLog.actionFailed("Loading Pluggy.ai accounts", error)
                    com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(error.message ?: pluggyAiLoadFailedMessage)
                },
            )
        }
    }

    fun discoverAkahuAccounts() {
        akahuDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Loading
        coroutineScope.launch {
            akahuDiscovery = suspendRunCatching { withContext(Dispatchers.IO) { repository.discoverAkahuAccounts() } }.fold(
                onSuccess = ::providerDiscovery,
                onFailure = { error ->
                    DiagnosticsLog.actionFailed("Loading Akahu accounts", error)
                    com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(error.message ?: akahuLoadFailedMessage)
                },
            )
        }
    }

    fun saveSimpleFinToken(token: String) {
        coroutineScope.launch {
            suspendRunCatching { withContext(Dispatchers.IO) { repository.setSimpleFinToken(token) } }
                .onSuccess {
                    loadBankSyncProviderStatus()
                    snackbarHostState.showSnackbar(navStrings.simplefinSaved)
                }
                .onFailure {
                    errorMessage = it.message?.takeIf(String::isNotBlank)
                        ?: navStrings.simplefinSaveFailed
                }
        }
    }

    fun saveGoCardlessCredentials(secretId: String, secretKey: String) {
        coroutineScope.launch {
            suspendRunCatching { withContext(Dispatchers.IO) { repository.setGoCardlessCredentials(secretId, secretKey) } }
                .onSuccess {
                    loadBankSyncProviderStatus()
                    snackbarHostState.showSnackbar(navStrings.gocardlessSaved)
                }
                .onFailure {
                    errorMessage = it.message?.takeIf(String::isNotBlank)
                        ?: navStrings.gocardlessSaveFailed
                }
        }
    }

    fun discoverSimpleFinAccounts() {
        simpleFinDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Loading
        coroutineScope.launch {
            simpleFinDiscovery = suspendRunCatching { withContext(Dispatchers.IO) { repository.discoverSimpleFinAccounts() } }.fold(
                onSuccess = { result ->
                    when (result) {
                        is com.azimulkabir.actua.data.network.SimpleFinAccountsResult.Available -> com.azimulkabir.actua.ui.banksync.DiscoveryState.Available(
                            result.accounts.map {
                                com.azimulkabir.actua.ui.banksync.DiscoveredBankAccount(
                                    it.id, it.name, it.orgName, bankId = it.orgDomain ?: it.orgId, bankName = it.orgName,
                                )
                            },
                        )
                        is com.azimulkabir.actua.data.network.SimpleFinAccountsResult.Error ->
                            com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(result.reason)
                    }
                },
                onFailure = { error ->
                    DiagnosticsLog.actionFailed("Loading SimpleFIN accounts", error)
                    com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(
                        error.message ?: navStrings.simplefinAccountsLoadFailed,
                    )
                },
            )
        }
    }

    fun loadGoCardlessInstitutions(country: String) {
        goCardlessInstitutionsLoading = true
        coroutineScope.launch {
            goCardlessInstitutions = suspendRunCatching { withContext(Dispatchers.IO) { repository.discoverGoCardlessInstitutions(country) } }
                .getOrElse { error ->
                    errorMessage = error.message?.takeIf(String::isNotBlank)
                        ?: navStrings.banksLoadFailedFormat.format(country)
                    emptyList()
                }.map { com.azimulkabir.actua.ui.banksync.GoCardlessInstitutionUi(it.id, it.name) }
            goCardlessInstitutionsLoading = false
        }
    }

    fun startGoCardlessAuthorization(institutionId: String) {
        coroutineScope.launch {
            suspendRunCatching { withContext(Dispatchers.IO) { repository.startGoCardlessAuthorization(institutionId) } }
                .onSuccess { webToken ->
                    goCardlessRequisitionId = webToken.requisitionId
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webToken.link)))
                }
                .onFailure {
                    errorMessage = it.message?.takeIf(String::isNotBlank)
                        ?: navStrings.gocardlessAuthorizationStartFailed
                }
        }
    }

    fun checkGoCardlessAccounts() {
        val requisitionId = goCardlessRequisitionId
        if (requisitionId == null) {
            goCardlessDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(
                navStrings.chooseBankFirst,
            )
            return
        }
        goCardlessDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Loading
        coroutineScope.launch {
            goCardlessDiscovery = suspendRunCatching { withContext(Dispatchers.IO) { repository.goCardlessAccountsForRequisition(requisitionId) } }.fold(
                onSuccess = { result ->
                    when (result) {
                        is com.azimulkabir.actua.data.network.GoCardlessAccountsResult.Available -> com.azimulkabir.actua.ui.banksync.DiscoveryState.Available(
                            result.accounts.map {
                                com.azimulkabir.actua.ui.banksync.DiscoveredBankAccount(
                                    "$requisitionId::${it.id}", it.name ?: it.iban ?: it.id, it.iban,
                                    bankName = it.institutionName,
                                )
                            },
                        )
                        is com.azimulkabir.actua.data.network.GoCardlessAccountsResult.Pending ->
                            com.azimulkabir.actua.ui.banksync.DiscoveryState.Pending(
                                navStrings.gocardlessAuthorizationPendingFormat.format(result.status),
                            )
                        is com.azimulkabir.actua.data.network.GoCardlessAccountsResult.Error ->
                            com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(result.reason)
                    }
                },
                onFailure = { error ->
                    DiagnosticsLog.actionFailed("Checking GoCardless accounts", error)
                    com.azimulkabir.actua.ui.banksync.DiscoveryState.Error(
                        error.message ?: navStrings.gocardlessAccountsCheckFailed,
                    )
                },
            )
        }
    }

    /** GoCardless ids are packed as "requisitionId::accountId" by [checkGoCardlessAccounts]; other providers use the bare id. */
    fun splitGoCardlessId(discoveredId: String): Pair<String?, String> {
        val parts = discoveredId.split("::", limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else null to discoveredId
    }

    fun linkBankAccount(discovered: com.azimulkabir.actua.ui.banksync.DiscoveredBankAccount, account: Account, source: String) {
        val (requisitionId, externalId) = when (source) {
            "goCardless" -> splitGoCardlessId(discovered.id)
            // Enable Banking keeps one `banks` row per account, keyed by the account id itself.
            "enableBanking" -> discovered.id to discovered.id
            // SimpleFIN's `banks` row is keyed on the org domain, or its id (#1004).
            "simpleFin" -> discovered.bankId to discovered.id
            // Actual's `findOrCreateBank(institution, orgDomain ?? orgId)` for Pluggy.ai and Akahu.
            "pluggyai", "akahu" -> discovered.bankId to discovered.id
            else -> null to discovered.id
        }
        val bankName = if (source == "enableBanking") enableBankingFlow.institutionFor(discovered.id) else discovered.bankName
        mutate(
            R.string.navigation_linking_account,
            onChanged = {
                goCardlessDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                simpleFinDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                pluggyAiDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                akahuDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                enableBankingFlow.reset()
            },
        ) { repository.linkBankAccount(account.id, externalId, source, requisitionId, bankName) }
    }

    fun createAndLinkBankAccount(
        discovered: com.azimulkabir.actua.ui.banksync.DiscoveredBankAccount, name: String, offBudget: Boolean, source: String,
    ) {
        val (requisitionId, externalId) = when (source) {
            "goCardless" -> splitGoCardlessId(discovered.id)
            "enableBanking" -> discovered.id to discovered.id
            "simpleFin" -> discovered.bankId to discovered.id
            // Actual's `findOrCreateBank(institution, orgDomain ?? orgId)` for Pluggy.ai and Akahu.
            "pluggyai", "akahu" -> discovered.bankId to discovered.id
            else -> null to discovered.id
        }
        val bankName = if (source == "enableBanking") enableBankingFlow.institutionFor(discovered.id) else discovered.bankName
        mutate(
            R.string.navigation_creating_account,
            onChanged = {
                goCardlessDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                simpleFinDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                pluggyAiDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                akahuDiscovery = com.azimulkabir.actua.ui.banksync.DiscoveryState.Idle
                enableBankingFlow.reset()
            },
        ) { repository.createLinkedAccount(name, offBudget, externalId, source, requisitionId, bankName) }
    }

    fun unlinkBankAccount(account: Account) {
        mutate(R.string.navigation_unlinking_account) { repository.unlinkBankAccount(account.id) }
    }

    return BankSyncNavigationActions(
        enableBankingFlow = enableBankingFlow,
        loadBankSyncProviderStatus = ::loadBankSyncProviderStatus,
        saveEnableBankingCredentials = ::saveEnableBankingCredentials,
        savePluggyAiCredentials = ::savePluggyAiCredentials,
        saveAkahuCredentials = ::saveAkahuCredentials,
        discoverPluggyAiAccounts = ::discoverPluggyAiAccounts,
        discoverAkahuAccounts = ::discoverAkahuAccounts,
        saveSimpleFinToken = ::saveSimpleFinToken,
        saveGoCardlessCredentials = ::saveGoCardlessCredentials,
        discoverSimpleFinAccounts = ::discoverSimpleFinAccounts,
        loadGoCardlessInstitutions = ::loadGoCardlessInstitutions,
        startGoCardlessAuthorization = ::startGoCardlessAuthorization,
        checkGoCardlessAccounts = ::checkGoCardlessAccounts,
        linkBankAccount = ::linkBankAccount,
        createAndLinkBankAccount = ::createAndLinkBankAccount,
        unlinkBankAccount = ::unlinkBankAccount,
    )
}
