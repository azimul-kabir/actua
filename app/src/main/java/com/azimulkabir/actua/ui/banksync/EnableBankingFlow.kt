package com.azimulkabir.actua.ui.banksync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azimulkabir.actua.data.network.EnableBankingAccount
import com.azimulkabir.actua.data.network.EnableBankingAuthResult
import com.azimulkabir.actua.data.network.EnableBankingAuthStart
import com.azimulkabir.actua.data.network.EnableBankingBank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Localized message templates for the Enable Banking flow, resolved by the UI layer. */
data class EnableBankingMessages(
    val starting: String,
    val wait: String,
    val loadFailed: String,
    val authorizeFailed: String,
)

/**
 * Drives the Enable Banking link flow: pick a country and bank, send the user to the bank's
 * consent page, wait for the server to report the accounts the bank granted, then hand them to the link
 * sheet. Network work runs on [io]; state is Compose-observable. Mirrors Actual's `enablebanking.ts`.
 */
class EnableBankingFlow(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val loadBanks: (country: String) -> List<EnableBankingBank>,
    private val startAuthorization: (EnableBankingBank) -> EnableBankingAuthStart,
    private val awaitAuthorization: (state: String) -> EnableBankingAuthResult,
    private val openUrl: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val messages: EnableBankingMessages,
) {
    var banks by mutableStateOf<List<EnableBankingBank>>(emptyList())
        private set
    var banksLoading by mutableStateOf(false)
        private set
    var discovery by mutableStateOf<DiscoveryState>(DiscoveryState.Idle)
        private set

    private var accountsById = emptyMap<String, EnableBankingAccount>()
    private var authorization: Job? = null

    fun loadBanks(country: String) {
        banksLoading = true
        scope.launch {
            val result = runCatching { withContext(io) { loadBanks.invoke(country) } }
            banks = result.getOrElse { error ->
                if (error is CancellationException) throw error
                onError(error.message?.takeIf(String::isNotBlank) ?: messages.loadFailed.format(country))
                emptyList()
            }
            banksLoading = false
        }
    }

    /** Starts the bank's consent flow, opens it in the browser and waits for the granted accounts. */
    fun authorize(bank: EnableBankingBank) {
        authorization?.cancel()
        discovery = DiscoveryState.Pending(messages.starting.format(bank.name))
        authorization = scope.launch {
            try {
                val start = withContext(io) { startAuthorization(bank) }
                openUrl(start.url)
                discovery = DiscoveryState.Pending(messages.wait.format(bank.name))
                discovery = when (val result = withContext(io) { awaitAuthorization(start.state) }) {
                    is EnableBankingAuthResult.Available -> {
                        accountsById = result.accounts.associateBy { it.accountId }
                        DiscoveryState.Available(result.accounts.map {
                            DiscoveredBankAccount(it.accountId, it.name, it.iban ?: it.institution)
                        })
                    }
                    is EnableBankingAuthResult.Error -> DiscoveryState.Error(result.reason)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                discovery = DiscoveryState.Error(error.message?.takeIf(String::isNotBlank) ?: messages.authorizeFailed.format(bank.name))
            }
        }
    }

    /** The institution (Actual's `banks.name`) for a discovered account id, once the bank has returned it. */
    fun institutionFor(accountId: String): String? = accountsById[accountId]?.institution

    /** Forgets the discovered accounts, e.g. after one has been linked. */
    fun reset() {
        authorization?.cancel()
        authorization = null
        accountsById = emptyMap()
        discovery = DiscoveryState.Idle
    }
}
