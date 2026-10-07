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

    @Test
    fun envelopeOverviewTotalsIncludeHiddenCategories() = withRepository { repository, db ->
        // Actual's envelope total-budgeted / total-spent / total-leftover count hidden categories (#909).
        db.exec("INSERT INTO zero_budgets (id, month, category, amount, carryover) VALUES ('202603-rent', 202603, 'rent', 100000, 0), ('202603-old', 202603, 'old', 5000, 0)")
        db.exec("INSERT INTO transactions (id, isParent, isChild, acct, category, amount, date, tombstone) VALUES ('o1', 0, 0, 'checking', 'old', -2000, 20260310, 0)")

        val overview = repository.budgetOverview("2026-03")

        assertEquals(105_000L, overview.budgetedCents)
        assertEquals(-2_000L, overview.spentCents)
        assertEquals(103_000L, overview.availableCents)
        assertEquals(null, overview.savedCents)
    }

    @Test
    fun trackingOverviewLeavesHiddenOutAndReportsSavings() = withRepository { repository, db ->
        // Actual's tracking totals skip hidden categories; real-saved for past months,
        // total-saved (projected) from the current month on (#909).
        db.exec("INSERT INTO preferences (id, value) VALUES ('budgetType', 'tracking')")
        db.exec(
            """INSERT INTO reflect_budgets (id, month, category, amount, carryover) VALUES
                ('202603-rent', 202603, 'rent', 100000, 0), ('202603-old', 202603, 'old', 5000, 0),
                ('202603-salary', 202603, 'salary', 300000, 0),
                ('209901-rent', 209901, 'rent', 100000, 0), ('209901-salary', 209901, 'salary', 250000, 0)""",
        )
        db.exec(
            """INSERT INTO transactions (id, isParent, isChild, acct, category, amount, date, tombstone) VALUES
                ('s1', 0, 0, 'checking', 'salary', 280000, 20260301, 0),
                ('r1', 0, 0, 'checking', 'rent', -100000, 20260302, 0),
                ('o1', 0, 0, 'checking', 'old', -2000, 20260310, 0)""",
        )

        val march = repository.budgetOverview("2026-03")
        assertEquals(100_000L, march.budgetedCents)
        assertEquals(-100_000L, march.spentCents)
        assertEquals(180_000L, march.savedCents)
        assertEquals("Saved" to 180_000L, march.lead())

        val future = repository.budgetOverview("2099-01")
        assertEquals(150_000L, future.savedCents)
        assertEquals("Projected savings" to 150_000L, future.lead())
    }

    @Test
    fun averagesMatchActualsSetNMonthAvg() = withRepository { repository, db ->
        // Upstream set3MonthAvg / setNMonthAvg (#910): Math.round over up to N months ending last
        // month, stopping at the first activity; expense averages negated, income kept positive.
        db.exec("INSERT INTO zero_budgets (id, month, category, amount, carryover) VALUES ('202502-rent', 202502, 'rent', 5000, 0)")
        db.exec(
            """INSERT INTO transactions (id, isParent, isChild, acct, category, amount, date, tombstone) VALUES
                ('p1', 0, 0, 'checking', 'power', -300, 20250115, 0),
                ('p2', 0, 0, 'checking', 'power', -600, 20250215, 0),
                ('p3', 0, 0, 'checking', 'power', -100, 20250220, 0),
                ('p4', 0, 0, 'checking', 'power', -900, 20250315, 0),
                ('p5', 0, 0, 'checking', 'power', -5000, 20250410, 0),
                ('o1', 0, 0, 'checking', 'old', -1200, 20250310, 0),
                ('s1', 0, 0, 'checking', 'salary', 300000, 20250201, 0),
                ('s2', 0, 0, 'checking', 'salary', 300000, 20250301, 0)""",
        )

        // Month-wide: visible expense categories only in an envelope budget. Rent's first activity
        // is its February budget row, so it averages two empty months to 0 and is unchanged.
        val preview = repository.averageBudgetPreview("2025-04", 3)
        assertEquals(listOf("power" to 633L), preview.changes.map { it.categoryId to it.proposedCents })
        assertEquals(1, preview.unchangedCount)
        repository.applyBudgetTemplate(preview)
        assertEquals(mapOf("power" to 633L), db.budgets(202504))

        // One category, including hidden and income ones: Old only has March; Salary is positive.
        repository.setCategoryAverage("old", "2025-04", 12)
        repository.setCategoryAverage("salary", "2025-04", 6)
        assertEquals(mapOf("old" to 1200L, "power" to 633L, "salary" to 300000L), db.budgets(202504))
    }

    @Test
    fun copiesOneCategoryFromLastMonthAndToYearEnd() = withRepository { repository, db ->
        // Upstream copySinglePreviousMonth and copyUntilYearEnd (#910).
        db.exec(
            """INSERT INTO zero_budgets (id, month, category, amount, carryover) VALUES
                ('202501-rent', 202501, 'rent', 1000, 0), ('202502-power', 202502, 'power', 500, 0),
                ('202510-rent', 202510, 'rent', 2500, 0), ('202512-rent', 202512, 'rent', 1, 0),
                ('202601-rent', 202601, 'rent', 7, 0)""",
        )

        repository.copyCategoryFromPreviousMonth("rent", "2025-02")
        // Power had no January row, so it is copied as 0.
        repository.copyCategoryFromPreviousMonth("power", "2025-02")
        assertEquals(mapOf("power" to 0L, "rent" to 1000L), db.budgets(202502))

        repository.copyCategoryToYearEnd("rent", "2025-10")
        assertEquals(mapOf("rent" to 2500L), db.budgets(202510))
        assertEquals(mapOf("rent" to 2500L), db.budgets(202511))
        assertEquals(mapOf("rent" to 2500L), db.budgets(202512))
        assertEquals(mapOf("rent" to 7L), db.budgets(202601))
    }

    @Test
    fun incomeHoldsAutomaticallyAndResetsForOneMonth() = withRepository { repository, db ->
        // Envelope income carryover ("automatically hold") and upstream resetIncomeCarryover (#910).
        repository.setCategoryCarryover("salary", true, "2025-05")
        assertEquals(true, repository.budgetGroups("2025-05").flatMap { it.categories }.first { it.id == "salary" }.carryoverEnabled)

        repository.resetIncomeHold("2025-06")

        assertEquals(1, db.carryover(202505, "salary"))
        assertEquals(0, db.carryover(202506, "salary"))
        assertEquals(1, db.carryover(202507, "salary"))
    }

    internal class Db(private val path: String) {
        fun exec(sql: String) = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL(sql) }

        fun budgets(month: Int): Map<String, Long> =
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT category, amount FROM zero_budgets WHERE month = ? ORDER BY category", arrayOf(month.toString())).use { c ->
                    buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
                }
            }

        fun carryover(month: Int, category: String): Int? =
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT carryover FROM zero_budgets WHERE month = ? AND category = ?", arrayOf(month.toString(), category))
                    .use { c -> if (c.moveToFirst()) c.getInt(0) else null }
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
