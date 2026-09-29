package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Brings a budget last uploaded by an older Actual up to the migrations Actua knows, the way Actual's
 * `migrate()` does (`packages/loot-core/src/server/migrate/migrations.ts` at 59fe126f): every missing
 * migration runs in id order and is then recorded, so `__migrations__` stays an ordered prefix of
 * Actual's list and Actual accepts the file (#719). SQL migrations are verbatim copies of the upstream
 * files in `resources/actual-migrations`, updated only by copying them from a newly audited Actual
 * version; the JavaScript migrations in range are ported below.
 *
 * Statements that would only fail because Actua already created the same table, column or index for
 * its own reads are skipped. Actua creates those with upstream's exact definitions.
 */
internal object ActualMigrations {
    /**
     * Actual's JavaScript cache migration. Budgets without it predate tables Actua requires, so they
     * are never upgraded here.
     */
    const val OLDEST_BASE = 1632571489012L
    private const val BAD_FILTERS_MIGRATION = 1685375406832L
    private const val FILTERS_MIGRATION = 1688749527273L
    private const val REMOVE_ACCOUNT_TYPE = 1686139660866L
    private const val SORTING_RENAME = 1738491452000L
    private const val MOVE_SELECTED_CATEGORIES = 1722717601000L
    private const val CREATE_DASHBOARD = 1722804019000L
    private const val PREFS = 1723665565000L
    private const val MULTIPLE_DASHBOARDS = 1765518577215L

    private val SQL_FILES = listOf(
        "1679728867040_rules_conditions.sql",
        "1681115033845_add_schedule_name.sql",
        "1682974838138_remove_payee_rules.sql",
        "1685007876842_add_category_hidden.sql",
        "1686139660866_remove_account_type.sql",
        "1688749527273_transaction_filters.sql",
        "1688841238000_add_account_type.sql",
        "1691233396000_add_schedule_next_date_tombstone.sql",
        "1694438752000_add_goal_targets.sql",
        "1697046240000_add_reconciled.sql",
        "1704572023730_add_account_sync_source.sql",
        "1704572023731_add_missing_goCardless_sync_source.sql",
        "1707267033000_reports.sql",
        "1712784523000_unhide_input_group.sql",
        "1716359441000_include_current.sql",
        "1720310586000_link_transfer_schedules.sql",
        "1720664867241_add_payee_favorite.sql",
        "1720665000000_goal_context.sql",
        "1730744182000_fix_dashboard_table.sql",
        "1736640000000_custom_report_sorting.sql",
        "1737158400000_add_learn_categories_to_payees.sql",
        "1738491452000_sorting_rename.sql",
        "1739139550000_bank_sync_page.sql",
        "1740506588539_add_last_reconciled_at.sql",
        "1745425408000_update_budgetType_pref.sql",
        "1749799110000_add_tags.sql",
        "1749799110001_tags_tombstone.sql",
        "1754611200000_add_category_template_settings.sql",
        "1759260219000_add_trim_interval_report_setting.sql",
        "1759842823172_add_isGlobal_to_preferences.sql",
        "1762178745667_rename_csv_skip_lines_pref.sql",
        "1768872504000_add_payee_locations.sql",
        "1769000000000_add_custom_upcoming_length.sql",
        "1778510362740_add_cleanup_groups_and_def.sql",
        "1780099200000_add_show_trend_lines_report_setting.sql",
        "1780327681000_add_tags_hidden.sql",
        "1780606215000_add_bank_sync_status.sql",
        "1780606215001_add_performance_indexes.sql",
        "1783004650757_schedule_sort_order.sql",
        "1787013118115_add_account_groups.sql",
    )
    private val sqlFileById: Map<Long, String> = SQL_FILES.associateBy { it.substringBefore('_').toLong() }
    private val PORTED = setOf(
        MOVE_SELECTED_CATEGORIES, CREATE_DASHBOARD, PREFS, MULTIPLE_DASHBOARDS, REMOVE_ACCOUNT_TYPE, SORTING_RENAME,
    )

    /** Whether [id] has a bundled SQL file or a port here. */
    fun supports(id: Long): Boolean = id in sqlFileById || id in PORTED

