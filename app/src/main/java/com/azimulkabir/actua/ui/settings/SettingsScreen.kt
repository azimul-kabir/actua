package com.azimulkabir.actua.ui.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.BuildConfig
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.home.Holiday
import com.azimulkabir.actua.data.home.MonthStartRule
import com.azimulkabir.actua.billing.createSupportBillingManager
import com.azimulkabir.actua.data.budget.ActiveTagRepository
import com.azimulkabir.actua.data.location.ForegroundLocationPermission
import com.azimulkabir.actua.data.preferences.BudgetDisplayFormats
import com.azimulkabir.actua.data.preferences.EffectiveDisplayFormats
import com.azimulkabir.actua.data.preferences.LocationPreferences
import com.azimulkabir.actua.ui.components.BudgetTypeConfirmDialog
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaListRow
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSectionHeader
import com.azimulkabir.actua.ui.components.MonthStartDayRow
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.navigation.mainDestinationLabelRes
import com.azimulkabir.actua.ui.theme.LocalCategoryStatusColors
import com.azimulkabir.actua.ui.theme.Sizes
import com.azimulkabir.actua.ui.theme.Spacing

internal enum class SettingsPage(@StringRes val titleRes: Int, val depth: Int) {
    Manage(R.string.settings_page_manage, 0),
    Tags(R.string.settings_page_tags, 1),
    General(R.string.settings_page_settings, 1),
    Transactions(R.string.settings_page_transactions_accounts, 2),
    Display(R.string.settings_page_display, 2),
    Privacy(R.string.settings_page_privacy, 2),
    Budget(R.string.settings_page_budget, 2),
    CategoryColors(R.string.settings_page_category_colors, 3),
    Holidays(R.string.settings_page_holidays, 3),
    Experimental(R.string.settings_page_experimental, 2),
    Support(R.string.settings_page_support, 2),
    About(R.string.settings_page_about, 2),
    Diagnostics(R.string.settings_page_diagnostics, 3),
}

internal fun isForwardSettingsNavigation(from: SettingsPage, to: SettingsPage): Boolean =
    to.depth > from.depth

