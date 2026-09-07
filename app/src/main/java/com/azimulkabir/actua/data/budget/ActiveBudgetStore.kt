package com.azimulkabir.actua.data.budget

import android.content.Context

/** Device-local choice of which installed Actual budget the UI should open. */
class ActiveBudgetStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("active_budget", Context.MODE_PRIVATE)

    var budgetId: String?
        get() = preferences.getString("budget_id", null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove("budget_id") else putString("budget_id", value)
            }.apply()
        }
}