    /** The bundled upstream SQL for [id], or null when it is ported or unknown. */
    fun sql(id: Long): String? = sqlFileById[id]?.let(::sqlResource)

    /**
     * Runs every pending Actual migration in one transaction and replays stored sync values into the
     * columns and tables they add. If anything fails, nothing changes and the budget opens as before.
     */
    fun upgrade(database: SQLiteDatabase, metadata: File?) {
        if (!database.hasTable("__migrations__")) return
        database.beginTransaction()
        try {
            patchBadMigrations(database)
            val applied = database.rawQuery("SELECT id FROM __migrations__", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) }
            }
            val pending = pending(applied)
            if (pending.isNotEmpty()) {
                val before = StoredMessageReplay.schema(database)
                pending.forEach { apply(database, it, metadata) }
                StoredMessageReplay.replay(database, before)
            }
            database.setTransactionSuccessful()
        } catch (error: Exception) {
            Log.w("ActualMigrations", "Could not apply Actual's migrations (${error.javaClass.simpleName})")
        } finally {
            database.endTransaction()
        }
    }

    /** Known upstream ids the database lacks, in order; empty when it can't or needn't be upgraded. */
    fun pending(applied: Set<Long>): List<Long> {
        val known = BlankBudgetFactory.MIGRATIONS
        if (known.any { it <= OLDEST_BASE && it !in applied }) return emptyList()
        return known.filter { it > OLDEST_BASE && it !in applied }
    }

    /** Actual's `patchBadMigrations`: a withdrawn filters migration is recorded as its replacement. */
    fun patchBadMigrations(database: SQLiteDatabase) {
        val bad = database.rawQuery(
            "SELECT 1 FROM __migrations__ WHERE id = ?", arrayOf(BAD_FILTERS_MIGRATION.toString()),
        ).use { it.moveToFirst() }
        if (!bad) return
        database.execSQL("DELETE FROM __migrations__ WHERE id = ?", arrayOf(BAD_FILTERS_MIGRATION))
        database.execSQL("INSERT OR IGNORE INTO __migrations__ (id) VALUES (?)", arrayOf(FILTERS_MIGRATION))
    }

    /** Applies [id] and records it. Runs inside the caller's transaction. */
    fun apply(database: SQLiteDatabase, id: Long, metadata: File?) {
        when (id) {
            MOVE_SELECTED_CATEGORIES -> moveSelectedCategories(database)
            CREATE_DASHBOARD -> createDashboardTable(database)
            PREFS -> createPreferences(database, metadata)
            MULTIPLE_DASHBOARDS -> multipleDashboards(database)
            // Android 9-13 SQLite (3.22-3.32) lacks DROP COLUMN (3.35) and Android 9 lacks RENAME
            // COLUMN (3.25), so these two keep the column and apply the same data change instead.
            REMOVE_ACCOUNT_TYPE -> {
                // Upstream drops accounts.type; 1688841238000 re-adds it empty.
                if (database.hasColumn("accounts", "type")) database.execSQL("UPDATE accounts SET type = NULL")
            }
            SORTING_RENAME -> {
                // Upstream renames sort_by, re-adds it defaulting to 'desc' and maps the old values.
                if (database.hasColumn("custom_reports", "sort_by")) database.execSQL(
                    """UPDATE custom_reports SET sort_by = CASE sort_by
                        WHEN 'Descending' THEN 'desc' WHEN 'Ascending' THEN 'asc'
                        WHEN 'Budget' THEN 'budget' WHEN 'Name' THEN 'name' ELSE 'desc' END""",
                )
            }
            else -> statements(sql(id) ?: error("No Actual migration $id")).forEach { execute(database, it) }
        }
        database.execSQL("INSERT OR IGNORE INTO __migrations__ (id) VALUES (?)", arrayOf(id))
    }

    /** Splits a migration file into statements, dropping comments and its own BEGIN/COMMIT. */
    fun statements(sql: String): List<String> = sql.lines()
        .joinToString("\n") { line -> line.substringBefore("--") }
        .split(';')
        .map(String::trim)
        .filter { it.isNotEmpty() && !TRANSACTION_CONTROL.matches(it) }

    /** What [execute] does with a statement, given what already exists. */
    fun shouldRun(
        statement: String,
        hasTable: (String) -> Boolean,
        hasColumn: (String, String) -> Boolean,
        hasIndex: (String) -> Boolean,
    ): Boolean {
        ADD_COLUMN.find(statement)?.let { return !hasColumn(it.groupValues[1], it.groupValues[2]) }
        CREATE_TABLE.find(statement)?.let { return it.groupValues[1].isNotEmpty() || !hasTable(it.groupValues[2]) }
        CREATE_INDEX.find(statement)?.let { return it.groupValues[1].isNotEmpty() || !hasIndex(it.groupValues[2]) }
        DROP_TABLE.find(statement)?.let { return it.groupValues[1].isNotEmpty() || hasTable(it.groupValues[2]) }
        return true
    }

    private fun execute(database: SQLiteDatabase, statement: String) {
        if (shouldRun(statement, database::hasTable, database::hasColumn, database::hasIndex)) database.execSQL(statement)
    }

    private fun sqlResource(name: String): String =
        requireNotNull(ActualMigrations::class.java.classLoader?.getResourceAsStream("actual-migrations/$name")) {
            "Missing bundled Actual migration $name"
        }.use { it.readBytes().decodeToString() }

    // 1722717601000_reports_move_selected_categories.js
    private fun moveSelectedCategories(database: SQLiteDatabase) {
        if (!database.hasTable("custom_reports") || !database.hasColumn("custom_reports", "selected_categories")) return
        val categories = database.rawQuery("SELECT id FROM categories WHERE tombstone = 0", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        data class Report(val id: String, val selected: String?, val conditions: String?)
        val reports = database.rawQuery(
            """SELECT id, selected_categories, conditions FROM custom_reports
                WHERE tombstone = 0 AND selected_categories IS NOT NULL""", null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(Report(cursor.getString(0), cursor.getString(1), cursor.getString(2))) }
        }
        reports.forEach { report ->
            // Like upstream's `x ? JSON.parse(x) : []`, an empty string counts as none.
            val conditions = report.conditions?.takeIf(String::isNotEmpty)?.let { JSONArray(it) } ?: JSONArray()
            val selected = report.selected?.takeIf(String::isNotEmpty)?.let { JSONArray(it) } ?: JSONArray()
            val selectedIds = (0 until selected.length()).map { selected.getJSONObject(it).optString("id") }
            if (categories.all { it in selectedIds }) return@forEach
            if ((0 until conditions.length()).any { conditions.optJSONObject(it)?.optString("field") == "category" }) return@forEach
            conditions.put(JSONObject()
                .put("field", "category").put("op", "oneOf")
                .put("value", JSONArray(selectedIds)).put("type", "id"))
            database.execSQL("UPDATE custom_reports SET conditions = ? WHERE id = ?", arrayOf(conditions.toString(), report.id))
        }
        database.execSQL("UPDATE custom_reports SET selected_categories = NULL WHERE tombstone = 0")
    }

    // 1722804019000_create_dashboard_table.js
    private fun createDashboardTable(database: SQLiteDatabase) {
        if (database.hasTable("dashboard")) return
        val reports = if (database.hasTable("custom_reports")) database.rawQuery(
            "SELECT id FROM custom_reports WHERE tombstone = 0 ORDER BY name COLLATE NOCASE ASC", null,
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } } else emptyList()
        database.execSQL(
            """CREATE TABLE dashboard
                (id TEXT PRIMARY KEY,
                 type TEXT,
                 width INTEGER,
                 height INTEGER,
                 x INTEGER,
                 y INTEGER,
                 meta TEXT,
                 tombstone INTEGER DEFAULT 0)""",
        )
        fun insert(type: String, width: Int, height: Int, x: Int, y: Int, meta: String?) = database.execSQL(
            "INSERT INTO dashboard (id, type, width, height, x, y, meta) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(UUID.randomUUID().toString(), type, width, height, x, y, meta),
        )
        if (reports.isEmpty()) {
            BlankBudgetFactory.DEFAULT_DASHBOARD.forEach { insert(it.type, it.width, it.height, it.x, it.y, it.meta) }
            return
        }
        insert("net-worth-card", 8, 2, 0, 0, null)
        insert("cash-flow-card", 4, 2, 8, 0, null)
        insert("spending-card", 4, 2, 0, 2, null)
        reports.forEachIndexed { index, reportId ->
            insert("custom-report", 4, 2, (index * 4) % 12, 2 + index / 3 * 2, JSONObject().put("id", reportId).toString())
        }
    }

    // 1723665565000_prefs.js: synced preferences move from metadata.json into the budget.
    private fun createPreferences(database: SQLiteDatabase, metadata: File?) {
        if (!database.hasTable("preferences")) {
            database.execSQL("CREATE TABLE preferences (id TEXT PRIMARY KEY, value TEXT)")
        }
        val prefs = runCatching { JSONObject(requireNotNull(metadata).readText()) }.getOrNull() ?: return
        prefs.keys().forEach { key ->
            if (SYNCED_PREF_KEYS.none { it.matches(key) }) return@forEach
            val value = prefs.opt(key)?.takeIf { it != JSONObject.NULL } ?: return@forEach
            database.execSQL("INSERT OR IGNORE INTO preferences (id, value) VALUES (?, ?)", arrayOf(key, value.toString()))
        }
    }

    // 1765518577215_multiple_dashboards.js
    private fun multipleDashboards(database: SQLiteDatabase) {
        if (!database.hasTable("dashboard_pages")) {
            database.execSQL("CREATE TABLE dashboard_pages (id TEXT PRIMARY KEY, name TEXT, tombstone INTEGER DEFAULT 0)")
        }
        if (!database.hasTable("dashboard")) return
        if (!database.hasColumn("dashboard", "dashboard_page_id")) {
            database.execSQL("ALTER TABLE dashboard ADD COLUMN dashboard_page_id TEXT")
        }
        val pageId = UUID.randomUUID().toString()
        database.execSQL("INSERT INTO dashboard_pages (id, name) VALUES (?, ?)", arrayOf(pageId, "Main"))
        database.execSQL("UPDATE dashboard SET dashboard_page_id = ?", arrayOf(pageId))
    }

    private val SYNCED_PREF_KEYS = listOf(
        "firstDayOfWeekIdx", "dateFormat", "numberFormat", "hideFraction", "isPrivacyEnabled", "budgetType",
    ).map { Regex(Regex.escape(it)) } + listOf(
        "^show-extra-balances-", "^hide-cleared-", "^parse-date-", "^csv-mappings-", "^csv-delimiter-",
        "^csv-has-header-", "^ofx-fallback-missing-payee-", "^flip-amount-", "^flags\\.",
    ).map { Regex("$it.*") }

    private val TRANSACTION_CONTROL = Regex("^(BEGIN( TRANSACTION)?|COMMIT|END)$", RegexOption.IGNORE_CASE)
    private val ADD_COLUMN = Regex("^ALTER\\s+TABLE\\s+(\\w+)\\s+ADD\\s+(?:COLUMN\\s+)?(\\w+)", RegexOption.IGNORE_CASE)
    private val CREATE_TABLE = Regex("^CREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)", RegexOption.IGNORE_CASE)
    private val CREATE_INDEX = Regex("^CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+(IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)", RegexOption.IGNORE_CASE)
    private val DROP_TABLE = Regex("^DROP\\s+TABLE\\s+(IF\\s+EXISTS\\s+)?(\\w+)", RegexOption.IGNORE_CASE)

    private fun SQLiteDatabase.hasTable(table: String): Boolean = rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table),
    ).use { it.moveToFirst() }

    private fun SQLiteDatabase.hasIndex(index: String): Boolean = rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(index),
    ).use { it.moveToFirst() }

    private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean =
        rawQuery("PRAGMA table_info(\"${table.replace("\"", "\"\"")}\")", null).use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(name) else null }
                .any { it.equals(column, ignoreCase = true) }
        }
}
