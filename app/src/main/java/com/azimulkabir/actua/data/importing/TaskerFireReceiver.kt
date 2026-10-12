package com.azimulkabir.actua.data.importing

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.azimulkabir.actua.data.notifications.CapturedTransactionNotifications
import android.os.Bundle

/**
 * Runs the Tasker plugin action "Queue transaction" (#1040): the stored bundle goes through the
 * same token check, validation and review queue as an [AutomationIntentReceiver] broadcast.
 * When Tasker waits for the result, a failure is reported with `%errmsg` set to the reason.
 */
class TaskerFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskerPlugin.ACTION_FIRE_SETTING) return
        val bundle = intent.getBundleExtra(TaskerPlugin.EXTRA_BUNDLE)
        val values = bundle?.keySet().orEmpty().associateWith { bundle?.getString(it) }
        val store = NotificationImportPreferences(context)
        val result = handleAutomationExtras(
            store.automationEnabled, store.automationToken, TaskerPlugin.extrasOf(values),
            profile = store.profile(), tokenAdvice = TaskerPlugin.TOKEN_ADVICE,
        )
        result.candidates.forEach { CapturedTransactionNotifications.queue(context, store, it) }
        result.rejection?.let(store::recordAutomationRejection) ?: store.clearAutomationRejection()
        if (isOrderedBroadcast) {
            val rejection = result.rejection
            if (rejection == null) {
                resultCode = Activity.RESULT_OK
            } else {
                resultCode = RESULT_FAILED
                getResultExtras(true).putBundle(TaskerPlugin.KEY_VARIABLES, Bundle().apply {
                    putString(TaskerPlugin.VARIABLE_ERROR_MESSAGE, rejection)
                })
            }
        }
    }

    private companion object {
        /** Tasker's `Setting.RESULT_CODE_FAILED`. */
        const val RESULT_FAILED = Activity.RESULT_FIRST_USER + 1
    }
}
