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

    @Test
    fun movesAndCoversAppendActualsMovementNote() = withRepository { repository, db ->
        // Upstream addMovementNotes (#908). Power is overspent by 20.00 in October.
        db.exec("INSERT INTO zero_budgets (id, month, category, amount, carryover) VALUES ('202610-rent', 202610, 'rent', 100000, 0)")
        db.exec("INSERT INTO transactions (id, isParent, isChild, acct, category, amount, date, tombstone) VALUES ('p1', 0, 0, 'checking', 'power', -2000, 20261005, 0)")
        db.exec("INSERT INTO notes (id, note) VALUES ('budget-2026-10', 'Plan')")
        val day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd", java.util.Locale.ENGLISH))

        // From To Budget into a category that isn't overspent: transferAvailable, no note.
        repository.transferBudget(null, null, "Bills", "Rent", 500, "2026-10", ActuaRepository.BudgetMoveKind.FROM_TO_BUDGET)
        repository.transferBudget(null, null, "Bills", "Rent", 500, "2026-10")
        assertEquals("Plan", db.note("budget-2026-10"))

        // Category to category (transferCategory) and To Budget covering overspending (coverOverspending).
        repository.transferBudget("Bills", "Rent", "Bills", "Power", 1_234, "2026-10")
        repository.transferBudget(null, null, "Bills", "Power", 766, "2026-10")
        // Category back to To Budget, and covering an overbudgeted To Budget.
        repository.transferBudget("Bills", "Rent", null, null, 100, "2026-10")
        repository.transferBudget("Bills", "Rent", null, null, 200, "2026-10", ActuaRepository.BudgetMoveKind.COVER_OVERBUDGETED)

        assertEquals(
            listOf(
                "Plan",
                "- Reassigned 12.34 from Rent → Power on $day",
                "- Reassigned 7.66 from To Budget → Power on $day",
                "- Reassigned 1.00 from Rent → To Budget on $day",
                "- Reassigned 2.00 from Rent → Overbudgeted on $day",
            ).joinToString("\n"),
            db.note("budget-2026-10"),
        )
        assertEquals(mapOf("power" to 2_000L, "rent" to 99_466L), db.budgets(202610))
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
