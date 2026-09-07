package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class SettingsPage(val title: String) {
    Main("More"), Transactions("Transactions & Automation"),
    Display("Display"), Privacy("Privacy"), Information("Information"),
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onConnectionClick: () -> Unit = {},
    hideDecimalPlaces: Boolean = false,
    onHideDecimalPlacesChange: (Boolean) -> Unit = {},
    currencyCode: String = "BDT",
    onCurrencyCodeChange: (String) -> Unit = {},
    currencySymbolOnly: Boolean = false,
    onCurrencySymbolOnlyChange: (Boolean) -> Unit = {},
    hideBalances: Boolean = false,
    onHideBalancesChange: (Boolean) -> Unit = {},
    appearance: String = "System",
    onAppearanceChange: (String) -> Unit = {},
    startPage: String = "Accounts",
    onStartPageChange: (String) -> Unit = {},
    accountOptions: List<String> = emptyList(),
    defaultAccount: String? = null,
    onDefaultAccountChange: (String?) -> Unit = {},
    groupTransactionsByDate: Boolean = true,
    onGroupTransactionsByDateChange: (Boolean) -> Unit = {},
    showAccountsMonthlySummary: Boolean = true,
    onShowAccountsMonthlySummaryChange: (Boolean) -> Unit = {},
    onCreditCardsClick: () -> Unit = {},
    onRulesClick: () -> Unit = {},
    conventionalAmountEntry: Boolean = false,
    onConventionalAmountEntryChange: (Boolean) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    BackHandler(enabled = page != SettingsPage.Main) { page = SettingsPage.Main }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsHeader(page.title, page != SettingsPage.Main) { page = SettingsPage.Main }
        when (page) {
            SettingsPage.Main -> {
                SettingsRow("Connection & Data", "Actual server, budgets, local data and backups", true, onConnectionClick)
                SettingsRow("Transactions & Automation", "Entry defaults, account summaries, cards and rules", true) { page = SettingsPage.Transactions }
                SettingsRow("Display", "Currency, appearance, start page and decimals", true) { page = SettingsPage.Display }
                SettingsRow("Privacy", "Control sensitive information on screen", true) { page = SettingsPage.Privacy }
                SettingsRow("Information", "About Actua and project credits", true) { page = SettingsPage.Information }
            }
            SettingsPage.Transactions -> {
                SettingsChoice("Default account", defaultAccount ?: "None", listOf("None") + accountOptions) {
                    onDefaultAccountChange(it.takeUnless { value -> value == "None" })
                }
                SettingsToggle("Group transactions by date", "Use dated sections in transaction lists", groupTransactionsByDate, onGroupTransactionsByDateChange)
                SettingsToggle("Conventional amount entry", "Type 324 as 324.00 instead of filling cents first",
                    conventionalAmountEntry, onConventionalAmountEntryChange)
                SettingsToggle("Account monthly summary", "Show Income, Expenses and Net at the top of Accounts",
                    showAccountsMonthlySummary, onShowAccountsMonthlySummaryChange)
                SettingsRow("Credit Cards & Billing Cycles", "Cycle spend, due dates and credit limits", true, onCreditCardsClick)
                SettingsRow("Rules", "Automatically categorize and transform transactions", true, onRulesClick)
            }
            SettingsPage.Display -> {
                SettingsChoice("Currency", currencyLabel(currencyCode), currencyOptions.map { it.first }) { selected ->
                    onCurrencyCodeChange(currencyOptions.first { it.first == selected }.second)
                }
                if (currencyCode.isNotBlank()) SettingsToggle("Symbol only",
                    "Show ${'$'} instead of US${'$'}, CA${'$'} or A${'$'} where applicable",
                    currencySymbolOnly, onCurrencySymbolOnlyChange)
                SettingsChoice("Appearance", appearance, listOf("System", "Light", "Dark"), onAppearanceChange)
                SettingsChoice(
                    "Start page",
                    startPage,
                    listOf("Budget", "Accounts", "Transactions", "Reports", "More"),
                    onStartPageChange,
                )
                SettingsToggle("Hide decimal places", "Round displayed amounts without changing their values",
                    hideDecimalPlaces, onHideDecimalPlacesChange)
            }
            SettingsPage.Privacy -> SettingsToggle("Hide balances", "Mask budget, account and transaction amounts",
                hideBalances, onHideBalancesChange)
            SettingsPage.Information -> ListItem(
                headlineContent = { Text("Actua") },
                supportingContent = { Text("Actual-compatible local budget and sync client") },
            )
        }
    }
}

@Composable
private fun SettingsHeader(title: String, showBack: Boolean, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (showBack) IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = if (showBack) 4.dp else 16.dp, vertical = 10.dp))
    }
}

private val currencyOptions = listOf(
    "None" to "",
    "৳ BDT" to "BDT",
    "${'$'} USD" to "USD",
    "€ EUR" to "EUR",
    "£ GBP" to "GBP",
    "C${'$'} CAD" to "CAD",
    "A${'$'} AUD" to "AUD",
    "¥ JPY" to "JPY",
    "₹ INR" to "INR",
    "¥ CNY" to "CNY",
    "S${'$'} SGD" to "SGD",
    "د.إ AED" to "AED",
    "ر.س SAR" to "SAR",
)

private fun currencyLabel(code: String): String =
    currencyOptions.firstOrNull { it.second == code }?.first ?: code.ifBlank { "None" }

@Composable
private fun SettingsChoice(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) { Text(value) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.distinct().forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            expanded = false
                            onChange(option)
                        })
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingsToggle(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(detail) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}

@Composable
private fun SettingsSection(label: String) {
    HorizontalDivider()
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun SettingsRow(label: String, detail: String, enabled: Boolean = false, onClick: () -> Unit = {}) {
    ListItem(headlineContent = { Text(label) }, supportingContent = {
        Text(if (enabled) detail else "$detail · Coming with backend port")
    }, trailingContent = {
        if (enabled) Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }, modifier = Modifier.clickable(enabled = enabled, onClick = onClick))
}
