package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * #667: a synthetic multi-month budget run through Actual's own budget spreadsheet
 * (`docs/tools/budget-fixture/generate.mjs`, @actual-app/api 26.9.0) and replayed through
 * [ActualBudgetDatabase.fetchBudgetMonth]. It covers overspending with and without rollover, a
 * manual hold and its reset, an income category held automatically, hidden categories and groups,
 * a split, an off-budget account, a deleted transaction and a category deleted into another one.
 * Differences fail unless [KNOWN_DIVERGENCES] lists them with their issue.
 */
class BudgetMonthParityFixtureTest {
    @Test fun envelopeMonthsMatchActual() = assertMatches("envelope")

    @Test fun trackingMonthsMatchActual() = assertMatches("tracking")

    private fun assertMatches(kind: String) {
        val fixture = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("budget-parity/upstream-26.9.0.json").bufferedReader().use { it.readText() },
        )
        val budget = fixture.getJSONObject(kind)
        val file = createDatabase(budget.getJSONObject("rows"))
        val mismatches = mutableListOf<String>()
        try {
            ActualBudgetDatabase.open(file).use { database ->
                val expected = budget.getJSONObject("expected")
                expected.keys().asSequence().sorted().forEach { month ->
                    val cells = expected.getJSONObject(month)
                    val actual = database.fetchBudgetMonth(month)
                    fun check(label: String, upstream: Any?, actua: Any?) {
                        if (upstream != actua) mismatches += "$kind $month $label: upstream $upstream, actua $actua"
                    }
                    if (kind == "envelope") {
                        check("to-budget", cells.getLong("toBudget"), actual.toBudgetCents)
                        check("buffered", cells.getLong("forNextMonth"), actual.bufferedCents)
                    } else {
                        check("is tracking", true, actual.isTracking)
                    }
                    val expenses = (actual.categories + actual.hiddenCategories).associateBy { it.categoryId }
                    val incomes = (actual.incomeCategories + actual.hiddenIncomeCategories).associateBy { it.categoryId }
                    val groups = cells.getJSONArray("groups")
                    for (g in 0 until groups.length()) {
                        val group = groups.getJSONObject(g)
                        val categories = group.getJSONArray("categories")
                        for (c in 0 until categories.length()) {
                            val category = categories.getJSONObject(c)
                            val id = category.getString("id")
                            if (group.getBoolean("is_income")) {
                                val row = incomes[id]
                                check("$id received", category.getLong("received"), row?.receivedCents)
                                if (kind == "tracking") check("$id budgeted", category.getLong("budgeted"), row?.budgetedCents)
                            } else {
                                val row = expenses[id]
                                check("$id budgeted", category.getLong("budgeted"), row?.budgetedCents)
                                check("$id spent", category.getLong("spent"), row?.spentCents)
                                check("$id balance", category.getLong("balance"), row?.availableCents)
                                check("$id carryover", category.getBoolean("carryover"), row?.carryoverEnabled)
                            }
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
        assertEquals(KNOWN_DIVERGENCES.filter { it.startsWith("$kind ") }, mismatches)
    }

    /** The fixture's rows in tables shaped like Actual's; ActualBudgetDatabase.open migrates the rest. */
    private fun createDatabase(rows: JSONObject): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "budget-parity-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            SCHEMA.forEach(db::execSQL)
            rows.keys().forEach { table ->
                val list = rows.getJSONArray(table)
                for (index in 0 until list.length()) insert(db, table, list.getJSONObject(index))
            }
        }
        return file
    }

    private fun insert(db: SQLiteDatabase, table: String, row: JSONObject) {
        val columns = row.keys().asSequence().toList()
        val values = columns.map { column -> row.opt(column).takeUnless { it == JSONObject.NULL } }
        db.execSQL(
            "INSERT INTO $table (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})",
            values.map { if (it is JSONArray || it is JSONObject) it.toString() else it }.toTypedArray(),
        )
    }

    private companion object {
        /** "<kind> <month> <cell>: upstream …, actua …" lines for known, filed divergences. */
        val KNOWN_DIVERGENCES = emptyList<String>()

        val SCHEMA = listOf(
            "CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, type TEXT, offbudget INTEGER, closed INTEGER, tombstone INTEGER, sort_order REAL)",
            "CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)",
            "CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, cat_group TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)",
            "CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)",
            "CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, transfer_acct TEXT, tombstone INTEGER)",
            "CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT)",
            "CREATE TABLE transactions (id TEXT PRIMARY KEY, isParent INTEGER, isChild INTEGER, acct TEXT, category TEXT, amount INTEGER, description TEXT, notes TEXT, date INTEGER, imported_description TEXT, transferred_id TEXT, cleared INTEGER, reconciled INTEGER, sort_order REAL, tombstone INTEGER, parent_id TEXT, financial_id TEXT, pending INTEGER DEFAULT 0, raw_synced_data TEXT)",
            "CREATE TABLE zero_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER, carryover INTEGER)",
            "CREATE TABLE reflect_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER, carryover INTEGER)",
            "CREATE TABLE zero_budget_months (id TEXT PRIMARY KEY, buffered INTEGER)",
            "CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)",
            "CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)",
            "CREATE TABLE preferences (id TEXT PRIMARY KEY, value TEXT)",
            "CREATE TABLE notes (id TEXT PRIMARY KEY, note TEXT)",
            "CREATE TABLE dashboard_pages (id TEXT PRIMARY KEY, name TEXT, tombstone INTEGER DEFAULT 0)",
            "CREATE TABLE dashboard (id TEXT PRIMARY KEY, type TEXT, x INTEGER, y INTEGER, meta TEXT, tombstone INTEGER DEFAULT 0, dashboard_page_id TEXT)",
            "CREATE TABLE rules (id TEXT PRIMARY KEY, stage TEXT, conditions_op TEXT, conditions TEXT, actions TEXT, tombstone INTEGER)",
            "CREATE TABLE schedules (id TEXT PRIMARY KEY, rule TEXT, name TEXT, posts_transaction INTEGER, completed INTEGER, custom_upcoming_length TEXT, tombstone INTEGER)",
            "CREATE TABLE schedules_next_date (id TEXT PRIMARY KEY, schedule_id TEXT, local_next_date INTEGER, local_next_date_ts INTEGER, base_next_date INTEGER, base_next_date_ts INTEGER)",
        )
    }
}
