package com.azimulkabir.actua.data.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BackupItem
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.security.CredentialStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StandaloneSchedulePostingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = BudgetFileManager(context)
    private val activeStore = ActiveBudgetStore(context)
    private val credentials = CredentialStore(context)

    private var originalActiveBudgetId: String? = null
    private val createdBudgetIds = mutableListOf<String>()

    @Before
    fun setUp() {
        originalActiveBudgetId = activeStore.budgetId
        credentials.clearCredentialsNow()
        ActualSyncRunner.resetForTesting()
        context.getSharedPreferences("actua-schedule-poster", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        activeStore.budgetId = originalActiveBudgetId
        ActualSyncRunner.resetForTesting()
        createdBudgetIds.forEach { id ->
            runCatching { files.deleteBudget(id) }
        }
        credentials.clearCredentialsNow()
        context.getSharedPreferences("actua-schedule-poster", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun standaloneBudgetPostsDueSchedulesLocallyWithoutServerCredentials() {
        // 1. Create a standalone budget (no cloudFileId, not demo)
        val budget = files.createBudget("Standalone Schedule ${UUID.randomUUID()}").also {
            createdBudgetIds.add(it.id)
        }
        assertTrue(budget.isUserLocalOnly)
        assertNull(budget.cloudFileId)

        // 2. Ensure no server credentials exist
        assertNull(credentials.token())
        assertTrue(credentials.serverUrl.isBlank())

        // 3. Seed account, payee, rule and due schedule in the standalone budget database
        val today = DayDate.today()
        val todayIso = "${today.year}-${today.month.toString().padStart(2, '0')}-${today.day.toString().padStart(2, '0')}"
        SQLiteDatabase.openDatabase(files.databaseFile(budget.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO accounts (id, name, type, offbudget, closed, tombstone, sort_order) VALUES ('checking', 'Checking', 'checking', 0, 0, 0, 1.0)")
            db.execSQL("INSERT INTO payees (id, name, transfer_acct, tombstone) VALUES ('landlord', 'Landlord', NULL, 0)")
            db.execSQL("INSERT INTO payee_mapping (id, targetId) VALUES ('landlord', 'landlord')")
            db.execSQL(
                """INSERT INTO rules (id, stage, conditions_op, conditions, actions, tombstone) VALUES (
                    'rent-rule',
                    NULL,
                    'and',
                    '[{"op":"is","field":"acct","value":"checking"},{"op":"is","field":"description","value":"landlord"},{"op":"is","field":"amount","value":-120000},{"op":"isapprox","field":"date","value":{"frequency":"monthly","start":"$todayIso"}}]',
                    '[{"op":"set","field":"category","value":"d4b0f075-3343-4408-91ed-fae94f74e5bf"},{"op":"link-schedule","value":"rent-schedule"}]',
                    0
                )""".trimIndent(),
            )
            db.execSQL(
                """INSERT INTO schedules (id, rule, active, completed, posts_transaction, tombstone, name, sort_order) VALUES (
                    'rent-schedule', 'rent-rule', 1, 0, 1, 0, 'Rent', 0.0
                )""".trimIndent(),
            )
            db.execSQL(
                """INSERT INTO schedules_next_date (id, schedule_id, local_next_date, local_next_date_ts, base_next_date, base_next_date_ts, tombstone) VALUES (
                    'rent-next', 'rent-schedule', ${today.yyyymmdd}, 100, ${today.yyyymmdd}, 100, 0
                )""".trimIndent(),
            )
        }

        activeStore.budgetId = budget.id
        val generationBefore = SyncSignals.dataGeneration.value

        // 4. Run sync runner with makeBackup = true
        val result = ActualSyncRunner.run(context, trigger = "Manual", makeBackup = true)

        // 5. Assert result is Success with 0 remote messages and 1 posted schedule
        assertTrue("Expected Success result, got $result", result is SyncRunResult.Success)
        val success = result as SyncRunResult.Success
        assertEquals(0, success.outcome.sentMessages)
        assertEquals(0, success.outcome.receivedMessages)
        assertEquals(0, success.outcome.attempts)
        assertEquals(1, success.postedSchedules)

        // 6. Assert SyncSignals.dataChanged() was triggered
        assertTrue(SyncSignals.dataGeneration.value > generationBefore)

        // 7. Verify transaction was created in database
        SQLiteDatabase.openDatabase(files.databaseFile(budget.id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT amount, schedule, acct FROM transactions WHERE tombstone = 0 AND schedule = 'rent-schedule'", null).use { cursor ->
                assertTrue("Expected posted transaction in database", cursor.moveToFirst())
                assertEquals(-120000L, cursor.getLong(0))
                assertEquals("rent-schedule", cursor.getString(1))
                assertEquals("checking", cursor.getString(2))
            }
        }

        // 8. Verify local backup was made
        val backups = BackupService(context, files).availableBackups(budget.id)
            .filterIsInstance<BackupItem.Archive>()
        assertEquals(1, backups.size)
    }

    @Test
    fun standaloneBudgetReusesRecentSuccessWhenCoalescingAllowed() {
        val budget = files.createBudget("Standalone Coalesce ${UUID.randomUUID()}").also {
            createdBudgetIds.add(it.id)
        }
        activeStore.budgetId = budget.id

        val firstResult = ActualSyncRunner.run(context, trigger = "Manual", allowRecentSuccess = false)
        assertTrue(firstResult is SyncRunResult.Success)

        val secondResult = ActualSyncRunner.run(context, trigger = "App open", allowRecentSuccess = true)
        assertEquals(firstResult, secondResult)
    }

    @Test
    fun demoBudgetReturnsNotConfiguredWithoutAttemptingSync() {
        DemoBudgetManager.recreate(files)
        activeStore.budgetId = DemoBudgetManager.BUDGET_ID

        val result = ActualSyncRunner.run(context, trigger = "Manual")
        assertEquals(SyncRunResult.NotConfigured, result)
    }
}