data class BudgetSelectionItem(
    val id: String,
    val name: String,
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onConnectionClick: () -> Unit = {},
    decimalPlacesMode: String = EffectiveDisplayFormats.FOLLOW_BUDGET,
    onDecimalPlacesModeChange: (String) -> Unit = {},
    showNotes: Boolean = true,
    onShowNotesChange: (Boolean) -> Unit = {},
    hideIncomeGroupInBudget: Boolean = false,
    onHideIncomeGroupInBudgetChange: (Boolean) -> Unit = {},
    monthStart: MonthStartRule = MonthStartRule.CALENDAR_MONTH,
    onMonthStartChange: (MonthStartRule) -> Unit = {},
    holidays: List<Holiday> = emptyList(),
    onHolidaysChange: (List<Holiday>) -> Unit = {},
    budgetType: String = "envelope",
    onBudgetTypeChange: (String) -> Unit = {},
    currencyCode: String = EffectiveDisplayFormats.FOLLOW_BUDGET,
    onCurrencyCodeChange: (String) -> Unit = {},
    currencySymbolOnly: Boolean = false,
    onCurrencySymbolOnlyChange: (Boolean) -> Unit = {},
    currencySymbolPosition: String = EffectiveDisplayFormats.FOLLOW_BUDGET,
    onCurrencySymbolPositionChange: (String) -> Unit = {},
    currencySymbolSpacing: String = EffectiveDisplayFormats.FOLLOW_BUDGET,
    onCurrencySymbolSpacingChange: (String) -> Unit = {},
    languageTag: String = "",
    onLanguageChange: (String) -> Unit = {},
    dateFormat: String = EffectiveDisplayFormats.FOLLOW_BUDGET,
    onDateFormatChange: (String) -> Unit = {},
    numberFormat: String = EffectiveDisplayFormats.FOLLOW_BUDGET,
    onNumberFormatChange: (String) -> Unit = {},
    privacyMode: String = EffectiveDisplayFormats.FOLLOW_BUDGET,
    onPrivacyModeChange: (String) -> Unit = {},
    /** The open budget's synced formats, which "Same as budget" uses. */
    budgetDisplayFormats: BudgetDisplayFormats = BudgetDisplayFormats(),
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
    onLoansClick: () -> Unit = {},
    onBankSyncClick: () -> Unit = {},
    /** Actual's experimental `enableBanking` flag; device-local and off by default. */
    enableBankingExperiment: Boolean = false,
    onEnableBankingExperimentChange: (Boolean) -> Unit = {},
    onBillsCalendarClick: () -> Unit = {},
    onRulesClick: () -> Unit = {},
    onSchedulesClick: () -> Unit = {},
    onImportTransactionsClick: () -> Unit = {},
    onPayeeLocationsClick: () -> Unit = {},
    onManagePayeesClick: () -> Unit = {},
    onReportsClick: () -> Unit = {},
    /** Reports and Home appear under Insights only while they aren't tabs in the bottom bar. */
    showReportsShortcut: Boolean = true,
    showHomeShortcut: Boolean = true,
    onHomeClick: () -> Unit = {},
    onCustomizeHomeClick: () -> Unit = {},
    onCustomizeTabBarClick: () -> Unit = {},
    conventionalAmountEntry: Boolean = true,
    onConventionalAmountEntryChange: (Boolean) -> Unit = {},
    /** Tab bar display options (labels, hide while scrolling); null hides those rows. */
    tabBarDisplay: com.azimulkabir.actua.ui.navigation.TabBarScrollState? = null,
    showCurrentBalanceSummary: Boolean = true,
    onShowCurrentBalanceSummaryChange: (Boolean) -> Unit = {},
    budgetOptions: List<BudgetSelectionItem> = emptyList(),
    activeBudgetId: String? = null,
    onBudgetChange: (String) -> Unit = {},
    returnToRootRequest: Int = 0,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // The fdroid flavor's manager never reports isAvailable, so the Support row stays hidden there.
    val supportBillingManager = remember { createSupportBillingManager(context) }
    val supportPurchaseState by supportBillingManager.state.collectAsState()
    DisposableEffect(supportBillingManager) {
        supportBillingManager.start()
        onDispose { supportBillingManager.stop() }
    }
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
        SettingsPage.Experimental, SettingsPage.Support, SettingsPage.About,
        -> SettingsPage.General
        SettingsPage.CategoryColors, SettingsPage.Holidays -> SettingsPage.Budget
        SettingsPage.Diagnostics -> SettingsPage.About
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
        if (shownPage == SettingsPage.Diagnostics) {
            DiagnosticsScreen(onBack = ::navigateBack, modifier = Modifier.fillMaxSize())
        } else if (shownPage == SettingsPage.Tags) {
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
                title = stringResource(shownPage.titleRes),
                onBack = if (shownPage != SettingsPage.Manage) ::navigateBack else null,
            ) {
                if (shownPage == SettingsPage.Manage) {
                    IconButton(onClick = { page = SettingsPage.General }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_open_settings))
                    }
                }
            }
            when (shownPage) {
                SettingsPage.Manage -> {
                    if (budgetOptions.isNotEmpty()) SettingsGroup {
                        ActiveBudgetSelector(
                            options = budgetOptions,
                            activeBudgetId = activeBudgetId,
                            onBudgetChange = onBudgetChange,
                        )
                    }
                    if (showHomeShortcut || showReportsShortcut) SettingsGroup(stringResource(R.string.settings_section_insights)) {
                        if (showHomeShortcut) {
                            SettingsRow(
                                stringResource(R.string.settings_manage_home),
                                stringResource(R.string.settings_manage_home_description),
                                true,
                                Icons.Outlined.Home,
                            ) {
                                openFullScreen(onHomeClick)
                            }
                        }
                        if (showHomeShortcut && showReportsShortcut) ActuaCardDivider()
                        if (showReportsShortcut) {
                            SettingsRow(
                                stringResource(R.string.settings_reports),
                                stringResource(R.string.settings_reports_description),
                                true,
                                Icons.Outlined.BarChart,
                            ) {
                                openFullScreen(onReportsClick)
                            }
                        }
                    }
                    SettingsGroup(stringResource(R.string.settings_section_automation)) {
                        SettingsRow(
                            stringResource(R.string.settings_bills_calendar),
                            stringResource(R.string.settings_bills_calendar_description),
                            true,
                            Icons.Outlined.CalendarMonth,
                        ) {
                            openFullScreen(onBillsCalendarClick)
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_scheduled_transactions),
                            stringResource(R.string.settings_scheduled_transactions_description),
                            true,
                            Icons.Outlined.Repeat,
                        ) {
                            openFullScreen(onSchedulesClick)
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_rules),
                            stringResource(R.string.settings_rules_description),
                            true,
                            Icons.Outlined.AutoAwesome,
                        ) {
                            openFullScreen(onRulesClick)
                        }
                    }
                    SettingsGroup(stringResource(R.string.settings_section_transactions_data)) {
                        SettingsRow(
                            stringResource(R.string.settings_tags),
                            stringResource(R.string.settings_tags_description),
                            true,
                            Icons.Outlined.Sell,
                        ) {
                            page = SettingsPage.Tags
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_payees),
                            stringResource(R.string.settings_payees_description),
                            true,
                            Icons.Outlined.Storefront,
                        ) {
                            openFullScreen(onManagePayeesClick)
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_import_transactions),
                            stringResource(R.string.settings_import_transactions_description),
                            true,
                            Icons.AutoMirrored.Outlined.ReceiptLong,
                        ) {
                            openFullScreen(onImportTransactionsClick)
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_connection_data),
                            stringResource(R.string.settings_connection_data_description),
                            true,
                            Icons.Outlined.Sync,
                        ) {
                            openFullScreen(onConnectionClick)
                        }
                    }
                    SettingsGroup(stringResource(R.string.settings_section_financial_setup)) {
                        SettingsRow(
                            stringResource(R.string.settings_bank_sync),
                            stringResource(R.string.settings_bank_sync_description),
                            true,
                            Icons.Outlined.AccountBalance,
                        ) {
                            openFullScreen(onBankSyncClick)
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_credit_cards),
                            stringResource(R.string.settings_credit_cards_description),
                            true,
                            Icons.Outlined.CreditCard,
                        ) {
                            openFullScreen(onCreditCardsClick)
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_loans),
                            stringResource(R.string.settings_loans_description),
                            true,
                            Icons.Outlined.AccountBalance,
                        ) {
                            openFullScreen(onLoansClick)
                        }
                    }
                }
                SettingsPage.General -> {
                    SettingsGroup(stringResource(R.string.settings_section_preferences)) {
                        SettingsRow(
                            stringResource(R.string.settings_home),
                            stringResource(R.string.settings_home_description),
                            true,
                            Icons.Outlined.Home,
                        ) {
                            // Unlike openFullScreen's other destinations, Customize Home is reached
                            // from this page rather than from Manage root, so the back gesture should
                            // return here instead of resetting all the way to Manage.
                            onCustomizeHomeClick()
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_page_transactions_accounts),
                            stringResource(R.string.settings_transactions_accounts_description),
                            true,
                            Icons.Outlined.AccountBalanceWallet,
                        ) {
                            page = SettingsPage.Transactions
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_page_display),
                            stringResource(R.string.settings_display_description),
                            true,
                            Icons.Outlined.Palette,
                        ) {
                            page = SettingsPage.Display
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_page_privacy),
                            stringResource(R.string.settings_privacy_description),
                            true,
                            Icons.Outlined.Lock,
                        ) {
                            page = SettingsPage.Privacy
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_budget),
                            stringResource(R.string.settings_budget_description),
                            true,
                            Icons.Outlined.PieChartOutline,
                        ) {
                            page = SettingsPage.Budget
                        }
                    }
                    if (supportPurchaseState.isAvailable) SettingsGroup(stringResource(R.string.settings_section_support)) {
                        SettingsRow(
                            stringResource(R.string.settings_support_actua),
                            if (supportPurchaseState.isPurchased) stringResource(R.string.settings_support_thank_you)
                            else stringResource(R.string.settings_support_description),
                            true,
                            Icons.Outlined.FavoriteBorder,
                        ) {
                            page = SettingsPage.Support
                        }
                    }
                    SettingsGroup(stringResource(R.string.settings_section_labs)) {
                        SettingsRow(
                            stringResource(R.string.settings_experimental),
                            stringResource(R.string.settings_experimental_description),
                            true,
                            Icons.Outlined.Science,
                        ) {
                            page = SettingsPage.Experimental
                        }
                    }
                    SettingsGroup(stringResource(R.string.settings_section_about)) {
                        SettingsRow(
                            stringResource(R.string.settings_about_actua),
                            stringResource(R.string.settings_about_actua_description),
                            true,
                            Icons.Outlined.Info,
                        ) {
                            page = SettingsPage.About
                        }
                    }
                }
                SettingsPage.Transactions -> {
                    SettingsGroup {
                        SettingsChoice(
                            stringResource(R.string.settings_default_account),
                            defaultAccount ?: DEFAULT_ACCOUNT_NONE,
                            listOf(SettingsChoiceOption(DEFAULT_ACCOUNT_NONE, stringResource(R.string.settings_none))) +
                                accountOptions.map { SettingsChoiceOption(it, it) },
                        ) {
                            onDefaultAccountChange(it.takeUnless { value -> value == DEFAULT_ACCOUNT_NONE })
                        }
                        SettingsDivider()
                        SettingsToggle(
                            stringResource(R.string.settings_group_transactions),
                            stringResource(R.string.settings_group_transactions_description),
                            groupTransactionsByDate,
                            onGroupTransactionsByDateChange,
                        )
                        SettingsDivider()
                        SettingsToggle(
                            stringResource(R.string.settings_conventional_amount_entry),
                            stringResource(R.string.settings_conventional_amount_entry_description),
                            conventionalAmountEntry,
                            onConventionalAmountEntryChange,
                        )
                        SettingsDivider()
                        SettingsToggle(
                            stringResource(R.string.settings_account_monthly_summary),
                            stringResource(R.string.settings_account_monthly_summary_description),
                            showAccountsMonthlySummary,
                            onShowAccountsMonthlySummaryChange,
                        )
                        SettingsDivider()
                        SettingsToggle(
                            stringResource(R.string.settings_current_balance_summary),
                            stringResource(R.string.settings_current_balance_summary_description),
                            showCurrentBalanceSummary,
                            onShowCurrentBalanceSummaryChange,
                        )
                    }
                    SettingsGroup {
                        SettingsRow(
                            stringResource(R.string.settings_credit_cards),
                            stringResource(R.string.settings_credit_cards_description),
                            true,
                            Icons.Outlined.CreditCard,
                        ) {
                            openFullScreen(onCreditCardsClick)
                        }
                        ActuaCardDivider()
                        SettingsRow(
                            stringResource(R.string.settings_loans),
                            stringResource(R.string.settings_loans_description),
                            true,
                            Icons.Outlined.AccountBalance,
                        ) {
                            openFullScreen(onLoansClick)
                        }
                    }
                }
                SettingsPage.Display -> {
                    SettingsGroup {
                        SettingsChoice(
                            stringResource(R.string.settings_language),
                            languageTag,
                            listOf(
                                SettingsChoiceOption("", stringResource(R.string.settings_language_system)),
                                SettingsChoiceOption("en", stringResource(R.string.settings_language_english)),
                                SettingsChoiceOption("pt-BR", stringResource(R.string.settings_language_portuguese_brazil)),
                            ),
                            onChange = onLanguageChange,
                        )
                        SettingsDivider()
                        val noneLabel = stringResource(R.string.settings_none)
                        SettingsChoice(
                            stringResource(R.string.settings_currency),
                            currencyCode,
                            currencyOptions.map { (labelRes, code) ->
                                SettingsChoiceOption(code, if (code.isEmpty()) noneLabel else stringResource(labelRes))
                            },
                            preview = if (currencyCode == EffectiveDisplayFormats.FOLLOW_BUDGET) {
                                stringResource(
                                    R.string.settings_budget_value_preview,
                                    currencyLabel(budgetDisplayFormats.currencyCode, noneLabel),
                                )
                            } else null,
                        ) { selected ->
                            onCurrencyCodeChange(selected)
                        }
                        if (currencyCode.isNotBlank() && currencyCode != EffectiveDisplayFormats.FOLLOW_BUDGET) {
                            SettingsDivider()
                            SettingsToggle(
                                stringResource(R.string.settings_symbol_only),
                                stringResource(R.string.settings_symbol_only_description),
                                currencySymbolOnly,
                                onCurrencySymbolOnlyChange,
                            )
                        }
                        val shownCurrency = if (currencyCode == EffectiveDisplayFormats.FOLLOW_BUDGET) {
                            budgetDisplayFormats.currencyCode
                        } else currencyCode
                        if (shownCurrency.isNotBlank()) {
                            SettingsDivider()
                            SettingsChoice(
                                stringResource(R.string.settings_symbol_position),
                                currencySymbolPosition,
                                listOf(
                                    SettingsChoiceOption(
                                        EffectiveDisplayFormats.FOLLOW_BUDGET,
                                        stringResource(R.string.settings_follow_budget),
                                    ),
                                    SettingsChoiceOption(
                                        EffectiveDisplayFormats.BEFORE_AMOUNT,
                                        stringResource(R.string.settings_symbol_before),
                                    ),
                                    SettingsChoiceOption(
                                        EffectiveDisplayFormats.AFTER_AMOUNT,
                                        stringResource(R.string.settings_symbol_after),
                                    ),
                                ),
                                preview = stringResource(R.string.settings_preview, currencyPlacementPreview()) +
                                    if (currencySymbolPosition == EffectiveDisplayFormats.FOLLOW_BUDGET) {
                                        if (budgetDisplayFormats.symbolAfterAmount) {
                                            stringResource(R.string.settings_symbol_position_after_preview)
                                        } else {
                                            stringResource(R.string.settings_symbol_position_before_preview)
                                        }
                                    } else "",
                                onChange = onCurrencySymbolPositionChange,
                            )
                            SettingsDivider()
                            SettingsChoice(
                                stringResource(R.string.settings_symbol_spacing),
                                currencySymbolSpacing,
                                listOf(
                                    SettingsChoiceOption(
                                        EffectiveDisplayFormats.FOLLOW_BUDGET,
                                        stringResource(R.string.settings_follow_budget),
                                    ),
                                    SettingsChoiceOption(
                                        EffectiveDisplayFormats.SPACE,
                                        stringResource(R.string.settings_space),
                                    ),
                                    SettingsChoiceOption(
                                        EffectiveDisplayFormats.NO_SPACE,
                                        stringResource(R.string.settings_no_space),
                                    ),
                                ),
                                preview = stringResource(R.string.settings_preview, currencyPlacementPreview()) +
                                    if (currencySymbolSpacing == EffectiveDisplayFormats.FOLLOW_BUDGET) {
                                        if (budgetDisplayFormats.spaceBetweenAmountAndSymbol) {
                                            stringResource(R.string.settings_symbol_spacing_space_preview)
                                        } else {
                                            stringResource(R.string.settings_symbol_spacing_no_space_preview)
                                        }
                                    } else "",
                                onChange = onCurrencySymbolSpacingChange,
                            )
                        }
                        SettingsDivider()
                        SettingsChoice(
                            stringResource(R.string.settings_date_format),
                            dateFormat,
                            listOf(
                                SettingsChoiceOption(
                                    EffectiveDisplayFormats.FOLLOW_BUDGET,
                                    stringResource(R.string.settings_follow_budget),
                                ),
                                SettingsChoiceOption("System default", stringResource(R.string.settings_system_default)),
                                SettingsChoiceOption("MM/DD/YYYY", stringResource(R.string.settings_date_mm_dd_yyyy)),
                                SettingsChoiceOption("DD/MM/YYYY", stringResource(R.string.settings_date_dd_mm_yyyy)),
                                SettingsChoiceOption("YYYY-MM-DD", stringResource(R.string.settings_date_yyyy_mm_dd)),
                                SettingsChoiceOption("MM.DD.YYYY", stringResource(R.string.settings_date_mm_dd_yyyy_dot)),
                                SettingsChoiceOption("DD.MM.YYYY", stringResource(R.string.settings_date_dd_mm_yyyy_dot)),
                                SettingsChoiceOption("DD-MM-YYYY", stringResource(R.string.settings_date_dd_mm_yyyy_dash)),
                            ),
                            preview = stringResource(
                                R.string.settings_preview,
                                datePreview(
                                    if (dateFormat == EffectiveDisplayFormats.FOLLOW_BUDGET) budgetDisplayFormats.dateFormat else dateFormat,
                                ),
                            ),
                            onChange = onDateFormatChange,
                        )
                        SettingsDivider()
                        SettingsChoice(
                            stringResource(R.string.settings_number_format),
                            numberFormat,
                            listOf(
                                SettingsChoiceOption(
                                    EffectiveDisplayFormats.FOLLOW_BUDGET,
                                    stringResource(R.string.settings_follow_budget),
                                ),
                                SettingsChoiceOption("System default", stringResource(R.string.settings_system_default)),
                                SettingsChoiceOption("1,234.56", stringResource(R.string.settings_number_comma_decimal)),
                                SettingsChoiceOption("1.234,56", stringResource(R.string.settings_number_period_decimal_comma)),
                                SettingsChoiceOption("1 234,56", stringResource(R.string.settings_number_space_decimal_comma)),
                                SettingsChoiceOption("1’234.56", stringResource(R.string.settings_number_apostrophe_dot)),
                                SettingsChoiceOption("1234.56", stringResource(R.string.settings_number_plain_decimal)),
                                SettingsChoiceOption("1,23,456.78", stringResource(R.string.settings_number_indian_grouping)),
                            ),
                            preview = stringResource(
                                R.string.settings_preview,
                                numberPreview(
                                    if (numberFormat == EffectiveDisplayFormats.FOLLOW_BUDGET) budgetDisplayFormats.numberFormat else numberFormat,
                                ),
                            ),
                            onChange = onNumberFormatChange,
                        )
                        SettingsDivider()
                        SettingsChoice(
                            stringResource(R.string.settings_decimal_places),
                            decimalPlacesMode,
                            listOf(
                                SettingsChoiceOption(
                                    EffectiveDisplayFormats.FOLLOW_BUDGET,
                                    stringResource(R.string.settings_follow_budget),
                                ),
                                SettingsChoiceOption(EffectiveDisplayFormats.SHOW, stringResource(R.string.settings_show)),
                                SettingsChoiceOption(EffectiveDisplayFormats.HIDE, stringResource(R.string.settings_hide)),
                            ),
                            preview = stringResource(R.string.settings_decimal_places_hiding) +
                                if (decimalPlacesMode == EffectiveDisplayFormats.FOLLOW_BUDGET) {
                                    if (budgetDisplayFormats.hideFraction) {
                                        stringResource(R.string.settings_decimal_places_budget_hidden)
                                    } else {
                                        stringResource(R.string.settings_decimal_places_budget_shown)
                                    }
                                } else "",
                            onChange = onDecimalPlacesModeChange,
                        )
                    }
                    SettingsGroup {
                        SettingsChoice(
                            stringResource(R.string.settings_appearance),
                            appearance,
                            listOf(
                                SettingsChoiceOption("System", stringResource(R.string.settings_appearance_system)),
                                SettingsChoiceOption("Light", stringResource(R.string.settings_appearance_light)),
                                SettingsChoiceOption("Dark", stringResource(R.string.settings_appearance_dark)),
                            ),
                            onChange = onAppearanceChange,
                        )
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            SettingsDivider()
                            SettingsToggle(
                                stringResource(R.string.settings_material_you_colors),
                                stringResource(R.string.settings_material_you_colors_description),
                                useDynamicColor,
                                onUseDynamicColorChange,
                            )
                        }
                    }
                    SettingsGroup {
                        SettingsChoice(
                            stringResource(R.string.settings_start_page),
                            startPage,
                            startPageOptions.map { option ->
                                SettingsChoiceOption(option, startPageLabel(option))
                            },
                            onChange = onStartPageChange,
                        )
                        SettingsDivider()
                        SettingsChoice(
                            stringResource(R.string.settings_bottom_navigation_labels),
                            if (tabBarDisplay?.showLabels != false) NAV_LABELS_ICONS_AND_NAMES else NAV_LABELS_ICONS_ONLY,
                            listOf(
                                SettingsChoiceOption(
                                    NAV_LABELS_ICONS_AND_NAMES,
                                    stringResource(R.string.settings_navigation_icons_names),
                                ),
                                SettingsChoiceOption(
                                    NAV_LABELS_ICONS_ONLY,
                                    stringResource(R.string.settings_navigation_icons_only),
                                ),
                            ),
                        ) { tabBarDisplay?.updateShowLabels(it == NAV_LABELS_ICONS_AND_NAMES) }
                        SettingsDivider()
                        SettingsToggle(
                            stringResource(R.string.settings_hide_tab_bar_on_scroll),
                            stringResource(R.string.settings_hide_tab_bar_on_scroll_description),
                            tabBarDisplay?.hideOnScroll != false,
                            { tabBarDisplay?.updateHideOnScroll(it) },
                        )
                        SettingsDivider()
                        SettingsRow(
                            stringResource(R.string.settings_tab_bar),
                            stringResource(R.string.settings_tab_bar_description),
                            true,
                        ) {
                            // Reached from this page (Display), so back should return here rather than
                            // resetting all the way to Manage the way openFullScreen's other rows do.
                            onCustomizeTabBarClick()
                        }
                        SettingsDivider()
                        SettingsToggle(
                            stringResource(R.string.settings_notes),
                            stringResource(R.string.settings_notes_description),
                            showNotes,
                            onShowNotesChange,
                        )
                    }
                }
                SettingsPage.Privacy -> {
                    SettingsGroup {
                        SettingsChoice(
                            stringResource(R.string.settings_balances),
                            privacyMode,
                            listOf(
                                SettingsChoiceOption(
                                    EffectiveDisplayFormats.FOLLOW_BUDGET,
                                    stringResource(R.string.settings_follow_budget),
                                ),
                                SettingsChoiceOption(EffectiveDisplayFormats.SHOW, stringResource(R.string.settings_show)),
                                SettingsChoiceOption(EffectiveDisplayFormats.HIDE, stringResource(R.string.settings_hide)),
                            ),
                            preview = stringResource(R.string.settings_balances_hiding) +
                                if (privacyMode == EffectiveDisplayFormats.FOLLOW_BUDGET) {
                                    if (budgetDisplayFormats.privacyEnabled) {
                                        stringResource(R.string.settings_balances_budget_hidden)
                                    } else {
                                        stringResource(R.string.settings_balances_budget_shown)
                                    }
                                } else "",
                            onChange = onPrivacyModeChange,
                        )
                    }
                    SettingsGroup(stringResource(R.string.settings_section_location_payees)) {
                        SettingsToggle(
                            stringResource(R.string.settings_record_payee_locations),
                            if (recordPayeeLocations && locationPermissionGranted) {
                                stringResource(R.string.settings_record_payee_locations_enabled_description)
                            } else {
                                stringResource(R.string.settings_record_payee_locations_disabled_description)
                            },
                            recordPayeeLocations,
                            ::setRecordPayeeLocations,
                        )
                        SettingsDivider()
                        SettingsRow(
                            stringResource(R.string.settings_payee_locations),
                            stringResource(R.string.settings_payee_locations_description),
                            true,
                        ) { openFullScreen(onPayeeLocationsClick) }
                    }
                    SettingsNote(
                        if (locationPermissionGranted) {
                            stringResource(R.string.settings_location_permission_allowed)
                        } else {
                            stringResource(R.string.settings_location_permission_denied)
                        },
                    )
                }
                SettingsPage.Budget -> {
                    val categoryStatusColors = LocalCategoryStatusColors.current
                    var pendingBudgetType by remember { mutableStateOf<String?>(null) }
                    SettingsGroup {
                        SettingsChoice(
                            stringResource(R.string.settings_budget_type),
                            budgetType,
                            listOf(
                                SettingsChoiceOption("envelope", stringResource(R.string.settings_budget_type_envelope)),
                                SettingsChoiceOption("tracking", stringResource(R.string.settings_budget_type_tracking)),
                            ),
                            onChange = { if (it != budgetType) pendingBudgetType = it },
                        )
                        pendingBudgetType?.let { newType ->
                            BudgetTypeConfirmDialog(
                                newType = newType,
                                onConfirm = {
                                    pendingBudgetType = null
                                    onBudgetTypeChange(newType)
                                },
                                onDismiss = { pendingBudgetType = null },
                            )
                        }
                        SettingsDivider()
                        SettingsToggle(
                            stringResource(R.string.settings_category_status_dot),
                            stringResource(R.string.settings_category_status_dot_description),
                            categoryStatusColors?.showDots ?: true,
                        ) { categoryStatusColors?.updateShowDots(it) }
                        SettingsDivider()
                        SettingsToggle(
                            stringResource(R.string.settings_color_balances),
                            stringResource(R.string.settings_color_balances_description),
                            categoryStatusColors?.colorBudgetedAmounts ?: false,
                        ) { categoryStatusColors?.updateColorBudgetedAmounts(it) }
                        SettingsDivider()
                        SettingsToggle(
                            stringResource(R.string.settings_hide_income_group),
                            stringResource(R.string.settings_hide_income_group_description),
                            hideIncomeGroupInBudget,
                            onHideIncomeGroupInBudgetChange,
                        )
                        SettingsDivider()
                        SettingsRow(
                            stringResource(R.string.settings_category_status_colors),
                            stringResource(R.string.settings_category_status_colors_description),
                            true,
                        ) { page = SettingsPage.CategoryColors }
                    }
                    SettingsGroup {
                        MonthStartDayRow(monthStart, onMonthStartChange)
                        SettingsDivider()
                        SettingsRow(
                            stringResource(R.string.settings_holidays_row_title),
                            if (holidays.isEmpty()) stringResource(R.string.settings_holidays_row_none)
                            else stringResource(R.string.settings_holidays_row_count, holidays.size),
                            true,
                        ) { page = SettingsPage.Holidays }
                    }
                    SettingsNote(
                        stringResource(R.string.settings_budget_mode_note),
                    )
                }
                SettingsPage.CategoryColors -> {
                    CategoryStatusColorSettings(Modifier.padding(top = Spacing.md))
                }
                SettingsPage.Holidays -> HolidaysSettings(holidays, onHolidaysChange)
                SettingsPage.Experimental -> {
                    SettingsGroup {
                        SettingsToggle(
                            stringResource(R.string.settings_enable_banking),
                            stringResource(R.string.settings_enable_banking_description),
                            enableBankingExperiment,
                            onEnableBankingExperimentChange,
                        )
                    }
                    SettingsNote(
                        stringResource(R.string.settings_experimental_note),
                    )
                }
                SettingsPage.Support -> {
                    SettingsNote(
                        stringResource(R.string.settings_support_intro),
                    )
                    SettingsGroup {
                        if (supportPurchaseState.isPurchased) {
                            ActuaFormRow(
                                icon = Icons.Outlined.FavoriteBorder,
                                label = stringResource(R.string.settings_supporter_badge),
                                value = null,
                                caption = stringResource(R.string.settings_supporter_thanks),
                            )
                        } else {
                            ActuaFormRow(
                                icon = Icons.Outlined.FavoriteBorder,
                                label = stringResource(R.string.settings_supporter_price, supportPurchaseState.priceLabel),
                                value = null,
                                caption = stringResource(R.string.settings_supporter_description),
                            )
                        }
                    }
                    if (!supportPurchaseState.isPurchased) {
                        SettingsNote(stringResource(R.string.settings_support_free))
                        Button(
                            enabled = supportPurchaseState.isAvailable && !supportPurchaseState.isProcessing,
                            onClick = { (context as? Activity)?.let(supportBillingManager::launchPurchase) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.screenHorizontal)
                                .height(Sizes.primaryButtonHeight),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            if (supportPurchaseState.isProcessing) {
                                CircularProgressIndicator(Modifier.padding(end = Spacing.sm).size(18.dp), strokeWidth = 2.dp)
                            }
                            Text(stringResource(R.string.settings_become_supporter))
                        }
                    }
                }
                SettingsPage.About -> {
                    SettingsGroup {
                        AboutRow(stringResource(R.string.app_name), stringResource(R.string.settings_about_summary, BuildConfig.VERSION_NAME))
                    }
                    SettingsGroup(stringResource(R.string.settings_section_project)) {
                        AboutRow(stringResource(R.string.settings_actua_github), stringResource(R.string.settings_actua_github_url)) {
                            uriHandler.openUri("https://github.com/azimul-kabir/actua")
                        }
                        SettingsDivider()
                        AboutRow(stringResource(R.string.settings_website), stringResource(R.string.settings_website_url)) {
                            uriHandler.openUri("https://actua.azimulkabir.com")
                        }
                        SettingsDivider()
                        AboutRow(stringResource(R.string.settings_developer_website), stringResource(R.string.settings_developer_website_url)) {
                            uriHandler.openUri("https://www.azimulkabir.com")
                        }
                        SettingsDivider()
                        AboutRow(stringResource(R.string.settings_faq), stringResource(R.string.settings_faq_description)) {
                            uriHandler.openUri("https://actua.azimulkabir.com#faq")
                        }
                        SettingsDivider()
                        AboutRow(stringResource(R.string.settings_join_discord), stringResource(R.string.settings_join_discord_description)) {
                            uriHandler.openUri("https://discord.gg/FhedEyP97f")
                        }
                        SettingsDivider()
                        AboutRow(
                            stringResource(R.string.settings_independent_project),
                            stringResource(R.string.settings_independent_project_description),
                        )
                        SettingsDivider()
                        AboutRow(stringResource(R.string.settings_contact), stringResource(R.string.settings_contact_url)) {
                            uriHandler.openUri("mailto:actua@azimulkabir.com")
                        }
                        SettingsDivider()
                        AboutRow(stringResource(R.string.settings_privacy_policy), stringResource(R.string.settings_privacy_policy_description)) {
                            uriHandler.openUri("https://github.com/azimul-kabir/actua/blob/main/PRIVACY.md")
                        }
                        SettingsDivider()
                        AboutRow(stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_description)) {
                            page = SettingsPage.Diagnostics
                        }
                    }
                    SettingsGroup(stringResource(R.string.settings_section_credits)) {
                        AboutRow(
                            stringResource(R.string.settings_actuali_ios),
                            stringResource(R.string.settings_actuali_ios_description),
                        )
                        SettingsDivider()
                        AboutRow(
                            stringResource(R.string.settings_actual_budget),
                            stringResource(R.string.settings_actual_budget_description),
                        ) { uriHandler.openUri("https://actualbudget.org") }
                    }
                    SettingsGroup(stringResource(R.string.settings_section_compatibility)) {
                        AboutRow(
                            stringResource(R.string.settings_android_requirement),
                            stringResource(R.string.settings_server_requirement),
                        )
                    }
                    SettingsGroup(stringResource(R.string.settings_section_license)) {
                        AboutRow(
                            stringResource(R.string.settings_gpl_license),
                            stringResource(R.string.settings_license_description),
                        )
                    }
                }
                SettingsPage.Tags, SettingsPage.Diagnostics -> Unit
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/**
 * The active budget at the top of Manage. With more than one local budget it opens a menu to switch,
 * styled like the Reports dashboard picker; with one it simply names the budget in use.
 */
