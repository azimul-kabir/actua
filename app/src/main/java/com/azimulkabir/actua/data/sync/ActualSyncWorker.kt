package com.azimulkabir.actua.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.schedules.ActualScheduleWriter
import com.azimulkabir.actua.data.schedules.SchedulePoster
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import com.azimulkabir.actua.data.security.CredentialStore
import java.util.concurrent.TimeUnit

sealed interface SyncRunResult {
    data class Success(val outcome: SyncOutcome, val postedSchedules: Int) : SyncRunResult
    data object NotConfigured : SyncRunResult
    data object EncryptionKeyUnavailable : SyncRunResult
}

/** Headless equivalent of iOS syncInBackground, excluding Wallet/FinanceKit. */
object ActualSyncRunner {
    @Synchronized
    fun run(context: Context, makeBackup: Boolean = false): SyncRunResult {
        val app = context.applicationContext
        val credentials = CredentialStore(app)
        val token = credentials.token() ?: return SyncRunResult.NotConfigured
        val serverUrl = credentials.serverUrl.takeIf(String::isNotBlank) ?: return SyncRunResult.NotConfigured
        val fallbackUrl = credentials.fallbackServerUrl.takeIf { it.isNotBlank() && it != serverUrl }
        val files = BudgetFileManager(app)
        val budgetId = ActiveBudgetStore(app).budgetId ?: return SyncRunResult.NotConfigured
        val metadata = files.listLocalBudgets().firstOrNull { it.id == budgetId } ?: return SyncRunResult.NotConfigured
        val fileId = metadata.cloudFileId ?: return SyncRunResult.NotConfigured
        val groupId = metadata.groupId ?: return SyncRunResult.NotConfigured
        val loadedKey = metadata.encryptKeyId?.let {
            BudgetEncryptionKeyStore(app).load(fileId) ?: return SyncRunResult.EncryptionKeyUnavailable
        }
        if (metadata.encryptKeyId != null && loadedKey?.keyId != metadata.encryptKeyId) return SyncRunResult.EncryptionKeyUnavailable
        return ActualBudgetDatabase.open(files.databaseFile(budgetId)).use { database ->
            val server = ActualServerClient()
            fun syncAt(url: String) = ActualSyncClient(url, token, server, database, fileId, groupId,
                loadedKey?.keyId, loadedKey?.let { ActualMessageCipher(it.key) }).sync()
            var outcome = try { syncAt(serverUrl) } catch (primary: Exception) {
                fallbackUrl?.let(::syncAt) ?: throw primary
            }
            val poster = SchedulePoster(app, database, ActualTransactionWriter(database), ActualScheduleWriter(database))
            val posted = poster.runIfNeeded(budgetId)
            if (posted > 0) outcome = try { syncAt(serverUrl) } catch (primary: Exception) {
                fallbackUrl?.let(::syncAt) ?: throw primary
            }
            if (makeBackup) runCatching { BackupService(app, files).makeBackup(budgetId) }
            SyncRunResult.Success(outcome, posted)
        }
    }
}

class ActualSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val status = SyncStatusStore(applicationContext); status.started()
        return try {
            when (val run = ActualSyncRunner.run(applicationContext, inputData.getBoolean(BACKUP_KEY, false))) {
                is SyncRunResult.Success -> { status.succeeded(run.outcome); Result.success() }
                SyncRunResult.NotConfigured -> { status.stoppedWithoutSync(); Result.success() }
                SyncRunResult.EncryptionKeyUnavailable -> {
                    status.failed(IllegalStateException("Unlock this encrypted budget before syncing")); Result.failure()
                }
            }
        } catch (error: Exception) {
            status.failed(error)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object { const val BACKUP_KEY = "makeBackup" }
}

object ActualSyncScheduler {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<ActualSyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf(ActualSyncWorker.BACKUP_KEY to true))
            .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** A short delay coalesces the CRDT cells produced by one user operation. */
    fun scheduleMutation(context: Context) {
        val request = OneTimeWorkRequestBuilder<ActualSyncWorker>().setInitialDelay(1, TimeUnit.SECONDS)
            .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun scheduleForeground(context: Context) {
        val request = OneTimeWorkRequestBuilder<ActualSyncWorker>().setConstraints(network).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private const val PERIODIC = "actua-periodic-sync"
    private const val IMMEDIATE = "actua-immediate-sync"
}
