package com.azimulkabir.actua.data.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BackupItem
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import com.azimulkabir.actua.data.sync.ActualSyncWorker
import com.azimulkabir.actua.data.sync.LoadedEncryptionKey
import com.azimulkabir.actua.data.sync.LocalBackupWorker
import com.azimulkabir.actua.data.sync.SyncStatusStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisconnectResetManagerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = BudgetFileManager(context)
    private val activeStore = ActiveBudgetStore(context)
    private val keyStore = BudgetEncryptionKeyStore(context)
    private val credentials = CredentialStore(context)

    private var originalActiveBudgetId: String? = null
    private val createdBudgetIds = mutableListOf<String>()

    @Before
    fun setUp() {
        originalActiveBudgetId = activeStore.budgetId
    }

    @After
    fun tearDown() {
        activeStore.budgetId = originalActiveBudgetId
        createdBudgetIds.forEach { id ->
            runCatching { files.deleteBudget(id) }
        }
        credentials.clearCredentialsNow()
    }

    @Test
    fun resetDeletesServerBudgetsAndPreservesStandaloneAndDemoBudgets() {
        // 1. Create a server-backed budget with a backup and encryption key
        val serverBudget = files.createBudget("Server Budget ${UUID.randomUUID()}").also {
            createdBudgetIds.add(it.id)
        }
        val cloudFileId = "cloud-${UUID.randomUUID()}"
        files.saveCloudRegistration(serverBudget.id, cloudFileId, "group-${UUID.randomUUID()}")
        BackupService(context, files).makeBackup(serverBudget.id)
        val serverKey = LoadedEncryptionKey(cloudFileId, ByteArray(32) { 7 })
        keyStore.store(cloudFileId, serverKey)

        // 2. Create a standalone user-local budget with a backup
        val standaloneBudget = files.createBudget("Standalone Budget ${UUID.randomUUID()}").also {
            createdBudgetIds.add(it.id)
        }
        BackupService(context, files).makeBackup(standaloneBudget.id)

        // 3. Ensure demo budget exists with a backup
        val demoMetadata = DemoBudgetManager.recreate(files)
        BackupService(context, files).makeBackup(demoMetadata.id)

        // 4. Set credentials and preferences
        credentials.saveConnection("https://actual.test", "synth-token")
        context.getSharedPreferences("actua-sync-status", Context.MODE_PRIVATE)
            .edit().putString("last_status", "ok").commit()
        context.getSharedPreferences("actua-schedule-poster", Context.MODE_PRIVATE)
            .edit().putInt("gated", 20261018).commit()
        context.getSharedPreferences("budget_ui_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("expanded", true).commit()
        context.getSharedPreferences("account_detail_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("hidden", false).commit()

        // 5. Run disconnect reset
        DisconnectResetManager(context).resetLocalServerData()

        // Assertions:
        // Server budget is deleted along with its backup and encryption key
        assertFalse(files.budgetDirectory(serverBudget.id).exists())
        assertNull(keyStore.load(cloudFileId))

        // Standalone budget and its backups are preserved
        assertTrue(files.budgetDirectory(standaloneBudget.id).exists())
        val standaloneBackups = BackupService(context, files).availableBackups(standaloneBudget.id)
            .filterIsInstance<BackupItem.Archive>()
        assertEquals(1, standaloneBackups.size)

        // Demo budget and its backups are preserved
        assertTrue(files.budgetDirectory(demoMetadata.id).exists())
        val demoBackups = BackupService(context, files).availableBackups(demoMetadata.id)
            .filterIsInstance<BackupItem.Archive>()
        assertEquals(1, demoBackups.size)

        // Credentials and caches are cleared
        assertNull(credentials.token())
        assertTrue(context.getSharedPreferences("actua-sync-status", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(context.getSharedPreferences("actua-schedule-poster", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(context.getSharedPreferences("budget_ui_preferences", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(context.getSharedPreferences("account_detail_preferences", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun queuedSyncCannotRepopulateStatusAfterDisconnectEvenAfterReconnecting() = runBlocking {
        val generation = ActualSyncRunner.workGeneration(context)
        // Work persisted before this upgrade has no generation field and belongs to generation zero.
        val legacyWorker = TestListenableWorkerBuilder<ActualSyncWorker>(context)
            .setInputData(workDataOf(ActualSyncWorker.BACKGROUND_KEY to true)).build()
        val worker = TestListenableWorkerBuilder<ActualSyncWorker>(context)
            .setInputData(workDataOf(
                ActualSyncWorker.GENERATION_KEY to generation,
                ActualSyncWorker.BACKGROUND_KEY to true,
            )).build()
        DisconnectResetManager(context).resetLocalServerData()
        // Reconnecting must not make a worker from the previous session valid again.
        credentials.saveConnection("https://actual.test", "synth-token")
        activeStore.budgetId = null
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(ListenableWorker.Result.success(), legacyWorker.doWork())
        assertTrue(context.getSharedPreferences("actua-sync-status", Context.MODE_PRIVATE).all.isEmpty())

        val freshWorker = TestListenableWorkerBuilder<ActualSyncWorker>(context)
            .setInputData(workDataOf(
                ActualSyncWorker.GENERATION_KEY to ActualSyncRunner.workGeneration(context),
                ActualSyncWorker.BACKGROUND_KEY to true,
            )).build()
        assertEquals(ListenableWorker.Result.success(), freshWorker.doWork())
        assertTrue(context.getSharedPreferences("actua-sync-status", Context.MODE_PRIVATE)
            .getLong("lastBackgroundRefresh", 0L) > 0L)
    }

    @Test
    fun queuedBackupCannotBackUpTheRetainedStandaloneBudgetAfterDisconnect() = runBlocking {
        val budget = files.createBudget("Standalone ${UUID.randomUUID()}").also { createdBudgetIds.add(it.id) }
        activeStore.budgetId = budget.id
        val worker = TestListenableWorkerBuilder<LocalBackupWorker>(context)
            .setInputData(workDataOf(ActualSyncWorker.GENERATION_KEY to ActualSyncRunner.workGeneration(context)))
            .build()
        DisconnectResetManager(context).resetLocalServerData()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertTrue(BackupService(context, files).availableBackups(budget.id).filterIsInstance<BackupItem.Archive>().isEmpty())

        // Newly scheduled work still runs for standalone budgets without server credentials.
        val freshWorker = TestListenableWorkerBuilder<LocalBackupWorker>(context)
            .setInputData(workDataOf(ActualSyncWorker.GENERATION_KEY to ActualSyncRunner.workGeneration(context)))
            .build()
        assertEquals(ListenableWorker.Result.success(), freshWorker.doWork())
        assertEquals(1, BackupService(context, files).availableBackups(budget.id).filterIsInstance<BackupItem.Archive>().size)
    }

    @Test
    fun resetWaitsForRunningWorkIncludingItsCompletionWrites() {
        val generation = ActualSyncRunner.workGeneration(context)
        val workerStarted = CountDownLatch(1)
        val finishWorker = CountDownLatch(1)
        val resetStarted = CountDownLatch(1)
        val resetFinished = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val work = executor.submit {
                ActualSyncRunner.runScheduledWork(context, generation) {
                    workerStarted.countDown()
                    check(finishWorker.await(10, TimeUnit.SECONDS))
                    // Completion writes used to run outside the sync lock.
                    assertEquals(1L, resetFinished.count)
                    SyncStatusStore(context).backgroundRefreshFinished()
                }
            }
            assertTrue(workerStarted.await(10, TimeUnit.SECONDS))
            val reset = executor.submit {
                resetStarted.countDown()
                DisconnectResetManager(context).resetLocalServerData()
                resetFinished.countDown()
            }
            assertTrue(resetStarted.await(10, TimeUnit.SECONDS))
            assertFalse("Reset must wait for the running worker", resetFinished.await(200, TimeUnit.MILLISECONDS))
            finishWorker.countDown()
            work.get(10, TimeUnit.SECONDS)
            reset.get(10, TimeUnit.SECONDS)
            assertTrue(context.getSharedPreferences("actua-sync-status", Context.MODE_PRIVATE).all.isEmpty())
        } finally {
            finishWorker.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun resetRetainsActiveSelectionWhenStandaloneBudgetIsSelected() {
        val standaloneBudget = files.createBudget("Standalone Active ${UUID.randomUUID()}").also {
            createdBudgetIds.add(it.id)
        }
        activeStore.budgetId = standaloneBudget.id

        DisconnectResetManager(context).resetLocalServerData()

        assertEquals(standaloneBudget.id, activeStore.budgetId)
    }

    @Test
    fun resetClearsActiveSelectionWhenServerBackedBudgetIsSelected() {
        val serverBudget = files.createBudget("Server Active ${UUID.randomUUID()}").also {
            createdBudgetIds.add(it.id)
        }
        files.saveCloudRegistration(serverBudget.id, "cloud-active-${UUID.randomUUID()}", "group-1")
        activeStore.budgetId = serverBudget.id

        DisconnectResetManager(context).resetLocalServerData()

        assertNull(activeStore.budgetId)
    }

    @Test
    fun resetClearsActiveSelectionWhenDemoBudgetIsSelected() {
        DemoBudgetManager.recreate(files)
        activeStore.budgetId = DemoBudgetManager.BUDGET_ID

        DisconnectResetManager(context).resetLocalServerData()

        assertNull(activeStore.budgetId)
    }
}
