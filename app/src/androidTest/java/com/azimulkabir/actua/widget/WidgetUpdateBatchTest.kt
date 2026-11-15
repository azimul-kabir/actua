package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.sync.MerkleNode
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.preferences.FavoritePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class WidgetUpdateBatchTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun quickTransactionAndEmptyPassesNeverOpenTheBudget() {
        val manager = AppWidgetManager.getInstance(context)
        WidgetUpdater.updateBatch(context, manager, emptyList()) { error("No widget needs a budget") }
        WidgetUpdater.updateBatch(
            context, manager, listOf(QuickTransactionWidgetProvider() to intArrayOf(-159805)),
        ) { error("Quick transaction only opens the app") }
    }

    @Test fun reportReadsKeepTheRepositoryBudgetWhenTheSelectionChanges() {
        val files = BudgetFileManager(context)
        val active = ActiveBudgetStore(context)
        val previous = active.budgetId
        val first = files.createBudget("First report ${UUID.randomUUID()}")
        val second = files.createBudget("Second report ${UUID.randomUUID()}")
        var repository: ActuaRepository? = null
        try {
            for ((budget, name) in listOf(first to "First account", second to "Second account")) {
                SQLiteDatabase.openDatabase(files.databaseFile(budget.id).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                    it.execSQL("INSERT INTO accounts (id, name, offbudget, closed, tombstone) VALUES ('checking', ?, 0, 0, 0)", arrayOf(name))
                }
                ActualBudgetDatabase.open(files.databaseFile(budget.id)).use {
                    it.saveClock(ActualBudgetDatabase.ClockRecord("2026-11-15T00:00:00.000Z-0000-aaaaaaaaaaaaaaaa", MerkleNode()))
                }
            }
            active.budgetId = first.id
            repository = ActuaRepository(context)
            active.budgetId = second.id
            assertEquals(listOf("First account"), repository.reportDataForWidgets()?.accounts?.map { it.name })
            val other = ActuaRepository(context)
            try {
                assertEquals(listOf("Second account"), other.reportDataForWidgets()?.accounts?.map { it.name })
            } finally { other.close() }
        } finally {
            repository?.close()
            active.budgetId = previous
            files.deleteBudget(first.id)
            files.deleteBudget(second.id)
        }
    }

    @Test fun onePassSharesItsRepositoryAndPreparesRowsForTheFactories() {
        val files = BudgetFileManager(context)
        val active = ActiveBudgetStore(context)
        val previous = active.budgetId
        val budget = files.createBudget("Widget batch ${UUID.randomUUID()}")
        val ids = intArrayOf(-159801, -159802, -159803, -159804)
        val file = files.databaseFile(budget.id)
        val aside = File(file.parentFile, "db.aside.sqlite")
        try {
            active.budgetId = budget.id
            val prime = ActuaRepository(context)
            val category = try {
                prime.budgetGroups().first { !it.isIncome && !it.hidden }.categories.first()
            } finally { prime.close() }
            FavoritePreferences(context).replace(budget.id, FavoritePreferences.Type.CATEGORY, setOf(requireNotNull(category.id)))
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("INSERT INTO accounts (id, name, offbudget, closed, tombstone) VALUES ('checking', 'Checking', 0, 0, 0)")
                // A second full probe would reject this date. These widgets need balances and budget
                // rows, so they must not read and decode the entire report transaction history.
                it.execSQL("INSERT INTO transactions (id, acct, amount, date, tombstone) VALUES ('bad-date', 'checking', -500, 20241340, 0)")
            }
            var opens = 0
            WidgetUpdater.updateBatch(
                context, AppWidgetManager.getInstance(context),
                listOf(
                    FavouriteCategoriesWidgetProvider() to intArrayOf(ids[0], ids[1]),
                    AccountBalancesWidgetProvider() to intArrayOf(ids[2]),
                    ScheduledTransactionsWidgetProvider() to intArrayOf(ids[3]),
                ),
            ) {
                opens++
                ActuaRepository(it)
            }
            assertEquals(1, opens)
            assertEquals(listOf(category.name), WidgetListSnapshots.rows(context, ids[0])?.map { it.name })
            assertEquals(listOf("Checking"), WidgetListSnapshots.rows(context, ids[2])?.map { it.name })

            // A launcher may request its rows after the update's connection has closed. Prove the
            // factories consume the prepared rows without reopening the (temporarily absent) file.
            assertTrue(file.renameTo(aside))
            val categories = WidgetListRows(context, ids[0], WidgetKind.Categories)
            val accounts = WidgetListRows(context, ids[2], WidgetKind.Accounts)
            val schedules = ScheduleWidgetRows(context, ids[3])
            categories.onDataSetChanged()
            accounts.onDataSetChanged()
            schedules.onDataSetChanged()
            assertEquals(1, categories.count)
            assertEquals(1, accounts.count)
            assertEquals(0, schedules.count)
            assertEquals(budget.id, active.budgetId)

            // Rows from the previous budget must never be handed to the new budget's factories.
            active.budgetId = "another-budget"
            assertNull(WidgetListSnapshots.rows(context, ids[0]))
            assertNull(WidgetListSnapshots.schedules(context, ids[3]))
        } finally {
            if (aside.exists()) check(aside.renameTo(file))
            ids.forEach(WidgetListSnapshots::remove)
            FavoritePreferences(context).replace(budget.id, FavoritePreferences.Type.CATEGORY, emptySet())
            active.budgetId = previous
            files.deleteBudget(budget.id)
        }
    }
}
