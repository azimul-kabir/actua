package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.BudgetDisplayOptions

/** Flow state retains its original navigation-composition lifetime and saveability. */
internal class SettingsPreferenceState(
    val greetingNickname: MutableState<String>,
    val decimalPlacesMode: MutableState<String>,
    val currencyCode: MutableState<String>,
    val currencySymbolPosition: MutableState<String>,
    val currencySymbolSpacing: MutableState<String>,
    val dateFormat: MutableState<String>,
    val numberFormat: MutableState<String>,
    val budgetView: MutableState<String>,
    val budgetCategoryView: MutableState<String>,
    val appearance: MutableState<String>,
    val startPage: MutableState<String>,
    val greetingEnabled: MutableState<Boolean>,
    val showUpcomingTransactions: MutableState<Boolean>,
    val currencySymbolOnly: MutableState<Boolean>,
    val favoritesOnly: MutableState<Boolean>,
    val privacyMode: MutableState<String>,
    val useDynamicColor: MutableState<Boolean>,
    val groupTransactionsByDate: MutableState<Boolean>,
    val showAccountsMonthlySummary: MutableState<Boolean>,
    val showAccountTypeIcons: MutableState<Boolean>,
    val conventionalAmountEntry: MutableState<Boolean>,
    val keepLastTransactionDate: MutableState<Boolean>,
    val showCurrentBalanceSummary: MutableState<Boolean>,
    val showRunningBalance: MutableState<Boolean>,
    val showNotes: MutableState<Boolean>,
    val hideIncomeGroupInBudget: MutableState<Boolean>,
    val showReportSummary: MutableState<Boolean>,
    val defaultAccount: MutableState<String?>,
    val budgetDisplay: MutableState<BudgetDisplayOptions>,
    val openSupportPage: MutableState<Boolean>,
)

@Composable
internal fun rememberSettingsPreferenceState(displayPreferences: DisplayPreferences): SettingsPreferenceState =
    SettingsPreferenceState(
        greetingNickname = remember { mutableStateOf(displayPreferences.greetingNickname) },
        decimalPlacesMode = remember { mutableStateOf(displayPreferences.decimalPlacesMode) },
        currencyCode = remember { mutableStateOf(displayPreferences.currencyCode) },
        currencySymbolPosition = remember { mutableStateOf(displayPreferences.currencySymbolPosition) },
        currencySymbolSpacing = remember { mutableStateOf(displayPreferences.currencySymbolSpacing) },
        dateFormat = remember { mutableStateOf(displayPreferences.dateFormat) },
        numberFormat = remember { mutableStateOf(displayPreferences.numberFormat) },
        budgetView = remember { mutableStateOf(displayPreferences.budgetView) },
        budgetCategoryView = remember { mutableStateOf(displayPreferences.budgetCategoryView) },
        appearance = remember { mutableStateOf(displayPreferences.appearance) },
        startPage = remember { mutableStateOf(displayPreferences.startPage) },
        greetingEnabled = remember { mutableStateOf(displayPreferences.greetingEnabled) },
        showUpcomingTransactions = remember {
            mutableStateOf(displayPreferences.showUpcomingTransactions)
        },
        currencySymbolOnly = remember { mutableStateOf(displayPreferences.currencySymbolOnly) },
        favoritesOnly = remember { mutableStateOf(displayPreferences.favoritesOnly) },
        privacyMode = remember { mutableStateOf(displayPreferences.privacyMode) },
        useDynamicColor = remember { mutableStateOf(displayPreferences.useDynamicColor) },
        groupTransactionsByDate = remember { mutableStateOf(displayPreferences.groupTransactionsByDate) },
        showAccountsMonthlySummary = remember { mutableStateOf(displayPreferences.showAccountsMonthlySummary) },
        showAccountTypeIcons = remember { mutableStateOf(displayPreferences.showAccountTypeIcons) },
        conventionalAmountEntry = remember { mutableStateOf(displayPreferences.conventionalAmountEntry) },
        keepLastTransactionDate = remember { mutableStateOf(displayPreferences.keepLastTransactionDate) },
        showCurrentBalanceSummary = remember { mutableStateOf(displayPreferences.showCurrentBalanceSummary) },
        showRunningBalance = remember { mutableStateOf(displayPreferences.showRunningBalance) },
        showNotes = remember { mutableStateOf(displayPreferences.showNotes) },
        hideIncomeGroupInBudget = remember { mutableStateOf(displayPreferences.hideIncomeGroupInBudget) },
        showReportSummary = remember { mutableStateOf(displayPreferences.showReportSummary) },
        defaultAccount = remember { mutableStateOf(displayPreferences.defaultAccount) },
        budgetDisplay = remember { mutableStateOf(displayPreferences.budgetDisplayOptions) },
        openSupportPage = rememberSaveable { mutableStateOf(false) },
    )
