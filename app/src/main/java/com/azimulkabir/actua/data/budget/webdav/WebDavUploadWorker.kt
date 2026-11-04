package com.azimulkabir.actua.data.budget.webdav

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.BudgetFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Durable, network-constrained background upload of the newest local backup to the configured WebDAV server,
 * like the Google Drive one: local backups succeed on their own, and an upload failure never affects local files.
 * What happens on each outcome lives in [ScheduledWebDavBackup].
 */
class WebDavUploadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val budgetId = inputData.getString(KEY_BUDGET_ID) ?: return@withContext Result.failure()
        val manager = WebDavBackupManager(applicationContext)
        val files = BudgetFileManager(applicationContext)

        val outcome = ScheduledWebDavBackup(
            isConfigured = manager::isConfigured,
            hasPassphrase = manager::hasEncryptionPassphrase,
            latestArchive = { id ->
                files.backupsDirectory(id).listFiles().orEmpty()
                    .filter { it.isFile && it.extension == "zip" }
                    .maxByOrNull { it.lastModified() }
            },
            upload = { id, archive -> manager.uploadBackup(id, archive) },
            recordError = manager::recordError,
            describe = manager::describe,
            passphraseMissingMessage = applicationContext.getString(R.string.webdav_error_password_missing),
        ).run(budgetId, runAttemptCount)

        when (outcome) {
            ScheduledWebDavBackup.Outcome.SUCCESS -> Result.success()
            ScheduledWebDavBackup.Outcome.RETRY -> Result.retry()
            ScheduledWebDavBackup.Outcome.FAILURE -> Result.failure()
        }
    }

    companion object {
        const val KEY_BUDGET_ID = "budgetId"
        const val WORK_TAG = "actua-webdav-upload"
    }
}

object WebDavBackupScheduler {
    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Queues an upload of [budgetId]'s newest local backup, replacing one that is still waiting. */
    fun scheduleUpload(context: Context, budgetId: String) {
        if (!WebDavBackupStore(context).isConfigured()) return

        val request = OneTimeWorkRequestBuilder<WebDavUploadWorker>()
            .setInputData(workDataOf(WebDavUploadWorker.KEY_BUDGET_ID to budgetId))
            .setConstraints(networkConstraint)
            .addTag(WebDavUploadWorker.WORK_TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "webdav-upload-$budgetId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(WebDavUploadWorker.WORK_TAG)
    }
}
