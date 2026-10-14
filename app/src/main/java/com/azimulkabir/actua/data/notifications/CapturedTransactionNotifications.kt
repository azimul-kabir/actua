package com.azimulkabir.actua.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.azimulkabir.actua.MainActivity
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.importing.ImportCandidate
import com.azimulkabir.actua.data.importing.ImportGroup
import com.azimulkabir.actua.data.importing.ImportHistoryEntry
import com.azimulkabir.actua.data.importing.ImportPreferences
import com.azimulkabir.actua.data.importing.NotificationImportPreferences
import com.azimulkabir.actua.data.importing.StatementFormat
import com.azimulkabir.actua.data.importing.importAccountMemoryKey
import com.azimulkabir.actua.data.importing.prefilledImportAccount
import com.azimulkabir.actua.data.sync.SyncSignals
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.applyDisplayFormats
import com.azimulkabir.actua.ui.components.formatMoneyCents

/**
 * Optional notifications for captured transactions (#1046): each queued row can be reviewed in
 * Actua, or imported with one tap when its account is already known. Importing goes through the
 * same [ActuaRepository.importTransactions] path as the review screen, only after a tap.
 */
object CapturedTransactionNotifications {
    const val ACTION_REVIEW = "com.azimulkabir.actua.action.REVIEW_CAPTURED"
    const val ACTION_IMPORT = "com.azimulkabir.actua.action.IMPORT_CAPTURED"
    const val EXTRA_QUEUE_ID = "queueId"
    const val EXTRA_ACCOUNT_ID = "accountId"
    private const val CHANNEL_ID = "captured-transactions"

    /** Queues [candidate] for review and, when enabled, schedules its notification. */
    fun queue(context: Context, store: NotificationImportPreferences, candidate: ImportCandidate) {
        val stored = store.enqueue(candidate)
        val queueId = stored.queueId ?: return
        if (!store.confirmNotificationsEnabled) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            "captured-notify-$queueId", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CapturedTransactionNotifyWorker>()
                .setInputData(workDataOf(EXTRA_QUEUE_ID to queueId)).build(),
        )
    }

    fun notificationId(queueId: String) = queueId.hashCode()

    /**
     * Drops the queued row [queueId] the user rejected on review, and its notification (#1076).
     * A notification still being prepared finds the row gone and posts nothing.
     */
    fun reject(context: Context, store: NotificationImportPreferences, queueId: String) {
        store.removeQueued(queueId)
        cancel(context, listOf(queueId))
    }

    /** Removes the notifications of rows imported or dropped elsewhere, e.g. on the review screen. */
    fun cancel(context: Context, queueIds: Collection<String>) {
        val manager = NotificationManagerCompat.from(context)
        queueIds.forEach { manager.cancel(notificationId(it)) }
    }

    internal fun allowed(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun post(context: Context, queueId: String, build: NotificationCompat.Builder.() -> Unit) {
        if (!allowed(context)) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Captured transactions", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Review or import transactions captured from notifications and automation apps" })
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.actua_launcher_monochrome)
            .setContentTitle("Transaction captured")
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.actua_launcher_monochrome)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(reviewIntent(context, queueId))
            .setAutoCancel(true)
            .apply(build)
        try {
            NotificationManagerCompat.from(context).notify(notificationId(queueId), builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    internal fun reviewIntent(context: Context, queueId: String): PendingIntent = PendingIntent.getActivity(
        context, notificationId(queueId),
        Intent(context, MainActivity::class.java).setAction(ACTION_REVIEW)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun importIntent(context: Context, queueId: String, accountId: String): PendingIntent = PendingIntent.getBroadcast(
        context, notificationId(queueId),
        Intent(context, CapturedImportReceiver::class.java).setAction(ACTION_IMPORT)
            .putExtra(EXTRA_QUEUE_ID, queueId).putExtra(EXTRA_ACCOUNT_ID, accountId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Formats amounts like the rest of the app, hiding them when balances are hidden. */
    internal fun amountFormatter(context: Context, repository: ActuaRepository): (Long) -> String {
        CurrencyDisplay.decimalPlaces = repository.budgetDecimalPlaces()
        val display = DisplayPreferences(context).resolve(repository.budgetDisplayFormats())
        applyDisplayFormats(display)
        return { cents ->
            if (display.hideBalances) "••••"
            else formatMoneyCents(cents, display.hideDecimals, showPositiveSign = true, respectBalanceVisibility = false)
        }
    }
}

/** What a captured row's notification says, and whether it may offer Import (#1046). */
data class CapturedNotificationContent(val title: String, val text: String, val importAccountId: String?)

fun capturedNotificationContent(payee: String, amountText: String, account: Account?): CapturedNotificationContent =
    CapturedNotificationContent(
        title = payee.trim().ifEmpty { "Captured transaction" },
        text = if (account != null) "$amountText · ${account.name}" else "$amountText · Choose an account in Actua",
        importAccountId = account?.id,
    )

/** Posts the notification for one queued row, with Import only when its account is pre-filled. */
class CapturedTransactionNotifyWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val store = NotificationImportPreferences(applicationContext)
        if (!store.confirmNotificationsEnabled || !CapturedTransactionNotifications.allowed(applicationContext)) {
            return Result.success()
        }
        val queueId = inputData.getString(CapturedTransactionNotifications.EXTRA_QUEUE_ID) ?: return Result.failure()
        val candidate = store.queued().firstOrNull { it.queueId == queueId } ?: return Result.success()
        val repository = ActuaRepository(applicationContext)
        val content = try {
            val format = CapturedTransactionNotifications.amountFormatter(applicationContext, repository)
            val account = if (!repository.isUsingActualBudget) null else {
                val budgetId = ActiveBudgetStore(applicationContext).budgetId ?: "no-budget"
                prefilledImportAccount(candidate.accountHint, candidate.sourceLabel, repository.accounts()) {
                    store.rememberedAccount(budgetId, it)
                }
            }
            capturedNotificationContent(candidate.payee, format(candidate.amountCents), account)
        } finally {
            repository.close()
        }
        CapturedTransactionNotifications.post(applicationContext, queueId) {
            setContentTitle(content.title)
            setContentText(content.text)
            addAction(0, "Review", CapturedTransactionNotifications.reviewIntent(applicationContext, queueId))
            content.importAccountId?.let { accountId ->
                addAction(0, "Import", CapturedTransactionNotifications.importIntent(applicationContext, queueId, accountId))
            }
        }
        return Result.success()
    }
}

/** Turns a tap on Import into background work; nothing is written on the main thread. */
class CapturedImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CapturedTransactionNotifications.ACTION_IMPORT) return
        val queueId = intent.getStringExtra(CapturedTransactionNotifications.EXTRA_QUEUE_ID) ?: return
        val accountId = intent.getStringExtra(CapturedTransactionNotifications.EXTRA_ACCOUNT_ID) ?: return
        WorkManager.getInstance(context).enqueueUniqueWork(
            "captured-import-$queueId", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CapturedImportWorker>().setInputData(workDataOf(
                CapturedTransactionNotifications.EXTRA_QUEUE_ID to queueId,
                CapturedTransactionNotifications.EXTRA_ACCOUNT_ID to accountId,
            )).build(),
        )
    }
}

