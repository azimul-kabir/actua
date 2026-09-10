package com.azimulkabir.actua.data.budget

import android.content.Context

/** Device-local choice of which installed Actual budget the UI should open. */
class ActiveBudgetStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("active_budget", Context.MODE_PRIVATE)

    var budgetId: String?
        get() {
            val id = preferences.getString("budget_id", null)
            if (DemoBudgetManager.isDemoBudget(id)) {
                runCatching { DemoBudgetManager.repairIfNeeded(BudgetFileManager(appContext)) }
            }
            return id
        }
        set(value) {
            preferences.edit().apply {
                if (value == null) remove("budget_id") else putString("budget_id", value)
            }.apply()
        }
}
