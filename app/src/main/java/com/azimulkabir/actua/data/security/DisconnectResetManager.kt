package com.azimulkabir.actua.data.security

import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
/**
 * Applies Actua's local reset policy after a deliberate server disconnect.
 *
 * Preserved device state:
 * - display/appearance/start-page preferences
 * - backup destination permission/selection
 * - credit-card notification opt-in
 * - local-only demo and standalone user budgets and their backups
 *
 * Cleared budget/session state:
 * - downloaded server budget directories and their retained local backups
 * - server credentials and active-budget selection (unless a standalone budget is active)
 * - per-budget encryption keys for removed server budgets
 * - sync status, automatic-schedule run gates and account/category UI expansion state
 * - scheduled credit-card reminder jobs derived from removed budgets
 *
 * Credit-card configuration itself lives in the budget preferences table and is
 * CRDT-synced, so the protected disconnect flow syncs every downloaded budget
 * before this reset runs.
 */
class DisconnectResetManager(context: Context) {
    private val app = context.applicationContext

    fun resetLocalServerData() {
        val files = BudgetFileManager(app)
        val encryptionKeys = BudgetEncryptionKeyStore(app)
        val activeStore = ActiveBudgetStore(app)
        val activeBudgetId = activeStore.budgetId
        val activeBudget = activeBudgetId?.let { id ->
            files.listLocalBudgets().firstOrNull { it.id == id }
        }

        files.listLocalBudgets()
            .filter { it.isServerBacked }
            .forEach { budget ->
                budget.cloudFileId?.let(encryptionKeys::remove)
                files.deleteBudget(budget.id)
            }

        if (activeBudget?.isUserLocalOnly != true) {
            activeStore.budgetId = null
        }

        app.getSharedPreferences("actua-sync-status", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("actua-schedule-poster", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("budget_ui_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("account_detail_preferences", Context.MODE_PRIVATE).edit().clear().commit()

        val notificationPrefs = app.getSharedPreferences("credit_card_notifications", Context.MODE_PRIVATE)
        val workNames = notificationPrefs.getStringSet("scheduled_work", emptySet()).orEmpty().toSet()
        val workManager = WorkManager.getInstance(app)
        workNames.forEach(workManager::cancelUniqueWork)
        notificationPrefs.edit().remove("scheduled_work").commit()

        CredentialStore(app).clearCredentialsNow()
    }

    fun restartIntoFreshConnectionState() {
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            app.startActivity(launch)
        }
    }
}
