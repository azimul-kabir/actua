package com.azimulkabir.actua.data.budget

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.StrictMode
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * #1710: reading the active budget's id must not query the demo database on the main thread, and the legacy
 * `schedules` repair (a table without `sort_order`, from beta.6) must still happen, once per process, before a
 * caller that goes on to open the database uses it.
 */
class DemoBudgetSchemaCheckTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = BudgetFileManager(context)
    private val store = ActiveBudgetStore(context)
    private var previousBudgetId: String? = null

    @Before fun createDemo() {
        previousBudgetId = store.budgetId
        runCatching { if (files.budgetDirectory(DEMO).exists()) files.deleteBudget(DEMO) }
        DemoBudgetManager.recreate(files)
        store.budgetId = DEMO
        DemoBudgetManager.invalidateSchemaCheck()
    }

    @After fun restore() {
        store.budgetId = previousBudgetId
        runCatching { files.deleteBudget(DEMO) }
        DemoBudgetManager.invalidateSchemaCheck()
    }

    @Test fun anOffMainReadRepairsALegacyDemoBeforeReturning() {
        makeDemoLegacy()
        assertFalse(schedulesHaveSortOrder())

        assertEquals(DEMO, offMain { store.budgetId })

        assertTrue("the read should have repaired the schedules table", schedulesHaveSortOrder())
        assertTrue(DemoBudgetManager.isSchemaChecked())
    }

    @Test fun aMainThreadReadDoesNoDiskWorkButTheDemoIsStillRepairedInTheBackground() {
        makeDemoLegacy()
        // Load the preferences file first: its first read is disk work that is not what this test is about.
        context.getSharedPreferences("active_budget", Context.MODE_PRIVATE).getString("budget_id", null)
        val violations = CopyOnWriteArrayList<String>()
        var id: String? = null

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var previous: StrictMode.ThreadPolicy? = null
        instrumentation.runOnMainSync {
            previous = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyListener(Executor { it.run() }) { violations += it.stackTraceToString() }
                    .build(),
            )
            id = store.budgetId
        }
        // StrictMode reports a violation when the current main-thread message ends, using the policy set at that
        // moment, so keep the policy until the main looper has drained.
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { previous?.let(StrictMode::setThreadPolicy) }

        assertEquals(DEMO, id)
        assertEquals(
            "reading the id on the main thread should not touch the disk",
            emptyList<String>(),
            violations.filter { it.contains("DemoBudgetManager") || it.contains("android.database.sqlite") },
        )
        // The background repair recreates the database, which can briefly lock it for this reader.
        waitUntil { runCatching { schedulesHaveSortOrder() }.getOrDefault(false) }
        assertTrue(schedulesHaveSortOrder())
    }

    @Test fun theCheckRunsOncePerProcess() {
        makeDemoLegacy()
        offMain { store.budgetId }
        assertTrue(schedulesHaveSortOrder())

        // Without a new check the legacy shape stays: this is the proof that later reads do not query the database.
        makeDemoLegacy()
        offMain { store.budgetId }

        assertFalse(schedulesHaveSortOrder())
    }

    @Test fun deletingTheDemoMakesTheNextReadCheckAgain() {
        offMain { store.budgetId }
        assertTrue(DemoBudgetManager.isSchemaChecked())

        files.deleteBudget(DEMO)

        assertFalse(DemoBudgetManager.isSchemaChecked())
    }

    private fun makeDemoLegacy() {
        SQLiteDatabase.openDatabase(files.databaseFile(DEMO).path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.execSQL("DROP TABLE IF EXISTS schedules")
            database.execSQL("CREATE TABLE schedules (id TEXT PRIMARY KEY, name TEXT)")
        }
    }

    private fun schedulesHaveSortOrder(): Boolean =
        SQLiteDatabase.openDatabase(files.databaseFile(DEMO).path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("PRAGMA table_info(schedules)", null).use { cursor ->
                val name = cursor.getColumnIndexOrThrow("name")
                buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }.contains("sort_order")
            }
        }

    private fun <T> offMain(block: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        try {
            return executor.submit<T>(block).get()
        } finally {
            executor.shutdown()
        }
    }

    private fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(50)
    }

    private companion object {
        const val DEMO = DemoBudgetManager.BUDGET_ID
    }
}
