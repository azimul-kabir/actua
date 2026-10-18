package com.azimulkabir.actua.data.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BackupItem
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import com.azimulkabir.actua.data.sync.LoadedEncryptionKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

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
