package com.azimulkabir.actua.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ActuaRepositoryRecoveryTest {
    @Test
    fun repositoryFallsBackWhenSelectedBudgetIsUnreadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val valid = files.createBudget("Valid ${UUID.randomUUID()}")
        val invalid = files.createBudget("Invalid ${UUID.randomUUID()}")
        try {
            SQLiteDatabase.openDatabase(files.databaseFile(invalid.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    """
                        INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type)
                        VALUES ('checking', 'Checking', 0, 0, 0, 1, 'checking')
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                        INSERT INTO transactions
                            (id, acct, amount, date, sort_order, tombstone, cleared, pending, reconciled)
                        VALUES
                            ('bad-date', 'checking', -500, 20241340, 1, 0, 1, 0, 0)
                    """.trimIndent(),
                )
            }
            ActiveBudgetStore(context).budgetId = invalid.id

            val repository = ActuaRepository(context)
            try {
                assertTrue(repository.isUsingActualBudget)
                assertEquals(valid.id, ActiveBudgetStore(context).budgetId)
            } finally {
                repository.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = null
            runCatching { files.deleteBudget(valid.id) }
            runCatching { files.deleteBudget(invalid.id) }
        }
    }
}
