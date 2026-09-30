package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.azimulkabir.actua.BuildConfig
import com.azimulkabir.actua.data.budget.ActiveTagRepository
import com.azimulkabir.actua.data.location.ForegroundLocationPermission
import com.azimulkabir.actua.data.preferences.LocationPreferences
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaListRow
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSectionHeader
import com.azimulkabir.actua.ui.theme.LocalCategoryStatusColors
import com.azimulkabir.actua.ui.theme.Spacing

internal enum class SettingsPage(val title: String, val depth: Int) {
    Manage("Manage", 0),
    Tags("Tags", 1),
    General("Settings", 1),
    Transactions("Transactions & Accounts", 2),
    Display("Display", 2),
    Privacy("Privacy", 2),
    Budget("Budget", 2),
    CategoryColors("Category status colors", 3),
    About("About", 2),
}

internal fun isForwardSettingsNavigation(from: SettingsPage, to: SettingsPage): Boolean =
    to.depth > from.depth

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onConnectionClick: () -> Unit = {},
    hideDecimalPlaces: Boolean = true,
    onHideDecimalPlacesChange: (Boolean) -> Unit = {},
    showNotes: Boolean = true,
    onShowNotesChange: (Boolean) -> Unit = {},
    hideIncomeGroupInBudget: Boolean = false,
    onHideIncomeGroupInBudgetChange: (Boolean) -> Unit = {},
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
    useDynamicColor: Boolean = false,
    onUseDynamicColorChange: (Boolean) -> Unit = {},
    startPage: String = "Budget",
    onStartPageChange: (String) -> Unit = {},
    startPageOptions: List<String> = listOf("Home", "Budget", "Transactions", "Accounts", "Manage"),
    accountOptions: List<String> = emptyList(),
    defaultAccount: String? = null,
    onDefaultAccountChange: (String?) -> Unit = {},
    groupTransactionsByDate: Boolean = true,
    onGroupTransactionsByDateChange: (Boolean) -> Unit = {},
    showAccountsMonthlySummary: Boolean = true,
    onShowAccountsMonthlySummaryChange: (Boolean) -> Unit = {},
    onCreditCardsClick: () -> Unit = {},
    onBankSyncClick: () -> Unit = {},
    onBillsCalendarClick: () -> Unit = {},
    onRulesClick: () -> Unit = {},
    onSchedulesClick: () -> Unit = {},
    onImportTransactionsClick: () -> Unit = {},
    onPayeeLocationsClick: () -> Unit = {},
    onReportsClick: () -> Unit = {},
    /** Reports and Home appear under Insights only while they aren't tabs in the bottom bar. */
    showReportsShortcut: Boolean = true,
    showHomeShortcut: Boolean = true,
    onHomeClick: () -> Unit = {},
    onCustomizeHomeClick: () -> Unit = {},
    onCustomizeTabBarClick: () -> Unit = {},
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
    val tagRepository = remember { ActiveTagRepository(context) }
    var tagVersion by remember { mutableStateOf(0L) }
    val managedTags = remember(tagVersion) { tagRepository.tags(tagVersion) }
    val tagCapabilities = remember(tagVersion) { tagRepository.capabilities(tagVersion) }
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
        SettingsPage.Tags -> SettingsPage.Manage
        SettingsPage.Transactions, SettingsPage.Display, SettingsPage.Privacy, SettingsPage.Budget,
        SettingsPage.About,
        -> SettingsPage.General
        SettingsPage.CategoryColors -> SettingsPage.Budget
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
            val opening = isForwardSettingsNavigation(initialState, targetState)
            if (opening) {
                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 5 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 10 })
            } else {
                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { -it / 5 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 10 })
            // Settings pages may have very different scrollable heights. Preserve directional
            // motion without animating the layout bounds of two complete page trees.
            }.using(SizeTransform(sizeAnimationSpec = { _, _ -> snap() }, clip = false))
        },
        label = "Settings navigation motion",
    ) { shownPage ->
        if (shownPage == SettingsPage.Tags) {
            ManageTagsScreen(
                tags = managedTags,
                hiddenSupported = tagCapabilities.hidden,
                onBack = ::navigateBack,
                onCreate = { name, color, description, hidden ->
                    runCatching { tagRepository.create(name, color, description, hidden) }
                        .onSuccess { tagVersion += 1 }
                },
                onUpdate = { tag, name, color, description, hidden ->
                    runCatching { tagRepository.update(tag, name, color, description, hidden) }
                        .onSuccess { if (it) tagVersion += 1 }
                },
                onDelete = { tag ->
                    runCatching { tagRepository.delete(tag) }
                        .onSuccess { if (it) tagVersion += 1 }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            ActuaScreenHeader(
                title = shownPage.title,
                onBack = if (shownPage != SettingsPage.Manage) ::navigateBack else null,
            ) {
                if (shownPage == SettingsPage.Manage) {
                    IconButton(onClick = { page = SettingsPage.General }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            }
            when (shownPage) {
                SettingsPage.Manage -> {
                    if (showHomeShortcut || showReportsShortcut) SettingsGroup("Insights") {
                        if (showHomeShortcut) {
                            SettingsRow("Home", "Your dashboard of favorites, upcoming bills and activity", true, Icons.Outlined.Home) {
                                openFullScreen(onHomeClick)
                            }
                        }
                        if (showHomeShortcut && showReportsShortcut) ActuaCardDivider()
                        if (showReportsShortcut) {
                            SettingsRow("Reports", "View dashboards and financial reports", true, Icons.Outlined.BarChart) {
                                openFullScreen(onReportsClick)
                            }
                        }
                    }
                    SettingsGroup("Automation") {
                        SettingsRow("Bills & Calendar", "Upcoming schedules and credit-card due dates", true, Icons.Outlined.CalendarMonth) {
                            openFullScreen(onBillsCalendarClick)
                        }
                        ActuaCardDivider()
                        SettingsRow("Scheduled Transactions", "Review recurring bills, income and upcoming dates", true, Icons.Outlined.Repeat) {
                            openFullScreen(onSchedulesClick)
                        }
                        ActuaCardDivider()
                        SettingsRow("Rules", "Automatically categorize and transform transactions", true, Icons.Outlined.AutoAwesome) {
                            openFullScreen(onRulesClick)
                        }
                    }
                    SettingsGroup("Transactions & data") {
                        SettingsRow("Tags", "Create, edit, color, hide and delete managed tags", true, Icons.Outlined.Sell) {
                            page = SettingsPage.Tags
                        }
                        ActuaCardDivider()
                        SettingsRow("Import Transactions", "Review a CSV bank statement before importing", true, Icons.AutoMirrored.Outlined.ReceiptLong) {
                            openFullScreen(onImportTransactionsClick)
                        }
                        ActuaCardDivider()
                        SettingsRow("Connection & Data", "Actual server, budgets, sync, backups and restore", true, Icons.Outlined.Sync) {
                            openFullScreen(onConnectionClick)
                        }
                    }
                    SettingsGroup("Financial setup") {
                        SettingsRow("Bank Sync", "Connect SimpleFIN or GoCardless and link accounts", true, Icons.Outlined.AccountBalance) {
                            openFullScreen(onBankSyncClick)
                        }
                        ActuaCardDivider()
                        SettingsRow("Credit Cards & Billing Cycles", "Cycle spend, due dates and credit limits", true, Icons.Outlined.CreditCard) {
                            openFullScreen(onCreditCardsClick)
                        }
                    }
                }
                SettingsPage.General -> {
                    SettingsGroup("Preferences") {
                        SettingsRow("Home", "Show, hide and reorder the sections on Home", true, Icons.Outlined.Home) {
                            // Unlike openFullScreen's other destinations, Customize Home is reached
                            // from this page rather than from Manage root, so the back gesture should
                            // return here instead of resetting all the way to Manage.
                            onCustomizeHomeClick()
                        }
                        ActuaCardDivider()
                        SettingsRow("Transactions & Accounts", "Entry defaults, transaction lists and account summaries", true, Icons.Outlined.AccountBalanceWallet) {
                            page = SettingsPage.Transactions
                        }
                        ActuaCardDivider()
                        SettingsRow("Display", "Currency, date, numbers, appearance and start page", true, Icons.Outlined.Palette) {
                            page = SettingsPage.Display
                        }
                        ActuaCardDivider()
                        SettingsRow("Privacy", "Balances and optional location-aware payee controls", true, Icons.Outlined.Lock) {
                            page = SettingsPage.Privacy
                        }
                        ActuaCardDivider()
                        SettingsRow("Budget", "Category status dot, progress bar colors and income group", true, Icons.Outlined.PieChartOutline) {
                            page = SettingsPage.Budget
                        }
                    }
                    SettingsGroup("About") {
                        SettingsRow("About Actua", "Version, project information, credits and license", true, Icons.Outlined.Info) {
                            page = SettingsPage.About
                        }
                    }
                }
                SettingsPage.Transactions -> {
                    SettingsGroup {
                        SettingsChoice("Default account", defaultAccount ?: "None", listOf("None") + accountOptions) {
                            onDefaultAccountChange(it.takeUnless { value -> value == "None" })
                        }
                        SettingsDivider()
                        SettingsToggle("Group transactions by date", "Use dated sections in transaction lists", groupTransactionsByDate, onGroupTransactionsByDateChange)
                        SettingsDivider()
                        SettingsToggle("Conventional amount entry", "Type 324 as 324.00 instead of filling cents first",
                            conventionalAmountEntry, onConventionalAmountEntryChange)
                        SettingsDivider()
                        SettingsToggle("Account monthly summary", "Show Income, Expenses and Net at the top of Accounts",
                            showAccountsMonthlySummary, onShowAccountsMonthlySummaryChange)
                        SettingsDivider()
                        SettingsToggle(
                            "Current balance summary",
                            "Show current, cleared, uncleared and reconciled balances inside accounts",
                            showCurrentBalanceSummary,
                            onShowCurrentBalanceSummaryChange,
                        )
                    }
                    SettingsGroup {
                        SettingsRow("Credit Cards & Billing Cycles", "Cycle spend, due dates and credit limits", true, Icons.Outlined.CreditCard) {
                            openFullScreen(onCreditCardsClick)
                        }
                    }
                }
                SettingsPage.Display -> {
                    SettingsGroup {
                        SettingsChoice("Currency", currencyLabel(currencyCode), currencyOptions.map { it.first }) { selected ->
                            onCurrencyCodeChange(currencyOptions.first { it.first == selected }.second)
                        }
                        if (currencyCode.isNotBlank()) {
                            SettingsDivider()
                            SettingsToggle("Symbol only",
                                "Show ${'$'} instead of US${'$'}, CA${'$'} or A${'$'} where applicable",
                                currencySymbolOnly, onCurrencySymbolOnlyChange)
                        }
                        SettingsDivider()
                        SettingsChoice(
                            "Date format",
                            dateFormat,
                            listOf("System default", "DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD"),
                            preview = "Preview: ${datePreview(dateFormat)}",
                            onChange = onDateFormatChange,
                        )
                        SettingsDivider()
                        SettingsChoice(
                            "Number format",
                            numberFormat,
                            listOf("System default", "1,234.56", "1.234,56", "1 234,56", "1234.56", "1,23,456.78"),
                            preview = "Preview: ${numberPreview(numberFormat)}",
                            onChange = onNumberFormatChange,
                        )
                        SettingsDivider()
                        SettingsToggle("Hide decimal places", "Round displayed amounts without changing their values",
                            hideDecimalPlaces, onHideDecimalPlacesChange)
                    }
                    SettingsGroup {
                        SettingsChoice("Appearance", appearance, listOf("System", "Light", "Dark"), onChange = onAppearanceChange)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            SettingsDivider()
                            SettingsToggle(
                                "Material You colors",
                                "Match colors to your wallpaper instead of Actua's default theme",
                                useDynamicColor,
                                onUseDynamicColorChange,
                            )
                        }
                    }
                    SettingsGroup {
                        SettingsChoice(
                            "Start page",
                            startPage,
                            startPageOptions,
                            onChange = onStartPageChange,
                        )
                        SettingsDivider()
                        SettingsChoice(
                            "Bottom navigation labels",
                            if (showBottomNavigationLabels) "Icons and names" else "Icons only",
                            listOf("Icons and names", "Icons only"),
                        ) { onShowBottomNavigationLabelsChange(it == "Icons and names") }
                        SettingsDivider()
                        SettingsRow("Tab Bar", "Show, hide and reorder the bottom navigation tabs", true) {
                            // Reached from this page (Display), so back should return here rather than
                            // resetting all the way to Manage the way openFullScreen's other rows do.
                            onCustomizeTabBarClick()
                        }
                        SettingsDivider()
                        SettingsToggle("Notes", "Show the Notes field on accounts and budget categories",
                            showNotes, onShowNotesChange)
                    }
                }
                SettingsPage.Privacy -> {
                    SettingsGroup {
                        SettingsToggle("Hide balances", "Mask budget, account and transaction amounts",
                            hideBalances, onHideBalancesChange)
                    }
                    SettingsGroup("Location-aware payees") {
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
                        SettingsDivider()
                        SettingsRow(
                            "Payee Locations",
                            "Inspect or delete coordinates saved in this budget",
                            true,
                        ) { openFullScreen(onPayeeLocationsClick) }
                    }
                    SettingsNote(
                        if (locationPermissionGranted) {
                            "Location permission: allowed while using the app"
                        } else {
                            "Location permission: not granted"
                        },
                    )
                }
                SettingsPage.Budget -> {
                    val categoryStatusColors = LocalCategoryStatusColors.current
                    SettingsGroup {
                        SettingsToggle(
                            "Category status dot",
                            "Show a status dot next to category names on Budget and Home",
                            categoryStatusColors?.showDots ?: true,
                        ) { categoryStatusColors?.updateShowDots(it) }
                        SettingsDivider()
                        SettingsToggle(
                            "Hide income group",
                            "Hide the income category group on the Budget tab",
                            hideIncomeGroupInBudget,
                            onHideIncomeGroupInBudgetChange,
                        )
                        SettingsDivider()
                        SettingsRow(
                            "Category status colors",
                            "Retint the unassigned, funded, spending, spent and overspent status colors",
                            true,
                        ) { page = SettingsPage.CategoryColors }
                    }
                }
                SettingsPage.CategoryColors -> {
                    CategoryStatusColorSettings(Modifier.padding(top = Spacing.md))
                }
                SettingsPage.About -> {
                    SettingsGroup {
                        AboutRow("Actua", "Native Android client for Actual Budget\nVersion ${BuildConfig.VERSION_NAME}")
                    }
                    SettingsGroup("Project") {
                        AboutRow("Actua on GitHub", "github.com/azimul-kabir/actua") {
                            uriHandler.openUri("https://github.com/azimul-kabir/actua")
                        }
                        SettingsDivider()
                        AboutRow("Website", "azimul-kabir.github.io/actua-website") {
                            uriHandler.openUri("https://azimul-kabir.github.io/actua-website")
                        }
                        SettingsDivider()
                        AboutRow("FAQ", "Answers to common questions about Actua") {
                            uriHandler.openUri("https://azimul-kabir.github.io/actua-website#faq")
                        }
                        SettingsDivider()
                        AboutRow("Join Discord", "Discuss Actua, test beta builds and help with development") {
                            uriHandler.openUri("https://discord.gg/FyGxRjmhw")
                        }
                        SettingsDivider()
                        AboutRow(
                            "Independent community project",
                            "Actua connects directly to your self-hosted Actual server and keeps budget data locally available offline. It is not affiliated with or endorsed by the Actual Budget team.",
                        )
                        SettingsDivider()
                        AboutRow("Contact", "actua.mobile@gmail.com") {
                            uriHandler.openUri("mailto:actua.mobile@gmail.com")
                        }
                        SettingsDivider()
                        AboutRow("Privacy Policy", "What data Actua stores, syncs and never collects") {
                            uriHandler.openUri("https://github.com/azimul-kabir/actua/blob/main/PRIVACY.md")
                        }
                    }
                    SettingsGroup("Credits") {
                        AboutRow(
                            "Actuali for iOS",
                            "Actua was originally based on and continues to reference Matt Farrell’s open-source Actuali project for tested behavior and design guidance.",
                        )
                        SettingsDivider()
                        AboutRow(
                            "Actual Budget",
                            "Synchronization behavior is compatible with the open-source Actual Budget project. Visit actualbudget.org to learn how it works and how to self-host a server.",
                        ) { uriHandler.openUri("https://actualbudget.org") }
                    }
                    SettingsGroup("Compatibility") {
                        AboutRow("Android 9 or later", "Requires a reachable self-hosted Actual Budget server.")
                    }
                    SettingsGroup("License") {
                        AboutRow(
                            "MIT License",
                            "Open-source notices and complete attribution are available in the repository’s LICENSE and NOTICE files.",
                        )
                    }
                }
                SettingsPage.Tags -> Unit
            }
            Spacer(Modifier.height(Spacing.xl))
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

private val currencyOptions = listOf(
    "None" to "",
    "د.إ AED" to "AED",
    "Arg${'$'} ARS" to "ARS",
    "A${'$'} AUD" to "AUD",
    "৳ BDT" to "BDT",
    "R${'$'} BRL" to "BRL",
    "Br BYN" to "BYN",
    "C${'$'} CAD" to "CAD",
    "Fr. CHF" to "CHF",
    "CLP${'$'} CLP" to "CLP",
    "¥ CNY" to "CNY",
    "Col${'$'} COP" to "COP",
    "₡ CRC" to "CRC",
    "Kč CZK" to "CZK",
    "kr DKK" to "DKK",
    "RD${'$'} DOP" to "DOP",
    "ج.م EGP" to "EGP",
    "€ EUR" to "EUR",
    "£ GBP" to "GBP",
    "Q GTQ" to "GTQ",
    "HK${'$'} HKD" to "HKD",
    "Ft HUF" to "HUF",
    "Rp IDR" to "IDR",
    "₪ ILS" to "ILS",
    "₹ INR" to "INR",
    "﷼ IRR" to "IRR",
    "J${'$'} JMD" to "JMD",
    "¥ JPY" to "JPY",
    "₩ KRW" to "KRW",
    "Rs. LKR" to "LKR",
    "L MDL" to "MDL",
    "ден MKD" to "MKD",
    "${'$'} MXN" to "MXN",
    "RM MYR" to "MYR",
    "S/ PEN" to "PEN",
    "₱ PHP" to "PHP",
    "Rs. PKR" to "PKR",
    "zł PLN" to "PLN",
    "ر.ق QAR" to "QAR",
    "lei RON" to "RON",
    "дин RSD" to "RSD",
    "₽ RUB" to "RUB",
    "ر.س SAR" to "SAR",
    "kr SEK" to "SEK",
    "S${'$'} SGD" to "SGD",
    "฿ THB" to "THB",
    "₺ TRY" to "TRY",
    "NT${'$'} TWD" to "TWD",
    "₴ UAH" to "UAH",
    "${'$'} USD" to "USD",
    "${'$'}U UYU" to "UYU",
    "UZS UZS" to "UZS",
)

private fun currencyLabel(code: String): String =
    currencyOptions.firstOrNull { it.second == code }?.first ?: code.ifBlank { "None" }

/** An optional section [label] over a rounded card holding the section's rows. */
@Composable
private fun SettingsGroup(label: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (label != null) ActuaSectionHeader(label) else Spacer(Modifier.height(Spacing.md))
    ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal), content = content)
}

/** Divider between text-only rows of a settings card. */
@Composable
private fun SettingsDivider() {
    ActuaCardDivider(inset = Spacing.screenHorizontal)
}

@Composable
private fun SettingsNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal + Spacing.xs, vertical = Spacing.sm),
    )
}

