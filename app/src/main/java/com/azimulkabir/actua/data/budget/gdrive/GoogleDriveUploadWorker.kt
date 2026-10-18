package com.azimulkabir.actua.data.budget.gdrive

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Durable, network-constrained background worker for uploading Actua backup archives to Google Drive.
 * Complies with issue #1168: local backups succeed independently; upload failures are retried safely
 * without affecting local files or retention.
 */
class GoogleDriveUploadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val budgetId = inputData.getString(KEY_BUDGET_ID) ?: return@withContext Result.failure()
        val manager = GoogleDriveBackupManager(applicationContext)

        if (!manager.isConnected()) {
            return@withContext Result.success()
        }

        if (!manager.hasEncryptionPassphrase()) {
            manager.recordError("Backup encryption password not set")
            return@withContext Result.failure()
        }

        val files = BudgetFileManager(applicationContext)
        val backupsDir = files.backupsDirectory(budgetId)
        val localArchives = backupsDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "zip" }
            .sortedByDescending { it.lastModified() }

        val latestArchive = localArchives.firstOrNull() ?: run {
            // If no local archive exists yet, create one
            runCatching { BackupService(applicationContext, files).makeBackup(budgetId) }.getOrNull()
        } ?: return@withContext Result.failure()

        try {
            manager.uploadBackup(budgetId, latestArchive)
            Result.success()
        } catch (e: GoogleDriveException.Unauthorized) {
            manager.recordError("Google Drive authentication expired. Please reconnect in Settings.")
            Result.failure()
        } catch (e: GoogleDriveException.QuotaExceeded) {
            manager.recordError("Google Drive storage quota exceeded.")
            Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                manager.recordError(e.message ?: "Google Drive backup failed")
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_BUDGET_ID = "budgetId"
        const val WORK_TAG = "actua-gdrive-upload"
    }
}

object GoogleDriveBackupScheduler {
    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleUpload(context: Context, budgetId: String) {
        val manager = GoogleDriveBackupManager(context)
        if (!manager.isConnected()) return

        val request = OneTimeWorkRequestBuilder<GoogleDriveUploadWorker>()
            .setInputData(workDataOf(GoogleDriveUploadWorker.KEY_BUDGET_ID to budgetId))
            .setConstraints(networkConstraint)
            .addTag(GoogleDriveUploadWorker.WORK_TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "gdrive-upload-$budgetId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(GoogleDriveUploadWorker.WORK_TAG)
    }
}
