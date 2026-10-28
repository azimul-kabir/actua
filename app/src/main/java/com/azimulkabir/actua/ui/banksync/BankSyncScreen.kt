package com.azimulkabir.actua.ui.banksync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.bank.BankSyncAccountSettings
import com.azimulkabir.actua.data.bank.BankSyncFlag
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormTextField
import com.azimulkabir.actua.ui.components.ActuaListRow
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSheetAction
import com.azimulkabir.actua.ui.components.ActuaSheetCard
import com.azimulkabir.actua.ui.components.ActuaSheetContent
import com.azimulkabir.actua.ui.components.ActuaSheetTitle
import com.azimulkabir.actua.ui.theme.Sizes
import com.azimulkabir.actua.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A bank-sync provider account discovered on the server, not yet linked to an Actua account. */
/** A provider account to link; [bankId] and [bankName] become Actual's `banks` row. */
data class DiscoveredBankAccount(
    val id: String, val label: String, val subtitle: String? = null,
    val bankId: String? = null, val bankName: String? = null,
)

sealed class DiscoveryState {
    data object Idle : DiscoveryState()
    data object Loading : DiscoveryState()
    data class Available(val accounts: List<DiscoveredBankAccount>) : DiscoveryState()
    /** e.g. GoCardless still awaiting the user's bank authorization. */
    data class Pending(val message: String) : DiscoveryState()
    data class Error(val message: String) : DiscoveryState()
}

data class GoCardlessInstitutionUi(val id: String, val name: String)

data class EnableBankingUiState(
    val configured: Boolean = false,
    val banks: List<String> = emptyList(),
    val banksLoading: Boolean = false,
    val discovery: DiscoveryState = DiscoveryState.Idle,
)

/** Countries Enable Banking offers banks in; the server filters `/aspsps` by ISO code. */
internal val enableBankingCountries = listOf(
    "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IS", "IE", "IT",
    "LV", "LT", "LU", "MT", "NL", "NO", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "CH", "GB",
)

