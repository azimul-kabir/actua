package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class DemoBudgetRepairTest {
    private val files = BudgetFileManager(InstrumentationRegistry.getInstrumentation().targetContext)

    @Before @After fun removeDemo() {
        runCatching { files.budgetDirectory(DemoBudgetManager.BUDGET_ID).deleteRecursively() }
    }

    @Test fun repairRecreatesBeta6DemoWithoutScheduleSortOrder() {
        writeDemoDatabase("CREATE TABLE schedules (id TEXT PRIMARY KEY, name TEXT)")

        DemoBudgetManager.repairIfNeeded(files)

        openDemo().use { db ->
            assertTrue(count(db, "SELECT COUNT(*) FROM pragma_table_info('schedules') WHERE name='sort_order'") == 1)
            assertTrue(count(db, "SELECT COUNT(*) FROM transactions") > 0)
        }
    }

    @Test fun repairLeavesDemoWithoutSchedulesTableAlone() {
        // A reset that has created the file but not its schema yet looks like this to a reader.
        writeDemoDatabase("CREATE TABLE placeholder (id TEXT)")

        DemoBudgetManager.repairIfNeeded(files)

        openDemo().use { db ->
            assertEquals(1, count(db, "SELECT COUNT(*) FROM sqlite_master WHERE name='placeholder'"))
            assertEquals(0, count(db, "SELECT COUNT(*) FROM sqlite_master WHERE name='transactions'"))
        }
    }

    @Test fun repairDuringResetNeverSeedsTwice() {
        DemoBudgetManager.recreate(files)
        val resetting = AtomicBoolean(true)
        val started = CountDownLatch(1)
        // Mirrors ActiveBudgetStore.budgetId reads arriving while the user resets the demo.
        val reader = thread {
            started.countDown()
            while (resetting.get()) DemoBudgetManager.repairIfNeeded(files)
        }
        started.await()
        repeat(5) { DemoBudgetManager.recreate(files) }
        resetting.set(false)
        reader.join()

        openDemo().use { db ->
            assertEquals(
                0,
                count(db, "SELECT COUNT(*) FROM transactions t WHERE t.category IS NOT NULL AND t.category NOT IN (SELECT id FROM categories)"),
            )
            assertEquals(
                0,
                count(db, "SELECT COUNT(*) FROM (SELECT acct, date, amount, description FROM transactions GROUP BY 1,2,3,4 HAVING COUNT(*) > 1)"),
            )
        }
        assertFalse(files.budgetDirectory(DemoBudgetManager.BUDGET_ID).listFiles().orEmpty().isEmpty())
    }

    private fun writeDemoDatabase(schema: String) {
        val file = files.databaseFile(DemoBudgetManager.BUDGET_ID)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { it.execSQL(schema) }
    }

    private fun openDemo() = SQLiteDatabase.openDatabase(
        files.databaseFile(DemoBudgetManager.BUDGET_ID).path, null, SQLiteDatabase.OPEN_READONLY,
    )

    private fun count(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
