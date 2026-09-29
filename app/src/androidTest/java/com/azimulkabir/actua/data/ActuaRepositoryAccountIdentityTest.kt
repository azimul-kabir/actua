package com.azimulkabir.actua.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Actual allows duplicate account names, so account actions must change only the account picked by id. */
class ActuaRepositoryAccountIdentityTest {
    @Test
    fun accountActionsChangeOnlyTheSelectedSameNamedAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Duplicate names ${UUID.randomUUID()}")
        val previousBudget = ActiveBudgetStore(context).budgetId
        try {
            SQLiteDatabase.openDatabase(files.databaseFile(budget.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    """
                        INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type)
                        VALUES ('first', 'Checking', 0, 0, 0, 1, 'checking'),
                               ('second', 'Checking', 0, 0, 0, 2, 'checking')
                    """.trimIndent(),
                )
            }
            ActiveBudgetStore(context).budgetId = budget.id

            val repository = ActuaRepository(context)
            try {
                assertTrue(repository.renameAccount("second", "Joint checking"))
                assertEquals(mapOf("first" to "Checking", "second" to "Joint checking"), column(files, budget.id, "name"))

                assertTrue(repository.setAccountType("second", "Savings"))
                assertEquals(mapOf("first" to "checking", "second" to "savings"), column(files, budget.id, "type"))

                SQLiteDatabase.openDatabase(files.databaseFile(budget.id).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                    it.execSQL("UPDATE accounts SET name = 'Checking', closed = 1")
                }
                assertTrue(repository.reopenAccount("second"))
                assertEquals(mapOf("first" to "1", "second" to "0"), column(files, budget.id, "closed"))
            } finally {
                repository.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = previousBudget
            runCatching { files.deleteBudget(budget.id) }
        }
    }

    private fun column(files: BudgetFileManager, budgetId: String, column: String): Map<String, String?> =
        SQLiteDatabase.openDatabase(files.databaseFile(budgetId).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT id, $column FROM accounts ORDER BY id", null).use { cursor ->
                buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
            }
        }
}
