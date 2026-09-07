package com.azimulkabir.actua.data.preferences

import android.content.Context

class DisplayPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)

    var hideDecimalPlaces: Boolean
        get() = preferences.getBoolean(HIDE_DECIMAL_PLACES, false)
        set(value) { preferences.edit().putBoolean(HIDE_DECIMAL_PLACES, value).apply() }

    var currencyCode: String
        get() = preferences.getString(CURRENCY_CODE, "BDT") ?: "BDT"
        set(value) { preferences.edit().putString(CURRENCY_CODE, value).apply() }

    var currencySymbolOnly: Boolean
        get() = preferences.getBoolean(CURRENCY_SYMBOL_ONLY, false)
        set(value) { preferences.edit().putBoolean(CURRENCY_SYMBOL_ONLY, value).apply() }

    var showHiddenCategories: Boolean
        get() = preferences.getBoolean(SHOW_HIDDEN_CATEGORIES, false)
        set(value) { preferences.edit().putBoolean(SHOW_HIDDEN_CATEGORIES, value).apply() }

    var showSpentColumn: Boolean
        get() = preferences.getBoolean(SHOW_SPENT_COLUMN, true)
        set(value) { preferences.edit().putBoolean(SHOW_SPENT_COLUMN, value).apply() }

    var showBudgetProgressBars: Boolean
        get() = preferences.getBoolean(SHOW_BUDGET_PROGRESS_BARS, false)
        set(value) { preferences.edit().putBoolean(SHOW_BUDGET_PROGRESS_BARS, value).apply() }

    var budgetView: String
        get() = preferences.getString(BUDGET_VIEW, "Table") ?: "Table"
        set(value) { preferences.edit().putString(BUDGET_VIEW, value).apply() }

    var showBudgetOverview: Boolean
        get() = preferences.getBoolean(SHOW_BUDGET_OVERVIEW, true)
        set(value) { preferences.edit().putBoolean(SHOW_BUDGET_OVERVIEW, value).apply() }

    var showGroupTotals: Boolean
        get() = preferences.getBoolean(SHOW_GROUP_TOTALS, true)
        set(value) { preferences.edit().putBoolean(SHOW_GROUP_TOTALS, value).apply() }

    var hideFullySpentCategories: Boolean
        get() = preferences.getBoolean(HIDE_FULLY_SPENT_CATEGORIES, false)
        set(value) { preferences.edit().putBoolean(HIDE_FULLY_SPENT_CATEGORIES, value).apply() }

    var hideBalances: Boolean
        get() = preferences.getBoolean(HIDE_BALANCES, false)
        set(value) { preferences.edit().putBoolean(HIDE_BALANCES, value).apply() }

    var appearance: String
        get() = preferences.getString(APPEARANCE, "System") ?: "System"
        set(value) { preferences.edit().putString(APPEARANCE, value).apply() }

    var startPage: String
        get() = preferences.getString(START_PAGE, "Accounts") ?: "Accounts"
        set(value) { preferences.edit().putString(START_PAGE, value).apply() }

    var defaultAccount: String?
        get() = preferences.getString(DEFAULT_ACCOUNT, null)
        set(value) { preferences.edit().apply {
            if (value == null) remove(DEFAULT_ACCOUNT) else putString(DEFAULT_ACCOUNT, value)
        }.apply() }

    var groupTransactionsByDate: Boolean
        get() = preferences.getBoolean(GROUP_TRANSACTIONS_BY_DATE, true)
        set(value) { preferences.edit().putBoolean(GROUP_TRANSACTIONS_BY_DATE, value).apply() }

    var showAccountsMonthlySummary: Boolean
        get() = preferences.getBoolean(SHOW_ACCOUNTS_MONTHLY_SUMMARY, true)
        set(value) { preferences.edit().putBoolean(SHOW_ACCOUNTS_MONTHLY_SUMMARY, value).apply() }

    var conventionalAmountEntry: Boolean
        get() = preferences.getBoolean(CONVENTIONAL_AMOUNT_ENTRY, false)
        set(value) { preferences.edit().putBoolean(CONVENTIONAL_AMOUNT_ENTRY, value).apply() }

    private companion object {
        const val HIDE_DECIMAL_PLACES = "hide_decimal_places"
        const val CURRENCY_CODE = "currency_code"
        const val CURRENCY_SYMBOL_ONLY = "currency_symbol_only"
        const val SHOW_HIDDEN_CATEGORIES = "show_hidden_categories"
        const val SHOW_SPENT_COLUMN = "show_spent_column"
        const val SHOW_BUDGET_PROGRESS_BARS = "show_budget_progress_bars"
        const val BUDGET_VIEW = "budget_view"
        const val SHOW_BUDGET_OVERVIEW = "show_budget_overview"
        const val SHOW_GROUP_TOTALS = "show_group_totals"
        const val HIDE_FULLY_SPENT_CATEGORIES = "hide_fully_spent_categories"
        const val HIDE_BALANCES = "hide_balances"
        const val APPEARANCE = "appearance"
        const val START_PAGE = "start_page"
        const val DEFAULT_ACCOUNT = "default_account"
        const val GROUP_TRANSACTIONS_BY_DATE = "group_transactions_by_date"
        const val SHOW_ACCOUNTS_MONTHLY_SUMMARY = "show_accounts_monthly_summary"
        const val CONVENTIONAL_AMOUNT_ENTRY = "conventional_amount_entry"
    }
}