sealed interface CapturedImportOutcome {
    data class Imported(val candidate: ImportCandidate, val accountName: String) : CapturedImportOutcome
    data object NotQueued : CapturedImportOutcome
    data class Failed(val reason: String) : CapturedImportOutcome
}

/**
 * Imports the queued row [queueId] into [accountId] exactly as the review screen would: one group,
 * cleared, through [ActuaRepository.importTransactions]. The row is claimed from the queue first so
 * a second tap cannot import it twice, and put back if the import fails.
 */
fun importCapturedTransaction(context: Context, queueId: String, accountId: String): CapturedImportOutcome {
    val store = NotificationImportPreferences(context)
    val candidate = store.removeQueued(queueId) ?: return CapturedImportOutcome.NotQueued
    val repository = ActuaRepository(context)
    val outcome = try {
        val account = repository.accounts().firstOrNull { it.id == accountId && !it.closed }
        when {
            !repository.isUsingActualBudget -> CapturedImportOutcome.Failed("No budget is open in Actua")
            account == null -> CapturedImportOutcome.Failed("That account is no longer open")
            repository.importTransactions(listOf(ImportGroup(accountId, listOf(candidate))), cleared = true) != 1 ->
                CapturedImportOutcome.Failed("Actua could not import it")
            else -> CapturedImportOutcome.Imported(candidate, requireNotNull(account).name)
        }
    } catch (error: Exception) {
        CapturedImportOutcome.Failed(error.message ?: "Actua could not import it")
    } finally {
        repository.close()
    }
    if (outcome is CapturedImportOutcome.Imported) {
        val budgetId = ActiveBudgetStore(context).budgetId ?: "no-budget"
        store.rememberAccounts(budgetId, mapOf(importAccountMemoryKey(candidate.accountHint, candidate.sourceLabel) to accountId))
        ImportPreferences(context).addHistory(ImportHistoryEntry(
            "Captured notification", StatementFormat.NOTIFICATION, outcome.accountName, 1, 0, System.currentTimeMillis(),
        ))
        // An open app reloads its balances and lists, as it does after a background sync.
        SyncSignals.dataChanged()
    } else {
        store.enqueue(candidate)
    }
    return outcome
}

class CapturedImportWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val queueId = inputData.getString(CapturedTransactionNotifications.EXTRA_QUEUE_ID) ?: return Result.failure()
        val accountId = inputData.getString(CapturedTransactionNotifications.EXTRA_ACCOUNT_ID) ?: return Result.failure()
        val context = applicationContext
        when (val outcome = importCapturedTransaction(context, queueId, accountId)) {
            is CapturedImportOutcome.Imported -> CapturedTransactionNotifications.post(context, queueId) {
                setContentTitle("Imported ${outcome.candidate.payee}")
                setContentText("Added to ${outcome.accountName}")
                setTimeoutAfter(10_000)
            }
            CapturedImportOutcome.NotQueued -> CapturedTransactionNotifications.cancel(context, listOf(queueId))
            is CapturedImportOutcome.Failed -> CapturedTransactionNotifications.post(context, queueId) {
                setContentTitle("Couldn't import")
                setContentText(outcome.reason)
                addAction(0, "Review", CapturedTransactionNotifications.reviewIntent(context, queueId))
            }
        }
        return Result.success()
    }
}
