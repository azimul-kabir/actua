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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

/** Enable Banking is experimental (Settings → Experimental); it is only shown while [visible]. */
data class EnableBankingUiState(
    val visible: Boolean = false,
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

private fun bankSyncStatusLabel(status: String?): String? = when (status) {
    null, "ok" -> null
    "reauth-required" -> "Needs reauthorization"
    "attention-required" -> "Needs attention"
    "rate-limit-exceeded" -> "Rate limited — try again later"
    "timed-out" -> "Bank connection timed out"
    "account-missing" -> "Bank no longer returns this account"
    else -> "Sync problem"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSyncScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    simpleFinConfigured: Boolean,
    goCardlessConfigured: Boolean,
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
    onLinkExisting: (DiscoveredBankAccount, Account, source: String) -> Unit,
    onCreateAndLink: (DiscoveredBankAccount, name: String, offBudget: Boolean, source: String) -> Unit,
    onUnlink: (Account) -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    var linkingSource by remember { mutableStateOf<Pair<DiscoveredBankAccount, String>?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        ActuaScreenHeader(title = "Bank Sync", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl)) {
            SectionLabel("Providers")
            ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                ProviderRow(
                    name = "SimpleFIN", configured = simpleFinConfigured,
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
                    name = "GoCardless", configured = goCardlessConfigured,
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
                if (enableBanking.visible) {
                    ActuaCardDivider(inset = Spacing.screenHorizontal)
                    ProviderRow(
                        name = "Enable Banking (experimental)", configured = enableBanking.configured,
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
                }
                ActuaCardDivider(inset = Spacing.screenHorizontal)
                ProviderRow(
                    name = "Pluggy.ai", configured = false, notSupported = true,
                    expanded = expanded == "pluggyai",
                    onClick = { expanded = if (expanded == "pluggyai") null else "pluggyai" },
                ) {
                    Text(
                        "Pluggy.ai account discovery and linking isn't available in Actua yet. " +
                            "Credentials can still be configured directly on your Actual server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
                    )
                }
            }

            if (linkedAccounts.isNotEmpty()) {
                SectionLabel("Linked accounts")
                ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                    linkedAccounts.forEachIndexed { index, account ->
                        if (index > 0) ActuaCardDivider(inset = Spacing.screenHorizontal)
                        val statusLabel = bankSyncStatusLabel(account.bankSyncStatus)
                        ActuaListRow(
                            title = { Text(account.name) },
                            subtitle = {
                                Text(
                                    statusLabel ?: (account.bankSyncSource.orEmpty().ifBlank { "Linked" }),
                                    color = if (statusLabel != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailing = { TextButton(onClick = { onUnlink(account) }) { Text("Unlink") } },
                        )
                    }
                }
            }
        }
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
    notSupported: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        ActuaListRow(
            title = { Text(name) },
            subtitle = {
                Text(
                    if (notSupported) "Not yet supported" else if (configured) "Configured" else "Not configured",
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
            "Paste the setup token from your SimpleFIN Bridge account.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("Setup token") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        Button(onClick = { onSaveToken(token); token = "" }, enabled = token.isNotBlank()) { Text("Save") }
        if (configured) {
            Spacer(Modifier.height(Spacing.sm))
            Button(onClick = onDiscover, enabled = discovery != DiscoveryState.Loading) { Text("Discover accounts") }
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
            "GoCardless (BankAccountData) is no longer accepting new accounts, but existing users remain supported.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(secretId, { secretId = it }, label = { Text("Secret ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(secretKey, { secretKey = it }, label = { Text("Secret Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.sm))
        Button(onClick = { onSaveCredentials(secretId, secretKey); secretId = ""; secretKey = "" },
            enabled = secretId.isNotBlank() && secretKey.isNotBlank()) { Text("Save") }
        if (configured) {
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { countryMenuOpen = true }) { Text("Country: $country") }
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
                    TextButton(onClick = { bankMenuOpen = true }) { Text("Choose bank to link") }
                    DropdownMenu(bankMenuOpen, { bankMenuOpen = false }) {
                        institutions.forEach { bank ->
                            DropdownMenuItem(text = { Text(bank.name) }, onClick = { bankMenuOpen = false; onAuthorize(bank.id) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Button(onClick = onCheckAccounts, enabled = discovery != DiscoveryState.Loading) {
                Text("I've authorized — check accounts")
            }
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
    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Could not open the key file.")
            require(text.isNotBlank()) { "The key file is empty." }
            secretKey = text
            keyFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "key file"
            keyFileError = null
        }.onFailure {
            secretKey = ""; keyFileName = null
            keyFileError = it.message ?: "Could not read the key file."
        }
    }
    Column {
        Text(
            "Experimental, like in Actual. Create an application at enablebanking.com and allow the redirect URL " +
                "<your Actual server>/enablebanking/auth_callback. Enter its Application ID and choose the private key file. " +
                "Both go to your Actual server and are not kept on this device.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            applicationId, { applicationId = it }, label = { Text("Application ID") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.xs))
        TextButton(onClick = { pickKey.launch(arrayOf("*/*")) }) { Text(keyFileName?.let { "Key file: $it" } ?: "Choose private key file") }
        keyFileError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(Spacing.sm))
        Button(
            onClick = { onSaveCredentials(applicationId.trim(), secretKey); applicationId = ""; secretKey = ""; keyFileName = null },
            enabled = applicationId.isNotBlank() && secretKey.isNotBlank(),
        ) { Text("Save") }
        if (state.configured) {
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { countryMenuOpen = true }) { Text("Country: $country") }
                DropdownMenu(countryMenuOpen, { countryMenuOpen = false }) {
                    enableBankingCountries.forEach { code ->
                        DropdownMenuItem(text = { Text(code) }, onClick = { country = code; countryMenuOpen = false; onLoadBanks(code) })
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (state.banksLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            if (state.banks.isEmpty() && !state.banksLoading) {
                TextButton(onClick = { onLoadBanks(country) }) { Text("Load banks for $country") }
            }
            if (state.banks.isNotEmpty()) {
                Box {
                    TextButton(onClick = { bankMenuOpen = true }) { Text("Choose bank to link") }
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
            Spacer(Modifier.width(8.dp)); Text("Loading…")
        }
        is DiscoveryState.Pending -> Text(discovery.message, modifier = Modifier.padding(top = Spacing.sm),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is DiscoveryState.Error -> Text(discovery.message, modifier = Modifier.padding(top = Spacing.sm),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        is DiscoveryState.Available -> Column(Modifier.padding(top = Spacing.sm)) {
            if (discovery.accounts.isEmpty()) {
                Text("No accounts found.", style = MaterialTheme.typography.bodySmall)
            } else discovery.accounts.forEach { account ->
                ActuaListRow(
                    title = { Text(account.label) },
                    subtitle = account.subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
                    trailing = { TextButton(onClick = { onLink(account) }) { Text("Link") } },
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
            ActuaSheetTitle("Link \"${discovered.label}\"")
            if (!creatingNew) {
                ActuaSheetCard {
                    candidates.forEachIndexed { index, account ->
                        if (index > 0) ActuaCardDivider()
                        ActuaSheetAction(account.name, icon = Icons.Outlined.AccountBalance,
                            onClick = { onLinkExisting(account) })
                    }
                }
                ActuaSheetCard {
                    ActuaSheetAction("Create a new account instead", icon = Icons.Outlined.Add,
                        onClick = { creatingNew = true })
                }
            } else {
                ActuaSheetCard {
                    ActuaFormTextField(icon = Icons.Outlined.Edit, label = "Account name", value = name,
                        onValueChange = { name = it })
                }
                Column(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                    Button(
                        onClick = { onCreateNew(name.trim(), false) },
                        enabled = name.isNotBlank(),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.primaryButtonHeight),
                    ) { Text("Create and link") }
                    if (candidates.isNotEmpty()) {
                        TextButton(onClick = { creatingNew = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Link to an existing account instead")
                        }
                    }
                }
            }
        }
    }
}
