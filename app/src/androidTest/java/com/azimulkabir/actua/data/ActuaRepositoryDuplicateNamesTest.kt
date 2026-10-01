package com.azimulkabir.actua.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import com.azimulkabir.actua.model.asTransferDraft
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Actual allows duplicate account names and one category name in several groups (#747). */
class ActuaRepositoryDuplicateNamesTest {
    @Test
    fun savingAndBulkCategorizingUseThePickedCategoryAndAccount() = withRepository { repository, rows ->
        repository.saveTransaction(Transaction(
            id = "", date = "2026-09-10", payee = "Shop", category = "Misc", account = "Checking",
            amount = -12, cleared = false, amountCents = -1_200,
            accountId = "checking-b", categoryId = "home-misc", categoryIsExplicit = true,
        ))
        val saved = rows().single()
        assertEquals(Row(saved.id, "checking-b", "home-misc", -1_200), saved)

        // Bulk Categorize saves the list row with only the category changed.
        val listed = repository.reportTransactions(listOf(saved.id)).single()
        assertEquals("checking-b" to "home-misc", listed.accountId to listed.categoryId)
        repository.saveTransaction(
            listed.copy(category = "Misc", categoryId = "food-misc", categoryIsExplicit = true).asTransferDraft(),
        )
        assertEquals(Row(saved.id, "checking-b", "food-misc", -1_200), rows().single())
    }

    @Test
    fun aTransferGoesToThePickedAccount() = withRepository { repository, rows ->
        repository.saveTransaction(Transaction(
            id = "", date = "2026-09-10", payee = "", category = "", account = "Savings",
            amount = -25, cleared = false, amountCents = -2_500, type = Type.TRANSFER,
            transferAccount = "Checking", accountId = "savings", transferAccountId = "checking-b",
        ))
        assertEquals(
            listOf("checking-b" to 2_500L, "savings" to -2_500L),
            rows().map { it.account to it.amount },
        )
    }

    @Test
    fun aDraftWithoutIdsStillSavesByName() = withRepository { repository, rows ->
        repository.saveTransaction(Transaction(
            id = "", date = "2026-09-10", payee = "Shop", category = "Groceries", account = "Savings",
            amount = -5, cleared = false, amountCents = -500, categoryIsExplicit = true,
        ))
        assertEquals(listOf("savings" to "groceries"), rows().map { it.account to it.category })
    }

    private data class Row(val id: String, val account: String, val category: String?, val amount: Long)

    private fun withRepository(block: (ActuaRepository, () -> List<Row>) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Duplicate names ${UUID.randomUUID()}")
        val previousBudget = ActiveBudgetStore(context).budgetId
        val path = files.databaseFile(budget.id).path
        try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    """
                        INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type)
                        VALUES ('checking-a', 'Checking', 0, 0, 0, 1, 'checking'),
                               ('checking-b', 'Checking', 0, 0, 0, 2, 'checking'),
                               ('savings', 'Savings', 0, 0, 0, 3, 'savings')
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                        INSERT INTO payees (id, name, transfer_acct, tombstone)
                        VALUES ('to-checking-a', '', 'checking-a', 0), ('to-checking-b', '', 'checking-b', 0),
                               ('to-savings', '', 'savings', 0)
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO payee_mapping (id, targetId) VALUES ('to-checking-a', 'to-checking-a'), " +
                        "('to-checking-b', 'to-checking-b'), ('to-savings', 'to-savings')",
                )
                db.execSQL(
                    "INSERT INTO category_groups (id, name, is_income, sort_order, tombstone, hidden) " +
                        "VALUES ('food', 'Food', 0, 1, 0, 0), ('home', 'Home', 0, 2, 0, 0)",
                )
                db.execSQL(
                    "INSERT INTO categories (id, name, is_income, cat_group, sort_order, tombstone, hidden) " +
                        "VALUES ('food-misc', 'Misc', 0, 'food', 1, 0, 0), ('groceries', 'Groceries', 0, 'food', 2, 0, 0), " +
                        "('home-misc', 'Misc', 0, 'home', 1, 0, 0)",
                )
                db.execSQL(
                    "INSERT INTO category_mapping (id, transferId) VALUES ('food-misc', 'food-misc'), " +
                        "('groceries', 'groceries'), ('home-misc', 'home-misc')",
                )
            }
            ActiveBudgetStore(context).budgetId = budget.id
            val repository = ActuaRepository(context)
            try {
                block(repository) {
                    SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        db.rawQuery(
                            "SELECT id, acct, category, amount FROM transactions WHERE tombstone = 0 ORDER BY acct, date, id",
                            null,
                        ).use { cursor ->
                            buildList {
                                while (cursor.moveToNext()) {
                                    add(Row(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3)))
                                }
                            }
                        }
                    }
                }
            } finally {
                repository.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = previousBudget
            runCatching { files.deleteBudget(budget.id) }
        }
    }
}
