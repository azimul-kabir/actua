package com.azimulkabir.actua.data.security

import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.DemoBudgetManager

/**
 * Applies Actua's local reset policy after a deliberate server disconnect.
 *
 * Server-backed budgets and their retained local backups are removed. Device-level
 * preferences (display, appearance, backup destination and the user's notification
 * opt-in) are preserved. The local-only demo budget is retained but deselected.
 */
class DisconnectResetManager(context: Context) {
    private val app = context.applicationContext

    fun resetLocalServerData() {
        val files = BudgetFileManager(app)
        val encryptionKeys = BudgetEncryptionKeyStore(app)

        files.listLocalBudgets()
            .filter { it.id != DemoBudgetManager.BUDGET_ID }
            .forEach { budget ->
                budget.cloudFileId?.let(encryptionKeys::remove)
                files.deleteBudget(budget.id)
            }

        ActiveBudgetStore(app).budgetId = null

        // Operational state belongs to the old local installation, not the server.
        app.getSharedPreferences("actua-sync-status", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("actua-schedule-poster", Context.MODE_PRIVATE).edit().clear().commit()

        // Budget/account UI expansion state contains IDs from the deleted budget.
        app.getSharedPreferences("budget_ui_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("account_detail_preferences", Context.MODE_PRIVATE).edit().clear().commit()

        // Preserve the user's reminder opt-in but cancel jobs derived from deleted cards.
        val notificationPrefs = app.getSharedPreferences("credit_card_notifications", Context.MODE_PRIVATE)
        val workNames = notificationPrefs.getStringSet("scheduled_work", emptySet()).orEmpty().toSet()
        val workManager = WorkManager.getInstance(app)
        workNames.forEach(workManager::cancelUniqueWork)
        notificationPrefs.edit().remove("scheduled_work").commit()

        // Credentials are cleared last so an incomplete reset can still be retried.
        CredentialStore(app).clearCredentialsNow()
    }

    fun restartIntoFreshConnectionState() {
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            app.startActivity(launch)
        }
    }
}