@Composable
private fun ActiveBudgetSelector(
    options: List<BudgetSelectionItem>,
    activeBudgetId: String?,
    onBudgetChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0) }
    val anchorWidthDp = with(LocalDensity.current) { anchorWidth.toDp() }
    val active = options.firstOrNull { it.id == activeBudgetId }
    val switchable = options.size > 1
    Box(Modifier.onSizeChanged { anchorWidth = it.width }) {
        ActuaFormRow(
            icon = Icons.Outlined.FolderOpen,
            label = stringResource(R.string.settings_active_budget_label),
            value = active?.name ?: stringResource(R.string.settings_select_budget),
            valueIsPlaceholder = active == null,
            trailing = if (switchable) {
                {
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(R.string.settings_switch_budget),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else null,
            onClick = if (switchable) ({ expanded = true }) else null,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(anchorWidthDp),
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            options.forEach { option ->
                val isActive = option.id == activeBudgetId
                DropdownMenuItem(
                    text = {
                        Text(
                            option.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = if (isActive) {
                        { Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.settings_active_budget)) }
                    } else null,
                    onClick = {
                        expanded = false
                        if (!isActive) onBudgetChange(option.id)
                    },
                )
            }
        }
    }
}

private fun datePreview(format: String): String = when (format) {
    "DD/MM/YYYY" -> "31/12/2026"
    "MM/DD/YYYY" -> "12/31/2026"
    "YYYY-MM-DD" -> "2026-12-31"
    "MM.DD.YYYY" -> "12.31.2026"
    "DD.MM.YYYY" -> "31.12.2026"
    "DD-MM-YYYY" -> "31-12-2026"
    else -> java.time.LocalDate.of(2026, 12, 31)
        .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
}

/** A sample amount in the applied display formats, so symbol placement can be checked before leaving. */
private fun currencyPlacementPreview(): String =
    formatMoneyCents(123456, hideDecimalPlaces = false, respectBalanceVisibility = false)

private fun numberPreview(format: String): String = when (format) {
    "1,234.56" -> "1,234.56"
    "1.234,56" -> "1.234,56"
    "1 234,56" -> "1 234,56"
    "1’234.56" -> "1’234.56"
    "1234.56" -> "1234.56"
    "1,23,456.78" -> "1,23,456.78"
    else -> java.text.NumberFormat.getNumberInstance().format(1234.56)
}

/** A currency menu entry: the ISO [code] stored in preferences and a display-label resource. */
private data class CurrencyOption(
    @StringRes val labelRes: Int,
    val code: String,
)

private val currencyOptions = listOf(
    CurrencyOption(R.string.settings_follow_budget, EffectiveDisplayFormats.FOLLOW_BUDGET),
    CurrencyOption(R.string.settings_none, ""),
    CurrencyOption(R.string.settings_currency_bdt, "BDT"),
    CurrencyOption(R.string.settings_currency_usd, "USD"),
    CurrencyOption(R.string.settings_currency_eur, "EUR"),
    CurrencyOption(R.string.settings_currency_gbp, "GBP"),
    CurrencyOption(R.string.settings_currency_cad, "CAD"),
    CurrencyOption(R.string.settings_currency_aud, "AUD"),
    CurrencyOption(R.string.settings_currency_jpy, "JPY"),
    CurrencyOption(R.string.settings_currency_inr, "INR"),
    CurrencyOption(R.string.settings_currency_cny, "CNY"),
    CurrencyOption(R.string.settings_currency_sgd, "SGD"),
    CurrencyOption(R.string.settings_currency_aed, "AED"),
    CurrencyOption(R.string.settings_currency_sar, "SAR"),
    CurrencyOption(R.string.settings_currency_ars, "ARS"),
    CurrencyOption(R.string.settings_currency_byn, "BYN"),
    CurrencyOption(R.string.settings_currency_brl, "BRL"),
    CurrencyOption(R.string.settings_currency_chf, "CHF"),
    CurrencyOption(R.string.settings_currency_clp, "CLP"),
    CurrencyOption(R.string.settings_currency_cop, "COP"),
    CurrencyOption(R.string.settings_currency_crc, "CRC"),
    CurrencyOption(R.string.settings_currency_czk, "CZK"),
    CurrencyOption(R.string.settings_currency_dkk, "DKK"),
    CurrencyOption(R.string.settings_currency_dop, "DOP"),
    CurrencyOption(R.string.settings_currency_egp, "EGP"),
    CurrencyOption(R.string.settings_currency_gtq, "GTQ"),
    CurrencyOption(R.string.settings_currency_hkd, "HKD"),
    CurrencyOption(R.string.settings_currency_huf, "HUF"),
    CurrencyOption(R.string.settings_currency_idr, "IDR"),
    CurrencyOption(R.string.settings_currency_ils, "ILS"),
    CurrencyOption(R.string.settings_currency_irr, "IRR"),
    CurrencyOption(R.string.settings_currency_jmd, "JMD"),
    CurrencyOption(R.string.settings_currency_krw, "KRW"),
    CurrencyOption(R.string.settings_currency_lkr, "LKR"),
    CurrencyOption(R.string.settings_currency_mdl, "MDL"),
    CurrencyOption(R.string.settings_currency_mkd, "MKD"),
    CurrencyOption(R.string.settings_currency_mxn, "MXN"),
    CurrencyOption(R.string.settings_currency_myr, "MYR"),
    CurrencyOption(R.string.settings_currency_pen, "PEN"),
    CurrencyOption(R.string.settings_currency_php, "PHP"),
    CurrencyOption(R.string.settings_currency_pkr, "PKR"),
    CurrencyOption(R.string.settings_currency_pln, "PLN"),
    CurrencyOption(R.string.settings_currency_qar, "QAR"),
    CurrencyOption(R.string.settings_currency_ron, "RON"),
    CurrencyOption(R.string.settings_currency_rsd, "RSD"),
    CurrencyOption(R.string.settings_currency_rub, "RUB"),
    CurrencyOption(R.string.settings_currency_sek, "SEK"),
    CurrencyOption(R.string.settings_currency_thb, "THB"),
    CurrencyOption(R.string.settings_currency_try, "TRY"),
    CurrencyOption(R.string.settings_currency_twd, "TWD"),
    CurrencyOption(R.string.settings_currency_uah, "UAH"),
    CurrencyOption(R.string.settings_currency_uyu, "UYU"),
    CurrencyOption(R.string.settings_currency_uzs, "UZS"),
)

/** The display label for a stored currency [code]; [noneLabel] localizes the no-currency entry. */
@Composable
private fun currencyLabel(code: String, noneLabel: String): String =
    currencyOptions.firstOrNull { it.code == code }?.let { option ->
        // The no-currency option's label is a translatable word, not a symbol-plus-code notation.
        if (option.code.isEmpty()) noneLabel else stringResource(option.labelRes)
    } ?: code.ifBlank { noneLabel }

/** An optional section [label] over a rounded card holding the section's rows. */
@Composable
private fun SettingsGroup(label: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (label != null) ActuaSectionHeader(label) else Spacer(Modifier.height(Spacing.md))
    ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal), content = content)
}

