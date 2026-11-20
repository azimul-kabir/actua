package com.azimulkabir.actua.data.budget

import android.content.Context
import android.os.Looper

/** Device-local choice of which installed Actual budget the UI should open. */
class ActiveBudgetStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("active_budget", Context.MODE_PRIVATE)

    var budgetId: String?
        get() {
            val id = preferences.getString("budget_id", null)
            if (DemoBudgetManager.isDemoBudget(id) && !DemoBudgetManager.isSchemaChecked()) {
                // Composition reads this id and must not touch the disk, so on the main thread the check only
                // starts in the background. Callers that go on to open the database (the repository, workers)
                // are off the main thread and run it here first, once per process.
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    DemoBudgetManager.repairInBackground { BudgetFileManager(appContext) }
                } else {
                    runCatching { DemoBudgetManager.repairIfNeeded(BudgetFileManager(appContext)) }
                }
            }
            return id
        }
        set(value) {
            preferences.edit().apply {
                if (value == null) remove("budget_id") else putString("budget_id", value)
            }.apply()
        }
}
