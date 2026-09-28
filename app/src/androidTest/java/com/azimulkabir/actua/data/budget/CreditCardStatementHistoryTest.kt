package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.ActuaRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditCardStatementHistoryTest {
    @Test fun closedCardHasNoStatementHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val activeBudget = ActiveBudgetStore(context)
        val previousBudgetId = activeBudget.budgetId
        val cardId = "demo-account-credit"
        runCatching { if (files.budgetDirectory(DemoBudgetManager.BUDGET_ID).exists()) files.deleteBudget(DemoBudgetManager.BUDGET_ID) }

        try {
            val metadata = DemoBudgetManager.recreate(files)
            activeBudget.budgetId = DemoBudgetManager.BUDGET_ID
            ActuaRepository(context).let { repository ->
                try {
                    assertTrue(repository.fetchRecentStatements(cardId).isNotEmpty())
                } finally {
                    repository.close()
                }
            }

            SQLiteDatabase.openDatabase(files.databaseFile(metadata.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("UPDATE accounts SET closed = 1 WHERE id = ?", arrayOf(cardId))
            }
            ActuaRepository(context).let { repository ->
                try {
                    assertTrue(repository.fetchRecentStatements(cardId).isEmpty())
                } finally {
                    repository.close()
                }
            }
        } finally {
            activeBudget.budgetId = previousBudgetId
            runCatching { files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
        }
    }
}
