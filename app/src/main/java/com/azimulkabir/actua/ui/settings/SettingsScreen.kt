package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.BuildConfig
import com.azimulkabir.actua.data.location.ForegroundLocationPermission
import com.azimulkabir.actua.data.preferences.LocationPreferences

private enum class SettingsPage(val title: String) {
    Manage("Manage"), General("Settings"), Transactions("Transactions & Accounts"),
    Display("Display"), Privacy("Privacy"), About("About"),
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onConnectionClick: () -> Unit = {},
    hideDecimalPlaces: Boolean = true,
    onHideDecimalPlacesChange: (Boolean) -> Unit = {},
    currencyCode: String = "",
    onCurrencyCodeChange: (String) -> Unit = {},
    currencySymbolOnly: Boolean = false,
    onCurrencySymbolOnlyChange: (Boolean) -> Unit = {},
    dateFormat: String = "System default",
    onDateFormatChange: (String) -> Unit = {},
    numberFormat: String = "System default",
    onNumberFormatChange: (String) -> Unit = {},
    hideBalances: Boolean = false,
    onHideBalancesChange: (Boolean) -> Unit = {},
    appearance: String = "System",
    onAppearanceChange: (String) -> Unit = {},
    startPage: String = "Budget",
    onStartPageChange: (String) -> Unit = {},
    accountOptions: List<String> = emptyList(),
    defaultAccount: String? = null,
    onDefaultAccountChange: (String?) -> Unit = {},
    groupTransactionsByDate: Boolean = true,
    onGroupTransactionsByDateChange: (Boolean) -> Unit = {},
    showAccountsMonthlySummary: Boolean = true,
    onShowAccountsMonthlySummaryChange: (Boolean) -> Unit = {},
    onCreditCardsClick: () -> Unit = {},
    onBillsCalendarClick: () -> Unit = {},
    onRulesClick: () -> Unit = {},
    onSchedulesClick: () -> Unit = {},
    onImportTransactionsClick: () -> Unit = {},
    onPayeeLocationsClick: () -> Unit = {},
    conventionalAmountEntry: Boolean = true,
    onConventionalAmountEntryChange: (Boolean) -> Unit = {},
    showBottomNavigationLabels: Boolean = true,
    onShowBottomNavigationLabelsChange: (Boolean) -> Unit = {},
    showCurrentBalanceSummary: Boolean = true,
    onShowCurrentBalanceSummaryChange: (Boolean) -> Unit = {},
    returnToRootRequest: Int = 0,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val locationPreferences = remember { LocationPreferences(context) }
    val initialLocationPermissionGranted = remember {
        ForegroundLocationPermission.isGranted(context)
    }
    var locationPermissionGranted by remember {
        mutableStateOf(initialLocationPermissionGranted)
    }
    var recordPayeeLocations by remember {
        mutableStateOf(
            locationPreferences.recordPayeeLocations && initialLocationPermissionGranted,
        )
    }
    LaunchedEffect(initialLocationPermissionGranted) {
        if (!initialLocationPermissionGranted && locationPreferences.recordPayeeLocations) {
            locationPreferences.recordPayeeLocations = false
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants.values.any { it } || ForegroundLocationPermission.isGranted(context)
        locationPermissionGranted = granted
        locationPreferences.recordPayeeLocations = granted
        recordPayeeLocations = granted
    }

    fun setRecordPayeeLocations(enabled: Boolean) {
        if (!enabled) {
            locationPreferences.recordPayeeLocations = false
            recordPayeeLocations = false
            return
        }
        if (ForegroundLocationPermission.isGranted(context)) {
            locationPermissionGranted = true
            locationPreferences.recordPayeeLocations = true
            recordPayeeLocations = true
        } else {
            locationPermissionLauncher.launch(ForegroundLocationPermission.permissions)
        }
    }

    var page by rememberSaveable { mutableStateOf(SettingsPage.Manage) }
    val scrollState = rememberScrollState()
    fun parentPage(current: SettingsPage): SettingsPage = when (current) {
        SettingsPage.Transactions, SettingsPage.Display, SettingsPage.Privacy, SettingsPage.About ->
            SettingsPage.General
        SettingsPage.General -> SettingsPage.Manage
        SettingsPage.Manage -> SettingsPage.Manage
    }
    fun navigateBack() {
        page = parentPage(page)
    }
    LaunchedEffect(returnToRootRequest) {
        if (returnToRootRequest > 0) {
            if (page != SettingsPage.Manage) page = SettingsPage.Manage else scrollState.animateScrollTo(0)
        }
    }
    BackHandler(enabled = page != SettingsPage.Manage, onBack = ::navigateBack)
    fun openFullScreen(action: () -> Unit) {
        page = SettingsPage.Manage
        action()
    }
    AnimatedContent(
        targetState = page,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val opening = initialState == SettingsPage.Manage && targetState != SettingsPage.Manage
            if (opening) {
                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 5 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 10 })
            } else {
                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { -it / 5 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 10 })
            }.using(SizeTransform(clip = false))
        },
        label = "Settings navigation motion",
    ) { shownPage ->
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
        SettingsHeader(
            title = shownPage.title,
            showBack = shownPage != SettingsPage.Manage,
            showSettings = shownPage == SettingsPage.Manage,
            onBack = ::navigateBack,
            onSettings = { page = SettingsPage.General },
        )
        when (shownPage) {
            SettingsPage.Manage -> {
                SettingsSection("Automation")
                SettingsRow("Bills & Calendar", "Upcoming schedules and credit-card due dates", true) {
                    openFullScreen(onBillsCalendarClick)
                }
                SettingsRow("Scheduled Transactions", "Review recurring bills, income and upcoming dates", true) {
                    openFullScreen(onSchedulesClick)
                }
                SettingsRow("Rules", "Automatically categorize and transform transactions", true) {
                    openFullScreen(onRulesClick)
                }
                SettingsSection("Transactions & data")
                SettingsRow("Import Transactions", "Review a CSV bank statement before importing", true) {
                    openFullScreen(onImportTransactionsClick)
                }
                SettingsRow("Connection & Data", "Actual server, budgets, sync, backups and restore", true) {
                    openFullScreen(onConnectionClick)
                }
                SettingsSection("Financial setup")
                SettingsRow("Credit Cards & Billing Cycles", "Cycle spend, due dates and credit limits", true) {
                    openFullScreen(onCreditCardsClick)
                }
            }
            SettingsPage.General -> {
                SettingsSection("Preferences")
                SettingsRow("Transactions & Accounts", "Entry defaults, transaction lists and account summaries", true) {
                    page = SettingsPage.Transactions
                }
                SettingsRow("Display", "Currency, date, numbers, appearance and start page", true) {
                    page = SettingsPage.Display
                }
                SettingsRow("Privacy", "Balances and optional location-aware payee controls", true) {
                    page = SettingsPage.Privacy
                }
                SettingsSection("About")
                SettingsRow("About Actua", "Version, project information, credits and license", true) {
                    page = SettingsPage.About
                }
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
                SettingsToggle(
                    "Current balance summary",
                    "Show current, cleared, uncleared and reconciled balances inside accounts",
                    showCurrentBalanceSummary,
                    onShowCurrentBalanceSummaryChange,
                )
                SettingsRow("Credit Cards & Billing Cycles", "Cycle spend, due dates and credit limits", true) {
                    openFullScreen(onCreditCardsClick)
                }
            }
            SettingsPage.Display -> {
                SettingsChoice("Currency", currencyLabel(currencyCode), currencyOptions.map { it.first }) { selected ->
                    onCurrencyCodeChange(currencyOptions.first { it.first == selected }.second)
                }
                if (currencyCode.isNotBlank()) SettingsToggle("Symbol only",
                    "Show ${'$'} instead of US${'$'}, CA${'$'} or A${'$'} where applicable",
                    currencySymbolOnly, onCurrencySymbolOnlyChange)
                SettingsChoice(
                    "Date format",
                    dateFormat,
                    listOf("System default", "DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD"),
                    onDateFormatChange,
                )
                Text(
                    "Preview: ${datePreview(dateFormat)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                SettingsChoice(
                    "Number format",
                    numberFormat,
                    listOf("System default", "1,234.56", "1.234,56", "1 234,56", "1234.56", "1,23,456.78"),
                    onNumberFormatChange,
                )
                Text(
                    "Preview: ${numberPreview(numberFormat)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                SettingsChoice("Appearance", appearance, listOf("System", "Light", "Dark"), onAppearanceChange)
                SettingsChoice(
                    "Start page",
                    startPage,
                    listOf("Budget", "Accounts", "Transactions", "Reports", "Manage"),
                    onStartPageChange,
                )
                SettingsChoice(
                    "Bottom navigation labels",
                    if (showBottomNavigationLabels) "Icons and names" else "Icons only",
                    listOf("Icons and names", "Icons only"),
                ) { onShowBottomNavigationLabelsChange(it == "Icons and names") }
                SettingsToggle("Hide decimal places", "Round displayed amounts without changing their values",
                    hideDecimalPlaces, onHideDecimalPlacesChange)
            }
            SettingsPage.Privacy -> {
                SettingsToggle("Hide balances", "Mask budget, account and transaction amounts",
                    hideBalances, onHideBalancesChange)
                SettingsSection("Location-aware payees")
                SettingsToggle(
                    "Record payee locations",
                    if (recordPayeeLocations && locationPermissionGranted) {
                        "Use your location only while Actua is open to remember eligible payees nearby. Coordinates stay in your Actual budget and sync with it."
                    } else {
                        "Optional and off by default. Enabling asks for foreground location permission. No background tracking or third-party location service is used."
                    },
                    recordPayeeLocations,
                    ::setRecordPayeeLocations,
                )
                SettingsRow(
                    "Payee Locations",
                    "Inspect or delete coordinates saved in this budget",
                    true,
                ) { openFullScreen(onPayeeLocationsClick) }
                Text(
                    if (locationPermissionGranted) {
                        "Location permission: allowed while using the app"
                    } else {
                        "Location permission: not granted"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            SettingsPage.About -> {
                ListItem(
                    headlineContent = { Text("Actua") },
                    supportingContent = {
                        Text("Native Android client for Actual Budget\nVersion ${BuildConfig.VERSION_NAME}")
                    },
                )
                SettingsSection("Project")
                ListItem(
                    headlineContent = { Text("Actua on GitHub") },
                    supportingContent = { Text("github.com/azimul-kabir/actua") },
                    trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/azimul-kabir/actua")
                    },
                )
                ListItem(
                    headlineContent = { Text("Independent community project") },
                    supportingContent = {
                        Text("Actua connects directly to your self-hosted Actual server and keeps budget data locally available offline. It is not affiliated with or endorsed by the Actual Budget team.")
                    },
                )
                SettingsSection("Credits")
                ListItem(
                    headlineContent = { Text("Actuali for iOS") },
                    supportingContent = {
                        Text("Actua was originally based on and continues to reference Matt Farrell’s open-source Actuali project for tested behavior and design guidance.")
                    },
                )
                ListItem(
                    headlineContent = { Text("Actual Budget") },
                    supportingContent = {
                        Text("Synchronization behavior is compatible with the open-source Actual Budget project.")
                    },
                )
                SettingsSection("Compatibility")
                ListItem(
                    headlineContent = { Text("Android 9 or later") },
                    supportingContent = { Text("Requires a reachable self-hosted Actual Budget server.") },
                )
                SettingsSection("License")
                ListItem(
                    headlineContent = { Text("MIT License") },
                    supportingContent = {
                        Text("Open-source notices and complete attribution are available in the repository’s LICENSE and NOTICE files.")
                    },
                )
            }
        }
    }
    }
}

private fun datePreview(format: String): String = when (format) {
    "DD/MM/YYYY" -> "31/12/2026"
    "MM/DD/YYYY" -> "12/31/2026"
    "YYYY-MM-DD" -> "2026-12-31"
    else -> java.time.LocalDate.of(2026, 12, 31)
        .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
}

private fun numberPreview(format: String): String = when (format) {
    "1,234.56" -> "1,234.56"
    "1.234,56" -> "1.234,56"
    "1 234,56" -> "1 234,56"
    "1234.56" -> "1234.56"
    "1,23,456.78" -> "1,23,456.78"
    else -> java.text.NumberFormat.getNumberInstance().format(1234.56)
}

@Composable
private fun SettingsHeader(
    title: String,
    showBack: Boolean,
    showSettings: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (showBack) IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
                .padding(horizontal = if (showBack) 4.dp else 16.dp, vertical = 10.dp),
        )
        if (showSettings) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }
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