/** Localized status line for a linked bank-sync account; null while healthy. */
@Composable
private fun bankSyncStatusLabel(status: String?): String? = when (status) {
    null, "ok" -> null
    "reauth-required" -> stringResource(R.string.fs_status_needs_reauth)
    "attention-required" -> stringResource(R.string.fs_status_needs_attention)
    "rate-limit-exceeded" -> stringResource(R.string.fs_status_rate_limited)
    "timed-out" -> stringResource(R.string.fs_status_timed_out)
    "account-missing" -> stringResource(R.string.fs_status_account_missing)
    else -> stringResource(R.string.fs_status_sync_problem)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSyncScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    simpleFinConfigured: Boolean,
    goCardlessConfigured: Boolean,
    pluggyAiConfigured: Boolean = false,
    akahuConfigured: Boolean = false,
    linkedAccounts: List<Account>,
    linkableAccounts: List<Account>,
    onSaveSimpleFinToken: (String) -> Unit,
    simpleFinDiscovery: DiscoveryState,
    onDiscoverSimpleFin: () -> Unit,
    onSaveGoCardlessCredentials: (secretId: String, secretKey: String) -> Unit,
    goCardlessInstitutions: List<GoCardlessInstitutionUi>,
    goCardlessInstitutionsLoading: Boolean,
    onLoadGoCardlessInstitutions: (country: String) -> Unit,
    onStartGoCardlessAuthorization: (institutionId: String) -> Unit,
    goCardlessDiscovery: DiscoveryState,
    onCheckGoCardlessAccounts: () -> Unit,
    enableBanking: EnableBankingUiState = EnableBankingUiState(),
    onSaveEnableBankingCredentials: (applicationId: String, secretKey: String) -> Unit = { _, _ -> },
    onLoadEnableBankingBanks: (country: String) -> Unit = {},
    onAuthorizeEnableBanking: (bankName: String) -> Unit = {},
    onSavePluggyAiCredentials: (clientId: String, clientSecret: String, itemIds: String) -> Unit = { _, _, _ -> },
    pluggyAiDiscovery: DiscoveryState = DiscoveryState.Idle,
    onDiscoverPluggyAi: () -> Unit = {},
    onSaveAkahuCredentials: (userToken: String, appToken: String) -> Unit = { _, _ -> },
    akahuDiscovery: DiscoveryState = DiscoveryState.Idle,
    onDiscoverAkahu: () -> Unit = {},
    syncSettingsFor: (Account) -> BankSyncAccountSettings? = { null },
    onSyncSettingChange: (Account, BankSyncFlag, Boolean) -> Unit = { _, _, _ -> },
    onLinkExisting: (DiscoveredBankAccount, Account, source: String) -> Unit,
    onCreateAndLink: (DiscoveredBankAccount, name: String, offBudget: Boolean, source: String) -> Unit,
    onUnlink: (Account) -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    var linkingSource by remember { mutableStateOf<Pair<DiscoveredBankAccount, String>?>(null) }
    var settingsAccount by remember { mutableStateOf<Account?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        ActuaScreenHeader(title = stringResource(R.string.fs_bank_sync_title), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl)) {
            SectionLabel(stringResource(R.string.fs_bank_sync_providers))
            ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                ProviderRow(
                    name = stringResource(R.string.fs_provider_simplefin), configured = simpleFinConfigured,
                    expanded = expanded == "simpleFin",
                    onClick = { expanded = if (expanded == "simpleFin") null else "simpleFin" },
                ) {
                    SimpleFinSetup(
                        configured = simpleFinConfigured,
                        onSaveToken = onSaveSimpleFinToken,
                        discovery = simpleFinDiscovery,
                        onDiscover = onDiscoverSimpleFin,
                        onLink = { linkingSource = it to "simpleFin" },
                    )
                }
                ActuaCardDivider(inset = Spacing.screenHorizontal)
                ProviderRow(
                    name = stringResource(R.string.fs_provider_gocardless), configured = goCardlessConfigured,
                    expanded = expanded == "goCardless",
                    onClick = { expanded = if (expanded == "goCardless") null else "goCardless" },
                ) {
                    GoCardlessSetup(
                        configured = goCardlessConfigured,
                        onSaveCredentials = onSaveGoCardlessCredentials,
                        institutions = goCardlessInstitutions,
                        institutionsLoading = goCardlessInstitutionsLoading,
                        onLoadInstitutions = onLoadGoCardlessInstitutions,
                        onAuthorize = onStartGoCardlessAuthorization,
                        discovery = goCardlessDiscovery,
                        onCheckAccounts = onCheckGoCardlessAccounts,
                        onLink = { linkingSource = it to "goCardless" },
                    )
                }
                ActuaCardDivider(inset = Spacing.screenHorizontal)
                ProviderRow(
                    name = stringResource(R.string.fs_provider_enable_banking), configured = enableBanking.configured,
                    expanded = expanded == "enableBanking",
                    onClick = { expanded = if (expanded == "enableBanking") null else "enableBanking" },
                ) {
                    EnableBankingSetup(
                        state = enableBanking,
                        onSaveCredentials = onSaveEnableBankingCredentials,
                        onLoadBanks = onLoadEnableBankingBanks,
                        onAuthorize = onAuthorizeEnableBanking,
                        onLink = { linkingSource = it to "enableBanking" },
                    )
                }
                ActuaCardDivider(inset = Spacing.screenHorizontal)
                ProviderRow(
                    name = stringResource(R.string.fs_provider_pluggyai), configured = pluggyAiConfigured,
                    expanded = expanded == "pluggyai",
                    onClick = { expanded = if (expanded == "pluggyai") null else "pluggyai" },
                ) {
                    PluggyAiSetup(
                        configured = pluggyAiConfigured,
                        onSaveCredentials = onSavePluggyAiCredentials,
                        discovery = pluggyAiDiscovery,
                        onDiscover = onDiscoverPluggyAi,
                        onLink = { linkingSource = it to "pluggyai" },
                    )
                }
                ActuaCardDivider(inset = Spacing.screenHorizontal)
                ProviderRow(
                    name = stringResource(R.string.fs_provider_akahu), configured = akahuConfigured,
                    expanded = expanded == "akahu",
                    onClick = { expanded = if (expanded == "akahu") null else "akahu" },
                ) {
                    AkahuSetup(
                        configured = akahuConfigured,
                        onSaveCredentials = onSaveAkahuCredentials,
                        discovery = akahuDiscovery,
                        onDiscover = onDiscoverAkahu,
                        onLink = { linkingSource = it to "akahu" },
                    )
                }
            }

            if (linkedAccounts.isNotEmpty()) {
                SectionLabel(stringResource(R.string.fs_linked_accounts))
                ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                    linkedAccounts.forEachIndexed { index, account ->
                        if (index > 0) ActuaCardDivider(inset = Spacing.screenHorizontal)
                        val statusLabel = bankSyncStatusLabel(account.bankSyncStatus)
                        ActuaListRow(
                            title = { Text(account.name) },
                            subtitle = {
                                Text(
                                    statusLabel ?: (account.bankSyncSource.orEmpty().ifBlank {
                                        stringResource(R.string.fs_bank_sync_linked)
                                    } + " · " + stringResource(R.string.fs_sync_settings)),
                                    color = if (statusLabel != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            onClick = { settingsAccount = account },
                            trailing = { TextButton(onClick = { onUnlink(account) }) { Text(stringResource(R.string.fs_unlink)) } },
                        )
                    }
                }
            }
        }
    }

    settingsAccount?.let { account ->
        SyncSettingsSheet(
            account = account,
            loadSettings = syncSettingsFor,
            onChange = { flag, value -> onSyncSettingChange(account, flag, value) },
            onDismiss = { settingsAccount = null },
        )
    }

    linkingSource?.let { (discovered, source) ->
        LinkAccountSheet(
            discovered = discovered,
            candidates = linkableAccounts,
            onDismiss = { linkingSource = null },
            onLinkExisting = { account -> onLinkExisting(discovered, account, source); linkingSource = null },
            onCreateNew = { name, offBudget -> onCreateAndLink(discovered, name, offBudget, source); linkingSource = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Spacing.screenHorizontal + Spacing.xs, end = Spacing.screenHorizontal,
            top = 20.dp, bottom = 6.dp,
        ),
    )
}

@Composable
private fun ProviderRow(
    name: String,
    configured: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        ActuaListRow(
            title = { Text(name) },
            subtitle = {
                Text(
                    stringResource(
                        if (configured) R.string.fs_provider_configured else R.string.fs_provider_not_configured,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onClick,
        )
        if (expanded) {
            Column(Modifier.padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.md)) { content() }
        }
    }
}

@Composable
private fun SimpleFinSetup(
    configured: Boolean,
    onSaveToken: (String) -> Unit,
    discovery: DiscoveryState,
    onDiscover: () -> Unit,
    onLink: (DiscoveredBankAccount) -> Unit,
) {
    var token by remember { mutableStateOf("") }
    Column {
        Text(
            stringResource(R.string.fs_simplefin_token_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text(stringResource(R.string.fs_setup_token)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        Button(onClick = { onSaveToken(token); token = "" }, enabled = token.isNotBlank()) { Text(stringResource(R.string.fs_save)) }
        if (configured) {
            Spacer(Modifier.height(Spacing.sm))
            Button(onClick = onDiscover, enabled = discovery != DiscoveryState.Loading) { Text(stringResource(R.string.fs_discover_accounts)) }
            DiscoveryResults(discovery, onLink)
        }
    }
}

@Composable
private fun GoCardlessSetup(
    configured: Boolean,
    onSaveCredentials: (String, String) -> Unit,
    institutions: List<GoCardlessInstitutionUi>,
    institutionsLoading: Boolean,
    onLoadInstitutions: (String) -> Unit,
    onAuthorize: (String) -> Unit,
    discovery: DiscoveryState,
    onCheckAccounts: () -> Unit,
    onLink: (DiscoveredBankAccount) -> Unit,
) {
    var secretId by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("GB") }
    var countryMenuOpen by remember { mutableStateOf(false) }
    var bankMenuOpen by remember { mutableStateOf(false) }
    Column {
        Text(
            stringResource(R.string.fs_gocardless_notice),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(secretId, { secretId = it }, label = { Text(stringResource(R.string.fs_secret_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(secretKey, { secretKey = it }, label = { Text(stringResource(R.string.fs_secret_key)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.sm))
        Button(onClick = { onSaveCredentials(secretId, secretKey); secretId = ""; secretKey = "" },
            enabled = secretId.isNotBlank() && secretKey.isNotBlank()) { Text(stringResource(R.string.fs_save)) }
        if (configured) {
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { countryMenuOpen = true }) { Text(stringResource(R.string.fs_country_label, country)) }
                DropdownMenu(countryMenuOpen, { countryMenuOpen = false }) {
                    listOf("GB", "DE", "FR", "ES", "IT", "NL", "IE", "SE").forEach { code ->
                        DropdownMenuItem(text = { Text(code) }, onClick = { country = code; countryMenuOpen = false; onLoadInstitutions(code) })
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (institutionsLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            if (institutions.isNotEmpty()) {
                Box {
                    TextButton(onClick = { bankMenuOpen = true }) { Text(stringResource(R.string.fs_choose_bank)) }
                    DropdownMenu(bankMenuOpen, { bankMenuOpen = false }) {
                        institutions.forEach { bank ->
                            DropdownMenuItem(text = { Text(bank.name) }, onClick = { bankMenuOpen = false; onAuthorize(bank.id) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Button(onClick = onCheckAccounts, enabled = discovery != DiscoveryState.Loading) {
                Text(stringResource(R.string.fs_gocardless_check_accounts))
            }
            DiscoveryResults(discovery, onLink)
        }
    }
}

@Composable
private fun PluggyAiSetup(
    configured: Boolean,
    onSaveCredentials: (clientId: String, clientSecret: String, itemIds: String) -> Unit,
    discovery: DiscoveryState,
    onDiscover: () -> Unit,
    onLink: (DiscoveredBankAccount) -> Unit,
) {
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var itemIds by remember { mutableStateOf("") }
    Column {
        Text(
            stringResource(R.string.fs_pluggyai_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(clientId, { clientId = it }, label = { Text(stringResource(R.string.fs_pluggyai_client_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            clientSecret, { clientSecret = it }, label = { Text(stringResource(R.string.fs_pluggyai_client_secret)) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(itemIds, { itemIds = it }, label = { Text(stringResource(R.string.fs_pluggyai_item_ids)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.sm))
        Button(
            onClick = { onSaveCredentials(clientId.trim(), clientSecret.trim(), itemIds.trim()); clientId = ""; clientSecret = ""; itemIds = "" },
            enabled = clientId.isNotBlank() && clientSecret.isNotBlank() && itemIds.isNotBlank(),
        ) { Text(stringResource(R.string.fs_save)) }
        if (configured) {
            Spacer(Modifier.height(Spacing.sm))
            Button(onClick = onDiscover, enabled = discovery != DiscoveryState.Loading) { Text(stringResource(R.string.fs_discover_accounts)) }
            DiscoveryResults(discovery, onLink)
        }
    }
}

@Composable
private fun AkahuSetup(
    configured: Boolean,
    onSaveCredentials: (userToken: String, appToken: String) -> Unit,
    discovery: DiscoveryState,
    onDiscover: () -> Unit,
    onLink: (DiscoveredBankAccount) -> Unit,
) {
    var userToken by remember { mutableStateOf("") }
    var appToken by remember { mutableStateOf("") }
    Column {
        Text(
            stringResource(R.string.fs_akahu_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            userToken, { userToken = it }, label = { Text(stringResource(R.string.fs_akahu_user_token)) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            appToken, { appToken = it }, label = { Text(stringResource(R.string.fs_akahu_app_token)) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        Button(
            onClick = { onSaveCredentials(userToken.trim(), appToken.trim()); userToken = ""; appToken = "" },
            enabled = userToken.isNotBlank() && appToken.isNotBlank(),
        ) { Text(stringResource(R.string.fs_save)) }
        if (configured) {
            Spacer(Modifier.height(Spacing.sm))
            Button(onClick = onDiscover, enabled = discovery != DiscoveryState.Loading) { Text(stringResource(R.string.fs_discover_accounts)) }
            DiscoveryResults(discovery, onLink)
        }
    }
}

@Composable
private fun EnableBankingSetup(
    state: EnableBankingUiState,
    onSaveCredentials: (String, String) -> Unit,
    onLoadBanks: (String) -> Unit,
    onAuthorize: (String) -> Unit,
    onLink: (DiscoveredBankAccount) -> Unit,
) {
    val context = LocalContext.current
    var applicationId by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var keyFileName by remember { mutableStateOf<String?>(null) }
    var keyFileError by remember { mutableStateOf<String?>(null) }
    var country by remember { mutableStateOf("DE") }
    var countryMenuOpen by remember { mutableStateOf(false) }
    var bankMenuOpen by remember { mutableStateOf(false) }
    val keyFileOpenFailed = stringResource(R.string.fs_key_file_open_failed)
    val keyFileEmpty = stringResource(R.string.fs_key_file_empty)
    val keyFileReadFailed = stringResource(R.string.fs_key_file_read_failed)
    val keyFileFallback = stringResource(R.string.fs_key_file_fallback)
    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error(keyFileOpenFailed)
            require(text.isNotBlank()) { keyFileEmpty }
            secretKey = text
            keyFileName = uri.lastPathSegment?.substringAfterLast('/') ?: keyFileFallback
            keyFileError = null
        }.onFailure {
            secretKey = ""; keyFileName = null
            keyFileError = it.message ?: keyFileReadFailed
        }
    }
    Column {
        Text(
            stringResource(R.string.fs_enable_banking_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            applicationId, { applicationId = it }, label = { Text(stringResource(R.string.fs_application_id)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.xs))
        TextButton(onClick = { pickKey.launch(arrayOf("*/*")) }) {
            Text(
                keyFileName?.let { stringResource(R.string.fs_key_file_selected, it) }
                    ?: stringResource(R.string.fs_choose_key_file),
            )
        }
        keyFileError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(Spacing.sm))
        Button(
            onClick = { onSaveCredentials(applicationId.trim(), secretKey); applicationId = ""; secretKey = ""; keyFileName = null },
            enabled = applicationId.isNotBlank() && secretKey.isNotBlank(),
        ) { Text(stringResource(R.string.fs_save)) }
        if (state.configured) {
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { countryMenuOpen = true }) { Text(stringResource(R.string.fs_country_label, country)) }
                DropdownMenu(countryMenuOpen, { countryMenuOpen = false }) {
                    enableBankingCountries.forEach { code ->
                        DropdownMenuItem(text = { Text(code) }, onClick = { country = code; countryMenuOpen = false; onLoadBanks(code) })
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (state.banksLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            if (state.banks.isEmpty() && !state.banksLoading) {
                TextButton(onClick = { onLoadBanks(country) }) { Text(stringResource(R.string.fs_load_banks_for, country)) }
            }
            if (state.banks.isNotEmpty()) {
                Box {
                    TextButton(onClick = { bankMenuOpen = true }) { Text(stringResource(R.string.fs_choose_bank)) }
                    DropdownMenu(bankMenuOpen, { bankMenuOpen = false }) {
                        state.banks.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { bankMenuOpen = false; onAuthorize(name) })
                        }
                    }
                }
            }
            DiscoveryResults(state.discovery, onLink)
        }
    }
}

@Composable
private fun DiscoveryResults(discovery: DiscoveryState, onLink: (DiscoveredBankAccount) -> Unit) {
    when (discovery) {
        is DiscoveryState.Loading -> Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.fs_loading))
        }
        is DiscoveryState.Pending -> Text(discovery.message, modifier = Modifier.padding(top = Spacing.sm),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is DiscoveryState.Error -> Text(discovery.message, modifier = Modifier.padding(top = Spacing.sm),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        is DiscoveryState.Available -> Column(Modifier.padding(top = Spacing.sm)) {
            if (discovery.accounts.isEmpty()) {
                Text(stringResource(R.string.fs_no_accounts_found), style = MaterialTheme.typography.bodySmall)
            } else discovery.accounts.forEach { account ->
                ActuaListRow(
                    title = { Text(account.label) },
                    subtitle = account.subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
                    trailing = { TextButton(onClick = { onLink(account) }) { Text(stringResource(R.string.fs_link)) } },
                )
            }
        }
        DiscoveryState.Idle -> {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkAccountSheet(
    discovered: DiscoveredBankAccount,
    candidates: List<Account>,
    onDismiss: () -> Unit,
    onLinkExisting: (Account) -> Unit,
    onCreateNew: (name: String, offBudget: Boolean) -> Unit,
) {
    var creatingNew by remember { mutableStateOf(candidates.isEmpty()) }
    var name by remember { mutableStateOf(discovered.label) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle(stringResource(R.string.fs_link_account_title, discovered.label))
            if (!creatingNew) {
                ActuaSheetCard {
                    candidates.forEachIndexed { index, account ->
                        if (index > 0) ActuaCardDivider()
                        ActuaSheetAction(account.name, icon = Icons.Outlined.AccountBalance,
                            onClick = { onLinkExisting(account) })
                    }
                }
                ActuaSheetCard {
                    ActuaSheetAction(stringResource(R.string.fs_create_new_account_instead), icon = Icons.Outlined.Add,
                        onClick = { creatingNew = true })
                }
            } else {
                ActuaSheetCard {
                    ActuaFormTextField(icon = Icons.Outlined.Edit, label = stringResource(R.string.fs_account_name), value = name,
                        onValueChange = { name = it })
                }
                Column(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                    Button(
                        onClick = { onCreateNew(name.trim(), false) },
                        enabled = name.isNotBlank(),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.primaryButtonHeight),
                    ) { Text(stringResource(R.string.fs_create_and_link)) }
                    if (candidates.isNotEmpty()) {
                        TextButton(onClick = { creatingNew = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.fs_link_existing_instead))
                        }
                    }
                }
            }
        }
    }
}

/** Actual's per-account Bank Sync toggles. Changes apply at once and are written through the synced preferences. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncSettingsSheet(
    account: Account,
    loadSettings: (Account) -> BankSyncAccountSettings?,
    onChange: (BankSyncFlag, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember(account.id) { mutableStateMapOf<BankSyncFlag, Boolean>() }
    // The budget database is read off the main thread; the toggles appear once their values are known.
    val loaded by produceState(false, account.id) {
        val settings = withContext(Dispatchers.IO) { loadSettings(account) }
        BankSyncFlag.entries.forEach { values[it] = settings?.get(it) ?: it.default }
        value = true
    }
    val rows = listOf(
        Triple(BankSyncFlag.ImportPending, R.string.fs_sync_import_pending, R.string.fs_sync_import_pending_desc),
        Triple(BankSyncFlag.ImportNotes, R.string.fs_sync_import_notes, R.string.fs_sync_import_notes_desc),
        Triple(BankSyncFlag.ImportTransactions, R.string.fs_sync_import_transactions, R.string.fs_sync_import_transactions_desc),
        Triple(BankSyncFlag.UpdateDates, R.string.fs_sync_update_dates, R.string.fs_sync_update_dates_desc),
        Triple(BankSyncFlag.ReimportDeleted, R.string.fs_sync_reimport_deleted, R.string.fs_sync_reimport_deleted_desc),
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ActuaSheetContent {
            ActuaSheetTitle(stringResource(R.string.fs_sync_settings_title, account.name))
            if (!loaded) {
                CircularProgressIndicator(Modifier.padding(Spacing.screenHorizontal).size(24.dp))
                return@ActuaSheetContent
            }
            ActuaSheetCard {
                rows.forEachIndexed { index, (flag, title, description) ->
                    if (index > 0) ActuaCardDivider()
                    val checked = values[flag] ?: flag.default
                    ActuaListRow(
                        title = { Text(stringResource(title)) },
                        subtitle = { Text(stringResource(description), style = MaterialTheme.typography.bodySmall) },
                        onClick = { values[flag] = !checked; onChange(flag, !checked) },
                        trailing = {
                            // The row handles the tap, so the switch itself is not a second target.
                            Switch(checked = checked, onCheckedChange = null)
                        },
                    )
                }
            }
            Text(
                stringResource(R.string.fs_sync_mappings_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            )
        }
    }
}
