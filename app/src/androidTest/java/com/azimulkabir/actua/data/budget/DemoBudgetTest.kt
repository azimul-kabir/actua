package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.ActuaRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class DemoBudgetTest {
    @Test fun demoBudgetIsLocalFeatureRichAndResettable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val activeBudget = ActiveBudgetStore(context)
        val previousBudgetId = activeBudget.budgetId
        runCatching { if (files.budgetDirectory(DemoBudgetManager.BUDGET_ID).exists()) files.deleteBudget(DemoBudgetManager.BUDGET_ID) }

        try {
            val metadata = DemoBudgetManager.recreate(files)
            assertEquals(DemoBudgetManager.BUDGET_ID, metadata.id)
            assertEquals(DemoBudgetManager.BUDGET_NAME, metadata.budgetName)
            assertNull(metadata.cloudFileId)
            assertNull(metadata.groupId)
            assertTrue(JSONObject(files.metadataFile(metadata.id).readText()).getBoolean("demo"))
            ActualBudgetDatabase.validate(files.databaseFile(metadata.id))

            SQLiteDatabase.openDatabase(files.databaseFile(metadata.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                assertEquals(4, count(db, "SELECT COUNT(*) FROM accounts WHERE tombstone=0"))
                assertEquals(10, count(db, "SELECT COUNT(*) FROM categories WHERE tombstone=0"))
                assertEquals(6, count(db, "SELECT COUNT(*) FROM categories WHERE goal_def IS NOT NULL"))
                assertTrue(count(db, "SELECT COUNT(*) FROM transactions WHERE tombstone=0") >= 50)
                assertTrue(count(db, "SELECT COUNT(*) FROM transactions WHERE transferred_id IS NOT NULL") >= 2)
                assertTrue(count(db, "SELECT COUNT(*) FROM transactions WHERE reconciled=0") >= 2)
                assertEquals(4, count(db, "SELECT COUNT(*) FROM schedules WHERE tombstone=0"))
                assertTrue(count(db, "SELECT COUNT(*) FROM rules WHERE tombstone=0") >= 7)
                assertEquals(1, count(db, "SELECT COUNT(*) FROM preferences WHERE id='actuali:credit_card:demo-account-credit'"))
                assertTrue(count(db, "SELECT COUNT(*) FROM dashboard WHERE tombstone=0") >= 3)
                assertEquals(
                    count(db, "SELECT COUNT(*) FROM payees WHERE tombstone=0"),
                    count(db, "SELECT COUNT(*) FROM payee_mapping"),
                )
            }

            // Mirror AppNavigation's eager reads. This specifically guards against
            // a demo DB that validates structurally but crashes on first render.
            activeBudget.budgetId = DemoBudgetManager.BUDGET_ID
            ActuaRepository(context).let { repository ->
                try {
                    val month = YearMonth.now().toString()
                    assertTrue(repository.isUsingActualBudget)
                    repository.budgetGroups(month)
                    repository.budgetOverview(month)
                    repository.accounts()
                    repository.transactions()
                    repository.categoryNames()
                    repository.payeeNames()
                    repository.reports()
                    repository.creditCards()
                    repository.rules()
                    repository.rulesSupported()
                    repository.scheduleOwnedRuleIds()
                    repository.ruleEditorData()
                    assertEquals(4, repository.schedules().size)
                } finally {
                    repository.close()
                }
            }

            SQLiteDatabase.openDatabase(files.databaseFile(metadata.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("DELETE FROM transactions")
                assertEquals(0, count(db, "SELECT COUNT(*) FROM transactions"))
            }

            DemoBudgetManager.recreate(files)
            SQLiteDatabase.openDatabase(files.databaseFile(metadata.id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertTrue(count(db, "SELECT COUNT(*) FROM transactions WHERE tombstone=0") >= 50)
                val cloudIdentity = JSONObject(files.metadataFile(metadata.id).readText())
                assertFalse(cloudIdentity.has("cloudFileId"))
                assertFalse(cloudIdentity.has("groupId"))
            }
        } finally {
            activeBudget.budgetId = previousBudgetId
            runCatching { files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
        }
    }

    private fun count(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