/** Locale-neutral sentinel value for "no default account"; only its label is translated. */
private const val DEFAULT_ACCOUNT_NONE = "\u0000"

/** Locale-neutral values for the bottom-navigation-labels choice; only their labels are translated. */
private const val NAV_LABELS_ICONS_AND_NAMES = "Icons and names"
private const val NAV_LABELS_ICONS_ONLY = "Icons only"

/**
 * The display label for a persisted start-page value. The stored values are the English
 * [com.azimulkabir.actua.ui.navigation] destination labels (matched against `MainDestination.label`),
 * so they stay untranslated; the shown label resolves from the destination's own `labelRes`, so
 * destinations added later localize without extending a per-screen mapping.
 */
@Composable
private fun startPageLabel(value: String): String =
    mainDestinationLabelRes(value)?.let { stringResource(it) } ?: value

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

/** One entry of a [SettingsChoice] menu: the stored [value] with the [label] shown for it. */
private data class SettingsChoiceOption(
    val value: String,
    val label: String,
)

/** A row showing the current [value] (and an optional [preview]); tapping it opens a menu of [options]. */
@Composable
private fun SettingsChoice(
    label: String,
    value: String,
    options: List<String>,
    preview: String? = null,
    onChange: (String) -> Unit,
) = SettingsChoice(label, value, options.map { SettingsChoiceOption(it, it) }, preview, onChange)

/**
 * As above, for choices whose stored value and displayed label differ (e.g. the app language).
 * Renamed on the JVM so it can overload the string-options variant despite generics erasure.
 */
@JvmName("SettingsChoiceWithOptions")
@Composable
private fun SettingsChoice(
    label: String,
    value: String,
    options: List<SettingsChoiceOption>,
    preview: String? = null,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == value }?.label ?: value
    Box {
        ActuaListRow(
            title = { Text(label, style = MaterialTheme.typography.bodyLarge) },
            subtitle = {
                Text(selectedLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
            options.distinctBy { it.value }.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = {
                    expanded = false
                    onChange(option.value)
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
    val shownDetail = if (enabled) detail else stringResource(R.string.settings_coming_with_backend, detail)
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
