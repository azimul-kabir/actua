package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Builds the same empty Actual schema and starter categories as Actuali's bundled template. */
internal object BlankBudgetFactory {
    fun create(destination: File) {
        destination.delete()
        val database = SQLiteDatabase.openOrCreateDatabase(destination, null)
        try {
            database.beginTransaction()
            SCHEMA.forEach(database::execSQL)
            MIGRATIONS.forEach { database.execSQL("INSERT INTO __migrations__ (id) VALUES (?)", arrayOf(it)) }
            STARTER_DATA.forEach(database::execSQL)
            database.setTransactionSuccessful()
        } finally {
            if (database.inTransaction()) database.endTransaction()
            database.close()
        }
    }

    private val SCHEMA = listOf(
        "CREATE TABLE __meta__ (key TEXT PRIMARY KEY, value TEXT)",
        "CREATE TABLE __migrations__ (id INT PRIMARY KEY NOT NULL)",
        "CREATE TABLE accounts (id TEXT PRIMARY KEY, account_id TEXT, name TEXT, balance_current INTEGER, balance_available INTEGER, balance_limit INTEGER, mask TEXT, official_name TEXT, subtype TEXT, bank TEXT, offbudget INTEGER DEFAULT 0, closed INTEGER DEFAULT 0, tombstone INTEGER DEFAULT 0, sort_order REAL, type TEXT, account_sync_source TEXT, last_sync TEXT, last_reconciled TEXT, bank_sync_status TEXT)",
        "CREATE TABLE banks (id TEXT PRIMARY KEY, bank_id TEXT, name TEXT, tombstone INTEGER DEFAULT 0)",
        "CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER DEFAULT 0, cat_group TEXT, sort_order REAL, tombstone INTEGER DEFAULT 0, hidden BOOLEAN NOT NULL DEFAULT 0, goal_def TEXT DEFAULT NULL, template_settings JSON DEFAULT '{\"source\": \"notes\"}', cleanup_def TEXT DEFAULT NULL)",
        "CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER DEFAULT 0, sort_order REAL, tombstone INTEGER DEFAULT 0, hidden BOOLEAN NOT NULL DEFAULT 0)",
        "CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)",
        "CREATE TABLE cleanup_groups (id TEXT PRIMARY KEY, name TEXT NOT NULL, tombstone INTEGER DEFAULT 0)",
        "CREATE TABLE created_budgets (month TEXT PRIMARY KEY)",
        "CREATE TABLE custom_reports (id TEXT PRIMARY KEY, name TEXT, start_date TEXT, end_date TEXT, date_static INTEGER DEFAULT 0, date_range TEXT, mode TEXT DEFAULT 'total', group_by TEXT DEFAULT 'Category', balance_type TEXT DEFAULT 'Expense', show_empty INTEGER DEFAULT 0, show_offbudget INTEGER DEFAULT 0, show_hidden INTEGER DEFAULT 0, show_uncategorized INTEGER DEFAULT 0, selected_categories TEXT, graph_type TEXT DEFAULT 'BarGraph', conditions TEXT, conditions_op TEXT DEFAULT 'and', metadata TEXT, interval TEXT DEFAULT 'Monthly', color_scheme TEXT, tombstone INTEGER DEFAULT 0, include_current INTEGER DEFAULT 0, sort_by TEXT DEFAULT 'desc', trim_intervals INTEGER DEFAULT 0, show_trend_lines INTEGER DEFAULT 0)",
        "CREATE TABLE dashboard (id TEXT PRIMARY KEY, type TEXT, width INTEGER, height INTEGER, x INTEGER, y INTEGER, meta TEXT, tombstone INTEGER DEFAULT 0, dashboard_page_id TEXT)",
        "CREATE TABLE dashboard_pages (id TEXT PRIMARY KEY, name TEXT, tombstone INTEGER DEFAULT 0)",
        "CREATE TABLE kvcache (key TEXT PRIMARY KEY, value TEXT)",
        "CREATE TABLE kvcache_key (id INTEGER PRIMARY KEY, key REAL)",
        "CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)",
        "CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)",
        "CREATE TABLE notes (id TEXT PRIMARY KEY, note TEXT)",
        "CREATE TABLE payee_locations (id TEXT PRIMARY KEY, payee_id TEXT, latitude REAL, longitude REAL, created_at INTEGER, tombstone INTEGER DEFAULT 0)",
        "CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT)",
        "CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, category TEXT, tombstone INTEGER DEFAULT 0, transfer_acct TEXT, favorite INTEGER DEFAULT 0, learn_categories BOOLEAN DEFAULT 1)",
        "CREATE TABLE pending_transactions (id TEXT PRIMARY KEY, acct INTEGER, amount INTEGER, description TEXT, date TEXT, FOREIGN KEY(acct) REFERENCES accounts(id))",
        "CREATE TABLE preferences (id TEXT PRIMARY KEY, value TEXT)",
        "CREATE TABLE reflect_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER DEFAULT 0, carryover INTEGER DEFAULT 0, goal INTEGER DEFAULT NULL, long_goal INTEGER DEFAULT NULL)",
        "CREATE TABLE rules (id TEXT PRIMARY KEY, stage TEXT, conditions TEXT, actions TEXT, tombstone INTEGER DEFAULT 0, conditions_op TEXT DEFAULT 'and')",
        "CREATE TABLE schedules (id TEXT PRIMARY KEY, rule TEXT, active INTEGER DEFAULT 0, completed INTEGER DEFAULT 0, posts_transaction INTEGER DEFAULT 0, tombstone INTEGER DEFAULT 0, name TEXT DEFAULT NULL, custom_upcoming_length TEXT DEFAULT NULL)",
        "CREATE TABLE schedules_json_paths (schedule_id TEXT PRIMARY KEY, payee TEXT, account TEXT, amount TEXT, date TEXT)",
        "CREATE TABLE schedules_next_date (id TEXT PRIMARY KEY, schedule_id TEXT, local_next_date INTEGER, local_next_date_ts INTEGER, base_next_date INTEGER, base_next_date_ts INTEGER, tombstone INTEGER DEFAULT 0)",
        "CREATE TABLE tags (id TEXT PRIMARY KEY, tag TEXT UNIQUE, color TEXT, description TEXT, tombstone INTEGER DEFAULT 0, hidden BOOLEAN DEFAULT 0)",
        "CREATE TABLE transaction_filters (id TEXT PRIMARY KEY, name TEXT, conditions TEXT, conditions_op TEXT DEFAULT 'and', tombstone INTEGER DEFAULT 0)",
        "CREATE TABLE transactions (id TEXT PRIMARY KEY, isParent INTEGER DEFAULT 0, isChild INTEGER DEFAULT 0, acct TEXT, category TEXT, amount INTEGER, description TEXT, notes TEXT, date INTEGER, financial_id TEXT, type TEXT, location TEXT, error TEXT, imported_description TEXT, starting_balance_flag INTEGER DEFAULT 0, transferred_id TEXT, sort_order REAL, tombstone INTEGER DEFAULT 0, cleared INTEGER DEFAULT 1, pending INTEGER DEFAULT 0, parent_id TEXT, schedule TEXT, reconciled INTEGER DEFAULT 0, raw_synced_data TEXT)",
        "CREATE TABLE zero_budget_months (id TEXT PRIMARY KEY, buffered INTEGER DEFAULT 0)",
        "CREATE TABLE zero_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER DEFAULT 0, carryover INTEGER DEFAULT 0, goal INTEGER DEFAULT NULL, long_goal INTEGER DEFAULT NULL)",
        "CREATE INDEX trans_category_date ON transactions(category, date)",
        "CREATE INDEX trans_category ON transactions(category)",
        "CREATE INDEX trans_date ON transactions(date)",
        "CREATE INDEX trans_parent_id ON transactions(parent_id)",
        "CREATE INDEX trans_sorted ON transactions(date DESC, starting_balance_flag, sort_order DESC, id)",
        "CREATE INDEX messages_crdt_search ON messages_crdt(dataset, row, `column`, timestamp)",
        "CREATE INDEX idx_payee_locations_payee_id ON payee_locations(payee_id)",
        "CREATE INDEX idx_payee_locations_tombstone_payee_created ON payee_locations(tombstone, payee_id, created_at)",
        "CREATE INDEX idx_payee_locations_geo_tombstone ON payee_locations(tombstone, latitude, longitude)",
        "CREATE INDEX idx_transactions_acct_tombstone ON transactions(acct, tombstone)",
        "CREATE INDEX idx_transactions_schedule ON transactions(schedule)",
    )

