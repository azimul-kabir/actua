package com.azimulkabir.actua.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ActuaDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE account_groups (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                sort_order INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                balance INTEGER NOT NULL DEFAULT 0,
                type TEXT NOT NULL,
                off_budget INTEGER NOT NULL DEFAULT 0,
                closed INTEGER NOT NULL DEFAULT 0,
                sort_order INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE category_groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                hidden INTEGER NOT NULL DEFAULT 0,
                sort_order INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id INTEGER NOT NULL REFERENCES category_groups(id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                assigned INTEGER NOT NULL DEFAULT 0,
                spent INTEGER NOT NULL DEFAULT 0,
                hidden INTEGER NOT NULL DEFAULT 0,
                sort_order INTEGER NOT NULL DEFAULT 0,
                UNIQUE(group_id, name)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE transactions (
                id TEXT PRIMARY KEY,
                date_key TEXT NOT NULL,
                payee TEXT NOT NULL,
                category TEXT NOT NULL,
                account TEXT NOT NULL,
                amount INTEGER NOT NULL,
                cleared INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX transactions_date_idx ON transactions(date_key, created_at DESC)")
        db.execSQL("CREATE INDEX transactions_account_idx ON transactions(account)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun seed(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            val groups = listOf(
                "Monthly bills" to listOf(
                    Triple("Rent", 35_000, 35_000), Triple("Electricity", 3_500, 2_700),
                    Triple("Internet", 1_500, 1_500), Triple("Mobile phone", 1_000, 720),
                ),
                "Daily spending" to listOf(
                    Triple("Groceries", 8_000, 4_760), Triple("Dining", 4_000, 1_900),
                    Triple("Transport", 5_000, 4_100), Triple("Household", 2_500, 850),
                ),
                "Quality of life" to listOf(
                    Triple("Health & fitness", 3_000, 1_250), Triple("Entertainment", 2_500, 2_800),
                    Triple("Personal care", 2_000, 620),
                ),
                "Savings goals" to listOf(
                    Triple("Emergency fund", 10_000, 0), Triple("Travel", 6_000, 0),
                ),
            )
            groups.forEachIndexed { groupIndex, (groupName, categories) ->
                db.execSQL(
                    "INSERT INTO category_groups(name, sort_order) VALUES(?, ?)",
                    arrayOf<Any>(groupName, groupIndex),
                )
                val groupId = db.compileStatement("SELECT id FROM category_groups WHERE name = ?").run {
                    bindString(1, groupName)
                    simpleQueryForLong()
                }
                categories.forEachIndexed { index, category ->
                    db.execSQL(
                        "INSERT INTO categories(group_id, name, assigned, spent, sort_order) VALUES(?, ?, ?, ?, ?)",
                        arrayOf<Any>(groupId, category.first, category.second, category.third, index),
                    )
                }
            }

            listOf(
                arrayOf<Any>("Everyday account", 48_250, "Bank", 0, 0, 0),
                arrayOf<Any>("Cash", 3_400, "Cash", 0, 0, 1),
                arrayOf<Any>("Savings", 86_500, "Savings", 0, 0, 2),
                arrayOf<Any>("Credit card", -12_780, "Credit", 0, 0, 3),
                arrayOf<Any>("Investment account", 125_000, "Investment", 1, 0, 4),
                arrayOf<Any>("Motorbike loan", -65_000, "Loan", 1, 0, 5),
                arrayOf<Any>("Old bank account", 0, "Bank", 0, 1, 6),
            ).forEach {
                db.execSQL(
                    "INSERT INTO accounts(name, balance, type, off_budget, closed, sort_order) VALUES(?, ?, ?, ?, ?, ?)",
                    it,
                )
            }

            listOf(
                arrayOf<Any>("1", "Today", "Agora Super Shop", "Groceries", "Everyday account", -2450, 1),
                arrayOf<Any>("2", "Today", "Salary", "Income", "Everyday account", 72000, 1),
                arrayOf<Any>("3", "Today", "Pathao", "Transport", "Credit card", -380, 0),
                arrayOf<Any>("4", "Yesterday", "DESCO", "Electricity", "Everyday account", -2700, 1),
                arrayOf<Any>("5", "Yesterday", "Coffee World", "Dining", "Credit card", -620, 0),
                arrayOf<Any>("6", "1 Sep 2026", "Landlord", "Rent", "Everyday account", -35000, 1),
                arrayOf<Any>("7", "1 Sep 2026", "ISP", "Internet", "Everyday account", -1500, 1),
            ).forEachIndexed { index, values ->
                db.execSQL(
                    "INSERT INTO transactions(id, date_key, payee, category, account, amount, cleared, created_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
                    values + (System.currentTimeMillis() - index),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        private const val DATABASE_NAME = "actua.db"
        private const val DATABASE_VERSION = 1
    }
}
