package com.azimulkabir.actua.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Budget actions against a real blank Actual budget, compared with loot-core `budget/actions.ts` (#667). */
class ActuaRepositoryBudgetActionsTest {
    @Test
    fun copyLastMonthCopiesOnlyStoredRowsOfVisibleCategories() = withRepository { repository, db ->
        // Upstream copyPreviousMonth copies last month's rows; a category without one keeps this
        // month's amount, and hidden categories are skipped (#906).
        db.exec("INSERT INTO zero_budgets (id, month, category, amount, carryover) VALUES ('202608-rent', 202608, 'rent', 100000, 0), ('202608-old', 202608, 'old', 5000, 0), ('202609-power', 202609, 'power', 3000, 0)")

        repository.copyPreviousMonthBudget("2026-09")

        assertEquals(mapOf("power" to 3000L, "rent" to 100000L), db.budgets(202609))
    }

    internal class Db(private val path: String) {
        fun exec(sql: String) = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL(sql) }

        fun budgets(month: Int): Map<String, Long> =
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT category, amount FROM zero_budgets WHERE month = ? ORDER BY category", arrayOf(month.toString())).use { c ->
                    buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
                }
            }

        fun note(id: String): String? =
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT note FROM notes WHERE id = ?", arrayOf(id)).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }
    }

    /**
     * A blank budget with an expense group (Rent, Power, hidden Old) and an income group (Salary),
     * opened as the active budget.
     */
    private fun withRepository(block: (ActuaRepository, Db) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Budget actions ${UUID.randomUUID()}")
        val previousBudget = ActiveBudgetStore(context).budgetId
        val path = files.databaseFile(budget.id).path
        val db = Db(path)
        try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { sql ->
                sql.execSQL("UPDATE categories SET tombstone = 1")
                sql.execSQL("UPDATE category_groups SET tombstone = 1")
                sql.execSQL("INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type) VALUES ('checking', 'Checking', 0, 0, 0, 1, 'checking')")
                sql.execSQL("INSERT INTO category_groups (id, name, is_income, hidden, tombstone, sort_order) VALUES ('bills', 'Bills', 0, 0, 0, 1), ('income', 'Income', 1, 0, 0, 2)")
                sql.execSQL(
                    """INSERT INTO categories (id, name, cat_group, is_income, hidden, tombstone, sort_order) VALUES
                        ('rent', 'Rent', 'bills', 0, 0, 0, 1), ('power', 'Power', 'bills', 0, 0, 0, 2),
                        ('old', 'Old', 'bills', 0, 1, 0, 3), ('salary', 'Salary', 'income', 1, 0, 0, 1)""",
                )
                sql.execSQL("INSERT INTO category_mapping (id, transferId) VALUES ('rent', 'rent'), ('power', 'power'), ('old', 'old'), ('salary', 'salary')")
            }
            ActiveBudgetStore(context).budgetId = budget.id
            val repository = ActuaRepository(context)
            try {
                block(repository, db)
            } finally {
                repository.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = previousBudget
            runCatching { files.deleteBudget(budget.id) }
        }
    }
}