    private val MIGRATIONS = longArrayOf(
        1548957970627,1550601598648,1555786194328,1561751833510,1567699552727,1582384163573,
        1597756566448,1608652596043,1608652596044,1612625548236,1614782639336,1615745967948,
        1616167010796,1618975177358,1632571489012,1679728867040,1681115033845,1682974838138,
        1685007876842,1686139660866,1688749527273,1688841238000,1691233396000,1694438752000,
        1697046240000,1704572023730,1704572023731,1707267033000,1712784523000,1716359441000,
        1720310586000,1720664867241,1720665000000,1722717601000,1722804019000,1723665565000,
        1730744182000,1736640000000,1737158400000,1738491452000,1739139550000,1740506588539,
        1745425408000,1749799110000,1749799110001,1754611200000,1759260219000,1759842823172,
        1762178745667,1765518577215,1768872504000,1769000000000,1778510362740,1780099200000,
        1780327681000,1780606215000,1780606215001,
    )

    private val STARTER_DATA = listOf(
        "INSERT INTO category_groups VALUES ('fc3825fd-b982-4b72-b768-5b30844cf832','Usual Expenses',0,16384,0,0)",
        "INSERT INTO category_groups VALUES ('2E1F5BDB-209B-43F9-AF2C-3CE28E380C00','Income',1,32768,0,0)",
        "INSERT INTO category_groups VALUES ('a137772f-cf2f-4089-9432-822d2ddc1466','Investments and Savings',0,32768,0,0)",
        "INSERT INTO categories VALUES ('541836f1-e756-4473-a5d0-6c1d3f06c7fa','Food',0,'fc3825fd-b982-4b72-b768-5b30844cf832',16384,0,0,NULL,'{\"source\": \"notes\"}',NULL)",
        "INSERT INTO categories VALUES ('af375fd4-d759-46b3-bffe-74a856151d57','General',0,'fc3825fd-b982-4b72-b768-5b30844cf832',32768,0,0,NULL,'{\"source\": \"notes\"}',NULL)",
        "INSERT INTO categories VALUES ('d4b0f075-3343-4408-91ed-fae94f74e5bf','Bills',0,'fc3825fd-b982-4b72-b768-5b30844cf832',49152,0,0,NULL,'{\"source\": \"notes\"}',NULL)",
        "INSERT INTO categories VALUES ('29ec2c58-8cd3-42fe-9187-7b5dfe0f70a6','Bills (Flexible)',0,'fc3825fd-b982-4b72-b768-5b30844cf832',65536,0,0,NULL,'{\"source\": \"notes\"}',NULL)",
        "INSERT INTO categories VALUES ('3c1699a5-522a-435e-86dc-93d900a14f0e','Income',1,'2E1F5BDB-209B-43F9-AF2C-3CE28E380C00',32768,0,0,NULL,'{\"source\": \"notes\"}',NULL)",
        "INSERT INTO categories VALUES ('506e8d9d-7ed0-4397-84e4-07a9185dc6b2','Starting Balances',1,'2E1F5BDB-209B-43F9-AF2C-3CE28E380C00',32768,0,0,NULL,'{\"source\": \"notes\"}',NULL)",
        "INSERT INTO categories VALUES ('6bbd8472-25d4-4cee-8a11-5bd9f7e83d61','Savings',0,'a137772f-cf2f-4089-9432-822d2ddc1466',32768,0,0,NULL,'{\"source\": \"notes\"}',NULL)",
        "INSERT INTO category_mapping SELECT id,id FROM categories",
    )
}