/** A row showing the current [value] (and an optional [preview]); tapping it opens a menu of [options]. */
@Composable
private fun SettingsChoice(
    label: String,
    value: String,
    options: List<String>,
    preview: String? = null,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActuaListRow(
            title = { Text(label, style = MaterialTheme.typography.bodyLarge) },
            subtitle = {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                preview?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            trailing = {
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.distinct().forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    expanded = false
                    onChange(option)
                })
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ActuaListRow(
        title = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        subtitle = { Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        onClick = { onCheckedChange(!checked) },
    )
}

/**
 * A navigation row. With an [icon] it is an [ActuaFormRow] like the Add transaction fields;
 * without one it lines up with the text-only toggle and choice rows it shares a card with.
 */
@Composable
private fun SettingsRow(
    label: String,
    detail: String,
    enabled: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit = {},
) {
    val shownDetail = if (enabled) detail else "$detail · Coming with backend port"
    if (icon != null) {
        ActuaFormRow(icon = icon, label = label, value = null, caption = shownDetail, enabled = enabled, onClick = onClick)
    } else ActuaListRow(
        title = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        subtitle = {
            Text(shownDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailing = {
            if (enabled) Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        enabled = enabled,
        onClick = onClick,
    )
}

/** A text row on the About page; with [onClick] it opens a link and shows a chevron. */
@Composable
private fun AboutRow(title: String, detail: String, onClick: (() -> Unit)? = null) {
    ActuaListRow(
        title = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        subtitle = {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailing = onClick?.let {
            { Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        onClick = onClick,
    )
}
