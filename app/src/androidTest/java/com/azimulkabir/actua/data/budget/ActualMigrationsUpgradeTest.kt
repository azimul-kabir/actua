package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * A budget last uploaded by a mid-2024 Actual is upgraded with Actual's own migrations, so its
 * `__migrations__` is again an ordered prefix Actual accepts (#719).
 */
class ActualMigrationsUpgradeTest {
    @Test fun olderBudgetGetsEveryActualMigrationInOrderAndActualsSchema() = withOldBudget { directory, file ->
        ActualBudgetDatabase.open(file).use { database ->
            assertEquals(listOf("acc"), database.fetchAccounts().map { it.id })
            assertEquals(setOf("cat-1", "cat-2"), database.fetchCategoryGroups().flatMap { it.categories }.map { it.id }.toSet())
            assertEquals(listOf("Main"), database.fetchDashboardPages().map { it.name })
        }

        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(BlankBudgetFactory.MIGRATIONS.toList(), longs(db, "SELECT id FROM __migrations__ ORDER BY id"))
            assertEquals(blankSchema(directory), schema(db))

            // 1723665565000 moved synced prefs out of metadata.json; 1745425408000 renamed 'report'.
            assertEquals("tracking", string(db, "SELECT value FROM preferences WHERE id = 'budgetType'"))
            assertEquals("MM/dd/yyyy", string(db, "SELECT value FROM preferences WHERE id = 'dateFormat'"))
            assertEquals("{}", string(db, "SELECT value FROM preferences WHERE id = 'csv-mappings-acc'"))
            assertNull(string(db, "SELECT value FROM preferences WHERE id = 'unrelatedKey'"))

            // 1722717601000 moved a partial category selection into conditions; 1736640000000 and
            // 1738491452000 mapped the sort order.
            val tableConditions = JSONArray(string(db, "SELECT conditions FROM custom_reports WHERE id = 'r-table'"))
            val category = tableConditions.getJSONObject(0)
            assertEquals("category", category.getString("field"))
            assertEquals("oneOf", category.getString("op"))
            assertEquals(listOf("cat-1"), category.getJSONArray("value").let { a -> (0 until a.length()).map(a::getString) })
            assertNull(string(db, "SELECT selected_categories FROM custom_reports WHERE id = 'r-table'"))
            assertNull(string(db, "SELECT conditions FROM custom_reports WHERE id = 'r-bar'"))
            assertEquals("budget", string(db, "SELECT sort_by FROM custom_reports WHERE id = 'r-table'"))
            assertEquals("desc", string(db, "SELECT sort_by FROM custom_reports WHERE id = 'r-bar'"))

            // 1722804019000 built a dashboard around the saved reports; 1765518577215 put it on Main.
            val pages = strings(db, "SELECT id FROM dashboard_pages WHERE tombstone = 0")
            assertEquals(listOf("Main"), strings(db, "SELECT name FROM dashboard_pages"))
            assertEquals(
                listOf("net-worth-card", "cash-flow-card", "spending-card", "custom-report", "custom-report"),
                strings(db, "SELECT type FROM dashboard ORDER BY rowid"),
            )
            assertEquals(listOf(pages.single()), strings(db, "SELECT DISTINCT dashboard_page_id FROM dashboard"))
            assertEquals(
                listOf("r-bar", "r-table"),
                strings(db, "SELECT meta FROM dashboard WHERE type = 'custom-report' ORDER BY rowid").map { JSONObject(it).getString("id") },
            )

            // Synced values stored while their column or table didn't exist are written in.
            assertEquals(5.5, db.rawQuery("SELECT sort_order FROM schedules WHERE id = 's1'", null).use { it.moveToFirst(); it.getDouble(0) }, 0.0)
            assertEquals("grp-1", string(db, "SELECT account_group_id FROM accounts WHERE id = 'acc'"))
            assertEquals("food", string(db, "SELECT tag FROM tags WHERE id = 't1'"))
            assertEquals(1L, longs(db, "SELECT hidden FROM tags WHERE id = 't1'").single())
        }

        // Opening again changes nothing.
        ActualBudgetDatabase.open(file).close()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(BlankBudgetFactory.MIGRATIONS.toList(), longs(db, "SELECT id FROM __migrations__ ORDER BY id"))
            assertEquals(1, strings(db, "SELECT id FROM dashboard_pages").size)
        }
    }

    @Test fun aFailingMigrationLeavesTheBudgetOpeningAsBefore() = withOldBudget { _, file ->
        // Without custom_reports, 1736640000000 cannot add its column; the whole upgrade rolls back,
        // including the migrations before it that had already run.
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("DROP TABLE custom_reports") }

        ActualBudgetDatabase.open(file).close()

        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val applied = longs(db, "SELECT id FROM __migrations__")
            assertFalse(1736640000000 in applied)
            assertFalse(1723665565000 in applied)
            assertFalse(1722804019000 in applied)
            assertFalse(hasTable(db, "preferences"))
            assertFalse(hasTable(db, "dashboard"))
        }
    }

    private fun withOldBudget(block: (File, File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "old-budget-${UUID.randomUUID()}").also { it.mkdirs() }
        val file = File(directory, "db.sqlite")
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                OLD_SCHEMA.forEach(db::execSQL)
                OLD_MIGRATIONS.forEach { db.execSQL("INSERT INTO __migrations__ (id) VALUES (?)", arrayOf(it)) }
                db.execSQL("INSERT INTO accounts (id, name, type, offbudget, closed, tombstone, sort_order) VALUES ('acc','Checking','checking',0,0,0,1)")
                db.execSQL("INSERT INTO category_groups (id, name, is_income, sort_order, tombstone, hidden) VALUES ('grp','Bills',0,1,0,0)")
                db.execSQL("INSERT INTO categories (id, name, is_income, cat_group, sort_order, tombstone, hidden) VALUES ('cat-1','Food',0,'grp',1,0,0), ('cat-2','Rent',0,'grp',2,0,0)")
                db.execSQL("INSERT INTO category_mapping VALUES ('cat-1','cat-1'), ('cat-2','cat-2')")
                db.execSQL(
                    "INSERT INTO custom_reports (id, name, graph_type, selected_categories, tombstone) VALUES (?,?,?,?,0), (?,?,?,?,0)",
                    arrayOf<Any?>(
                        "r-table", "Table", "TableGraph", """[{"id":"cat-1"}]""",
                        "r-bar", "Bars", "BarGraph", """[{"id":"cat-1"},{"id":"cat-2"}]""",
                    ),
                )
                db.execSQL("INSERT INTO schedules (id, rule, active, completed, posts_transaction, tombstone) VALUES ('s1', NULL, 1, 0, 0, 0)")
                listOf(
                    Triple("schedules", "s1", "sort_order" to "N:5.5"),
                    Triple("accounts", "acc", "account_group_id" to "S:grp-1"),
                    Triple("tags", "t1", "tag" to "S:food"),
                    Triple("tags", "t1", "hidden" to "N:1"),
                    Triple("tags", "t1", "tombstone" to "N:0"),
                ).forEachIndexed { index, (dataset, row, cell) ->
                    db.execSQL(
                        "INSERT INTO messages_crdt (timestamp, dataset, row, `column`, value) VALUES (?, ?, ?, ?, ?)",
                        arrayOf("2024-07-01T00:00:0$index.000Z-0000-0123456789abcdef", dataset, row, cell.first, cell.second),
                    )
                }
            }
            File(directory, "metadata.json").writeText(
                JSONObject().put("id", "old-budget").put("budgetType", "report").put("dateFormat", "MM/dd/yyyy")
                    .put("csv-mappings-acc", "{}").put("unrelatedKey", "x").toString(),
            )
            block(directory, file)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun blankSchema(directory: File): Map<String, Set<String>> {
        val blank = File(directory, "blank.sqlite").also(BlankBudgetFactory::create)
        return SQLiteDatabase.openDatabase(blank.path, null, SQLiteDatabase.OPEN_READONLY).use(::schema)
    }

    /** Table -> columns, plus every named index under "#indexes". */
    private fun schema(db: SQLiteDatabase): Map<String, Set<String>> {
        val tables = strings(db, "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")
        return tables.associateWith { table ->
            db.rawQuery("PRAGMA table_info(\"$table\")", null).use { cursor ->
                val name = cursor.getColumnIndexOrThrow("name")
                buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
            }
        } + ("#indexes" to strings(db, "SELECT name FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL").toSet())
    }

    private fun hasTable(db: SQLiteDatabase, table: String) =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { it.moveToFirst() }

    private fun string(db: SQLiteDatabase, sql: String): String? =
        db.rawQuery(sql, null).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }

    private fun strings(db: SQLiteDatabase, sql: String): List<String> =
        db.rawQuery(sql, null).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun longs(db: SQLiteDatabase, sql: String): List<Long> =
        db.rawQuery(sql, null).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }

    private companion object {
        /** Actual's schema after migration 1720665000000 (default-db.sqlite plus every migration up to it, at 59fe126f). */
        val OLD_SCHEMA = listOf(
            """CREATE TABLE __meta__ (key TEXT PRIMARY KEY, value TEXT)""",
            """CREATE TABLE __migrations__ (id INT PRIMARY KEY NOT NULL)""",
            """CREATE TABLE accounts
                   (id TEXT PRIMARY KEY,
                    account_id TEXT,
                    name TEXT,
                    balance_current INTEGER,
                    balance_available INTEGER,
                    balance_limit INTEGER,
                    mask TEXT,
                    official_name TEXT,
                    subtype TEXT,
                    bank TEXT,
                    offbudget INTEGER DEFAULT 0,
                    closed INTEGER DEFAULT 0,
                    tombstone INTEGER DEFAULT 0, sort_order REAL, type TEXT, account_sync_source TEXT)""",
            """CREATE TABLE banks
                 (id TEXT PRIMARY KEY,
                  bank_id TEXT,
                  name TEXT,
                  tombstone INTEGER DEFAULT 0)""",
            """CREATE TABLE categories
                 (id TEXT PRIMARY KEY,
                  name TEXT,
                  is_income INTEGER DEFAULT 0,
                  cat_group TEXT,
                  sort_order REAL,
                  tombstone INTEGER DEFAULT 0, hidden BOOLEAN NOT NULL DEFAULT 0, goal_def TEXT DEFAULT null)""",
            """CREATE TABLE category_groups
                   (id TEXT PRIMARY KEY,
                    name TEXT,
                    is_income INTEGER DEFAULT 0,
                    sort_order REAL,
                    tombstone INTEGER DEFAULT 0, hidden BOOLEAN NOT NULL DEFAULT 0)""",
            """CREATE TABLE category_mapping
                  (id TEXT PRIMARY KEY,
                   transferId TEXT)""",
            """CREATE TABLE created_budgets (month TEXT PRIMARY KEY)""",
            """CREATE TABLE custom_reports
                  (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    start_date TEXT,
                    end_date TEXT,
                    date_static INTEGER DEFAULT 0,
                    date_range TEXT,
                    mode TEXT DEFAULT 'total',
                    group_by TEXT DEFAULT 'Category',
                    balance_type TEXT DEFAULT 'Expense',
                    show_empty INTEGER DEFAULT 0,
                    show_offbudget INTEGER DEFAULT 0,
                    show_hidden INTEGER DEFAULT 0,
                    show_uncategorized INTEGER DEFAULT 0,
                    selected_categories TEXT,
                    graph_type TEXT DEFAULT 'BarGraph',
                    conditions TEXT,
                    conditions_op TEXT DEFAULT 'and',
                    metadata TEXT,
                    interval TEXT DEFAULT 'Monthly',
                    color_scheme TEXT,
                    tombstone INTEGER DEFAULT 0
                  , include_current INTEGER DEFAULT 0)""",
            """CREATE TABLE kvcache (key TEXT PRIMARY KEY, value TEXT)""",
            """CREATE TABLE kvcache_key (id INTEGER PRIMARY KEY, key REAL)""",
            """CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)""",
            """CREATE TABLE messages_crdt
                 (id INTEGER PRIMARY KEY,
                  timestamp TEXT NOT NULL UNIQUE,
                  dataset TEXT NOT NULL,
                  row TEXT NOT NULL,
                  column TEXT NOT NULL,
                  value BLOB NOT NULL)""",
            """CREATE TABLE notes (id TEXT PRIMARY KEY, note TEXT)""",
            """CREATE TABLE payee_mapping
                  (id TEXT PRIMARY KEY,
                   targetId TEXT)""",
            """CREATE TABLE payees
                  (id TEXT PRIMARY KEY,
                   name TEXT,
                   category TEXT,
                   tombstone INTEGER DEFAULT 0,
                   transfer_acct TEXT, favorite INTEGER DEFAULT 0 DEFAULT FALSE)""",
            """CREATE TABLE pending_transactions
                  (id TEXT PRIMARY KEY,
                   acct INTEGER,
                   amount INTEGER,
                   description TEXT,
                   date TEXT,
                   FOREIGN KEY(acct) REFERENCES accounts(id))""",
            """CREATE TABLE reflect_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER DEFAULT 0, carryover INTEGER DEFAULT 0, goal INTEGER DEFAULT null, long_goal INTEGER DEFAULT null)""",
            """CREATE TABLE rules
                  (id TEXT PRIMARY KEY,
                   stage TEXT,
                   conditions TEXT,
                   actions TEXT,
                   tombstone INTEGER DEFAULT 0, conditions_op TEXT DEFAULT 'and')""",
            """CREATE TABLE schedules
                  (id TEXT PRIMARY KEY,
                   rule TEXT,
                   active INTEGER DEFAULT 0,
                   completed INTEGER DEFAULT 0,
                   posts_transaction INTEGER DEFAULT 0,
                   tombstone INTEGER DEFAULT 0, name TEXT DEFAULT NULL)""",
            """CREATE TABLE schedules_json_paths
                  (schedule_id TEXT PRIMARY KEY,
                   payee TEXT,
                   account TEXT,
                   amount TEXT,
                   date TEXT)""",
            """CREATE TABLE schedules_next_date
                  (id TEXT PRIMARY KEY,
                   schedule_id TEXT,
                   local_next_date INTEGER,
                   local_next_date_ts INTEGER,
                   base_next_date INTEGER,
                   base_next_date_ts INTEGER, tombstone INTEGER DEFAULT 0)""",
            """CREATE TABLE transaction_filters
                  (id TEXT PRIMARY KEY,
                   name TEXT,
                   conditions TEXT,
                   conditions_op TEXT DEFAULT 'and',
                   tombstone INTEGER DEFAULT 0)""",
            """CREATE TABLE transactions
                  (id TEXT PRIMARY KEY,
                   isParent INTEGER DEFAULT 0,
                   isChild INTEGER DEFAULT 0,
                   acct TEXT,
                   category TEXT,
                   amount INTEGER,
                   description TEXT,
                   notes TEXT,
                   date INTEGER,
                   financial_id TEXT,
                   type TEXT,
                   location TEXT,
                   error TEXT,
                   imported_description TEXT,
                   starting_balance_flag INTEGER DEFAULT 0,
                   transferred_id TEXT,
                   sort_order REAL,
                   tombstone INTEGER DEFAULT 0, cleared INTEGER DEFAULT 1, pending INTEGER DEFAULT 0, parent_id TEXT, schedule TEXT, reconciled INTEGER DEFAULT 0)""",
            """CREATE TABLE zero_budget_months (id TEXT PRIMARY KEY, buffered INTEGER DEFAULT 0)""",
            """CREATE TABLE zero_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER DEFAULT 0, carryover INTEGER DEFAULT 0, goal INTEGER DEFAULT null, long_goal INTEGER DEFAULT null)""",
            """CREATE INDEX messages_crdt_search ON messages_crdt(dataset, row, column, timestamp)""",
            """CREATE INDEX trans_category ON transactions(category)""",
            """CREATE INDEX trans_category_date ON transactions(category, date)""",
            """CREATE INDEX trans_date ON transactions(date)""",
            """CREATE INDEX trans_parent_id ON transactions(parent_id)""",
            """CREATE INDEX trans_sorted ON transactions(date desc, starting_balance_flag, sort_order desc, id)""",
        )
        val OLD_MIGRATIONS = listOf(1548957970627L, 1550601598648L, 1555786194328L, 1561751833510L, 1567699552727L, 1582384163573L, 1597756566448L, 1608652596043L, 1608652596044L, 1612625548236L, 1614782639336L, 1615745967948L, 1616167010796L, 1618975177358L, 1632571489012L, 1679728867040L, 1681115033845L, 1682974838138L, 1685007876842L, 1686139660866L, 1688749527273L, 1688841238000L, 1691233396000L, 1694438752000L, 1697046240000L, 1704572023730L, 1704572023731L, 1707267033000L, 1712784523000L, 1716359441000L, 1720310586000L, 1720664867241L, 1720665000000L)
    }
}
